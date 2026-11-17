/*
 * 开发者平台 · 应用外壳交互（零外部依赖、零网络请求、零密钥）
 *
 * 所有跨层能力都经 XQApp.call() → 外层 BridgeProtocol → 内层 BridgeV1 双重校验。
 * 页面如实呈现「被拒绝」的结果，不用本地假数据伪装成已连接 / 已执行。
 */
(function () {
  'use strict';
  function $(id) { return document.getElementById(id); }
  var feedbackTimer = null;

  function feedback(level, text) {
    var bar = $('feedback');
    if (!bar) return;
    bar.setAttribute('data-level', level || 'info');
    $('feedback-text').textContent = String(text || '');
    bar.classList.add('on');
    if (feedbackTimer) clearTimeout(feedbackTimer);
    feedbackTimer = setTimeout(function () {
      bar.classList.remove('on');
      feedbackTimer = null;
    }, (level === 'error' || level === 'warning') ? 6000 : 4000);
  }

  // 页面隐藏时清掉一次性提示定时器，避免隐藏 WebView 后台空转。
  document.addEventListener('visibilitychange', function () {
    if (document.hidden && feedbackTimer) {
      clearTimeout(feedbackTimer);
      feedbackTimer = null;
      var bar = $('feedback');
      if (bar) bar.classList.remove('on');
    }
  });

  /** 统一处理响应：拒绝与超时都显式可见，不静默吞掉。 */
  function after(o, onOk) {
    if (!o) { feedback('error', '未收到原生响应'); return; }
    if (!o.ok) {
      var reason = o.error || 'UNKNOWN';
      feedback(reason === 'TIMEOUT' ? 'warning' : 'error', '原生已拒绝 · ' + reason);
      return;
    }
    onOk(o.result || {});
  }

  /** 用 DOM API 构建卡片，不做 HTML 字符串拼接，避免任何注入面。 */
  function el(tag, cls, text) {
    var n = document.createElement(tag);
    if (cls) n.className = cls;
    if (text != null) n.textContent = String(text);
    return n;
  }

  function renderApps(o) {
    var box = $('apps');
    if (!box) return;
    var list = o && o.ok && o.result && o.result.apps;
    if (!list || !list.length) {
      box.textContent = '';
      box.appendChild(el('p', 'dim', '应用注册表为空，或本次读取被原生拒绝。'));
      return;
    }
    box.textContent = '';
    list.forEach(function (a) {
      var card = el('div', 'app');
      var icon = el('div', 'icon', a.iconLabel || '◇');
      var meta = el('div', 'meta');
      meta.appendChild(el('b', null, a.displayName || a.id));
      meta.appendChild(el('span', null, a.subtitle || ''));
      meta.appendChild(el('span', 'mono', a.route || ''));
      var tag = el('div', 'tag', a.offlineCapability === 'FULL' ? '离线可用'
          : a.offlineCapability === 'READ_ONLY' ? '离线只读' : '仅在线');
      card.appendChild(icon);
      card.appendChild(meta);
      card.appendChild(tag);
      box.appendChild(card);
    });
  }

  function renderMatrix() {
    var body = $('matrix-body');
    if (!body || !window.XQApp) return;
    body.textContent = '';
    XQApp.methods.forEach(function (m) {
      var tr = document.createElement('tr');
      tr.appendChild(el('td', 'mono', m));
      var denied = XQApp.deniedByDefault[m];
      tr.appendChild(denied
          ? el('td', 'pill-no', '不授予 · ' + denied)
          : el('td', 'pill-ok', '最小权限内'));
      body.appendChild(tr);
    });
    $('method-count').textContent = XQApp.methods.length + ' 条';
  }

  function setLink(state, note) {
    var dot = $('link-dot'), txt = $('link-text'), n = $('link-note');
    if (dot) dot.setAttribute('data-s', state);
    if (txt) txt.textContent = {
      DISCONNECTED: '未连接', CHECKING: '检查中', CONNECTED: '已连接', ERROR: '连接错误'
    }[state] || state;
    if (n) n.textContent = note || '';
  }

  function init() {
    renderMatrix();

    XQApp.call('xqly.env.getInfo', {}, function (o) {
      after(o, function (r) {
        var offline = !!r.offline;
        $('offline-banner').classList.toggle('hidden', !offline);
        if (offline) {
          setLink('DISCONNECTED', '离线只读：远程会话不可用');
          feedback('warning', '离线只读模式：仅保留外壳与帮助内容');
        }
      });
    });

    XQApp.call('xqly.theme.subscribe', {}, function (o) {
      after(o, function (r) {
        $('theme-badge').textContent = (r.theme || 'dark') + ' · ' +
            (r.mode || 'glass') + ' · ' + (r.breakpoint || 'phone');
      });
    });

    XQApp.call('xqly.nav.open', { appId: 'dev-portal' }, renderApps);
  }

  function bind() {
    $('feedback-close').addEventListener('click', function () {
      $('feedback').classList.remove('on');
      if (feedbackTimer) { clearTimeout(feedbackTimer); feedbackTimer = null; }
    });
    $('btn-close').addEventListener('click', function () { XQApp.close(); });
    $('btn-drawer').addEventListener('click', function () {
      $('section-apps').scrollIntoView({ behavior: 'smooth', block: 'start' });
    });
    $('btn-help').addEventListener('click', function () {
      $('section-help').scrollIntoView({ behavior: 'smooth', block: 'start' });
    });

    $('btn-connect').addEventListener('click', function () {
      setLink('CHECKING', '正在校验端点白名单…');
      XQApp.call('xqly.devportal.connect',
          { endpointId: 'default', transport: 'wss' }, function (o) {
        if (!o || !o.ok) {
          setLink('ERROR', '端点未配置或越权，已拒绝');
          feedback('warning', '连接被拒绝 · ' + ((o && o.error) || 'NO_RESPONSE') +
              '（v1 不预置服务器，不伪造连接成功）');
          return;
        }
        var r = o.result || {};
        setLink(r.link || 'CHECKING', r.note || '等待原生配对确认');
        feedback('info', r.note || '已提交连接请求');
      });
    });

    $('btn-ota').addEventListener('click', function () {
      XQApp.call('xqly.ota.check', { channel: 'stable' }, function (o) {
        after(o, function (r) {
          feedback('success', '更新渠道：' + (r.channel || 'stable') + '（仅查询，不下载不安装）');
        });
      });
    });

    $('btn-send').addEventListener('click', function () {
      var text = $('msg').value || '';
      $('stream').textContent = '已提交发送请求，等待原生校验…';
      XQApp.call('xqly.devportal.send',
          { requestId: 'r' + Date.now().toString(36), payload: text.slice(0, 512) },
          function (o) {
        if (!o || !o.ok) {
          $('stream').textContent = '发送被拒绝：' + ((o && o.error) || 'NO_RESPONSE') +
              '。未建立已确认连接前不会产生任何流式内容。';
          feedback('warning', '发送被拒绝 · ' + ((o && o.error) || 'NO_RESPONSE'));
          return;
        }
        var r = o.result || {};
        $('stream').textContent = r.accepted
            ? '已接受发送请求。' : (r.note || '发送未被接受');
        feedback(r.accepted ? 'success' : 'info', r.accepted ? '已发送' : (r.note || '未发送'));
      });
    });

    $('btn-approve').addEventListener('click', function () {
      XQApp.call('xqly.devportal.approveTool',
          { toolId: 'demo.tool', scope: 'workspace', grant: 'once' }, function (o) {
        if (!o || !o.ok) {
          feedback('error', '工具审批被拒绝 · ' + ((o && o.error) || 'NO_RESPONSE') +
              '（v1 不提供工具执行能力）');
          return;
        }
        feedback('info', '审批请求已记录（v1 不执行任何工具）');
      });
    });
  }

  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', function () { bind(); init(); }, { once: true });
  } else { bind(); init(); }
})();
