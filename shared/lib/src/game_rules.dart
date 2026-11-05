import 'dart:math';
import 'foundation.dart';

enum Dir { left, right, up, down }

class MoveResult {
  final bool moved;
  final int gain;
  final int spawnedValue;
  final int maxTile;
  final bool won;
  final bool over;

  const MoveResult(this.moved, this.gain, this.spawnedValue, this.maxTile,
      this.won, this.over);
}

class GameState {
  List<List<int>> board;
  int score;
  int best;
  int moves;
  int seed;
  bool over;
  bool won;
  bool keepPlaying;
  int hold;
  int next;
  int undosLeft;
  List<String> achievements;
  bool daily;

  GameState({
    required this.board,
    this.score = 0,
    this.best = 0,
    this.moves = 0,
    this.seed = 1,
    this.over = false,
    this.won = false,
    this.keepPlaying = false,
    this.hold = 0,
    this.next = 0,
    this.undosLeft = 3,
    List<String>? achievements,
    this.daily = false,
  }) : achievements = achievements ?? <String>[];
}

/// 「方块合成 2.0」确定性规则引擎（纯 Dart，不依赖任何 UI / 平台）。
///
/// Windows 键盘、Windows 触屏、Android 滑动共用这一份规则；
/// 渲染与输入在各自外壳中处理，逻辑步长固定、可重放、可测试。
class GameRules {
  GameRules._();

  static const int size = 4;
  static const int undoLimit = 3;
  static const int winTile = 2048;

  static List<List<int>> _empty() =>
      List<List<int>>.generate(size, (_) => List<int>.filled(size, 0));

  static GameState newGame(int seed, int best, {bool daily = false}) {
    final GameState s = GameState(
      board: _empty(), seed: seed, best: best, daily: daily, next: 0,
    );
    _spawn(s, Random(seed));
    _spawn(s, Random(seed + 0x9e3779b9));
    s.next = _peek(s);
    return s;
  }

  static int _peek(GameState s) =>
      (Random(s.seed + s.moves * 31 + s.score).nextDouble() < 0.9) ? 2 : 4;

  static bool _spawn(GameState s, Random rnd) {
    final List<List<int>> cells = <List<int>>[];
    for (int y = 0; y < size; y++) {
      for (int x = 0; x < size; x++) {
        if (s.board[y][x] == 0) cells.add(<int>[y, x]);
      }
    }
    if (cells.isEmpty) return false;
    final List<int> c = cells[rnd.nextInt(cells.length)];
    s.board[c[0]][c[1]] = rnd.nextDouble() < 0.9 ? 2 : 4;
    return true;
  }

  static List<int> _line(GameState s, Dir d, int i, bool reverse) {
    final List<int> out = <int>[];
    for (int j = 0; j < size; j++) {
      int x = 0, y = 0;
      switch (d) {
        case Dir.left:
          x = j; y = i;
          break;
        case Dir.right:
          x = size - 1 - j; y = i;
          break;
        case Dir.up:
          x = i; y = j;
          break;
        case Dir.down:
          x = i; y = size - 1 - j;
          break;
      }
      out.add(s.board[y][x]);
    }
    return reverse ? out.reversed.toList() : out;
  }

  static void _writeLine(GameState s, Dir d, int i, List<int> vals) {
    final List<int> v = List<int>.filled(size, 0);
    for (int j = 0; j < size && j < vals.length; j++) {
      v[j] = vals[j];
    }
    for (int j = 0; j < size; j++) {
      int x = 0, y = 0;
      switch (d) {
        case Dir.left:
          x = j; y = i;
          break;
        case Dir.right:
          x = size - 1 - j; y = i;
          break;
        case Dir.up:
          x = i; y = j;
          break;
        case Dir.down:
          x = i; y = size - 1 - j;
          break;
      }
      s.board[y][x] = v[j];
    }
  }

  static MoveResult move(GameState s, Dir dir) {
    if (s.over) {
      return MoveResult(false, 0, 0, maxTile(s.board), s.won, true);
    }
    final List<List<int>> before = copyBoard(s.board);
    final bool reverse = dir == Dir.right || dir == Dir.down;
    int gain = 0;

    for (int i = 0; i < size; i++) {
      final List<int> raw = _line(s, dir, i, reverse)
          .where((int v) => v != 0)
          .toList();
      final List<int> merged = <int>[];
      int j = 0;
      while (j < raw.length) {
        if (j + 1 < raw.length && raw[j] == raw[j + 1]) {
          final int v = raw[j] * 2;
          merged.add(v);
          gain += v;
          j += 2;
        } else {
          merged.add(raw[j]);
          j += 1;
        }
      }
      _writeLine(s, dir, i, merged);
    }

    // 位置变化也要算 moved（例如 [2,0,0,0] 左移）；以整盘快照为准。
    bool boardChanged = false;
    if (!boardChanged) {
      for (int y = 0; y < size; y++) {
        for (int x = 0; x < size; x++) {
          if (before[y][x] != s.board[y][x]) {
            boardChanged = true;
            break;
          }
        }
      }
    }
    if (!boardChanged) {
      s.board = before;
      return MoveResult(false, 0, 0, maxTile(s.board), s.won, s.over);
    }

    s.score += gain;
    if (s.score > s.best) s.best = s.score;
    s.moves += 1;

    final Random rnd = Random(s.seed + s.moves * 0x9e3779b97f4a7c15 + gain);
    _spawn(s, rnd);
    s.next = _peek(s);

    final int mx = maxTile(s.board);
    if (!s.won && mx >= winTile) {
      s.won = true;
    }
    final bool can = canMove(s.board);
    if (!can) s.over = true;

    if (!s.achievements.contains('first_merge') && gain > 0) {
      s.achievements.add('first_merge');
    }
    if (!s.achievements.contains('tile_128') && mx >= 128) {
      s.achievements.add('tile_128');
    }
    return MoveResult(true, gain, 0, mx, s.won, s.over);
  }

