/*
 * AI 工作台前端桥封装 + 资源静态契约测试（零依赖：node tests/ai-workbench.test.js）
 *
 * 用 vm 在隔离沙箱里加载 assets/apps/ai-workbench/bridge-v1.js，
 * 验证外层 workbench 消息形状、xqly-bridge/1 envelope、appId=ai-workbench、
 * 只响应匹配 id/版本/appId、零常驻定时器、离线队列，
 * 以及前端资源不引入外部脚本 / 网络 / 密钥 API / 硬编码色值。
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
const DIR = path.join(ROOT, 'assets', 'apps', 'ai-workbench');
const HREF = 'file:///android_asset/apps/ai-workbench/index.html';
const BRIDGE = fs.readFileSync(path.join(DIR, 'bridge-v1.js'), 'utf8');

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
      readyState: 'complete',
      addEventListener: (n, fn) => { listeners[n] = fn; },
      dispatchEvent: () => {},
    },
    dispatchEvent: () => {},
    addEventListener: (n, fn) => { listeners[n] = fn; },
  };
  const sandbox = {
    window: win,
    CustomEvent: function (type, opts) { this.type = type; this.detail = opts && opts.detail; },
    setInterval: () => { throw new Error('禁止注册 setInterval'); },
    setTimeout: () => { throw new Error('bridge-v1.js 不应注册 setTimeout'); },
    console: console,
  };
  vm.createContext(sandbox);
  vm.runInContext(BRIDGE, sandbox, { filename: 'bridge-v1.js' });
  return { win, sent, listeners, sandbox };
}

function outer(sent) { return JSON.parse(sent[sent.length - 1]); }
function inner(outerMsg) { return JSON.parse(outerMsg.args.envelope); }

console.log('\n── AI 工作台 · 前端桥封装 ──');

t('XQApp 暴露版本 / appId / 15 条工作台方法', () => {
  const { win } = load();
  assert.strictEqual(win.XQApp.version, 'xqly-bridge/1');
  assert.strictEqual(win.XQApp.appId, 'ai-workbench');
  assert.strictEqual(win.XQApp.methods.length, 15);
  assert.ok(win.XQApp.methods.every(m => m.startsWith('xqly.workbench.')));
});

t('call 产出合法外层 workbench 消息 + 内层 envelope', () => {
  const { win, sent } = load({ ready: 'complete' });
  win.XQApp.call('xqly.workbench.snapshot', {}, () => {});
  assert.strictEqual(sent.length, 1);
  const o = outer(sent), i = inner(o);
  assert.strictEqual(o.v, 2);
  assert.strictEqual(o.type, 'workbench');
  assert.strictEqual(o.origin, HREF);
  assert.strictEqual(i.bridge, 'xqly-bridge/1');
  assert.strictEqual(i.appId, 'ai-workbench');
  assert.strictEqual(i.method, 'xqly.workbench.snapshot');
  assert.ok(/^w\d+_[0-9a-z]+$/.test(i.id), '工作台 id 格式');
  assert.strictEqual(o.id, i.id, '内外 id 一致');
});

t('onMessage 只接受匹配版本 + appId + id 的响应', () => {
  const { win } = load({ ready: 'complete' });
  let hits = 0;
  const cid = win.XQApp.call('xqly.workbench.env', {}, () => { hits++; });
  win.XQApp.onMessage({ bridge: 'xqly-bridge/0', appId: 'ai-workbench', id: cid, ok: true });
  assert.strictEqual(hits, 0, '版本不符不回调');
  win.XQApp.onMessage({ bridge: 'xqly-bridge/1', appId: 'dev-portal', id: cid, ok: true });
  assert.strictEqual(hits, 0, 'appId 不符不回调');
  win.XQApp.onMessage({ bridge: 'xqly-bridge/1', appId: 'ai-workbench', id: 'unknown', ok: true });
  assert.strictEqual(hits, 0, '未知 id 不回调');
  win.XQApp.onMessage({ bridge: 'xqly-bridge/1', appId: 'ai-workbench', id: cid, ok: true, result: { capability: 'local-only' } });
  assert.strictEqual(hits, 1, '匹配响应回调');
});

t('越权能力如实标注不授予', () => {
  const { win } = load();
  const d = win.XQApp.deniedByDefault;
  assert.ok(d['xqly.telemetry.report']);
  assert.ok(d['xqly.devportal.connect']);
  assert.ok(d['xqly.credential.unlock']);
  assert.ok(d['xqly.devportal.approveTool']);
});

t('零常驻定时器 / 零网络 API / 零密钥 API', () => {
  const app = fs.readFileSync(path.join(DIR, 'app.js'), 'utf8');
  const bridge = BRIDGE;
  const all = app + bridge;
  for (const bad of ['setInterval(', 'fetch(', 'XMLHttpRequest', 'eval(', 'localStorage', 'api_key', 'Authorization']) {
    assert.ok(!all.includes(bad), '出现禁止调用 ' + bad);
  }
  assert.ok(!all.includes('innerHTML'), '不应拼接 HTML');
});

t('工作台四资源全部被 MANIFEST.json 收录', () => {
  const m = JSON.parse(fs.readFileSync(path.join(ROOT, 'assets', 'MANIFEST.json'), 'utf8'));
  const paths = new Set(m.files.map(f => f.p));
  for (const n of ['index.html', 'app.css', 'app.js', 'bridge-v1.js']) {
    assert.ok(paths.has('apps/ai-workbench/' + n), '清单缺少 ' + n);
  }
});

t('index.html 引入共享令牌且无外部脚本', () => {
  const html = fs.readFileSync(path.join(DIR, 'index.html'), 'utf8');
  assert.ok(html.includes('design-tokens.css'), '缺少共享令牌');
  assert.ok(!html.includes('<script src="http'), '外部脚本');
  assert.ok(html.includes('不内置'), '未声明能力边界');
});

console.log('\n' + '='.repeat(58));
console.log(' 结果: ' + passed + ' passed, ' + failed + ' failed, ' + (passed + failed) + ' total');
console.log('='.repeat(58));
process.exitCode = failed === 0 ? 0 : 1;
