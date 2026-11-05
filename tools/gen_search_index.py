#!/usr/bin/env python3
"""
从随 APK 内置的离线镜像生成可持久化的本地全文检索索引。

只使用真实存在的离线内容（article_*.json、论坛列表快照、API 快照、
路由页 HTML、Wiki 编译产物中的中文文本），不编造条目、不联网、不上传任何数据。

输出格式与 cn.xqly.app.core.SearchIndex.deserialize 一致：
  [{"id":"/forum/post/1","title":"...","body":"...","tags":[...],"favorite":false}, ...]
id 全部落在 UrlPolicy.SITE_PATH_PREFIXES 白名单内，可被 openPage 安全打开。

用法：
  python3 tools/gen_search_index.py           # 内容变化时重新生成
  python3 tools/gen_search_index.py --check  # 只校验
"""
import argparse
import glob
import json
import os
import re
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
DATA = os.path.join(ROOT, "assets", "data")
SITE = os.path.join(ROOT, "assets", "site")
OUT = os.path.join(DATA, "search-index.json")

ROUTES = [
    ("/", "首页", ["导航", "首页"]),
    ("/announcements", "公告", ["导航", "公告"]),
    ("/faction-beta", "阵营内测", ["导航", "指南"]),
    ("/forum", "玩家社区", ["导航", "社区"]),
    ("/kb", "知识库", ["导航", "指南"]),
    ("/monitor", "服务监控", ["导航", "监控"]),
    ("/team", "管理团队", ["导航", "指南"]),
    ("/wiki", "Wiki 知识库", ["导航", "Wiki"]),
    ("/login", "登录", ["导航", "账户"]),
    ("/register", "注册", ["导航", "账户"]),
]

# (快照文件, 归属路由, 标题, 标签)。归到真实路由下，命中后可直接打开对应页面。
SNAPSHOTS = [
    ("api_forum_categories.json", "/forum", "社区分类", ["社区", "分类", "指南"]),
    ("api_forum_tags_hot.json", "/forum", "热门标签", ["社区", "标签"]),
    ("api_forum_stats.json", "/forum", "社区数据概览", ["社区", "统计"]),
    ("api_forum_config.json", "/forum", "社区首页配置", ["社区", "设置"]),
    ("api_forum_articles_1_comments.json", "/forum", "帖子评论快照", ["社区", "评论"]),
    ("api_kb_info.json", "/kb", "知识库信息", ["指南", "知识库"]),
    ("api_monitor_servers.json", "/monitor", "服务监控列表", ["监控", "状态"]),
    ("api_staff_public_team.json", "/team", "管理团队名册", ["团队", "指南"]),
]


def clean(s, limit=4000):
    if s is None:
        return ""
    if not isinstance(s, str):
        s = json.dumps(s, ensure_ascii=False)
    s = re.sub(r"<[^>]+>", " ", s)
    s = re.sub(r"```.*?```", " ", s, flags=re.S)
    s = re.sub(r"[|#*`>]+", " ", s)
    s = re.sub(r"-{3,}", " ", s)
    s = re.sub(r"\s+", " ", s).strip()
    return s[:limit]


def jload(path):
    try:
        with open(path, encoding="utf-8") as f:
            return json.load(f)
    except Exception:
        return None


def article_docs():
    """文章详情优先（含 content），论坛列表快照补齐未被详情覆盖的条目。"""
    seen = {}
    for p in sorted(glob.glob(os.path.join(DATA, "article_*.json"))):
        a = (jload(p) or {}).get("data") or {}
        if not isinstance(a, dict) or not a.get("title"):
            continue
        ident = a.get("id")
        if ident is None:
            m = re.findall(r"\d+", os.path.basename(p))
            ident = m[-1] if m else os.path.basename(p)
        seen[str(ident)] = a

    for p in sorted(glob.glob(os.path.join(DATA, "api_forum_articles_*.json"))):
        d = (jload(p) or {}).get("data") or {}
        items = d.get("items") if isinstance(d, dict) else (d if isinstance(d, list) else [])
        if not isinstance(items, list):
            continue
        for a in items:
            if not isinstance(a, dict) or not a.get("title"):
                continue
            key = str(a.get("id"))
            if key not in seen:
                seen[key] = a

    out = []
    for ident, a in sorted(seen.items(), key=lambda kv: (len(kv[0]), kv[0])):
        tags = []
        cat = a.get("category")
        if isinstance(cat, dict) and cat.get("name"):
            tags.append(str(cat["name"]))
        for t in (a.get("tags") or []):
            if isinstance(t, dict) and t.get("name"):
                tags.append(str(t["name"]))
            elif isinstance(t, str):
                tags.append(t)
        out.append({
            "id": "/forum/post/" + ident,
            "title": clean(a.get("title"), 200),
            "body": clean(a.get("content") or a.get("summary") or ""),
            "tags": list(dict.fromkeys(tags))[:8] or ["社区", "公告"],
            "favorite": bool(a.get("favorited")),
        })
    return out


