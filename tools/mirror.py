#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""星穹旅驿 全站深度镜像（可移植复现版）

用法：
    python3 tools/mirror.py --all
    python3 tools/mirror.py --static --api --pages
    python3 tools/mirror.py --all --base https://xqly.xt91tv.shop

依赖：pip install requests playwright && playwright install chromium

产出（相对项目根）：
    assets/site/            静态资源镜像（供 WebView 离线加载）
    assets/data/            API JSON 快照
    assets/content_index.json / pages_text.json
"""
import os
import re
import sys
import json
import time
import argparse
import mimetypes
from urllib.parse import urljoin, urlparse, unquote
from concurrent.futures import ThreadPoolExecutor, as_completed

try:
    import requests
except ImportError:
    sys.exit("缺少 requests：pip install requests")

PROJECT = os.environ.get(
    "XQLY_PROJECT",
    os.path.dirname(os.path.dirname(os.path.abspath(__file__))),
)
BASE = "https://xqly.xt91tv.shop"
HOST = "xqly.xt91tv.shop"
SITE = os.path.join(PROJECT, "assets", "site")
DATA = os.path.join(PROJECT, "assets", "data")
UA = ("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
      "(KHTML, like Gecko) Chrome/120.0 Safari/537.36")

ROUTES = [
    "/", "/faction-beta", "/team", "/forum", "/forum/my", "/forum/recycle",
    "/forum/new", "/forum/post/1", "/forum/post/2", "/forum/post/3",
    "/login", "/register", "/announcements", "/kb", "/monitor",
    "/wiki/", "/admin/",
]
API_ENDPOINTS = [
    "/api/forum/articles?page=1&pageSize=50",
    "/api/forum/articles?page=2&pageSize=50",
    "/api/forum/articles?sort=hot&page=1&pageSize=50",
    "/api/forum/articles?category=guide&page=1&pageSize=50",
    "/api/forum/articles?category=home&page=1&pageSize=50",
    "/api/forum/articles/1", "/api/forum/articles/2", "/api/forum/articles/3",
    "/api/forum/articles/1/comments",
    "/api/forum/categories", "/api/forum/config", "/api/forum/stats",
    "/api/forum/tags/hot", "/api/forum/users/1/stats",
    "/api/kb/info", "/api/monitor/servers", "/api/staff/public/team",
]
ENTRIES = ["/", "/wiki/", "/admin/"]

ASSET_RE = re.compile(
    r'(?:["\'(]|\b)(?:/?assets/[A-Za-z0-9_.\-]+\.(?:js|mjs|css|png|jpe?g|webp|svg|'
    r'woff2?|ttf|eot|gif|ico|json|mp4|wasm))', re.I)
CHUNK_RE = re.compile(r'["\']\./([A-Za-z0-9_.\-]+\.(?:js|css))["\']')
UPLOAD_RE = re.compile(r'/api/announcement/uploads/[A-Za-z0-9_./\-]+\.(?:png|jpe?g|webp|gif)')


def log(*a):
    print(*a, flush=True)


def session():
    s = requests.Session()
    s.headers.update({"User-Agent": UA, "Accept-Language": "zh-CN,zh;q=0.9"})
    return s


def rel_of(url):
    """URL -> 镜像内的相对路径；区分 API 响应与 SPA 路由"""
    p = unquote(urlparse(url).path).lstrip("/")
    if not p:
        return "index.html"
    if p.endswith("/"):
        return p + "index.html"
    # API 响应按原路径落盘（离线拦截时会先命中 data/ 快照，这里是二层备份）
    if p.startswith("api/"):
        return p
    if "." not in os.path.basename(p):
        if p.startswith(("wiki", "admin")):
            return p + "/index.html"
        return "index.html"          # 主站 SPA history fallback
    return p


def save(rel, content):
    dst = os.path.join(SITE, rel)
    os.makedirs(os.path.dirname(dst), exist_ok=True)
    with open(dst, "wb") as f:
        f.write(content)
    return len(content)


def fetch(s, url, timeout=25):
    try:
        r = s.get(url, timeout=timeout, allow_redirects=True)
        if r.status_code == 200:
            return r.content
    except Exception as e:
        log(f"  ! {url} -> {type(e).__name__}")
    return None


def do_static(s, base):
    """递归抓静态资源：入口 HTML -> JS/CSS -> 动态 chunk -> 上传图片"""
    log("\n=== [1/4] 静态资源 ===")
    os.makedirs(SITE, exist_ok=True)
    os.makedirs(DATA, exist_ok=True)
    saved, failed, seen = {}, [], set()

    # 1. 入口 HTML
    pending = []
    for e in ENTRIES:
        url = urljoin(base, e)
        c = fetch(s, url)
        if c:
            rel = "index.html" if e == "/" else e.strip("/") + "/index.html"
            saved[rel] = save(rel, c)
            pending.append((url, c.decode("utf-8", "ignore"), rel))
            log(f"  入口 {e} -> {rel} ({len(c)} B)")
        else:
            failed.append(url)

    # 2. 从 HTML/JS/CSS 中提取资源引用，迭代直到收敛
    for round_no in range(1, 12):
        found = set()
        for parent_url, text, parent_rel in pending:
            for m in ASSET_RE.findall(text) + ["./" + x for x in CHUNK_RE.findall(text)]:
                u = urljoin(parent_url, m.lstrip("(\"'"))
                if urlparse(u).netloc and HOST not in urlparse(u).netloc:
                    continue
                found.add(u.split("?")[0])
            for m in UPLOAD_RE.findall(text):
                found.add(urljoin(base, m))
        new = [u for u in found if u not in seen]
        if not new:
            break
        seen.update(new)
        log(f"  第 {round_no} 轮：发现 {len(new)} 个新资源")
        next_pending = []
        with ThreadPoolExecutor(max_workers=12) as ex:
            futs = {ex.submit(fetch, s, u): u for u in new}
            for fu in as_completed(futs):
                u = futs[fu]
                c = fu.result()
                if not c:
                    failed.append(u)
                    continue
                rel = rel_of(u)
                saved[rel] = save(rel, c)
                if rel.endswith((".js", ".css", ".html", ".json")):
                    next_pending.append((u, c.decode("utf-8", "ignore"), rel))
        pending = next_pending
        if not pending:
            break

    log(f"  静态文件 {len(saved)} 个 / {sum(saved.values())/1024/1024:.2f} MB，失败 {len(failed)}")
    return saved, failed


def norm_name(path, query):
    key = re.sub(r"^/api/", "", path)
    norm = re.sub(r"[^A-Za-z0-9]+", "_", "api_" + key).lower()
    if query:
        norm += "_" + re.sub(r"[^A-Za-z0-9]+", "_", query).lower()
    return norm


def do_api(s, base):
    log("\n=== [2/4] API 快照 ===")
    ok = 0
    for ep in API_ENDPOINTS:
        path, _, query = ep.partition("?")
        url = urljoin(base, ep)
        c = fetch(s, url, 20)
        if not c:
            log(f"  ! {ep}")
            continue
        # 同时落到 site/api/...（供拦截映射）与 data/（供快照匹配）
        save(rel_of(url), c)
        name = norm_name(path, query)
        if re.match(r"^/api/forum/articles/\d+$", path):
            name = "article_" + path.rsplit("/", 1)[-1]
        with open(os.path.join(DATA, name + ".json"), "wb") as f:
            f.write(c)
        ok += 1
    log(f"  API 快照 {ok}/{len(API_ENDPOINTS)}")
    return ok


def do_pages(base):
    log("\n=== [3/4] 页面正文渲染 ===")
    try:
        from playwright.sync_api import sync_playwright
    except ImportError:
        log("  跳过（未安装 playwright）")
        return {}, []
    pages, failed = {}, []
    chromium = os.environ.get("CHROMIUM_PATH", "/usr/bin/chromium")

    with sync_playwright() as pw:
        try:
            b = pw.chromium.launch(headless=True, executable_path=chromium,
                                   args=["--no-sandbox", "--disable-dev-shm-usage"])
        except Exception:
            b = pw.chromium.launch(headless=True, args=["--no-sandbox"])
        ctx = b.new_context(viewport={"width": 390, "height": 844}, user_agent=UA)

        # 网络响应监听：把实际加载到的资源补落盘（兜底）
        def on_response(r):
            if HOST not in r.url or r.status != 200:
                return
            try:
                rel = rel_of(r.url)
                dst = os.path.join(SITE, rel)
                if not os.path.exists(dst):
                    body = r.body()
                    if body:
                        save(rel, body)
            except Exception:
                pass
        ctx.on("response", on_response)

        for route in ROUTES:
            pg = ctx.new_page()
            try:
                pg.goto(urljoin(base, route), wait_until="domcontentloaded", timeout=30000)
                for _ in range(8):
                    pg.wait_for_timeout(400)
                    if len(pg.evaluate("()=>document.body.innerText||''")) > 500:
                        break
                pages[route] = {
                    "title": pg.title(),
                    "text": pg.evaluate("()=>document.body.innerText||''"),
                }
                log(f"  {route:<22} {len(pages[route]['text']):>6} 字")
            except Exception as e:
                failed.append(route)
                log(f"  ! {route} -> {type(e).__name__}")
            finally:
                pg.close()
        b.close()

    with open(os.path.join(PROJECT, "assets", "pages_text.json"), "w", encoding="utf-8") as f:
        json.dump({k: v["text"] for k, v in pages.items()}, f, ensure_ascii=False, indent=2)
    log(f"  页面 {len(pages)}/{len(ROUTES)}，总字数 {sum(len(v['text']) for v in pages.values())}")
    return pages, failed


def do_index():
    files = [os.path.join(dp, f) for dp, _, fs in os.walk(SITE) for f in fs]
    data = [f for f in os.listdir(DATA) if f.endswith(".json")] if os.path.isdir(DATA) else []
    idx = {
        "source": BASE,
        "crawled_at": time.strftime("%Y-%m-%d %H:%M:%S"),
        "stats": {
            "static_files": len(files),
            "static_bytes": sum(os.path.getsize(f) for f in files),
            "api_snapshots": len(data),
        },
    }
    with open(os.path.join(PROJECT, "assets", "content_index.json"), "w", encoding="utf-8") as f:
        json.dump(idx, f, ensure_ascii=False, indent=2)
    log(f"\n=== 索引已写入 assets/content_index.json: {idx['stats']}")


def main():
    ap = argparse.ArgumentParser(description="星穹旅驿全站镜像工具")
    ap.add_argument("--all", action="store_true", help="执行全部步骤")
    ap.add_argument("--static", action="store_true")
    ap.add_argument("--api", action="store_true")
    ap.add_argument("--pages", action="store_true")
    ap.add_argument("--base", default=BASE)
    a = ap.parse_args()
    if not (a.all or a.static or a.api or a.pages):
        a.all = True

    global HOST
    HOST = urlparse(a.base).netloc or HOST
    s = session()
    log(f"目标: {a.base}   项目根: {PROJECT}")
    try:
        r = s.get(a.base, timeout=15)
        log(f"连通性: HTTP {r.status_code}")
    except Exception as e:
        log(f"!! 站点不可达：{e}")

    if a.all or a.static:
        do_static(s, a.base)
    if a.all or a.api:
        do_api(s, a.base)
    if a.all or a.pages:
        do_pages(a.base)
    do_index()
    log("\n=== 完成 ===")


if __name__ == "__main__":
    main()