  static GameState hold(GameState s) {
    if (s.over) return s;
    final int cur = s.hold;
    s.hold = s.next == 0 ? 2 : s.next;
    if (cur == 0) {
      s.next = _peek(s);
    } else {
      s.next = cur;
    }
    return s;
  }

  static GameState undo(GameState current, GameState? snapshot) {
    if (snapshot == null || current.undosLeft <= 0) return current;
    final GameState s = restore(save(snapshot), current.best);
    s.undosLeft = current.undosLeft - 1;
    return s;
  }

  static GameState snapshot(GameState s) => restore(save(s), s.best);

  static List<List<int>> copyBoard(List<List<int>> b) =>
      List<List<int>>.generate(size, (int y) => List<int>.from(b[y]));

  static int maxTile(List<List<int>> b) {
    int m = 0;
    for (final List<int> row in b) {
      for (final int v in row) {
        if (v > m) m = v;
      }
    }
    return m;
  }

  static bool canMove(List<List<int>> b) {
    for (int y = 0; y < size; y++) {
      for (int x = 0; x < size; x++) {
        if (b[y][x] == 0) return true;
        if (x + 1 < size && b[y][x] == b[y][x + 1]) return true;
        if (y + 1 < size && b[y][x] == b[y + 1][x]) return true;
      }
    }
    return false;
  }

  static int level(GameState s) => 1 + (maxTile(s.board) ~/ 128);

  static int dailySeed(int year, int month, int day) =>
      year * 10000 + month * 100 + day;

  /// 色盲友好：不同值对应不同形状，不依赖颜色区分。
  static const Map<int, String> shapes = <int, String>{
    2: '●', 4: '■', 8: '▲', 16: '◆', 32: '★', 64: '⬟',
    128: '✚', 256: '⬢', 512: '✦', 1024: '❖', 2048: '✧',
  };

  static String shapeFor(int v) => shapes[v] ?? '◇';

  static String save(GameState s) => Json.encode(<String, Object?>{
        'board': <Object?>[
          for (final List<int> row in s.board)
            <Object?>[for (final int v in row) v],
        ],
        'score': s.score,
        'moves': s.moves,
        'seed': s.seed,
        'over': s.over,
        'won': s.won,
        'keepPlaying': s.keepPlaying,
        'hold': s.hold,
        'next': s.next,
        'undosLeft': s.undosLeft,
        'achievements': <Object?>[for (final String a in s.achievements) a],
        'daily': s.daily,
      });

  static GameState restore(Object? raw, int best) {
    Map<String, Object?> m = <String, Object?>{};
    if (raw is String) {
      try {
        m = Json.object(raw);
      } catch (_) {
        m = <String, Object?>{};
      }
    } else if (raw is Map) {
      m = raw.map((k, v) => MapEntry<String, Object?>(k.toString(), v));
    }
    final List<List<int>> b = _empty();
    final List<Object?> rb = Json.list(m, 'board');
    for (int y = 0; y < size && y < rb.length; y++) {
      final Object? row = rb[y];
      if (row is! List) continue;
      for (int x = 0; x < size && x < row.length; x++) {
        final Object? v = row[x];
        final int n = v is int ? v : (v is num ? v.toInt() : 0);
        b[y][x] = _clampTile(n);
      }
    }
    int score = Json.integer(m, 'score', 0);
    if (score < 0) score = 0;
    int moves = Json.integer(m, 'moves', 0);
    if (moves < 0) moves = 0;
    int undos = Json.integer(m, 'undosLeft', undoLimit);
    if (undos < 0) undos = 0;
    if (undos > undoLimit) undos = undoLimit;

    final List<String> ach = <String>[];
    for (final Object? a in Json.list(m, 'achievements')) {
      if (a is String && ach.length < 32) ach.add(a);
    }

    final GameState s = GameState(
      board: b,
      score: score,
      best: best,
      moves: moves,
      seed: Json.integer(m, 'seed', 1),
      over: Json.boolean(m, 'over', false),
      won: Json.boolean(m, 'won', false),
      keepPlaying: Json.boolean(m, 'keepPlaying', false),
      hold: _clampTile(Json.integer(m, 'hold', 0)),
      next: _clampTile(Json.integer(m, 'next', 0)),
      undosLeft: undos,
      achievements: ach,
      daily: Json.boolean(m, 'daily', false),
    );
    // 空盘（非法存档 / 首次启动）自动开新局，而不是显示不可玩的死局。
    if (maxTile(b) == 0) return newGame(s.seed <= 0 ? 1 : s.seed, best);
    s.over = !canMove(b);
    return s;
  }

  static int _clampTile(int v) {
    if (v <= 0) return 0;
    if (v > 1 << 20) return 0;
    // 只允许 2 的幂，防止注入非法块值。
    if ((v & (v - 1)) != 0) return 0;
    return v;
  }
}
