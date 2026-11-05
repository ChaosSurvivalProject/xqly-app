import 'sync_package.dart';

/// 可选云端传输接口定义（本次**不实现服务器、不写真实地址、不虚构凭据**）。
///
/// 与提示词 7.3 一致；云端默认关闭、界面显示「未配置」，
/// 没有服务器时本地同步包仍然完整可用。
class CloudEndpoints {
  CloudEndpoints._();

  static const String changes = '/v1/sync/changes';
  static const String item = '/v1/sync/items/{id}';
  static const String session = '/v1/sync/session';

  static const Map<String, String> methods = <String, String>{
    'GET  /v1/sync/changes?since=': '拉取增量变更',
    'POST /v1/sync/changes': '提交本地变更',
    'GET  /v1/sync/items/{id}': '读取单条密文',
    'DELETE /v1/sync/session': '结束会话',
  };

  static String itemPath(String id) => item.replaceFirst('{id}', id);
}

/// 云端配置。默认全部为空/关闭，避免任何隐式联网。
class CloudConfig {
  final bool enabled;
  final String baseUrl; // 允许为空；为空时 UI 显示「未配置」
  final String sessionId;
  final bool endToEndEncryption;

  const CloudConfig({
    this.enabled = false,
    this.baseUrl = '',
    this.sessionId = '',
    this.endToEndEncryption = true,
  });

  bool get configured =>
      enabled &&
      baseUrl.startsWith('https://') &&
      sessionId.isNotEmpty;

  /// 未配置时界面必须显示这个文案，不得显示虚假同步状态。
  String get statusLabel => configured ? '已配置（待连接）' : '未配置';

  Map<String, Object?> toJson() => <String, Object?>{
        'enabled': enabled,
        'baseUrl': baseUrl,
        'sessionId': sessionId.isEmpty ? '' : '[REDACTED]',
        'endToEndEncryption': endToEndEncryption,
      };
}

/// 传输抽象。本地优先实现走文件 / 二维码 / 局域网发现，
/// 云端实现留待有真实服务与授权后补齐；业务层只依赖此接口。
abstract class SyncTransport {
  String get name;
  bool get available;

  /// 发送同步包；可取消。返回是否完整送达。
  Future<bool> send(SyncPackage pack, {void Function(double progress)? onProgress});

  /// 接收同步包；原始数据必须再经 `SyncPackage.decode` 校验。
  Future<String?> receive();

  /// 取消当前传输；取消后不得生成半份有效数据。
  void cancel();
}

/// 本地传输方式枚举（不含任何虚构服务器）。
class LocalTransport {
  LocalTransport._();

  static const List<String> methods = <String>[
    '文件（用户选择位置）',
    '二维码配对',
    '局域网设备发现（需用户确认信任）',
    'Wi-Fi 直连',
  ];

  /// 配对凭据短时有效，不得长期暴露。
  static const int pairingTtlSeconds = 120;

  static bool pairingExpired(int createdAtMillis, int nowMillis) =>
      nowMillis - createdAtMillis > pairingTtlSeconds * 1000;

  /// 局域网传输必须加密且经过明确的设备信任确认。
  static bool canTransfer({
    required bool encrypted,
    required bool deviceTrusted,
    required bool userConfirmed,
  }) =>
      encrypted && deviceTrusted && userConfirmed;
}
