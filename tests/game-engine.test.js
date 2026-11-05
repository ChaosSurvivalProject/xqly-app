/*
 * 方块合成规则引擎 JVM/Node 侧回归测试（零依赖：node tests/game-engine.test.js）
 * 验证确定性、计分、撤销、hold、结束判定、存档往返、每日种子、色盲形状。
 * 与 Java GameRules 测试共同构成「逻辑与渲染解耦」的双侧证据。
 */
'use strict';
const assert = require('assert');
const G = require('../assets/game-engine.js');

let passed = 0, failed = 0;
function t(name, fn) {
  try { fn(); passed++; console.log('  PASS  ' + name); }
  catch (e) { failed++; console.log('  FAIL  ' + name + '  ->  ' + e.message); }
}
function board(rows) { return rows.map(r => r.slice()); }

console.log('==========================================================');
console.log(' 星穹旅驿 · 方块合成规则引擎 Node 单元测试');
console.log(' Node: ' + process.version + ' | 引擎: assets/game-engine.js');
console.log('==========================================================\n[确定性]\n');

t('同 seed 同操作序列结果完全一致', () => {
  const a = G.newState(42, 0), b = G.newState(42, 0);
  const seq = ['left', 'up', 'right', 'down', 'left', 'up'];
  for (const d of seq) { G.move(a, d); G.move(b, d); }
  assert.deepStrictEqual(G.save(a), G.save(b));
});
t('不同 seed 起手不同', () => {
  let diff = false;
  for (let s = 1; s < 20; s++)
    if (JSON.stringify(G.save(G.newState(s, 0))) !== JSON.stringify(G.save(G.newState(1000, 0)))) diff = true;
  assert.ok(diff);
});
t('PRNG 确定性且值域 [0,1)', () => {
  const a = new G.Rng(7), b = new G.Rng(7);
  for (let i = 0; i < 100; i++) { const v = a.next(); assert.strictEqual(v, b.next()); assert.ok(v >= 0 && v < 1); }
});

console.log('\n[移动与计分]\n');
t('2+2 合并得 4 分并生成新块', () => {
  const s = G.newState(1, 0);
  s.board = board([[2, 2, 0, 0], [0, 0, 0, 0], [0, 0, 0, 0], [0, 0, 0, 0]]);
  s.next = 2;
  const r = G.move(s, 'left');
  assert.strictEqual(r.changed, true);
  assert.strictEqual(r.gain, 4);
  assert.strictEqual(s.score, 4);
  assert.strictEqual(r.spawned, true);
});
t('无效移动不改变棋盘不生成', () => {
  const s = G.newState(1, 0);
  s.board = board([[2, 0, 0, 0], [0, 0, 0, 0], [0, 0, 0, 0], [0, 0, 0, 0]]);
  const before = JSON.stringify(G.save(s));
  const r = G.move(s, 'left');
  assert.strictEqual(r.changed, false);
  assert.strictEqual(JSON.stringify(G.save(s)), before);
});
t('一次移动只合并相邻一次（2,2,2,2 → 4,4）', () => {
  const s = G.newState(1, 0);
  s.board = board([[2, 2, 2, 2], [0, 0, 0, 0], [0, 0, 0, 0], [0, 0, 0, 0]]);
  G.move(s, 'left');
  assert.strictEqual(s.board[0][0], 4);
  assert.strictEqual(s.board[0][1], 4);
  assert.strictEqual(s.board[0][2], 0);
});
t('四方向镜像一致', () => {
  const mk = () => { const s = G.newState(5, 0); s.board = board([[2, 2, 0, 0], [0, 0, 0, 0], [0, 0, 0, 0], [0, 0, 0, 0]]); return s; };
  const l = mk(), r = mk();
  G.move(l, 'left'); G.move(r, 'right');
  assert.strictEqual(l.board[0][0], 4);
  assert.strictEqual(r.board[0][3], 4);
});

