#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""可选脱敏：清除镜像正文中原站的生产运维细节（服务器路径、内网 IP、联系方式等）。

默认只预览不改动；确认无误后加 --apply 才会就地修改。
注意：就地修改不会生成备份文件，避免敏感内容残留。

用法：
    python3 tools/redact.py                 # 预览命中数量与样例
    python3 tools/redact.py --apply        # 执行脱敏
"""
import os
import re
import sys
import argparse

PROJECT = os.environ.get(
    "XQLY_PROJECT",
    os.path.dirname(os.path.dirname(os.path.abspath(__file__))),
)
TARGETS = [
    os.path.join(PROJECT, "assets", "data"),
    os.path.join(PROJECT, "assets", "archive.html"),
    os.path.join(PROJECT, "docs", "全站内容档案.html"),
]
RULES = [
    ("服务器绝对路径", re.compile(
        r'/(?:root|home|var|etc|opt|srv|data|usr/local)/[A-Za-z0-9_./\-]{2,}'), "[PATH]"),
    ("内网 IP", re.compile(
        r'\b(?:10|172\.(?:1[6-9]|2\d|3[01])|192\.168)\.\d{1,3}\.\d{1,3}\.\d{1,3}\b'), "[IP]"),
    ("邮箱", re.compile(r'[A-Za-z0-9._%+\-]+@[A-Za-z0-9.\-]+\.[A-Za-z]{2,}'), "[EMAIL]"),
    ("手机号", re.compile(r'(?<!\d)1[3-9]\d{9}(?!\d)'), "[PHONE]"),
    ("备份文件名", re.compile(
        r'\b[A-Za-z0-9_.\-]+\.bak[-_][A-Za-z0-9_.\-]+'), "[BACKUP]"),
]


def collect():
    out = []
    for t in TARGETS:
        if os.path.isfile(t):
            out.append(t)
        elif os.path.isdir(t):
            for dp, _, fs in os.walk(t):
                for f in fs:
                    if f.endswith((".json", ".html", ".txt", ".md")):
                        out.append(os.path.join(dp, f))
    return sorted(out)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--apply", action="store_true", help="真正写入（默认仅预览）")
    a = ap.parse_args()
    files = collect()
    total = {name: 0 for name, _, _ in RULES}
    samples = {name: [] for name, _, _ in RULES}

    for path in files:
        try:
            src = open(path, encoding="utf-8").read()
        except (UnicodeDecodeError, OSError):
            continue
        text = src
        for name, pat, repl in RULES:
            hits = pat.findall(text)
            if hits:
                total[name] += len(hits)
                for h in hits[:2]:
                    h = h if isinstance(h, str) else str(h)
                    if len(samples[name]) < 4:
                        samples[name].append((os.path.relpath(path, PROJECT), h[:110]))
                text = pat.sub(repl, text)
        if a.apply and text != src:
            with open(path, "w", encoding="utf-8") as f:
                f.write(text)

    mode = "已执行脱敏" if a.apply else "预览模式（未修改任何文件，加 --apply 生效）"
    print(f"=== {mode} ===")
    print(f"扫描文件: {len(files)}")
    for name, _, _ in RULES:
        print(f"  {name:<14} {total[name]:>4} 处")
        for f, s in samples[name][:2]:
            print(f"      {f}: {s}")
    if not a.apply and sum(total.values()):
        print("\n提示：assets/archive.html 与 docs/全站内容档案.html 也包含正文，")
        print("      如需同步更新 APK，请重新执行 tools/mirror.py 之后重新构建。")


if __name__ == "__main__":
    main()
