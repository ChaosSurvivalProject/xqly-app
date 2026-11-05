import 'foundation.dart';
import 'sync_package.dart';

enum ConflictKind { progress, gameSave, setting, deleteVsEdit, tag }

class Conflict {
  final String id;
  final ConflictKind kind;
  final SyncItem local;
  final SyncItem incoming;

  const Conflict(this.id, this.kind, this.local, this.incoming);
}

class SyncDiff {
  final List<SyncItem> added;
  final List<SyncItem> modified;
  final List<SyncItem> deleted;
  final List<Conflict> conflicts;

  const SyncDiff(this.added, this.modified, this.deleted, this.conflicts);

  int get total =>
      added.length + modified.length + deleted.length + conflicts.length;
  bool get hasConflict => conflicts.isNotEmpty;
}

/// 用户逐项选择：保留本地 / 采用对方 / 合并 / 双方都保留。
enum ResolutionChoice { keepLocal, takeIncoming, merge, keepBoth }

class Resolution {
  final String id;
  final ResolutionChoice choice;

  const Resolution(this.id, this.choice);
}

class MergeResult {
  final List<SyncItem> merged;
  final List<Conflict> unresolved;
  final int applied;

  const MergeResult(this.merged, this.unresolved, this.applied);

  /// 未确认冲突时，返回的是**本地库原样**，调用方不得写入。
  bool get safeToApply => unresolved.isEmpty;
}

/// 本地优先同步的合并规则（纯 Dart，双平台共享）。
///
/// 规则：
/// - 标签：集合并集，天然可合并；
/// - 阅读进度：逻辑时钟较新者胜；时钟相同但内容不同 → 交用户选择；
/// - 游戏存档：**不盲目覆盖**，默认双方都保留，由用户选择；
/// - 设置：逐项决定，默认保留本地（设备差异大，避免意外覆盖）；
/// - 删除：墓碑优先，但「删除 vs 编辑」冲突交用户，不做静默删除；
/// - 任何未确认冲突都会阻止应用，本地主库保持原状。
class SyncMerge {
  SyncMerge._();

  static SyncDiff diff(List<SyncItem> local, List<SyncItem> incoming) {
    final Map<String, SyncItem> mine = <String, SyncItem>{
      for (final SyncItem it in local) it.id: it,
    };
    final List<SyncItem> added = <SyncItem>[];
    final List<SyncItem> modified = <SyncItem>[];
    final List<SyncItem> deleted = <SyncItem>[];
    final List<Conflict> conflicts = <Conflict>[];

    for (final SyncItem inc in incoming) {
      final SyncItem? cur = mine[inc.id];
      if (cur == null) {
        if (inc.deleted) {
          deleted.add(inc);
        } else {
          added.add(inc);
        }
        continue;
      }
      if (inc.deleted) {
        // 对端删除，本地在更晚的时钟上又编辑过 → 冲突。
        if (cur.logicalClock.compareTo(inc.logicalClock) > 0 &&
            !cur.deleted) {
          conflicts.add(Conflict(inc.id, ConflictKind.deleteVsEdit, cur, inc));
        } else {
          deleted.add(inc);
        }
        continue;
      }
      if (cur.deleted) {
        conflicts.add(Conflict(inc.id, ConflictKind.deleteVsEdit, cur, inc));
        continue;
      }

      // counter 相同意味着双方「同时」写入（并发），不能靠设备 ID 的全序
      // 悄悄判定谁赢；这类冲突必须交给用户逐项选择。
      final bool same = Json.encode(inc.payload) == Json.encode(cur.payload);
      if (same) continue;

      if (inc.kind == SyncKind.gameSave) {
        conflicts.add(Conflict(inc.id, ConflictKind.gameSave, cur, inc));
        continue;
      }
      if (inc.kind == SyncKind.setting) {
        conflicts.add(Conflict(inc.id, ConflictKind.setting, cur, inc));
        continue;
      }
      // 标签集合可无损合并；即使时钟不同也走合并分支，不静默丢弃任一方的标签。
      if (inc.kind == SyncKind.tag) {
        conflicts.add(Conflict(inc.id, ConflictKind.tag, cur, inc));
        continue;
      }
      if (inc.clock == cur.clock) {
        conflicts.add(Conflict(
            inc.id,
            inc.kind == SyncKind.progress
                ? ConflictKind.progress
                : ConflictKind.tag,
            cur, inc));
        continue;
      }
      if (inc.clock > cur.clock) modified.add(inc);
      // 否则本地在更晚的逻辑时刻更新过，忽略对端旧值。
    }
    return SyncDiff(added, modified, deleted, conflicts);
  }

