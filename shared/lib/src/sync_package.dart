import 'foundation.dart';
import 'identity.dart';
import 'sha256.dart';

/// 允许进入同步包的业务类型。
/// 刻意**不含**缓存正文、离线镜像、窗口几何、多标签状态、凭据与日志。
class SyncKind {
  SyncKind._();

  static const String favorite = 'favorite';
  static const String tag = 'tag';
  static const String progress = 'progress';
  static const String bookmark = 'bookmark';
  static const String setting = 'setting';
  static const String gameSave = 'gameSave';
  static const String tombstone = 'tombstone';

  static const Set<String> all = <String>{
    favorite, tag, progress, bookmark, setting, gameSave, tombstone,
  };
}

/// 允许同步的设置项白名单。设备专属项（窗口位置、DPI、标签页）一律不同步。
class SyncSettings {
  SyncSettings._();

  static const Set<String> keys = <String>{
    'theme', 'mode', 'fontScale', 'lineSpacing', 'maxWidthDp',
    'haptics', 'sound', 'daily_challenge', 'reduce_motion',
  };
}

/// 逻辑时钟（Lamport + 设备 ID 稳定定序）。
///
/// 不依赖两台设备的系统时间判断先后：counter 相同用 deviceId 字典序兜底，
/// 得到全序，因此弱网、时钟漂移、跨时区下合并结果确定且可重放。
class LogicalClock {
  final int counter;
  final String deviceId;

  const LogicalClock(this.counter, this.deviceId);

  LogicalClock tick() => LogicalClock(counter + 1, deviceId);

  LogicalClock observe(LogicalClock other) =>
      LogicalClock((counter > other.counter ? counter : other.counter) + 1, deviceId);

  int compareTo(LogicalClock o) {
    final int c = counter.compareTo(o.counter);
    return c != 0 ? c : deviceId.compareTo(o.deviceId);
  }

  Map<String, Object?> toJson() => <String, Object?>{
        'counter': counter,
        'deviceId': deviceId,
      };

  static LogicalClock? fromJson(Object? raw) {
    if (raw is! Map) return null;
    final Map<String, Object?> m =
        raw.map((k, v) => MapEntry<String, Object?>(k.toString(), v));
    final int c = Json.integer(m, 'counter', -1);
    final String d = Json.str(m, 'deviceId', '');
    if (c < 0 || !validDeviceId(d)) return null;
    return LogicalClock(c, d);
  }
}

bool validDeviceId(String s) => RegExp(r'^[A-Za-z0-9_-]{8,64}$').hasMatch(s);
bool validItemId(String s) => RegExp(r'^[A-Za-z0-9_.:\-]{1,128}$').hasMatch(s);

class SyncItem {
  final String id;
  final String kind;
  final Map<String, Object?> payload;
  final int clock;
  final String deviceId;
  final bool deleted;

  const SyncItem(this.id, this.kind, this.payload, this.clock, this.deviceId,
      {this.deleted = false});

  LogicalClock get logicalClock => LogicalClock(clock, deviceId);

  Map<String, Object?> toJson() => <String, Object?>{
        'id': id,
        'kind': kind,
        'payload': payload,
        'clock': clock,
        'deviceId': deviceId,
        'deleted': deleted,
      };

  static SyncItem? fromJson(Object? raw) {
    if (raw is! Map) return null;
    final Map<String, Object?> m =
        raw.map((k, v) => MapEntry<String, Object?>(k.toString(), v));
    final String id = Json.str(m, 'id', '');
    final String kind = Json.str(m, 'kind', '');
    if (!validItemId(id) || !SyncKind.all.contains(kind)) return null;
    final int clock = Json.integer(m, 'clock', -1);
    final String dev = Json.str(m, 'deviceId', '');
    if (clock < 0 || !validDeviceId(dev)) return null;

    final Object? pv = m['payload'];
    final Map<String, Object?> payload = pv is Map
        ? pv.map((k, v) => MapEntry<String, Object?>(k.toString(), v))
        : <String, Object?>{};
    if (payload.length > 32) return null;
    for (final MapEntry<String, Object?> e in payload.entries) {
      if (_forbiddenKey(e.key)) return null;
      if (e.value is String && (e.value as String).length > 8192) return null;
    }
    // 设置项逐项白名单，防止把设备专属窗口状态夹带进来。
    if (kind == SyncKind.setting) {
      for (final String k in payload.keys) {
        if (!SyncSettings.keys.contains(k)) return null;
      }
    }
    return SyncItem(id, kind, payload, clock, dev,
        deleted: Json.boolean(m, 'deleted', false));
  }

  static bool _forbiddenKey(String k) {
    final String low = k.toLowerCase();
    return const <String>[
      'token', 'password', 'cookie', 'keystore', 'privatekey', 'secret',
      'cache', 'mirror', 'window', 'tabs', 'dpi', 'installpath',
    ].any((String bad) => low.contains(bad));
  }
}

enum SyncReject {
  tooLarge,
  badJson,
  badSchema,
  badDeviceId,
  badPackageId,
  badClock,
  badItem,
  tooManyItems,
  checksumMismatch,
  forbiddenKind,
  truncated,
}

/// 「同步包」：本地优先、不强制账号的跨设备传输单元。
///
/// 生命周期：导出 → 文件/二维码/局域网传输 → 接收端 `decode` 全量校验 →
/// 预览 diff → 用户确认 → 合并。任何一步失败都返回完整失败，**不产生半份数据**；
/// 中断后本地主库不变，可随时重试。
class SyncPackage {
  final String packageId;
  final String deviceId;
  final int createdAtMillis; // 仅用于展示，不参与先后判定
  final LogicalClock clock;
  final List<SyncItem> items;
  final String checksum;

