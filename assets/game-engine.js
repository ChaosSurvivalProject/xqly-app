/*
 * 星穹旅驿 · 方块合成 规则引擎（纯逻辑，零 DOM / 零网络 / 零依赖）
 * - 与渲染完全解耦：宿主只负责把 state 画出来
 * - 确定性：同一 seed + 同一操作序列 → 完全相同棋盘（每日挑战 / 复盘 / 测试）
 * - 与 cn.xqly.app.core.GameRules 的 Java 实现语义一致（JVM 侧另有单测覆盖）
 * - 同时支持浏览器（window.XQGame）与 Node（module.exports）
 */
(function (root, factory) {
  var api = factory();
  if (typeof module === 'object' && module.exports) module.exports = api;
  else root.XQGame = api;
})(typeof globalThis !== 'undefined' ? globalThis : this, function () {
  'use strict';
  var SIZE = 4, UNDO_LIMIT = 3;

  // 确定性 PRNG（xorshift32），不依赖 Math.random
  function Rng(seed) {
    this.s = (seed | 0) || 0x9E3779B9;
  }
  Rng.prototype.next = function () {
    var x = this.s;
    x ^= x << 13; x ^= x >>> 17; x ^= x << 5;
    this.s = x | 0;
    return (this.s >>> 0) / 4294967296;
  };
  Rng.prototype.int = function (n) { return Math.floor(this.next() * n) % n; };
  Rng.prototype.tile = function () { return this.next() < 0.9 ? 2 : 4; };

  function blankBoard() {
    var b = [], y, x;
    for (y = 0; y < SIZE; y++) { b[y] = []; for (x = 0; x < SIZE; x++) b[y][x] = 0; }
    return b;
  }
  function clone(b) { return b.map(function (r) { return r.slice(); }); }
  function changed(a, b) {
    for (var y = 0; y < SIZE; y++) for (var x = 0; x < SIZE; x++) if (a[y][x] !== b[y][x]) return true;
    return false;
  }
  function isPow2(n) { return n > 0 && (n & (n - 1)) === 0; }
  function clampTile(n) { n = Math.floor(Number(n) || 0); return isPow2(n) && n <= 1073741824 ? n : 0; }
  function maxTile(b) {
    var m = 0;
    for (var y = 0; y < SIZE; y++) for (var x = 0; x < SIZE; x++) m = Math.max(m, b[y][x]);
    return m;
  }
  function canMoveBoard(b) {
    for (var y = 0; y < SIZE; y++) for (var x = 0; x < SIZE; x++) {
      if (b[y][x] === 0) return true;
      if (x + 1 < SIZE && b[y][x] === b[y][x + 1]) return true;
      if (y + 1 < SIZE && b[y][x] === b[y + 1][x]) return true;
    }
    return false;
  }

  function newState(seed, best) {
    var st = {
      board: blankBoard(), score: 0, best: Math.max(0, best || 0), moves: 0,
      combo: 0, undosLeft: UNDO_LIMIT, won: false, over: false, keepPlaying: false,
      seed: seed | 0, hold: 0, holdUsed: false, next: 2, achievements: [],
      level: 0, rank: 0
    };
    var rng = new Rng(seed);
    st.next = rng.tile();
    spawn(st, rng); spawn(st, rng);
    st.level = levelOf(st);
    return st;
  }

  function spawn(st, rng) {
    var empty = [], y, x;
    for (y = 0; y < SIZE; y++) for (x = 0; x < SIZE; x++) if (st.board[y][x] === 0) empty.push([y, x]);
    if (!empty.length) return false;
    var p = empty[rng.int(empty.length)];
    st.board[p[0]][p[1]] = st.next;
    st.next = rng.tile();
    return true;
  }

  function lineFor(b, dir, i) {
    var l = [], j;
    for (j = 0; j < SIZE; j++) {
      if (dir === 'left') l.push(b[i][j]);
      else if (dir === 'right') l.push(b[i][SIZE - 1 - j]);
      else if (dir === 'up') l.push(b[j][i]);
      else l.push(b[SIZE - 1 - j][i]);
    }
    return l;
  }
  function setLine(b, dir, i, out) {
    for (var j = 0; j < SIZE; j++) {
      if (dir === 'left') b[i][j] = out[j];
      else if (dir === 'right') b[i][SIZE - 1 - j] = out[j];
      else if (dir === 'up') b[j][i] = out[j];
      else b[SIZE - 1 - j][i] = out[j];
    }
  }
  function compress(line) {
    var a = line.filter(function (v) { return v !== 0; }), out = [], gain = 0, merges = 0, i = 0;
    while (i < a.length) {
      if (i + 1 < a.length && a[i] === a[i + 1]) {
        var v = a[i] * 2; out.push(v); gain += v; merges++; i += 2;
      } else { out.push(a[i]); i++; }
    }
    while (out.length < SIZE) out.push(0);
    return { line: out, gain: gain, merges: merges };
  }

  function move(st, dir) {
    if (st.over) return { changed: false, gain: 0, merges: 0, spawned: false, wonNow: false, overNow: true };
    var before = clone(st.board), gain = 0, merges = 0, i, r;
    for (i = 0; i < SIZE; i++) {
      r = compress(lineFor(st.board, dir, i));
      setLine(st.board, dir, i, r.line);
      gain += r.gain; merges += r.merges;
    }
    if (!changed(before, st.board))
      return { changed: false, gain: 0, merges: 0, spawned: false, wonNow: false, overNow: false };

    st.score += gain; st.moves++;
    st.combo = merges > 0 ? st.combo + 1 : 0;
    if (st.score > st.best) st.best = st.score;
    st.holdUsed = false;
    var rng = new Rng((st.seed + st.moves * 2654435761 + gain) | 0);
    var spawned = spawn(st, rng);
    var mt = maxTile(st.board), wonNow = false;
    if (mt >= 2048 && !st.won && !st.keepPlaying) { st.won = true; wonNow = true; }
    unlock(st, mt);
    st.level = levelOf(st);
    st.over = !canMoveBoard(st.board);
    return { changed: true, gain: gain, merges: merges, spawned: spawned, wonNow: wonNow, overNow: st.over };
  }

  function hold(st) {
    if (st.holdUsed || st.over) return false;
    var incoming = st.next;
    var rng = new Rng((st.seed + st.moves * 31 + st.score) | 0);
    st.next = st.hold === 0 ? rng.tile() : st.hold;
    st.hold = incoming;
    st.holdUsed = true;
    return true;
  }

  function undo(current, snapshot) {
    if (!snapshot || current.undosLeft <= 0) return current;
    var s = restore(save(snapshot), current.best);
    s.undosLeft = current.undosLeft - 1;
    s.best = Math.max(current.best, snapshot.best || 0);
    s.over = !canMoveBoard(s.board);
    return s;
  }

  function levelOf(st) {
    var m = maxTile(st.board), lv = 0;
    while (m > 2) { m >>= 1; lv++; }
    return lv;
  }

  var ACHIEVEMENTS = [
    ['first_merge', '初次合成', function (s) { return s.moves >= 1; }],
    ['combo5', '五连合成', function (s) { return s.combo >= 5; }],
    ['tile128', '抵达 128', function (s) { return maxTile(s.board) >= 128; }],
    ['tile1024', '抵达 1024', function (s) { return maxTile(s.board) >= 1024; }],
    ['astral', '合成星穹方块', function (s) { return maxTile(s.board) >= 2048; }]
  ];
  function unlock(st) {
    for (var i = 0; i < ACHIEVEMENTS.length; i++) {
      var a = ACHIEVEMENTS[i];
      if (a[2](st) && st.achievements.indexOf(a[1]) < 0 && st.achievements.length < 32)
        st.achievements.push(a[1]);
    }
  }

  function save(st) {
    return {
      board: clone(st.board), score: st.score, best: st.best, moves: st.moves,
      combo: st.combo, undosLeft: st.undosLeft, won: st.won, over: st.over,
      keepPlaying: st.keepPlaying, seed: st.seed, hold: st.hold, holdUsed: st.holdUsed,
      next: st.next, achievements: st.achievements.slice(), level: st.level
    };
  }

  function restore(obj, best) {
    var st = newState(0, best || 0);
    if (!obj || typeof obj !== 'object') return st;
    if (typeof obj === 'string') { try { obj = JSON.parse(obj); } catch (e) { return st; } }
    if (Array.isArray(obj.board) && obj.board.length === SIZE) {
      var b = blankBoard(), valid = true;
      for (var y = 0; y < SIZE; y++) {
        if (!Array.isArray(obj.board[y]) || obj.board[y].length !== SIZE) { valid = false; break; }
        for (var x = 0; x < SIZE; x++) b[y][x] = clampTile(obj.board[y][x]);
      }
      if (valid) st.board = b;
    }
    st.score = Math.max(0, Math.floor(Number(obj.score) || 0));
    st.best = Math.max(st.best, Math.floor(Number(obj.best) || 0));
    st.moves = Math.max(0, Math.floor(Number(obj.moves) || 0));
    st.combo = Math.max(0, Math.floor(Number(obj.combo) || 0));
    st.undosLeft = Math.max(0, Math.min(UNDO_LIMIT, Math.floor(Number(obj.undosLeft)) || 0));
    st.won = !!obj.won; st.keepPlaying = !!obj.keepPlaying;
    st.seed = obj.seed | 0; st.hold = clampTile(obj.hold);
    st.holdUsed = !!obj.holdUsed;
    st.next = clampTile(obj.next) || 2;
    if (Array.isArray(obj.achievements))
      st.achievements = obj.achievements.filter(function (a) { return typeof a === 'string' && a.length <= 32; }).slice(0, 32);
    var any = false;
    for (var i = 0; i < SIZE; i++) for (var j = 0; j < SIZE; j++) if (st.board[i][j]) any = true;
    if (!any) return newState(st.seed, st.best);
    st.level = levelOf(st);
    st.over = !canMoveBoard(st.board);
    return st;
  }
  function snapshot(st) { return restore(save(st), st.best); }

  function dailySeed(year, month, day) {
    // FNV-1a 64 位的低 32 位近似，保证同日期同种子、跨设备一致
    var h = 0x811c9dc5, parts = [year, month, day, 0x58514c59];
    for (var i = 0; i < parts.length; i++) {
      h ^= (parts[i] | 0); h = Math.imul(h, 0x01000193) | 0;
    }
    return h >>> 0;
  }

  var SHAPES = {
    2: '●', 4: '■', 8: '▲', 16: '◆', 32: '★', 64: '⬟', 128: '✚', 256: '⬢',
    512: '✦', 1024: '❖', 2048: '✧'
  };
  function shapeFor(v) { return SHAPES[v] || '◈'; }

  return {
    SIZE: SIZE, UNDO_LIMIT: UNDO_LIMIT, SHAPES: SHAPES,
    Rng: Rng, newState: newState, move: move, hold: hold, undo: undo,
    save: save, restore: restore, snapshot: snapshot,
    maxTile: maxTile, canMove: canMoveBoard, level: levelOf,
    dailySeed: dailySeed, shapeFor: shapeFor, clone: clone
  };
});