  static MergeResult merge(
    List<SyncItem> local,
    List<SyncItem> incoming,
    List<Resolution> resolutions, {
    required String localDeviceId,
    required int nextClock,
  }) {
    final SyncDiff d = diff(local, incoming);
    final Map<String, Resolution> res = <String, Resolution>{
      for (final Resolution r in resolutions) r.id: r,
    };
    final Map<String, SyncItem> out = <String, SyncItem>{
      for (final SyncItem it in local) it.id: it,
    };
    int applied = 0;
    final List<Conflict> unresolved = <Conflict>[];

    for (final SyncItem it in d.added) {
      out[it.id] = it;
      applied++;
    }
    for (final SyncItem it in d.modified) {
      out[it.id] = it;
      applied++;
    }
    for (final SyncItem it in d.deleted) {
      // 墓碑本身保留，用于后续同步传播删除，不静默抹掉记录。
      out[it.id] = SyncItem(it.id, SyncKind.tombstone,
          <String, Object?>{'of': it.kind}, it.clock, it.deviceId,
          deleted: true);
      applied++;
    }

    for (final Conflict c in d.conflicts) {
      final Resolution? r = res[c.id];
      if (r == null) {
        // 标签并集合并无歧义，可自动应用；其余冲突必须用户逐项确认。
        if (c.kind == ConflictKind.tag) {
          out[c.id] = _mergeTags(c, localDeviceId, nextClock + applied);
          applied++;
        } else {
          unresolved.add(c);
        }
        continue;
      }
      switch (r.choice) {
        case ResolutionChoice.keepLocal:
          break;
        case ResolutionChoice.takeIncoming:
          out[c.id] = c.incoming;
          applied++;
          break;
        case ResolutionChoice.merge:
          if (c.kind == ConflictKind.tag) {
            out[c.id] = _mergeTags(c, localDeviceId, nextClock + applied);
            applied++;
          } else {
            unresolved.add(c); // 非标签类型没有自动合并语义
          }
          break;
        case ResolutionChoice.keepBoth:
          // 游戏存档 / 冲突记录：保留双方版本，生成带后缀的新 id。
          final String forkId = '${c.id}.${c.incoming.deviceId}';
          if (validItemId(forkId)) {
            out[forkId] = SyncItem(forkId, c.incoming.kind, c.incoming.payload,
                c.incoming.clock, c.incoming.deviceId);
            applied++;
          } else {
            unresolved.add(c);
          }
          break;
      }
    }

    // 只要还有未确认冲突，返回本地原样，杜绝「半份有效数据」。
    if (unresolved.isNotEmpty) {
      return MergeResult(List<SyncItem>.from(local), unresolved, 0);
    }
    return MergeResult(out.values.toList(), unresolved, applied);
  }

  static SyncItem _mergeTags(Conflict c, String deviceId, int clock) {
    final Set<String> tags = <String>{};
    for (final Map<String, Object?> m in <Map<String, Object?>>[
      c.local.payload,
      c.incoming.payload,
    ]) {
      final Object? t = m['tags'];
      if (t is List) {
        for (final Object? x in t) {
          if (x is String && x.length <= 64) tags.add(x);
        }
      }
    }
    final int mergedClock = (c.local.clock > c.incoming.clock
            ? c.local.clock
            : c.incoming.clock) +
        1;
    return SyncItem(c.id, SyncKind.tag, <String, Object?>{
      'tags': <Object?>[...tags]..sort(),
    }, mergedClock > clock ? mergedClock : clock, deviceId);
  }
}
