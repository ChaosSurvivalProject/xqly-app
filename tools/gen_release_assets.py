#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""从真实产物目录生成 SHA256SUMS.txt 与 SBOM.spdx.json（SPDX 2.3）。

只扫描目录里实际存在的文件；不创建占位假文件，不虚构 Windows 产物。
用法：
    python3 tools/gen_release_assets.py <release_dir> <version>

本机（Linux）运行只会包含真实存在的资产（Android APK）；Windows 安装版与
便携 ZIP 由 .github/workflows/release.yml 在 windows-latest 上生成后一并纳入。
"""
import hashlib
import json
import os
import sys
import time
from datetime import datetime, timezone

# 真实组件清单：运行时零第三方库，构建期工具与可选系统组件如实列出。
COMPONENTS = [
    ("xqly_core (Dart shared)", "2.0.1", "MIT", "Organization: ChaosSurvivalProject",
     "纯 Dart 双平台共享业务核心，零第三方依赖"),
    ("xqly-android (native Java + WebView)", "2.0.1", "MIT",
     "Organization: ChaosSurvivalProject", "零 Gradle 构建链，运行时无第三方库"),
    ("xqly-windows-core (C++17)", "2.0.1", "MIT",
     "Organization: ChaosSurvivalProject", "平台无关核心，真实编译与测试"),
    ("xqly-windows-shell (Win32)", "2.0.1", "MIT",
     "Organization: ChaosSurvivalProject", "原生 Win32 外壳"),
    ("Android SDK build-tools", "35.0.0", "LicenseRef-Android-SDK",
     "Organization: Google", "仅构建期，不随包分发"),
    ("Android platform", "android-35", "LicenseRef-Android-SDK",
     "Organization: Google", "仅构建期，不随包分发"),
    ("OpenJDK / javac", "17", "GPL-2.0-only WITH Classpath-exception-2.0",
     "Organization: Eclipse Temurin", "仅构建期"),
    ("Dart SDK", "2.17.0", "BSD-3-Clause", "Organization: Google", "构建与测试期"),
    ("Node.js", "22", "MIT", "Organization: OpenJS Foundation", "仅游戏引擎测试"),
    ("Python", "3.11", "PSF-2.0", "Organization: Python Software Foundation",
     "仅索引/清单/图标/发布资产生成"),
    ("CMake", ">=3.15", "BSD-3-Clause", "Organization: Kitware", "仅构建期"),
    ("Visual Studio 2022 MSVC", "17.x", "LicenseRef-MSVC",
     "Organization: Microsoft", "仅 Windows 构建期"),
    ("Windows SDK (makeappx/signtool)", "10.x", "LicenseRef-Windows-SDK",
     "Organization: Microsoft", "仅 Windows 打包与签名期"),
    ("Android System WebView", "system", "LicenseRef-System-Component",
     "Organization: Google / OEM", "运行时系统组件，不随包分发"),
    ("WebView2 Runtime", "system-optional", "LicenseRef-System-Component",
     "Organization: Microsoft", "Windows 可选运行时，不静默下载"),
    ("Pillow", "preinstalled", "HPND", "Organization: Pillow contributors",
     "仅构建期生成自绘图标，不随包分发"),
]

SKIP = {"SHA256SUMS.txt", "SBOM.spdx.json"}


def sha256(path):
    h = hashlib.sha256()
    with open(path, "rb") as f:
        for chunk in iter(lambda: f.read(1 << 20), b""):
            h.update(chunk)
    return h.hexdigest()


def main():
    if len(sys.argv) < 3:
        print(__doc__)
        return 2
    d, version = sys.argv[1], sys.argv[2]
    if not os.path.isdir(d):
        print("目录不存在: " + d, file=sys.stderr)
        return 1

    files = sorted(
        f for f in os.listdir(d)
        if os.path.isfile(os.path.join(d, f)) and f not in SKIP
    )

    sums = os.path.join(d, "SHA256SUMS.txt")
    with open(sums, "w", encoding="utf-8", newline="\n") as out:
        for f in files:
            out.write("{}  {}\n".format(sha256(os.path.join(d, f)), f))

    epoch = os.environ.get("SOURCE_DATE_EPOCH")
    created = (datetime.fromtimestamp(int(epoch), timezone.utc)
               if epoch else datetime.now(timezone.utc))
    created_str = created.strftime("%Y-%m-%dT%H:%M:%SZ")

    packages = [{
        "SPDXID": "SPDXRef-Package-xqly",
        "name": "星穹旅驿",
        "versionInfo": version,
        "downloadLocation": "NOASSERTION",
        "filesAnalyzed": False,
        "licenseConcluded": "MIT",
        "licenseDeclared": "MIT",
        "copyrightText": "Copyright (C) 2026 星穹旅驿运营团队",
        "supplier": "Organization: ChaosSurvivalProject",
        "comment": "客户端自研代码以 MIT 开源；官网文字/图片/商标/站点素材不在 MIT 授权范围内，见 NOTICE.md",
    }]
    for i, (name, ver, lic, supplier, comment) in enumerate(COMPONENTS):
        packages.append({
            "SPDXID": "SPDXRef-Package-component-{}".format(i + 1),
            "name": name,
            "versionInfo": ver,
            "downloadLocation": "NOASSERTION",
            "filesAnalyzed": False,
            "licenseConcluded": lic,
            "licenseDeclared": lic,
            "supplier": supplier,
            "comment": comment,
        })

    spdx_files = []
    for i, f in enumerate(files):
        spdx_files.append({
            "SPDXID": "SPDXRef-File-{}".format(i + 1),
            "fileName": "./" + f,
            "checksums": [{
                "algorithm": "SHA256",
                "checksumValue": sha256(os.path.join(d, f)),
            }],
            "licenseConcluded": "NOASSERTION",
            "copyrightText": "NOASSERTION",
            "fileTypes": ["BINARY"],
        })

    sbom = {
        "spdxVersion": "SPDX-2.3",
        "dataLicense": "CC0-1.0",
        "SPDXID": "SPDXRef-DOCUMENT",
        "name": "xqly-{}".format(version),
        "documentNamespace": "https://github.com/ChaosSurvivalProject/xqly-app/spdx/{}".format(version),
        "creationInfo": {
            "created": created_str,
            "creators": ["Tool: gen_release_assets.py", "Organization: ChaosSurvivalProject"],
            "licenseListVersion": "3.21",
        },
        "documentDescribes": ["SPDXRef-Package-xqly"],
        "packages": packages,
        "files": spdx_files,
        "relationships": [
            {
                "spdxElementId": "SPDXRef-DOCUMENT",
                "relationshipType": "DESCRIBES",
                "relatedSpdxElement": "SPDXRef-Package-xqly",
            }
        ] + [
            {
                "spdxElementId": "SPDXRef-Package-xqly",
                "relationshipType": "CONTAINS",
                "relatedSpdxElement": "SPDXRef-File-{}".format(i + 1),
            }
            for i in range(len(files))
        ],
        "hasExtractedLicensingInfos": [],
        "comment": "仅列出真实存在的产物与真实使用的组件；无虚构服务器、AI Key 或第三方运行时库。",
    }

    with open(os.path.join(d, "SBOM.spdx.json"), "w", encoding="utf-8", newline="\n") as f:
        json.dump(sbom, f, ensure_ascii=False, indent=2)
        f.write("\n")

    print("生成 {} 个资产的摘要与 SBOM：".format(len(files)))
    for f in files:
        print("  {}  {}".format(sha256(os.path.join(d, f))[:12], f))
    print("  SHA256SUMS.txt / SBOM.spdx.json（{} 个组件）".format(len(COMPONENTS)))
    return 0


if __name__ == "__main__":
    sys.exit(main())