def route_docs():
    out = []
    for path, name, tags in ROUTES:
        rel = path.lstrip("/") or "index.html"
        parts = []
        for cand in (os.path.join(SITE, rel), os.path.join(SITE, rel, "index.html")):
            if os.path.isfile(cand):
                try:
                    with open(cand, encoding="utf-8", errors="ignore") as f:
                        parts.append(clean(f.read(), 800))
                except Exception:
                    pass
        body = " ".join(x for x in parts if x).strip()
        out.append({
            "id": path,
            "title": name,
            "body": (body + " " + name + " 离线镜像页面").strip()[:1200],
            "tags": tags,
            "favorite": False,
        })
    return out


def snapshot_docs():
    out = []
    for name, route, title, tags in SNAPSHOTS:
        path = os.path.join(DATA, name)
        if not os.path.isfile(path):
            continue
        raw = jload(path)
        if raw is None:
            continue
        # 只索引 API 返回的业务数据，去掉 code/message 等信封字段
        payload = raw.get("data") if isinstance(raw, dict) else raw
        out.append({
            "id": route,
            "title": title,
            "body": clean(json.dumps(payload, ensure_ascii=False), 2500),
            "tags": tags + ["离线快照"],
            "favorite": False,
        })
    return out


def wiki_doc():
    """把 Wiki 编译产物里的真实中文片段聚合成一条可检索的目录条目。"""
    fragments = []
    names = []
    for p in sorted(glob.glob(os.path.join(SITE, "wiki", "**", "*"), recursive=True)):
        if not os.path.isfile(p):
            continue
        rel = os.path.relpath(p, SITE).replace(os.sep, "/")
        names.append(rel)
        if ".md." not in rel or not rel.endswith(".js"):
            continue
        try:
            with open(p, encoding="utf-8", errors="ignore") as f:
                src = f.read(400000)
        except Exception:
            continue
        for m in re.findall(r"[\u4e00-\u9fff][\u4e00-\u9fff\w，。、：；！？（）\s]{10,160}", src):
            t = clean(m, 160)
            if len(t) >= 10:
                fragments.append(t)
    if not names:
        return []
    body = " ".join(dict.fromkeys(fragments))[:4000]
    toc = " ".join(os.path.basename(n) for n in names[:80])
    return [{
        "id": "/wiki",
        "title": "Wiki 知识库 · 离线目录",
        "body": (body + " 离线文件：" + toc)[:4500],
        "tags": ["Wiki", "指南", "离线快照"],
        "favorite": False,
    }]


def build_docs():
    docs = []
    by_id = {}
    for d in article_docs() + route_docs() + snapshot_docs() + wiki_doc():
        if not d.get("id") or not d.get("title"):
            continue
        if d["id"] in by_id:
            prev = by_id[d["id"]]
            prev["body"] = (prev["body"] + " " + d["body"])[:4500]
            prev["tags"] = list(dict.fromkeys(prev["tags"] + d["tags"]))[:8]
            continue
        by_id[d["id"]] = d
        docs.append(d)
    return docs


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--check", action="store_true")
    args = ap.parse_args()

    docs = build_docs()
    if len(docs) < 10:
        raise SystemExit("索引条目过少（%d），拒绝生成以免交付空搜索" % len(docs))
    text = json.dumps(docs, ensure_ascii=False, indent=2) + "\n"

    if args.check:
        try:
            with open(OUT, encoding="utf-8") as f:
                same = f.read() == text
        except Exception:
            same = False
        print(("search-index.json 校验通过" if same else "search-index.json 需要重新生成")
              + "：%d 条" % len(docs))
        return 0 if same else 1

    try:
        with open(OUT, encoding="utf-8") as f:
            if f.read() == text:
                print("内容未变化，保留现有 search-index.json：%d 条" % len(docs))
                return 0
    except Exception:
        pass
    with open(OUT, "w", encoding="utf-8") as f:
        f.write(text)
    print("已生成 search-index.json：%d 条，%.1f KB"
          % (len(docs), len(text.encode("utf-8")) / 1024.0))
    return 0


if __name__ == "__main__":
    sys.exit(main())
