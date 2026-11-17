enum FeedbackLevel { info, success, warning, error }

/// 一条临时反馈。统一位置，不插入标题、表单或按钮之间。
class Feedback {
  final String id;
  final FeedbackLevel level;
  final String text;
  final int durationMs;
  final bool dismissible;
  final String action;

  const Feedback({
    required this.id,
    required this.level,
    required this.text,
    required this.durationMs,
    required this.dismissible,
    this.action = '',
  });

  int get priority => <FeedbackLevel, int>{
        FeedbackLevel.info: 1,
        FeedbackLevel.success: 2,
        FeedbackLevel.warning: 3,
        FeedbackLevel.error: 4,
      }[level]!;

  Map<String, Object?> toJson() => <String, Object?>{
        'id': id,
        'level': level.name,
        'text': text,
        'durationMs': durationMs,
        'dismissible': dismissible,
        'action': action,
      };
}

/// 由 Dart 核心维护的**单一**临时反馈队列。
/// 同一时刻最多展示一条（高优先级抢占）；危险提示 6s、普通 4s、长文本可手动关闭。
/// Android / Windows 用底部固定条，WebView 用顶部安全区下固定条。
class FeedbackQueue {
  FeedbackQueue._();

  static const int dangerMs = 6000;
  static const int normalMs = 4000;
  static const int longTextThreshold = 120;
  static const int maxQueue = 16;
  static const String placementAndroid = 'bottom-bar';
  static const String placementWebView = 'top-safe-bar';

  static int durationFor(FeedbackLevel level, String text) =>
      level == FeedbackLevel.error || level == FeedbackLevel.warning
          ? dangerMs
          : normalMs;

  static Feedback make(
    String id,
    FeedbackLevel level,
    String text, {
    String action = '',
    int? durationMs,
  }) {
    final bool long = text.length > longTextThreshold;
    return Feedback(
      id: id,
      level: level,
      text: text,
      durationMs: durationMs ?? durationFor(level, text),
      dismissible: long,
      action: action.length <= 2 ? action : '',
    );
  }
}

/// 有界队列运行时状态（每应用一个实例，互不串扰）。
class FeedbackQueueState {
  final List<Feedback> _pending = <Feedback>[];
  Feedback? _current;
  int _startedAt = 0;

  Feedback? get current => _current;
  int get pendingCount => _pending.length;

  /// 入队并返回当前应展示的那一条。高优先级抢占低优先级。
  Feedback push(Feedback f, int nowMs) {
    if (_current == null) {
      _current = f;
      _startedAt = nowMs;
      return f;
    }
    if (f.priority > _current!.priority) {
      // 低优先级未过期的提示退回队列头部，避免丢失。
      if (nowMs - _startedAt < _current!.durationMs) {
        _pending.insert(0, _current!);
      }
      _current = f;
      _startedAt = nowMs;
      return f;
    }
    if (_pending.length >= FeedbackQueue.maxQueue) {
      _pending.removeAt(0); // 有界，防内存增长
    }
    _pending.add(f);
    return _current!;
  }

  /// 时间推进：过期后按优先级取出下一条；队列为空则清空。
  Feedback? tick(int nowMs) {
    if (_current == null) return null;
    if (_current!.dismissible && _pending.isEmpty) {
      // 长文本可手动关闭：不自动消失，等待 dismiss()。
      return _current;
    }
    if (nowMs - _startedAt < _current!.durationMs) return _current;
    return _next(nowMs);
  }

  Feedback? dismiss(String id, int nowMs) {
    if (_current != null && _current!.id == id && _current!.dismissible) {
      return _next(nowMs);
    }
    _pending.removeWhere((Feedback f) => f.id == id);
    return _current;
  }

  Feedback? _next(int nowMs) {
    if (_pending.isEmpty) {
      _current = null;
      _startedAt = 0;
      return null;
    }
    int best = 0;
    for (int i = 1; i < _pending.length; i++) {
      if (_pending[i].priority > _pending[best].priority) best = i;
    }
    _current = _pending.removeAt(best);
    _startedAt = nowMs;
    return _current;
  }

  void clear() {
    _pending.clear();
    _current = null;
    _startedAt = 0;
  }
}
