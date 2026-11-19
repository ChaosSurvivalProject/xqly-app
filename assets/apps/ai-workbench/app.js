/*
 * AI 工作台 · 应用外壳交互（零外部依赖、零网络请求、零密钥）
 * 所有跨层能力都经 XQApp.call() → 外层 BridgeProtocol → 内层 BridgeV1 双重校验。
 * 页面如实呈现拒绝与边界，不用本地假数据伪装成已连接 / 已执行。
 */
(function () {
  'use strict';
  function $(id) { return document.getElementById(id); }
  var feedbackTimer = null;

  function el(tag, cls, text) {
    var n = document.createElement(tag);
    if (cls) n.className = cls;
    if (text != null) n.textContent = String(text);
    return n;
  }

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

  document.addEventListener('visibilitychange', function () {
    if (document.hidden && feedbackTimer) {
      clearTimeout(feedbackTimer);
      feedbackTimer = null;
      var bar = $('feedback');
      if (bar) bar.classList.remove('on');
    }
  });

  function after(o, onOk) {
    if (!o) { feedback('error', '未收到原生响应'); return; }
    if (!o.ok) {
      feedback('error', '原生拒绝 · ' + (o.error || 'UNKNOWN'));
      return;
    }
    onOk(o.result);
  }

  // ---- 运行状态 ----
  function loadState() {
    XQApp.call('xqly.workbench.snapshot', {}, function (o) {
      after(o, function (r) {
        if (!r) return;
        var h = r.selfcheck || {};
        $('health-badge').textContent = 'fail=' + (h.fail || 0) + ' · 运行时未提供=' + (h.unavailable || 0);
        $('log-count').textContent = (r.logSize || 0) + ' 条';
        renderChecks();
      });
    });
  }

  function renderChecks() {
    XQApp.call('xqly.workbench.selfcheck', {}, function (o) {
      after(o, function (items) {
        var box = $('check-list');
        box.textContent = '';
        if (!items || !items.length) { box.appendChild(el('p', 'dim', '自检未返回数据。')); return; }
        items.forEach(function (i) { addCheck(box, i.label, i.state, i.note); });
      });
    });
  }

  function addCheck(box, label, state, note) {
    var row = el('div', 'check');
    var dot = el('span', 'dot');
    dot.setAttribute('data-s', state);
    row.appendChild(dot);
    row.appendChild(el('b', null, label));
    row.appendChild(el('span', 'dim', ' · ' + note));
    box.appendChild(row);
  }

  // ---- 会话 ----
  function loadSessions() {
    XQApp.call('xqly.workbench.session.list', {}, function (o) {
      after(o, function (list) {
        var box = $('session-list');
        box.textContent = '';
        if (!list || !list.length) { box.appendChild(el('p', 'dim', '尚无会话。')); return; }
        list.forEach(function (s) {
          var item = el('div', 'item');
          var meta = el('div', 'meta');
          meta.appendChild(el('b', null, s.title || s.id));
          meta.appendChild(el('span', 'mono', s.id + ' · ' + s.messageCount + ' 条'));
          item.appendChild(meta);
          item.appendChild(el('span', 'tag', '本地'));
          box.appendChild(item);
        });
      });
    });
  }

  // ---- 供应商 ----
  function loadProviders() {
    XQApp.call('xqly.workbench.provider.list', {}, function (o) {
      after(o, function (list) {
        var box = $('provider-list');
        box.textContent = '';
        if (!list || !list.length) { box.appendChild(el('p', 'dim', '目录为空。')); return; }
        list.forEach(function (p) {
          var item = el('div', 'item');
          var meta = el('div', 'meta');
          meta.appendChild(el('b', null, p.displayName));
          meta.appendChild(el('span', 'mono', p.host ? p.host : '自建端点（由用户配置）'));
          item.appendChild(meta);
          var tag = el('span', 'tag' + (p.configured ? ' on' : ''), p.configured ? '已配置' : '未配置');
          item.appendChild(tag);
          box.appendChild(item);
        });
      });
    });
  }

  // ---- 插件 ----
  function loadPlugins() {
    XQApp.call('xqly.workbench.plugin.list', {}, function (o) {
      after(o, function (list) {
        var box = $('plugin-list');
        box.textContent = '';
        if (!list || !list.length) { box.appendChild(el('p', 'dim', '尚无插件。')); return; }
        list.forEach(function (p) {
          var item = el('div', 'item');
          var meta = el('div', 'meta');
          meta.appendChild(el('b', null, p.displayName));
          meta.appendChild(el('span', 'mono', p.id + ' · ' + p.source + ' · v' + p.version));
          item.appendChild(meta);
          var btn = el('button', 'btn sm' + (p.enabled ? ' primary' : ''), p.enabled ? '停用' : '启用');
          btn.setAttribute('type', 'button');
          btn.addEventListener('click', function () {
            XQApp.call('xqly.workbench.plugin.toggle', { id: p.id, enabled: !p.enabled }, function (r) {
              after(r, function () { loadPlugins(); });
            });
          });
          item.appendChild(btn);
          box.appendChild(item);
        });
      });
    });
  }

  function bind() {
    $('feedback-close').addEventListener('click', function () {
      $('feedback').classList.remove('on');
      if (feedbackTimer) { clearTimeout(feedbackTimer); feedbackTimer = null; }
    });
    $('btn-close').addEventListener('click', function () { XQApp.close(); });

    $('btn-session-create').addEventListener('click', function () {
      var id = ($('session-id').value || '').trim() ||
          ('s' + Date.now().toString(36));
      XQApp.call('xqly.workbench.session.create', { id: id, title: $('session-title').value || '未命名会话' }, function (o) {
        after(o, function () { $('session-title').value = ''; loadSessions(); feedback('success', '会话已创建（仅本地草稿）'); });
      });
    });
    $('btn-msg-append').addEventListener('click', function () {
      var id = ($('session-id').value || '').trim();
      if (!id) { feedback('warning', '请先填写会话 id 并新建会话'); return; }
      XQApp.call('xqly.workbench.session.append', {
        id: id, role: $('msg-role').value, text: $('msg-text').value || ''
      }, function (o) {
        after(o, function () { $('msg-text').value = ''; loadSessions(); feedback('info', '已追加到本地会话（不发送）'); });
      });
    });

    $('btn-plugin-add').addEventListener('click', function () {
      XQApp.call('xqly.workbench.plugin.register', {
        id: $('plugin-id').value || '', displayName: $('plugin-name').value || '',
        source: $('plugin-source').value, version: $('plugin-version').value || '1.0.0'
      }, function (o) {
        after(o, function () { loadPlugins(); feedback('info', '插件已登记（不加载任何代码）'); });
      });
    });

    $('btn-dropzone-check').addEventListener('click', function () {
      XQApp.call('xqly.workbench.dropzone.check', { raw: $('dropzone-path').value || '' }, function (o) {
        after(o, function (r) {
          if (!r) return;
          $('dropzone-result').textContent = r.allowed
              ? '判定：允许 · 安全标识 ' + (r.safeId || '')
              : '判定：拒绝 · ' + (r.reason || 'UNKNOWN');
        });
      });
    });

    $('btn-log-add').addEventListener('click', function () {
      XQApp.call('xqly.workbench.log.append', {
        level: $('log-level').value, code: 'UI', text: $('log-text').value || ''
      }, function (o) {
        after(o, function () { $('log-text').value = ''; loadState(); feedback('success', '日志已记录（入库前脱敏）'); });
      });
    });
    $('btn-log-query').addEventListener('click', function () {
      XQApp.call('xqly.workbench.log.query', { keyword: $('log-keyword').value || '', tail: 64 }, function (o) {
        after(o, function (list) {
          var box = $('log-stream');
          box.textContent = '';
          if (!list || !list.length) { box.textContent = '无匹配日志。'; return; }
          list.forEach(function (e) {
            box.appendChild(el('div', null, '[' + e.level.toUpperCase() + '] ' + e.code + ' · ' + e.text));
          });
        });
      });
    });

    // 权限矩阵：工作台 15 条方法，全部本地操作。
    var body = $('matrix-body');
    XQApp.methods.forEach(function (m) {
      var tr = document.createElement('tr');
      tr.appendChild(el('td', 'mono', m));
      tr.appendChild(el('td', 'pill-ok', '本地操作 · 无网络'));
      body.appendChild(tr);
    });
    $('method-count').textContent = XQApp.methods.length + ' 条';
  }

  function init() {
    loadState();
    loadSessions();
    loadProviders();
    loadPlugins();
  }

  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', function () { bind(); init(); }, { once: true });
  } else { bind(); init(); }
})();
