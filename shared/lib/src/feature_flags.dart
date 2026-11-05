import 'foundation.dart';

/// 功能开关与 Kill Switch 定义。未知能力默认关闭。
class FlagDef {
  final String key;
  final bool defaultValue;
  final String description;

  const FlagDef(this.key, this.defaultValue, this.description);
}

class FeatureFlags {
  FeatureFlags._();

  static const List<FlagDef> definitions = <FlagDef>[
    FlagDef('online_enabled', true, '在线直连官方站点'),
    FlagDef('auto_update', true, '启动后延迟检查更新'),
    FlagDef('update_download', true, '允许下载更新包'),
    FlagDef('content_pack', true, '离线内容包中心'),
    FlagDef('bridge', true, '版本化 postMessage 桥'),
    FlagDef('game', true, '方块合成'),
    FlagDef('glass_anim', true, '玻璃流光动效'),
    FlagDef('haptics', true, '触感反馈'),
    FlagDef('daily_challenge', true, '每日挑战'),
    FlagDef('local_sync', true, '本地优先同步包'),
    FlagDef('cloud_sync', false, '云端同步（默认关闭，需配置）'),
    FlagDef('notifications', true, 'Windows 通知（默认不常驻托盘）'),
    FlagDef('autostart', false, '开机启动（默认关闭，需明确同意）'),
    FlagDef('minimize_to_tray', false, '最小化到托盘（默认关闭）'),
    FlagDef('scroll_compact', false, '滚动收拢（默认关闭，需可用性验证）'),
    FlagDef('sound', false, '音效（默认不打扰）'),
  ];

  static Map<String, bool> defaults() => <String, bool>{
        for (final FlagDef f in definitions) f.key: f.defaultValue,
      };

  /// 忽略未知键与非布尔值；异常一律回退默认值，不让坏配置关闭安全边界。
  static Map<String, bool> parse(String? json) {
    final Map<String, bool> out = defaults();
    if (json == null || json.isEmpty) return out;
    try {
      final Map<String, Object?> m = Json.object(json);
      for (final MapEntry<String, Object?> e in m.entries) {
        if (e.value is bool && out.containsKey(e.key)) {
          out[e.key] = e.value as bool;
        }
      }
    } catch (_) {
      return defaults();
    }
    return out;
  }

  static bool enabled(Map<String, bool>? flags, String key) {
    if (flags == null) {
      for (final FlagDef f in definitions) {
        if (f.key == key) return f.defaultValue;
      }
      return false;
    }
    return flags[key] ?? false;
  }

  /// 用户覆盖只允许把「开」关掉，不能绕过默认关闭的能力。
  static bool effective(Map<String, bool> flags, String key, String override) {
    if (!enabled(flags, key)) return false;
    return override != '0';
  }

  static String toJson(Map<String, bool> flags) => Json.encode(flags);
}
