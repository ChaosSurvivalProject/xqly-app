/*
 * 星穹旅驿 · 页面侧桥 shim（v2）
 *
 * 为什么需要它：原生 injectBridgeShim 在 WebViewClient.onPageFinished 才注入，
 * 而内置页面在解析阶段就要发 getState / gameLoad。原生 shim 里的
 * `if (window.XQ) return;` 保证本文件优先、不会被覆盖，因此这里必须自带
 * onMessage / pause / resume 全套能力。
 *
 * 安全约束：
 *  - 只暴露 postMessage 一个原生接口，消息一律带版本、id、类型、origin。
 *  - origin 用 location.href，由 BridgeProtocol 与原生记录的页面 URL 做同源比对。
 *  - 不读取、不缓存、不回传任何令牌或文件内容；失败只入队，不重试攻击。
 */
(function (root) {
  'use strict';
  if (root.XQ) return;

  var seq = 0;
  var queue = [];
  var flushed = false;
  var BRIDGE_VERSION = 2;

  function nativePost(payload) {
    try {
      if (root.XQLYBridge && typeof root.XQLYBridge.postMessage === 'function') {
        root.XQLYBridge.postMessage(payload);
        return true;
      }
    } catch (e) { /* 接口尚未就绪或页面不可信：静默入队 */ }
    return false;
  }

  function flush() {
    flushed = true;
    var pending = queue.slice();
    queue.length = 0;
    for (var i = 0; i < pending.length; i++) nativePost(pending[i]);
  }

  function emit(name) {
    try { root.document.dispatchEvent(new Event(name)); } catch (e) { }
  }

  var XQ = {
    version: BRIDGE_VERSION,

    /** 唯一的出站通道；type 与参数必须落在 BridgeProtocol 白名单内。 */
    post: function (type, args) {
      var payload;
      try {
        payload = JSON.stringify({
          v: BRIDGE_VERSION,
          id: 'p' + (++seq) + '_' + Date.now(),
          type: String(type),
          args: args || {},
          origin: root.location.href
        });
      } catch (e) { return; }
      if (!nativePost(payload) && !flushed) {
        queue.push(payload);
        if (queue.length > 64) queue.shift();   // 有界队列，防内存增长
      }
    },

    /** 由原生 reply() 调用；统一派发为 xqly CustomEvent，页面自行按 type/id 过滤。 */
    onMessage: function (o) {
      try { root.dispatchEvent(new CustomEvent('xqly', { detail: o })); } catch (e) { }
    },

    /** 生命周期：离开页面时暂停音频、定时器与非必要动画。 */
    pause: function () { emit('xqlypause'); },
    resume: function () { emit('xqlyresume'); },

    /** 供原生注入完成后补发排队消息（若原生未跳过注入）。 */
    __flush: flush
  };

  root.XQ = XQ;

  if (root.document.readyState === 'loading') {
    root.document.addEventListener('DOMContentLoaded', flush, { once: true });
  } else {
    flush();
  }
})(window);
