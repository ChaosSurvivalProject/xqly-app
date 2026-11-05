/// 星穹旅驿 · Windows + Android 双平台共享业务核心。
///
/// 纯 Dart、零 Flutter 依赖、零第三方依赖。Windows 外壳与 Android 客户端
/// 通过版本化平台通道调用这里的规则；领域逻辑不触碰文件系统、网络实现或 UI。
///
/// 用法：
/// ```dart
/// import 'package:xqly_core/xqly_core.dart';
/// final v = UpdateVerdict.parseRelease(json);
/// ```
library xqly_core;

export 'src/app_state.dart';
export 'src/bridge_protocol.dart';
export 'src/content_manifest.dart';
export 'src/design_tokens.dart';
export 'src/feature_flags.dart';
export 'src/foundation.dart';
export 'src/game_rules.dart';
export 'src/identity.dart';
export 'src/search_index.dart';
export 'src/sha256.dart';
export 'src/sync_merge.dart';
export 'src/sync_package.dart';
export 'src/sync_transport.dart';
export 'src/update_verdict.dart';
export 'src/url_policy.dart';
export 'src/version_policy.dart';
