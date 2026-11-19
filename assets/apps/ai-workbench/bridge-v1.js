/*
 * xqly-bridge/1 · AI 工作台独立桥封装
 *
 * 沙箱约束（与原生 BridgeV1 / BridgeProtocol 两层校验一一对应）：
 *  - 本模块不引入任何外部脚本、不发网络请求、不读密钥；
 *  - 出站只走唯一原生接口 XQLYBridge.postMessage，外层 type 固定为 "workbench"，
 *    args 只允许一个 envelope 字符串；内层按 xqly-bridge/1 做方法/权限/参数校验。
 *  - 入站只由原生 workbenchReply() 调用 window.XQApp.onMessage，且只投递到当前可见浮层。
 *  - 零常驻定时器：过期调用在下一次 call 时惰性清理。
 */
(function (root) {
  'use strict';
  if (root.XQApp) return;

  var OUTER_V = 2;
  var BRIDGE = 'xqly-bridge/1';
  var APP = 'ai-workbench';
  var MAX_ENVELOPE = 1900;
  var TIMEOUT_MS = 12000;

  var seq = 0;
  var pending = Object.create(null);
  var queue = [];
  var flushed = false;

  // 工作台 15 条方法的静态镜像，仅用于 UI 展示权限矩阵。
  var METHODS = [
    'xqly.workbench.env', 'xqly.workbench.snapshot',
    'xqly.workbench.session.list', 'xqly.workbench.session.create',
    'xqly.workbench.session.append', 'xqly.workbench.session.remove',
    'xqly.workbench.provider.list', 'xqly.workbench.provider.mark',
    'xqly.workbench.plugin.list', 'xqly.workbench.plugin.register',
    'xqly.workbench.plugin.toggle', 'xqly.workbench.dropzone.check',
    'xqly.workbench.selfcheck', 'xqly.workbench.log.append',
    'xqly.workbench.log.query'
  ];

  // 明确不授予的能力：网络 / 凭据 / 工具审批 / 遥测 / 诊断上报。
  var DENIED_BY_DEFAULT = {
    'xqly.devportal.connect': 'network',
    'xqly.credential.unlock': 'credential',
    'xqly.devportal.approveTool': 'user_approval',
    'xqly.telemetry.report': 'telemetry',
    'xqly.error.report': 'diagnostics'
  };

  function id() { return 'w' + (++seq) + '_' + Date.now().toString(36); }
  function origin() { return root.location.href; }

  function nativePost(payload) {
    try {
      if (root.XQLYBridge && typeof root.XQLYBridge.postMessage === 'function') {
        root.XQLYBridge.postMessage(payload);
        return true;
      }
    } catch (e) { }
    return false;
  }

  function flush() {
    flushed = true;
    var list = queue.slice();
    queue.length = 0;
    for (var i = 0; i < list.length; i++) nativePost(list[i]);
  }

  function sweep() {
    var now = Date.now();
    for (var k in pending) {
      if (!Object.prototype.hasOwnProperty.call(pending, k)) continue;
      if (now - pending[k].at > TIMEOUT_MS) {
        var p = pending[k];
        delete pending[k];
        if (p.cb) { try { p.cb({ ok: false, error: 'TIMEOUT', id: k }); } catch (e) { } }
      }
    }
  }

  function call(method, params, cb) {
    sweep();
    var cid = id();
    var inner;
    try {
      inner = JSON.stringify({
        bridge: BRIDGE, appId: APP, id: cid,
        method: String(method), params: params || {}
      });
    } catch (e) { if (cb) cb({ ok: false, error: 'BAD_PARAMS' }); return cid; }
    if (inner.length > MAX_ENVELOPE) {
      if (cb) cb({ ok: false, error: 'TOO_LARGE', id: cid });
      return cid;
    }
    var outer;
    try {
      outer = JSON.stringify({
        v: OUTER_V, id: cid, type: 'workbench',
        args: { envelope: inner }, origin: origin()
      });
    } catch (e) { if (cb) cb({ ok: false, error: 'BAD_ENVELOPE' }); return cid; }

    pending[cid] = { cb: cb, at: Date.now() };
    if (!nativePost(outer) && !flushed) {
      queue.push(outer);
      if (queue.length > 32) { queue.shift(); delete pending[cid]; }
    }
    return cid;
  }

  function onMessage(o) {
    if (!o || o.bridge !== BRIDGE || o.appId !== APP) return;
    var p = pending[o.id];
    if (p) {
      delete pending[o.id];
      if (p.cb) { try { p.cb(o); } catch (e) { } }
    }
    try {
      root.dispatchEvent(new CustomEvent('xqlyapp', { detail: o }));
    } catch (e) { }
  }

  function close() {
    try {
      root.XQLYBridge.postMessage(JSON.stringify({
        v: OUTER_V, id: id(), type: 'closeOverlay', args: {}, origin: origin()
      }));
    } catch (e) { }
  }

  root.XQApp = {
    version: BRIDGE,
    appId: APP,
    methods: METHODS,
    deniedByDefault: DENIED_BY_DEFAULT,
    call: call,
    onMessage: onMessage,
    close: close
  };

  if (root.document.readyState === 'loading') {
    root.document.addEventListener('DOMContentLoaded', flush, { once: true });
  } else { flush(); }
})(window);
