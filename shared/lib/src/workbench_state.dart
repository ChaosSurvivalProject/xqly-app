import 'apps.dart';
import 'workbench_chat.dart';
import 'workbench_log.dart';
import 'workbench_plugin.dart';
import 'workbench_provider.dart';
import 'workbench_selfcheck.dart';

/// AI 工作台聚合状态机（Dart 侧，纯逻辑、零 Android 依赖）。
///
/// 对标 harness 的会话/供应商/插件/投递区/自检/诊断交互，
/// 但**不内置运行时、不执行工具、不保存密钥、不发模型请求**。
class WorkbenchState {
  final String appId = AppRegistry.aiWorkbenchId;
  final ChatStore chats = ChatStore();
  final WorkbenchProvider providers = WorkbenchProvider();
  final WorkbenchPlugin plugins = WorkbenchPlugin();
  final WorkbenchLog log = WorkbenchLog();
  static const String offlineCapability = 'local-only';

  static const String intro =
      '本地 AI 工作台 · 会话与清单留在设备 · 不内置模型与运行时';

  /// 只暴露非敏感聚合状态；不含密钥、令牌、设备标识或文件路径。
  Map<String, Object?> snapshot({
    required bool manifestOk,
    required bool tokensOk,
    required bool bridgeOk,
    required bool signedOk,
    required bool offlineAssetsOk,
    int freeMb = 0,
  }) {
    final List<CheckItem> checks = WorkbenchSelfCheck.run(
      manifestOk: manifestOk,
      tokensOk: tokensOk,
      bridgeOk: bridgeOk,
      signedOk: signedOk,
      offlineAssetsOk: offlineAssetsOk,
      freeMb: freeMb,
    );
    return <String, Object?>{
      'appId': appId,
      'bridge': AppRegistry.bridgeVersion,
      'offlineCapability': offlineCapability,
      'intro': intro,
      'canExecute': false,
      'canCallModel': false,
      'telemetry': false,
      'credentialStored': false,
      'sessionCount': chats.count,
      'pluginCount': plugins.count,
      'providerCount': WorkbenchProvider.catalog.length,
      'logSize': log.size,
      'selfcheck': WorkbenchSelfCheck.summary(checks),
    };
  }

  static Map<String, Object?> envInfo() => <String, Object?>{
        'appId': AppRegistry.aiWorkbenchId,
        'bridge': AppRegistry.bridgeVersion,
        'theme': 'dark',
        'language': 'zh-CN',
        'breakpoint': 'phone',
        'capability': offlineCapability,
        'canExecute': false,
        'canCallModel': false,
      };

  /// 离线空状态：写清现在没有内容 / 为什么 / 下一步。
  static Map<String, Object?> emptyState() => <String, Object?>{
        'title': '本地工作台已就绪，但不内置模型与运行时',
        'why': '本模块对标 AI 工作台的交互与本地数据管理，不含 PRoot / Ubuntu / Node，也不发起模型请求。',
        'next': '可记录本地会话草稿、登记供应商与插件清单、运行环境自检；模型推理请在你信任的外部服务完成。',
        'notFound': '未执行任何工具或模型调用',
        'action': '运行环境自检',
      };
}
