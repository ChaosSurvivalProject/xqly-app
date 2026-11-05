// 由「星穹玻璃」唯一真源生成双平台可消费的令牌文件。
//
//   cd shared && dart tool/gen_tokens.dart ..       # 生成
//   cd shared && dart tool/gen_tokens.dart .. --check   # 门禁：与已生成文件比对
//
// 产物：
//   shared/design-tokens.json                    # 供测试与文档读取
//   assets/design-tokens.css                     # Android WebView 页面
//   windows/runner/resources/design-tokens.json  # Windows 原生外壳自绘
import 'dart:io';

import 'package:xqly_core/xqly_core.dart';

Map<String, Object?> build() {
  final List<Object?> themes = <Object?>[
    for (final XTheme t in XTheme.values)
      for (final XMode m in XMode.values) DesignTokens.all(t, m),
  ];
  return <String, Object?>{
    'brand': DesignTokens.brandName,
    'version': AppIdentity.versionName,
    'generatedBy': 'shared/tool/gen_tokens.dart',
    'schema': 1,
    'themes': themes,
  };
}

void main(List<String> args) {
  final String root = args.isNotEmpty ? args[0] : '..';
  final bool check = args.contains('--check');
  final Map<String, Object?> payload = build();

  final String json = Json.encode(payload) + '\n';
  final String css = DesignTokens.toCss();

  final Map<String, String> outputs = <String, String>{
    '$root/shared/design-tokens.json': json,
    '$root/assets/design-tokens.css': css,
    '$root/windows/runner/resources/design-tokens.json': json,
  };

  int bad = 0;
  outputs.forEach((String path, String content) {
    final File f = File(path);
    if (check) {
      if (!f.existsSync()) {
        stdout.writeln('  缺少: $path');
        bad++;
      } else if (f.readAsStringSync() != content) {
        stdout.writeln('  内容与真源不一致: $path');
        bad++;
      } else {
        stdout.writeln('  OK  $path');
      }
    } else {
      f.parent.createSync(recursive: true);
      f.writeAsStringSync(content);
      stdout.writeln('  生成 $path (${content.length} bytes)');
    }
  });

  if (check && bad > 0) {
    stderr.writeln('设计令牌校验失败：$bad 个文件需要重新生成');
    exit(1);
  }
  stdout.writeln(check
      ? '设计令牌校验通过（${outputs.length} 个文件）'
      : '设计令牌生成完成（${outputs.length} 个文件，6 套主题映射）');
}
