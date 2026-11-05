#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""校验离线镜像：复刻 MainActivity.shouldInterceptRequest 的映射逻辑，
起本地 HTTP 服务后用无头浏览器遍历所有路由，检查能否完整渲染。

用法：python3 tools/verify_offline.py [--port 8899]
"""
import os
import re
import argparse
import threading
import mimetypes
from http.server import ThreadingHTTPServer, BaseHTTPRequestHandler
from urllib.parse import urlparse, unquote

PROJECT = os.environ.get(
    "XQLY_PROJECT",
    os.path.dirname(os.path.dirname(os.path.abspath(__file__))),
)
SITE = os.path.join(PROJECT, "assets", "site")
DATA = os.path.join(PROJECT, "assets", "data")
ROUTES = [
    "/", "/faction-beta", "/team", "/forum", "/forum/my", "/forum/recycle",
    "/forum/new", "/forum/post/1", "/forum/post/2", "/forum/post/3",
    "/login", "/register", "/announcements", "/kb", "/monitor",
    "/wiki/", "/admin/",
]
MIME = {
    "js": "application/javascript", "mjs": "application/javascript",
    "css": "text/css", "html": "text/html", "htm": "text/html",
    "json": "application/json", "woff2": "font/woff2", "woff": "font/woff",
    "ttf": "font/ttf", "svg": "image/svg+xml", "png": "image/png",
    "jpg": "image/jpeg", "jpeg": "image/jpeg", "webp": "image/webp",
    "gif": "image/gif", "ico": "image/x-icon", "mp4": "video/mp4",
}
stats = {"hit": 0, "api": 0, "fallback": 0, "miss": 0, "bad": []}


def mime_of(rel, is_api):
    ext = rel.rsplit(".", 1)[-1].lower() if "." in rel else ""
    if ext in MIME:
        return MIME[ext] + ("; charset=utf-8" if ext in
                            ("js", "css", "html", "json", "svg") else "")
    if is_api:
        return "application/json; charset=utf-8"
    # 无扩展名的 SPA 路由必须按 HTML 返回，否则会被浏览器当成下载
    return "text/html; charset=utf-8"


def api_snapshot(path, query):
    key = re.sub(r"^/api/", "", path)
    parts = key.split("/")
    if len(parts) >= 3 and parts[0] == "forum" and parts[1] == "articles" and parts[2].isdigit():
        f = os.path.join(DATA, "article_" + parts[2] + ".json")
        if os.path.isfile(f):
            return f
    norm = re.sub(r"[^a-z0-9]+", "_", ("api_" + key).lower())
    want = norm + ("_" + re.sub(r"[^a-z0-9]+", "_", query.lower()) if query else "")
    names = [f for f in os.listdir(DATA) if f.endswith(".json")] if os.path.isdir(DATA) else []
    for f in names:
        n = f[:-5].lower()
        if n in (norm, want):
            return os.path.join(DATA, f)
    for f in names:
        n = f[:-5].lower()
        if n.startswith(norm) or want.startswith(n):
            return os.path.join(DATA, f)
    return None


def resolve(path, query):
    rel = unquote(path).lstrip("/")
    is_api = rel.startswith("api/")
    if is_api:
        f = api_snapshot("/" + rel, query)
        if f:
            stats["api"] += 1
            return f
    candidates = [os.path.join(SITE, rel)]
    if rel.endswith("/"):
        candidates.append(os.path.join(SITE, rel, "index.html"))
    if "." not in os.path.basename(rel):
        if rel.startswith("wiki"):
            candidates.append(os.path.join(SITE, "wiki", "index.html"))
        elif rel.startswith("admin"):
            candidates.append(os.path.join(SITE, "admin", "index.html"))
        else:
            candidates.append(os.path.join(SITE, "index.html"))
    for f in candidates:
        if os.path.isfile(f):
            if f == candidates[0]:
                stats["hit"] += 1
            else:
                stats["fallback"] += 1
            return f
    stats["miss"] += 1
    return None


class Handler(BaseHTTPRequestHandler):
    def log_message(self, *a):
        pass

    def do_GET(self):
        u = urlparse(self.path)
        f = resolve(u.path, u.query)
        if not f:
            stats["bad"].append((u.path, 404))
            self.send_error(404)
            return
        try:
            body = open(f, "rb").read()
        except OSError:
            self.send_error(500)
            return
        rel = os.path.relpath(f, SITE if f.startswith(SITE) else DATA)
        self.send_response(200)
        self.send_header("Content-Type", mime_of(rel, u.path.startswith("/api/")))
        self.send_header("Content-Length", str(len(body)))
        self.send_header("Access-Control-Allow-Origin", "*")
        self.end_headers()
        self.wfile.write(body)

    def _ok_write(self):
        """模拟 MainActivity 对写请求返回空成功响应，避免 501 误报"""
        body = b'{"code":0,"data":{},"message":"ok"}'
        self.send_response(200)
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Content-Length", str(len(body)))
        self.send_header("Access-Control-Allow-Origin", "*")
        self.end_headers()
        self.wfile.write(body)

    do_POST = _ok_write
    do_PUT = _ok_write
    do_PATCH = _ok_write
    do_DELETE = _ok_write


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--port", type=int, default=8899)
    a = ap.parse_args()
    srv = ThreadingHTTPServer(("127.0.0.1", a.port), Handler)
    threading.Thread(target=srv.serve_forever, daemon=True).start()
    root = f"http://127.0.0.1:{a.port}"
    print(f"离线映射服务已启动: {root}\n")

    try:
        from playwright.sync_api import sync_playwright
    except ImportError:
        print("未安装 playwright，仅做文件级校验")
        print(f"  静态文件: {sum(len(fs) for _,_,fs in os.walk(SITE))}")
        return

    chromium = os.environ.get("CHROMIUM_PATH", "/usr/bin/chromium")
    with sync_playwright() as pw:
        try:
            b = pw.chromium.launch(headless=True, executable_path=chromium,
                                   args=["--no-sandbox", "--disable-dev-shm-usage"])
        except Exception:
            b = pw.chromium.launch(headless=True, args=["--no-sandbox"])
        ok = 0
        for route in ROUTES:
            pg = b.new_page(viewport={"width": 390, "height": 844})
            errs = []
            pg.on("response", lambda r: errs.append((r.url, r.status))
                  if r.status >= 400 else None)
            try:
                pg.goto(root + route, wait_until="domcontentloaded", timeout=20000)
                pg.wait_for_timeout(1400)
                n = len(pg.evaluate("()=>document.body.innerText||''"))
                good = n > 120 and not errs
                ok += bool(good)
                print(f"  {'OK ' if good else 'WARN'} {route:<22} 文本 {n:>5} 字"
                      + (f"  HTTP错误 {errs[:2]}" if errs else ""))
            except Exception as e:
                print(f"  FAIL {route:<22} {type(e).__name__}")
            finally:
                pg.close()
        b.close()
    srv.shutdown()
    print(f"\n渲染通过 {ok}/{len(ROUTES)}  |  映射统计: {stats}")


if __name__ == "__main__":
    main()