  SyncPackage(this.packageId, this.deviceId, this.createdAtMillis, this.clock,
      this.items, this.checksum);

  static const int maxChars = 8 * 1024 * 1024;
  static const int maxItems = 20000;

  /// 创建同步包。`items` 中的设备专属数据会在编码时被过滤。
  static SyncPackage create({
    required String packageId,
    required String deviceId,
    required int createdAtMillis,
    required LogicalClock clock,
    required List<SyncItem> items,
  }) {
    final List<SyncItem> clean = <SyncItem>[
      for (final SyncItem it in items)
        if (SyncKind.all.contains(it.kind)) it,
    ];
    final String sum = _compute(<String, Object?>{
      'schema': AppIdentity.syncSchema,
      'packageId': packageId,
      'deviceId': deviceId,
      'createdAt': createdAtMillis,
      'clock': clock.toJson(),
      'items': <Object?>[for (final SyncItem it in clean) it.toJson()],
    });
    return SyncPackage(packageId, deviceId, createdAtMillis, clock, clean, sum);
  }

  static String _compute(Map<String, Object?> body) =>
      sha256OfString(Json.encode(body));

  Map<String, Object?> _body() => <String, Object?>{
        'schema': AppIdentity.syncSchema,
        'packageId': packageId,
        'deviceId': deviceId,
        'createdAt': createdAtMillis,
        'clock': clock.toJson(),
        'items': <Object?>[for (final SyncItem it in items) it.toJson()],
      };

  String encode() => Json.encode(<String, Object?>{
        ..._body(),
        'checksum': checksum,
      });

  /// 严格解码：全有或全无。校验和不匹配 = 传输被截断或篡改，直接拒绝。
  static XResult<SyncPackage> decode(String raw) {
    if (raw.isEmpty || raw.length > maxChars) {
      return const XResult<SyncPackage>.failure('TOO_LARGE');
    }
    final Map<String, Object?> root;
    try {
      root = Json.object(raw);
    } catch (_) {
      return const XResult<SyncPackage>.failure('BAD_JSON');
    }

    final int schema = Json.integer(root, 'schema', -1);
    if (schema != AppIdentity.syncSchema) {
      return const XResult<SyncPackage>.failure('BAD_SCHEMA');
    }

    final String pid = Json.str(root, 'packageId', '');
    final String dev = Json.str(root, 'deviceId', '');
    if (!RegExp(r'^[A-Za-z0-9_-]{8,64}$').hasMatch(pid)) {
      return const XResult<SyncPackage>.failure('BAD_PACKAGE_ID');
    }
    if (!validDeviceId(dev)) {
      return const XResult<SyncPackage>.failure('BAD_DEVICE_ID');
    }

    final LogicalClock? clock = LogicalClock.fromJson(root['clock']);
    if (clock == null) {
      return const XResult<SyncPackage>.failure('BAD_CLOCK');
    }

    final String? declared = normalizeSha256(Json.str(root, 'checksum', ''));
    if (declared == null) {
      return const XResult<SyncPackage>.failure('CHECKSUM_MISSING');
    }
    final Map<String, Object?> body = Map<String, Object?>.from(root)
      ..remove('checksum');
    final String actual = _compute(body);
    if (actual != declared) {
      // 尾部被截断时 JSON 已解析失败；能解析但哈希不符 = 篡改或不完整。
      return const XResult<SyncPackage>.failure('CHECKSUM_MISMATCH');
    }

    final List<Object?> rawItems = Json.list(root, 'items');
    if (rawItems.length > maxItems) {
      return const XResult<SyncPackage>.failure('TOO_MANY_ITEMS');
    }
    final List<SyncItem> items = <SyncItem>[];
    final Set<String> ids = <String>{};
    for (final Object? r in rawItems) {
      final SyncItem? it = SyncItem.fromJson(r);
      if (it == null || !ids.add(it.id)) {
        return const XResult<SyncPackage>.failure('BAD_ITEM');
      }
      items.add(it);
    }

    return XResult<SyncPackage>.success(SyncPackage(
      pid,
      dev,
      Json.integer(root, 'createdAt', 0),
      clock,
      items,
      declared,
    ));
  }

  /// 接收前预览：新增 / 修改 / 删除 / 冲突数量（不改动任何数据）。
  Map<String, int> summaryAgainst(List<SyncItem> local) {
    final Map<String, SyncItem> mine = <String, SyncItem>{
      for (final SyncItem it in local) it.id: it,
    };
    int added = 0, modified = 0, deleted = 0, conflict = 0;
    for (final SyncItem inc in items) {
      final SyncItem? cur = mine[inc.id];
      if (cur == null) {
        inc.deleted ? deleted++ : added++;
        continue;
      }
      if (inc.deleted) {
        deleted++;
        continue;
      }
      final int cmp = inc.logicalClock.compareTo(cur.logicalClock);
      if (cmp > 0) {
        modified++;
      } else if (cmp == 0 && Json.encode(inc.payload) != Json.encode(cur.payload)) {
        conflict++;
      }
    }
    return <String, int>{
      'added': added,
      'modified': modified,
      'deleted': deleted,
      'conflict': conflict,
    };
  }
}
