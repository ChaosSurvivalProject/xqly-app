/*
 * dev-portal 前端桥封装 + 资源静态契约测试（零依赖：node tests/dev-portal.test.js）
 *
 * 用 vm 在隔离沙箱里加载 assets/apps/dev-portal/bridge-v1.js，
 * 验证外层 BridgeProtocol 消息形状、xqly-bridge/1 envelope、
 * 幂等 id、只响应匹配 id/版本/appId、零常驻定时器、离线队列，
 * 以及前端资源不引入外部脚本 / 不硬编码色值 / 全部被内容清单收录。
 */
'use strict';
const assert = require('assert');
const fs = require('fs');
const path = require('path');
const vm = require('vm');

let passed = 0, failed = 0;
function t(name, fn) {
  try { fn(); passed++; console.log('  PASS  ' + name); }
  catch (e) { failed++; console.log('  FAIL  ' + name + '  ->  ' + e.message); }
}

const ROOT = path.join(__dirname, '..');
const DIR = path.join(ROOT, 'assets', 'apps', 'dev-portal');
const HREF = 'file:///android_asset/apps/dev-portal/index.html';

function load(opts) {
  const o = opts || {};
  const sent = [];
  const listeners = {};
  const win = {
    location: { href: HREF },
    XQLYBridge: {
      postMessage: (p) => {
        if (o.throwOnPost) throw new Error('接口未就绪');
        sent.push(p);
      },
    },
    document: {
      readyState: o.defer ? 'loading' : 'complete',
      addEventListener: (n, fn) => { listeners[n] = fn; },
      dispatchEvent: () => {},
    },
    dispatchEvent: () => {},
    addEventListener: (n, fn) => { listeners[n] = fn; },
  };
  const sandbox = {
    window: win,
    CustomEvent: function (type, opts) {
      this.type = type;
      this.detail = opts && opts.detail;
    },
    // 若模块注册任何常驻定时器，加载即失败：性能红线。
    setInterval: () => { throw new Error('禁止注册 setInterval'); },
    setTimeout: () => { throw new Error('bridge-v1.js 不应注册 setTimeout'); },
    console: console,
  };
  vm.createContext(sandbox);
  vm.runInContext(fs.readFileSync(path.join(DIR, 'bridge-v1.js'), 'utf8'),
    sandbox, { filename: 'bridge-v1.js' });
  return { win, sent, listeners, sandbox };
}

function outer(p) { return JSON.parse(p); }
function inner(p) { return JSON.parse(outer(p).args.envelope); }

console.log('==========================================================');
console.log(' 星穹旅驿 · dev-portal 前端桥与资源契约 Node 单元测试');
console.log(' Node: ' + process.version);
console.log('==========================================================\n[桥封装]\n');

t('模块导出版本 / appId / 16 条方法与默认拒绝清单', () => {
  const { win } = load();
  assert.strictEqual(win.XQApp.version, 'xqly-bridge/1');
  assert.strictEqual(win.XQApp.appId, 'dev-portal');
  assert.strictEqual(win.XQApp.methods.length, 16);
  assert.strictEqual(win.XQApp.deniedByDefault['xqly.telemetry.report'], 'telemetry:optional');
  assert.strictEqual(win.XQApp.deniedByDefault['xqly.devportal.approveTool'], 'user_approval');
});

t('出站消息严格符合外层 BridgeProtocol：type=devportal / 单 envelope / 同源 origin', () => {
  const { win, sent } = load();
  win.XQApp.call('xqly.env.getInfo', {}, () => {});
  assert.strictEqual(sent.length, 1);
  const m = outer(sent[0]);
  assert.strictEqual(m.v, 2);
  assert.strictEqual(m.type, 'devportal');
  assert.strictEqual(m.origin, HREF);
  assert.deepStrictEqual(Object.keys(m.args), ['envelope']);
  assert.strictEqual(typeof m.args.envelope, 'string');
  assert.ok(/^[A-Za-z0-9_.-]+$/.test(m.id));
});

t('envelope 内层符合 xqly-bridge/1：bridge/appId/id/method/params 完整', () => {
  const { win, sent } = load();
  const params = { channel: 'stable' };
  const cid = win.XQApp.call('xqly.ota.check', params, () => {});
  const e = inner(sent[0]);
  assert.strictEqual(e.bridge, 'xqly-bridge/1');
  assert.strictEqual(e.appId, 'dev-portal');
  assert.strictEqual(e.method, 'xqly.ota.check');
  assert.deepStrictEqual(e.params, params);
  assert.strictEqual(e.id, cid);
  assert.strictEqual(e.id, outer(sent[0]).id);
});

t('每次调用 id 唯一且不含点号（同时满足内外层 id 正则）', () => {
  const { win, sent } = load();
  const ids = [];
  for (let i = 0; i < 20; i++) ids.push(win.XQApp.call('xqly.ui.setTitle', { title: 't' }, () => {}));
  assert.strictEqual(sent.length, 20);
  assert.strictEqual(new Set(ids).size, 20);
  for (const id of ids) assert.ok(/^[A-Za-z0-9_-]+$/.test(id));
});

