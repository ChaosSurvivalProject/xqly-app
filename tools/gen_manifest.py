#!/usr/bin/env python3
"""
生成 / 校验 assets/MANIFEST.json —— 内置内容清单（签名清单 → 文件哈希 → 校验）。

与 cn.xqly.app.core.ContentManifest.verify 的约束一一对应：
  - schema=1，version 严格单调递增（防止被判定为版本回退）
  - 每个条目含 p / sha256 / size，路径必须通过 UrlPolicy.safeAssetPath 等价校验
  - 清单自身不纳入清单，避免自引用循环

用法：
  python3 tools/gen_manifest.py            # 内容变化时重新生成；未变化则保持不动
  python3 tools/gen_manifest.py --check    # 只校验，不写文件；不一致返回 1
"""
import argparse
import hashlib
import json
import os
import sys
import time

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
ASSETS = os.path.join(ROOT, "assets")
MANIFEST = os.path.join(ASSETS, "MANIFEST.json")


def safe_asset_path(p):
    """与 UrlPolicy.safeAssetPath 等价：防 ZIP 路径穿越。"""
    if not p or len(p) > 512:
        return False
    if p.startswith("/") or p.startswith("\\") or ".." in p or "\\" in p or "\0" in p:
        return False
    if len(p) >= 2 and p[1] == ":" and p[0].isalpha():
        return False
    return all(ord(c) >= 0x20 for c in p)


def collect():
    out = []
    for base, dirs, files in os.walk(ASSETS):
        dirs.sort()
        files.sort()
        for name in files:
            full = os.path.join(base, name)
            rel = os.path.relpath(full, ASSETS).replace(os.sep, "/")
            if rel == "MANIFEST.json":
                continue
            if not safe_asset_path(rel):
                raise SystemExit("不安全资产路径: %r" % rel)
            with open(full, "rb") as f:
                data = f.read()
            out.append({
                "p": rel,
                "sha256": hashlib.sha256(data).hexdigest(),
                "size": len(data),
            })
    return out


def key_map(files):
    return {e["p"]: (e["sha256"], e["size"]) for e in files}


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--check", action="store_true", help="只校验，不写文件")
    args = ap.parse_args()

    files = collect()
    if not files:
        raise SystemExit("assets/ 为空，拒绝生成空清单")

    old = None
    if os.path.exists(MANIFEST):
        try:
            with open(MANIFEST, encoding="utf-8") as f:
                old = json.load(f)
        except Exception:
            old = None

    unchanged = old is not None and key_map(old.get("files", [])) == key_map(files)
    if unchanged:
        if args.check:
            print("MANIFEST.json 校验通过：%d 个文件，version=%s"
                  % (len(files), old.get("version")))
            return 0
        print("内容未变化，保留现有 MANIFEST.json（version=%s）" % old.get("version"))
        return 0

    if args.check:
        print("MANIFEST.json 与 assets 不一致，需要重新生成", file=sys.stderr)
        return 1

    old_version = int(old.get("version", 0)) if old else 0
    # 严格单调递增：即使构建时钟回拨，也不会触发客户端的 ROLLBACK 拒绝
    version = max(int(time.time()), old_version + 1)
    doc = {"schema": 1, "version": version, "files": files}
    with open(MANIFEST, "w", encoding="utf-8") as f:
        json.dump(doc, f, ensure_ascii=False, indent=2)
        f.write("\n")

    total = sum(e["size"] for e in files)
    print("已生成 MANIFEST.json：%d 个文件，%.2f MB，version=%d"
          % (len(files), total / 1048576.0, version))
    return 0


if __name__ == "__main__":
    sys.exit(main())
