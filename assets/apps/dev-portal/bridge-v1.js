/*
 * xqly-bridge/1 · 开发者平台独立桥封装
 *
 * 沙箱约束（与原生 BridgeV1 / BridgeProtocol 两层校验一一对应）：
 *  - 本模块不引入任何外部脚本、不发网络请求、不读 localStorage 之外的凭据、不持有密钥。
 *  - 出站只走唯一原生接口 XQLYBridge.postMessage，外层 type 固定为 "devportal"，
 *    args 只允许一个 envelope 字符串；内层再按 xqly-bridge/1 做方法/权限/参数校验。
 *  - 入站只由原生 portalReply() 调用 window.XQApp.onMessage，且只投递到当前可见浮层。
 *  - 零常驻定时器：过期调用在下一次 call 时惰性清理，避免隐藏 WebView 后台空转。
 */
(function (root) {
  'use strict';
  if (root.XQApp) return;

  var OUTER_V = 2;
  var BRIDGE = 'xqly-bridge/1';
  var APP = 'dev-portal';
  var MAX_ENVELOPE = 1900;      // 外层 BridgeProtocol.MAX_ARG_LEN=2048，留余量
  var TIMEOUT_MS = 12000;

  var seq = 0;
  var pending = Object.create(null);
  var queue = [];
  var flushed = false;

  /** 16 条方法的静态镜像，仅用于 UI 展示权限矩阵；真正的判定在原生 BridgeV1。 */
  var METHODS = [
    'xqly.env.getInfo', 'xqly.ui.setTitle', 'xqly.ui.showFeedback',
    'xqly.theme.subscribe', 'xqly.nav.open', 'xqly.link.open',
    'xqly.credential.getEndpoint', 'xqly.credential.unlock',
    'xqly.devportal.connect', 'xqly.devportal.send', 'xqly.devportal.approveTool',
    'xqly.file.pickSave', 'xqly.clipboard.copy', 'xqly.ota.check',
    'xqly.telemetry.report', 'xqly.error.report'
  ];

  /** 默认不授予的能力：凭据 / 遥测 / 工具执行。UI 如实标注，不做虚假可点。 */
  var DENIED_BY_DEFAULT = {
    'xqly.credential.getEndpoint': 'credential',
    'xqly.credential.unlock': 'credential',
    'xqly.telemetry.report': 'telemetry:optional',
    'xqly.devportal.approveTool': 'user_approval'
  };

  function id() { return 'd' + (++seq) + '_' + Date.now().toString(36); }
  function origin() { return root.location.href; }

  function nativePost(payload) {
    try {
      if (root.XQLYBridge && typeof root.XQLYBridge.postMessage === 'function') {
        root.XQLYBridge.postMessage(payload);
        return true;
      }
    } catch (e) { /* 接口未就绪或页面不可信：静默入队，不重试攻击 */ }
    return false;
  }

  function flush() {
    flushed = true;
    var list = queue.slice();
    queue.length = 0;
    for (var i = 0; i < list.length; i++) nativePost(list[i]);
  }

  /** 惰性清理过期 pending：无 setInterval，隐藏浮层零后台开销。 */
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

  /**
   * 发送一条调用。cb 收到原生响应对象 { bridge, appId, id, ok, method, result?, error? }。
   * 返回本次调用 id（不含点号，同时满足外层与内层的 id 正则）。
   */
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
        v: OUTER_V, id: cid, type: 'devportal',
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

  /** 原生 portalReply() 的唯一回投入口。 */
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

  /** 关闭浮层走外层桥的 closeOverlay（不是 devportal 作用域），由原生收敛所有浮层。 */
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