t('onMessage 只接受匹配版本与 appId 的响应', () => {
  const { win } = load();
  let hits = 0;
  const cid = win.XQApp.call('xqly.env.getInfo', {}, () => { hits++; });
  win.XQApp.onMessage({ bridge: 'xqly-bridge/0', appId: 'dev-portal', id: cid, ok: true });
  assert.strictEqual(hits, 0, '版本不符不应回调');
  win.XQApp.onMessage({ bridge: 'xqly-bridge/1', appId: 'web', id: cid, ok: true });
  assert.strictEqual(hits, 0, 'appId 不符不应回调');
  win.XQApp.onMessage({ bridge: 'xqly-bridge/1', appId: 'dev-portal', id: 'unknown', ok: true });
  assert.strictEqual(hits, 0, '未知 id 不应回调');
  win.XQApp.onMessage({ bridge: 'xqly-bridge/1', appId: 'dev-portal', id: cid, ok: true, result: { theme: 'dark' } });
  assert.strictEqual(hits, 1, '匹配响应应回调');
});

t('同一 id 的响应只回调一次（幂等去重）', () => {
  const { win } = load();
  let hits = 0;
  const cid = win.XQApp.call('xqly.env.getInfo', {}, () => { hits++; });
  const msg = { bridge: 'xqly-bridge/1', appId: 'dev-portal', id: cid, ok: true };
  win.XQApp.onMessage(msg);
  win.XQApp.onMessage(msg);
  assert.strictEqual(hits, 1);
});

t('超长 envelope 在本地被拒绝，不产生出站消息', () => {
  const { win, sent } = load();
  let err = '';
  win.XQApp.call('xqly.ui.setTitle', { title: 'x'.repeat(3000) }, (o) => { err = o.error; });
  assert.strictEqual(sent.length, 0);
  assert.strictEqual(err, 'TOO_LARGE');
});

t('close() 走外层 closeOverlay 通道，而非 devportal 作用域', () => {
  const { win, sent } = load();
  win.XQApp.close();
  assert.strictEqual(sent.length, 1);
  const m = outer(sent[0]);
  assert.strictEqual(m.type, 'closeOverlay');
  assert.deepStrictEqual(m.args, {});
  assert.strictEqual(m.v, 2);
});

t('接口未就绪时有界入队，DOMContentLoaded 后一次性 flush', () => {
  const { win, sent, listeners } = load({ defer: true, throwOnPost: true });
  for (let i = 0; i < 3; i++) win.XQApp.call('xqly.env.getInfo', {}, () => {});
  assert.strictEqual(sent.length, 0);
  win.XQLYBridge.postMessage = (p) => { sent.push(p); };
  listeners['DOMContentLoaded']();
  assert.strictEqual(sent.length, 3);
  for (const p of sent) assert.strictEqual(outer(p).type, 'devportal');
});

t('加载过程零常驻定时器（setInterval/setTimeout 均未被调用）', () => {
  assert.doesNotThrow(() => load());
});

console.log('\n[前端资源静态契约]\n');

const read = (f) => fs.readFileSync(path.join(DIR, f), 'utf8');

t('四个资源齐备且 HTML 引入共享设计令牌、无外部脚本', () => {
  for (const f of ['index.html', 'app.css', 'app.js', 'bridge-v1.js']) {
    assert.ok(fs.existsSync(path.join(DIR, f)), '缺少 ' + f);
  }
  const html = read('index.html');
  assert.ok(html.includes('design-tokens.css'), '未引入共享令牌');
  assert.ok(!/<script[^>]+src="https?:/.test(html), '引入了外部脚本');
  assert.ok(html.includes('app.css') && html.includes('bridge-v1.js') && html.includes('app.js'));
});

t('前端零外部请求 / 零密钥 / 零常驻定时器', () => {
  const js = read('bridge-v1.js') + read('app.js');
  for (const bad of ['fetch(', 'XMLHttpRequest', 'eval(', 'new Image',
    'api_key', 'Authorization', 'setInterval(', 'https://']) {
    assert.ok(!js.includes(bad), '出现禁止调用 ' + bad);
  }
});

t('app.css 不硬编码色值，全部走 design-tokens.css 变量', () => {
  const css = read('app.css');
  assert.ok(!/#[0-9a-fA-F]{3,8}\b/.test(css), '出现硬编码 hex 色值');
  assert.ok(css.includes('var(--xq-background)'), '未使用共享背景令牌');
  assert.ok(css.includes('prefers-reduced-motion'), '缺少减弱动效适配');
  assert.ok(css.includes('focus-visible'), '缺少键盘焦点态');
  assert.ok(css.includes('min-height: 48px'), '触摸目标小于 48px');
});

t('app.js 用 DOM API 构建卡片，不拼接 innerHTML', () => {
  const js = read('app.js');
  assert.ok(!/\.innerHTML\s*=/.test(js), '出现 HTML 字符串拼接注入面');
  assert.ok(!js.includes('setInterval('), '出现常驻定时器');
  assert.ok(js.includes('visibilitychange'), '隐藏页面未清理提示定时器');
});

t('四个资源全部被 MANIFEST.json 收录', () => {
  const m = JSON.parse(fs.readFileSync(path.join(ROOT, 'assets', 'MANIFEST.json'), 'utf8'));
  const paths = new Set(m.files.map((f) => f.p));
  for (const f of ['index.html', 'app.css', 'app.js', 'bridge-v1.js']) {
    assert.ok(paths.has('apps/dev-portal/' + f), '清单未收录 ' + f);
  }
});

console.log('\n==========================================================');
console.log(' 结果: ' + passed + ' passed, ' + failed + ' failed, ' + (passed + failed) + ' total');
console.log('==========================================================');
process.exitCode = failed === 0 ? 0 : 1;