console.log('\n[撤销 / hold]\n');
t('撤销恢复棋盘并递减次数', () => {
  const s = G.newState(7, 0), snap = G.snapshot(s);
  G.move(s, 'left');
  const u = G.undo(s, snap);
  assert.strictEqual(u.undosLeft, G.UNDO_LIMIT - 1);
  assert.deepStrictEqual(u.board, snap.board);
  assert.strictEqual(u.score, snap.score);
});
t('撤销次数耗尽后拒绝', () => {
  const s = G.newState(7, 0), snap = G.snapshot(s);
  s.undosLeft = 0;
  assert.strictEqual(G.undo(s, snap), s);
});
t('hold 每回合限一次并交换预览', () => {
  const s = G.newState(9, 0);
  assert.strictEqual(G.hold(s), true);
  assert.strictEqual(G.hold(s), false);
  assert.ok(s.hold !== 0 && s.next !== 0);
});

console.log('\n[结束与等级]\n');
t('满盘无相邻可合并 → over', () => {
  const s = G.newState(1, 0);
  s.board = board([[2, 4, 2, 4], [4, 2, 4, 2], [2, 4, 2, 4], [4, 2, 4, 2]]);
  assert.strictEqual(G.canMove(s.board), false);
});
t('有空格可继续', () => {
  const s = G.newState(1, 0);
  s.board = board([[2, 4, 2, 4], [4, 2, 4, 2], [2, 4, 2, 4], [4, 2, 4, 0]]);
  assert.strictEqual(G.canMove(s.board), true);
});
t('等级随最大方块提升', () => {
  const s = G.newState(1, 0);
  s.board = board([[2, 0, 0, 0], [0, 0, 0, 0], [0, 0, 0, 0], [0, 0, 0, 0]]);
  assert.strictEqual(G.level(s), 0);
  s.board[0][0] = 2048;
  assert.strictEqual(G.level(s), 10);
});

console.log('\n[存档 / 每日挑战 / 无障碍]\n');
t('存档往返一致（含成就）', () => {
  const s = G.newState(3, 100);
  G.move(s, 'up');
  const r = G.restore(G.save(s), 100);
  assert.deepStrictEqual(G.save(r), G.save(s));
});
t('非法存档安全回退到可玩新局', () => {
  const s = G.restore('{not json', 100);
  assert.ok(G.maxTile(s.board) > 0);
});
t('存档裁剪非法数值（防注入/超大）', () => {
  const s = G.restore({ board: [[3, 0, 0, 0], [0, 0, 0, 0], [0, 0, 0, 0], [0, 0, 0, 0]],
                        score: -50, moves: 1e12 }, 0);
  // 3 不是 2 的幂，必须被裁剪；全空棋盘会触发「自动开新局」保护，
  // 因此该格只能是 0 或合法的 2 的幂，绝不能原样保留非法值。
  const v = s.board[0][0];
  assert.ok(v === 0 || (v > 0 && (v & (v - 1)) === 0), '非法方块值不得原样保留');
  assert.notStrictEqual(v, 3);
  assert.strictEqual(s.score, 0);
});
t('每日挑战种子按本地日期确定性', () => {
  assert.strictEqual(G.dailySeed(2026, 11, 5), G.dailySeed(2026, 11, 5));
  assert.notStrictEqual(G.dailySeed(2026, 11, 5), G.dailySeed(2026, 11, 6));
});
t('色盲友好形状按值唯一', () => {
  assert.notStrictEqual(G.shapeFor(2), G.shapeFor(4));
  assert.notStrictEqual(G.shapeFor(8), G.shapeFor(16));
  assert.strictEqual(typeof G.shapeFor(2048), 'string');
});
t('成就解锁（初次合成）', () => {
  const s = G.newState(1, 0);
  s.board = board([[2, 2, 0, 0], [0, 0, 0, 0], [0, 0, 0, 0], [0, 0, 0, 0]]);
  G.move(s, 'left');
  assert.ok(s.achievements.indexOf('初次合成') >= 0);
});

console.log('\n----------------------------------------------------------');
console.log(' 结果: ' + passed + ' passed, ' + failed + ' failed, ' + (passed + failed) + ' total');
console.log('==========================================================');
process.exit(failed === 0 ? 0 : 1);
