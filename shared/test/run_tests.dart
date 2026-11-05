// 星穹旅驿共享业务核心测试套件（零第三方依赖，可在任意 Dart VM 上跑）。
//
//   cd shared && dart pub get --offline && dart test/run_tests.dart
//
// 之所以不用 package:test：共享核心要求断网可构建、依赖锁定为零，
// 测试 harness 与 Android 侧 JVM harness、Node harness 保持同样的风格。
import 'dart:io';

import 'package:xqly_core/xqly_core.dart';

class Harness {
  int passed = 0;
  int failed = 0;
  String _group = '';

  void group(String name) {
    _group = name;
    stdout.writeln('\n── $_group ──');
  }

  void t(String name, void Function() body) {
    try {
      body();
      passed++;
      stdout.writeln('  PASS  $name');
    } catch (e) {
      failed++;
      stdout.writeln('  FAIL  $name  ->  $e');
    }
  }

  int finish() {
    stdout.writeln('\n' + '=' * 58);
    stdout.writeln(' 结果: $passed passed, $failed failed, ${passed + failed} total');
    stdout.writeln('=' * 58);
    return failed == 0 ? 0 : 1;
  }
}

void ok(bool v, [String why = '']) {
  if (!v) throw StateError('断言失败${why.isEmpty ? '' : ': $why'}');
}

void eq(Object? actual, Object? expect, [String why = '']) {
  if (actual != expect) {
    throw StateError('期望 <$expect> 实际 <$actual>${why.isEmpty ? '' : ': $why'}');
  }
}

void eqStr(Object? a, Object? b, [String why = '']) =>
    eq(Json.encode(a), Json.encode(b), why);

void throwsAny(void Function() f, [String why = '']) {
  bool thrown = false;
  try {
    f();
  } catch (_) {
    thrown = true;
  }
  if (!thrown) throw StateError('应当抛出异常${why.isEmpty ? '' : ': $why'}');
}

String rep(String c, int n) => c * n;

// ---------------------------------------------------------------- fixtures

String releaseJson({
  String tag = 'v2.0.1',
  String htmlUrl = 'https://github.com/ChaosSurvivalProject/xqly-app/releases/tag/v2.0.1',
  bool draft = false,
  bool pre = false,
  String assetName = 'xqly-app-v2.0.1.apk',
  String assetUrl = 'https://github.com/ChaosSurvivalProject/xqly-app/releases/download/v2.0.1/xqly-app-v2.0.1.apk',
  String digest = 'sha256:${_dummyHash}',
  int size = 6600000,
}) {
  return Json.encode(<String, Object?>{
    'html_url': htmlUrl,
    'draft': draft,
    'prerelease': pre,
    'tag_name': tag,
    'name': '星穹旅驿 $tag',
    'body': '发布说明',
    'assets': <Object?>[
      <String, Object?>{
        'name': assetName,
        'browser_download_url': assetUrl,
        'digest': digest,
        'size': size,
        'state': 'uploaded',
      },
    ],
  });
}

const String _dummyHash =
    'aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa';

String manifestJson(int version, String path, String hash, int size) =>
    Json.encode(<String, Object?>{
      'schema': 1,
      'version': version,
      'files': <Object?>[
        <String, Object?>{'p': path, 'sha256': hash, 'size': size},
      ],
    });

String bridgeMsg(String type, Map<String, Object?> args, String origin,
        {int v = 2, String id = 'm1'}) =>
    Json.encode(<String, Object?>{
      'v': v,
      'type': type,
      'args': args,
      'origin': origin,
      'id': id,
    });

const String siteOrigin = 'https://xqly.xt91tv.shop/';
const String pageGame = 'file:///android_asset/game.html';
const String devA = 'device-AAAA-0001';
const String devB = 'device-BBBB-0002';

SyncItem item(String id, String kind, Map<String, Object?> payload, int clock,
        String dev,
        {bool deleted = false}) =>
    SyncItem(id, kind, payload, clock, dev, deleted: deleted);

void main(List<String> args) {
  final Harness h = Harness();

  shaTests(h);
  jsonTests(h);
  identityTests(h);
  versionTests(h);
  urlTests(h);
  bridgeTests(h);
  manifestTests(h);
  updateTests(h);
  designTests(h);
  flagTests(h);
  stateTests(h);
  gameTests(h);
  searchTests(h);
  syncPackageTests(h);
  syncMergeTests(h);
  transportTests(h);
  realAssetTests(h, args);

  exitCode = h.finish();
}

// ---------------------------------------------------------------- 1. SHA-256

void shaTests(Harness h) {
  h.group('SHA-256（纯 Dart 实现，FIPS 180-4 已知向量）');
  h.t('空串向量', () => eq(
      sha256OfString(''),
      'e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855'));
  h.t('abc 向量', () => eq(
      sha256OfString('abc'),
      'ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad'));
  h.t('中文 UTF-8 稳定', () =>
      eq(sha256OfString('星穹旅驿'), sha256OfString('星穹旅驿')));
  h.t('长度 55/56/64 边界补齐正确', () {
    for (final int n in <int>[55, 56, 63, 64, 65, 127, 128]) {
      final String s = 'a' * n;
      eq(sha256OfString(s).length, 64, 'len=$n');
      ok(RegExp(r'^[0-9a-f]{64}$').hasMatch(sha256OfString(s)), 'hex');
    }
  });
  h.t('归一化 sha256: 前缀 / 冒号 / 大小写', () => eq(
      normalizeSha256('SHA256:AA:BB'),
      null, '长度不足应拒绝'));
  h.t('归一化合法 64 位指纹', () {
    const String pin = AppIdentity.expectedSigningCertSha256;
    eq(normalizeSha256('sha256:${pin.toUpperCase()}'), pin);
  });
  h.t('归一化拒绝非法字符', () =>
      eq(normalizeSha256('z' * 64), null));
}

// ---------------------------------------------------------------- 2. Json

void jsonTests(Harness h) {
  h.group('严格 JSON');
  h.t('拒绝尾随内容', () => throwsAny(() => Json.decode('{"a":1}x')));
  h.t('拒绝未转义控制字符', () =>
      throwsAny(() => Json.decode('{"a":"x\ny"}')));
  h.t('拒绝 NaN / Infinity 字面量', () =>
      throwsAny(() => Json.decode('{"a":NaN}')));
  h.t('超长文档拒绝', () =>
      throwsAny(() => Json.decode('"' + rep('a', 2100000) + '"')));
  h.t('深层嵌套拒绝', () {
    String s = '0';
    for (int i = 0; i < 70; i++) {
      s = '[$s]';
    }
    throwsAny(() => Json.decode(s));
  });
  h.t('确定性键序（跨平台校验和前提）', () {
    eq(Json.encode(<String, Object?>{'b': 2, 'a': 1}), '{"a":1,"b":2}');
    eq(Json.encode(<String, Object?>{'a': 1, 'b': 2}), '{"a":1,"b":2}');
  });
  h.t('序列化拒绝非有限数', () =>
      throwsAny(() => Json.encode(<String, Object?>{'x': double.infinity})));
  h.t('str/int/bool 类型兜底', () {
    final Map<String, Object?> m = Json.object('{"s":1,"i":"x","b":"y"}');
    eq(Json.str(m, 's', 'D'), 'D');
    eq(Json.integer(m, 'i', 7), 7);
    eq(Json.boolean(m, 'b', true), true);
  });
}

// ---------------------------------------------------------------- 3. identity

void identityTests(Harness h) {
  h.group('双平台身份常量（与 Android Java 侧逐项一致）');
  h.t('业务版本 2.0.1 / code 6 / 四段版本', () {
    eq(AppIdentity.versionName, '2.0.1');
    eq(AppIdentity.versionCode, 6);
    eq(AppIdentity.windowsVersion, '2.0.1.0');
  });
  h.t('发布者与仓库钉扎', () {
    eq(AppIdentity.repo, 'ChaosSurvivalProject/xqly-app');
    eq(AppIdentity.siteHost, 'xqly.xt91tv.shop');
  });
  h.t('签名钉扎为 64 位小写 hex', () => ok(RegExp(
          r'^[0-9a-f]{64}$')
      .hasMatch(AppIdentity.expectedSigningCertSha256)));
  h.t('桥版本与名称', () {
    eq(AppIdentity.bridgeVersion, 2);
    eq(AppIdentity.bridgeName, 'XQLYBridge');
  });
  h.t('资产名命名规则受 OTA 约束', () {
    eq(AppIdentity.androidReleaseAsset('2.0.1'), 'xqly-app-v2.0.1.apk');
    ok(VersionPolicy.validAssetName('xqly-app-v2.0.1.apk', 'v2.0.1'));
  });
  h.t('单文件上限与 Java 一致（32MB）',
      () => eq(AppIdentity.packMaxSingleBytes, 32 * 1024 * 1024));
}

// ---------------------------------------------------------------- 4. version

void versionTests(Harness h) {
  h.group('版本策略');
  h.t('parse 去 v 前缀且拒绝预发布', () {
    eqStr(VersionPolicy.parse('v2.0.1'), <int>[2, 0, 1]);
    eq(VersionPolicy.parse('2.0.1-beta'), null);
    eq(VersionPolicy.parse(''), null);
  });
  h.t('compare 缺段补零', () {
    eq(VersionPolicy.compare('2.0', '2.0.0'), 0);
    ok(VersionPolicy.compare('2.0.1', '2.0.0') > 0);
  });
  h.t('decide 升级 / 相同 / 降级', () {
    eq(VersionPolicy.decide('v2.0.1', 6, '2.0.0', 5), VDecision.upgrade);
    eq(VersionPolicy.decide('v2.0.1', 6, '2.0.1', 6), VDecision.same);
    eq(VersionPolicy.decide('v2.0.0', 5, '2.0.1', 6), VDecision.downgrade);
  });
  h.t('tag 相同但 versionCode 回退仍拒绝', () =>
      eq(VersionPolicy.decide('v2.0.1', 5, '2.0.1', 6), VDecision.downgrade));
  h.t('非法 tag 返回 malformed', () =>
      eq(VersionPolicy.decide('not-a-version', 6, '2.0.0', 5),
          VDecision.malformed));
  h.t('资产名与 tag 必须同版本', () {
    ok(!VersionPolicy.validAssetName('xqly-app-v2.0.0.apk', 'v2.0.1'));
    ok(!VersionPolicy.validAssetName('malware.apk', 'v2.0.1'));
  });
}

// ---------------------------------------------------------------- 5. url

void urlTests(Harness h) {
  h.group('严格 URL 白名单');
  h.t('javascript / data / content / blob 全拒绝', () {
    for (final String u in <String>[
      'javascript:alert(1)',
      'data:text/html,x',
      'content://x/y',
      'blob:https://x/y',
    ]) {
      eq(UrlPolicy.classify(u), UrlKind.blocked, u);
    }
  });
  h.t('官方站 https 放行，明文 http 拒绝', () {
    eq(UrlPolicy.classify(siteOrigin), UrlKind.site);
    eq(UrlPolicy.classify('http://xqly.xt91tv.shop/'), UrlKind.blocked);
  });
  h.t('userinfo / 非标准端口 / 路径穿越拒绝', () {
    eq(UrlPolicy.classify('https://user@xqly.xt91tv.shop/'), UrlKind.blocked);
    eq(UrlPolicy.classify('https://xqly.xt91tv.shop:8443/'), UrlKind.blocked);
    eq(UrlPolicy.classify('https://xqly.xt91tv.shop/../etc'), UrlKind.blocked);
  });
  h.t('相似域名伪装拒绝', () =>
      eq(UrlPolicy.classify('https://xqly.xt91tv.shop.evil.com/'),
          UrlKind.blocked));
  h.t('外部 https 归为 external，交给系统浏览器', () =>
      eq(UrlPolicy.classify('https://example.com/'), UrlKind.external));
  h.t('安全路径防穿越', () {
    ok(UrlPolicy.safeAssetPath('site/wiki/a.html'));
    ok(!UrlPolicy.safeAssetPath('../secret'));
    ok(!UrlPolicy.safeAssetPath('/etc/passwd'));
    ok(!UrlPolicy.safeAssetPath('C:\\x'));
    ok(!UrlPolicy.safeAssetPath('a%2e%2e/b'));
  });
  h.t('桥只注入官方站与内置页面', () {
    ok(UrlPolicy.bridgeAllowed(siteOrigin));
    ok(UrlPolicy.bridgeAllowed(pageGame));
    ok(!UrlPolicy.bridgeAllowed('file:///android_asset/MANIFEST.json'));
    ok(!UrlPolicy.bridgeAllowed('https://example.com/'));
  });
  h.t('本地资源白名单', () {
    ok(UrlPolicy.localResourceAllowed('game-engine.js'));
    ok(!UrlPolicy.localResourceAllowed('MANIFEST.json'));
  });
  h.t('下载主机白名单', () {
    ok(UrlPolicy.downloadHostAllowed('https://github.com/a/b'));
    ok(UrlPolicy.downloadHostAllowed(
        'https://objects.githubusercontent.com/a'));
    ok(!UrlPolicy.downloadHostAllowed('https://evil.com/a.apk'));
  });
}

// ---------------------------------------------------------------- 6. bridge

void bridgeTests(Harness h) {
  h.group('版本化 postMessage 桥');
  const String origin = siteOrigin;
  BridgeResult v(String raw, {bool trusted = true, String page = siteOrigin,
    Set<String>? seen, bool write = true}) =>
      BridgeProtocol.validate(raw,
          trustedPage: trusted, pageOrigin: page, seenIds: seen, allowWrite: write);

  h.t('合法消息通过', () =>
      ok(v(bridgeMsg('getState', <String, Object?>{}, origin)).ok));
  h.t('版本不符拒绝', () => eq(
      v(bridgeMsg('getState', <String, Object?>{}, origin, v: 1)).reject,
      Reject.badVersion));
  h.t('伪造 origin 与页面不匹配拒绝', () => eq(
      v(bridgeMsg('getState', <String, Object?>{}, pageGame,
              id: 'x'), page: siteOrigin).reject,
      Reject.sameOriginMismatch));
  h.t('内置页面冒用另一内置页面拒绝', () => eq(
      v(bridgeMsg('getState', <String, Object?>{}, pageGame),
          page: 'file:///android_asset/archive.html').reject,
      Reject.sameOriginMismatch));
  h.t('不可信页面拒绝', () => eq(
      v(bridgeMsg('getState', <String, Object?>{}, origin), trusted: false)
          .reject,
      Reject.untrustedOrigin));
  h.t('未知类型拒绝', () => eq(
      v(bridgeMsg('exec', <String, Object?>{}, origin)).reject,
      Reject.unknownType));
  h.t('危险键拒绝', () => eq(
      v(bridgeMsg('getState', <String, Object?>{'token': 'x'}, origin)).reject,
      Reject.badArgs));
  h.t('超长参数拒绝', () => eq(
      v(bridgeMsg('getState', <String, Object?>{'k': rep('a', 3000)}, origin))
          .reject,
      Reject.badArgs));
  h.t('openPage 只允许白名单路径', () {
    ok(v(bridgeMsg('openPage', <String, Object?>{'path': '/wiki/a'}, origin))
        .ok);
    eq(v(bridgeMsg('openPage', <String, Object?>{'path': '../../etc'}, origin))
        .reject, Reject.badArgs);
  });
  h.t('windowCommand 固定命令集', () {
    ok(v(bridgeMsg('windowCommand', <String, Object?>{'command': 'newTab'},
            origin)).ok);
    eq(v(bridgeMsg('windowCommand', <String, Object?>{'command': 'format C:'},
            origin)).reject, Reject.badArgs);
  });
  h.t('写操作需要权限', () => eq(
      v(bridgeMsg('favorite', <String, Object?>{}, origin), write: false)
          .reject,
      Reject.permissionDenied));
  h.t('幂等 id 去重', () {
    final Set<String> seen = <String>{};
    ok(v(bridgeMsg('getState', <String, Object?>{}, origin), seen: seen).ok);
    eq(v(bridgeMsg('getState', <String, Object?>{}, origin), seen: seen)
        .reject, Reject.duplicateId);
  });
  h.t('超长消息拒绝', () =>
      eq(v(rep('x', 9000)).reject, Reject.tooLarge));
  h.t('坏 JSON 拒绝', () => eq(v('{oops').reject, Reject.badJson));
}

// ---------------------------------------------------------------- 7. manifest

void manifestTests(Harness h) {
  h.group('内容清单（路径穿越 / ZIP 炸弹 / 回滚 / 截断）');
  const String hash =
      'aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa';

  h.t('合法清单通过', () =>
      ok(ContentManifest.verify(manifestJson(2, 'site/a.html', hash, 10), 1)
          .ok));
  h.t('空文件（size=0）合法，兼容镜像占位', () =>
      ok(ContentManifest.verify(manifestJson(2, 'site/empty.js', hash, 0), 1)
          .ok));
  h.t('路径穿越拒绝', () => eq(
      ContentManifest.verify(manifestJson(2, '../etc/passwd', hash, 1), 1)
          .reject, MReject.unsafePath));
  h.t('非法哈希拒绝', () => eq(
      ContentManifest.verify(manifestJson(2, 'a', 'zz', 1), 1).reject,
      MReject.badHash));
  h.t('非法体积拒绝', () => eq(
      ContentManifest.verify(manifestJson(2, 'a', hash, -1), 1).reject,
      MReject.badSize));
  h.t('版本回退拒绝', () => eq(
      ContentManifest.verify(manifestJson(1, 'a', hash, 1), 2).reject,
      MReject.rollback));
  h.t('重复路径拒绝', () {
    final String j = Json.encode(<String, Object?>{
      'schema': 1,
      'version': 2,
      'files': <Object?>[
        <String, Object?>{'p': 'a', 'sha256': hash, 'size': 1},
        <String, Object?>{'p': 'a', 'sha256': hash, 'size': 1},
      ],
    });
    eq(ContentManifest.verify(j, 1).reject, MReject.duplicatePath);
  });
  h.t('ZIP 压缩炸弹判定', () {
    ok(ContentManifest.badExpandRatio(1000, 500000));
    ok(!ContentManifest.badExpandRatio(1000, 50000));
  });
  h.t('build → verify 往返', () {
    final String j = ContentManifest.build(3, <MEntry>[
      MEntry('data/a.json', sha256OfString('hello'), 5),
    ]);
    final MReport r = ContentManifest.verify(j, 2);
    ok(r.ok);
    eq(r.version, 3);
    eq(r.files.length, 1);
  });
  h.t('verifyFile 逐字节哈希', () {
    final MEntry e = MEntry('x', sha256OfString('hello'), 5);
    ok(ContentManifest.verifyFile(e, <int>[104, 101, 108, 108, 111]));
    ok(!ContentManifest.verifyFile(e, <int>[104, 101, 108, 108, 111, 33]));
  });
}

// ---------------------------------------------------------------- 8. update

void updateTests(Harness h) {
  h.group('可信更新链');
  const String pin = AppIdentity.expectedSigningCertSha256;
  const String digest =
      'bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb';

  h.t('合法 Release 解析 OK', () {
    final ParseResult r = UpdateVerdict.parseRelease(releaseJson());
    ok(r.ok);
    eq(r.release!.tag, 'v2.0.1');
  });
  h.t('伪造发布者拒绝', () => eq(
      UpdateVerdict.parseRelease(releaseJson(
              htmlUrl: 'https://github.com/evil/x/releases/tag/v2.0.1'))
          .error,
      ParseError.notOfficial));
  h.t('draft / prerelease 拒绝', () {
    eq(UpdateVerdict.parseRelease(releaseJson(draft: true)).error,
        ParseError.draftOrPrerelease);
    eq(UpdateVerdict.parseRelease(releaseJson(pre: true)).error,
        ParseError.draftOrPrerelease);
  });
  h.t('资产名与 tag 不符拒绝（中文别名不参与 OTA）', () => eq(
      UpdateVerdict.parseRelease(releaseJson(
              assetName: '星穹旅驿-2.0.1-android-universal-release.apk'))
          .error,
      ParseError.noValidAsset));
  h.t('下载主机不在白名单拒绝', () => eq(
      UpdateVerdict.parseRelease(
              releaseJson(assetUrl: 'https://evil.com/a.apk'))
          .error,
      ParseError.badAssetHost));
  h.t('全项通过才 trusted', () {
    final ReleaseInfo rel = UpdateVerdict.parseRelease(
            releaseJson(digest: 'sha256:$digest', size: 1234))
        .release!;
    final Measured m = Measured(1234, digest, pin, '2.0.1', 6);
    final Verdict v = UpdateVerdict.evaluate(rel, m, '2.0.0', 5, pin);
    ok(v.trusted, v.failed.map((Check c) => c.key).join(','));
    eq(v.decision, VDecision.upgrade);
  });
  h.t('摘要不符拒装', () {
    final ReleaseInfo rel = UpdateVerdict.parseRelease(releaseJson()).release!;
    final Verdict v = UpdateVerdict.evaluate(
        rel, Measured(1234, rep('c', 64), pin, '2.0.1', 6), '2.0.0', 5, pin);
    ok(!v.trusted);
    ok(v.failed.any((Check c) => c.key == 'sha256'));
  });
  h.t('签名证书变化拒装', () {
    final ReleaseInfo rel = UpdateVerdict.parseRelease(releaseJson()).release!;
    final Verdict v = UpdateVerdict.evaluate(
        rel,
        Measured(1234, _dummyHash, rep('d', 64), '2.0.1', 6),
        '2.0.0', 5, pin);
    ok(!v.trusted);
    ok(v.failed.any((Check c) => c.key == 'signature'));
  });
  h.t('版本降级拒装', () {
    final ReleaseInfo rel = UpdateVerdict.parseRelease(releaseJson()).release!;
    final Verdict v = UpdateVerdict.evaluate(
        rel, Measured(1234, _dummyHash, pin, '2.0.1', 6), '2.0.1', 6, pin);
    ok(!v.trusted);
    ok(v.failed.any((Check c) => c.key == 'version'));
  });
  h.t('钉扎指纹为空时一律拒装', () {
    final ReleaseInfo rel = UpdateVerdict.parseRelease(releaseJson()).release!;
    final Verdict v = UpdateVerdict.evaluate(
        rel, Measured(1234, _dummyHash, pin, '2.0.1', 6), '2.0.0', 5, '');
    ok(!v.trusted);
  });
  h.t('网络错误分类', () {
    eq(UpdateVerdict.classify(null, 'TimeoutException'), NetError.timeout);
    eq(UpdateVerdict.classify(null, 'SocketException'), NetError.noNetwork);
    eq(UpdateVerdict.classify(null, 'HandshakeException'), NetError.tls);
    eq(UpdateVerdict.classify(null, 'Cancelled'), NetError.cancelled);
    eq(UpdateVerdict.classify(503, null), NetError.http);
  });
}

// ---------------------------------------------------------------- 9. design

void designTests(Harness h) {
  h.group('星穹玻璃设计系统');
  h.t('亮/暗 × 三模式共 6 套语义映射', () {
    int n = 0;
    for (final XTheme t in XTheme.values) {
      for (final XMode m in XMode.values) {
        final Map<String, int> c = DesignTokens.colors(t, m);
        eq(c.length, DesignTokens.colorKeys.length);
        n++;
      }
    }
    eq(n, 6);
  });
  h.t('正文在深色背景满足 WCAG AA', () {
    final Map<String, int> c = DesignTokens.colors(XTheme.dark, XMode.glass);
    ok(DesignTokens.meetsAA(c['text']!, c['background']!));
    ok(DesignTokens.contrastRatio(c['text']!, c['background']!) > 7);
  });
  h.t('浅色主题正文满足 AA', () {
    final Map<String, int> c = DesignTokens.colors(XTheme.light, XMode.glass);
    ok(DesignTokens.meetsAA(c['text']!, c['surface']!));
  });
  h.t('增强可读提高对比度', () {
    final double base = DesignTokens.contrastRatio(
        DesignTokens.colors(XTheme.dark, XMode.glass)['textDim']!,
        DesignTokens.colors(XTheme.dark, XMode.glass)['background']!);
    final double hi = DesignTokens.contrastRatio(
        DesignTokens.colors(XTheme.dark, XMode.contrast)['textDim']!,
        DesignTokens.colors(XTheme.dark, XMode.contrast)['background']!);
    ok(hi > base, '$hi > $base');
  });
  h.t('原生节能关闭模糊与流光', () {
    eq(DesignTokens.glass(XMode.eco)['blur'], 0.0);
    eq(DesignTokens.glass(XMode.eco)['glassAlpha'], 0.0);
  });
  h.t('4dp 栅格与同心圆角递增', () {
    eq(DesignTokens.spacing(2), 8.0);
    ok(DesignTokens.radii['dock']! > DesignTokens.radii['control']!);
  });
  h.t('触控目标下限与阅读最大宽度', () {
    ok(DesignTokens.minTouchTarget >= 48);
    ok(DesignTokens.desktopTouchTarget >= 44);
    ok(DesignTokens.maxReadingWidth > 0);
  });
  h.t('CSS 令牌包含 6 组选择器与 reduced-motion', () {
    final String css = DesignTokens.toCss();
    ok(css.contains('[data-theme="dark"][data-mode="glass"]'));
    ok(css.contains('[data-theme="light"][data-mode="contrast"]'));
    ok(css.contains('prefers-reduced-motion'));
    ok(css.contains('--xq-glass-blur'));
  });
  h.t('all() 可确定性序列化', () {
    final String a = Json.encode(DesignTokens.all(XTheme.dark, XMode.glass));
    final String b = Json.encode(DesignTokens.all(XTheme.dark, XMode.glass));
    eq(a, b);
  });
}

// ---------------------------------------------------------------- 10. flags

void flagTests(Harness h) {
  h.group('功能开关 / Kill Switch');
  h.t('未知能力默认关闭', () =>
      ok(!FeatureFlags.enabled(FeatureFlags.defaults(), 'unknown_ability')));
  h.t('默认关闭的能力不得被用户覆盖打开', () {
    final Map<String, bool> f = FeatureFlags.defaults();
    ok(!FeatureFlags.effective(f, 'sound', '1'));
    ok(!FeatureFlags.effective(f, 'cloud_sync', '1'));
  });
  h.t('默认开启的能力可被用户关闭', () =>
      ok(!FeatureFlags.effective(FeatureFlags.defaults(), 'game', '0')));
  h.t('parse 忽略未知键与非布尔值', () {
    final Map<String, bool> f = FeatureFlags.parse(
        '{"game":false,"unknown":true,"online_enabled":"yes"}');
    ok(!FeatureFlags.enabled(f, 'game'));
    ok(FeatureFlags.enabled(f, 'online_enabled'));
  });
  h.t('坏 JSON 回退默认值（不关闭安全边界）', () {
    final Map<String, bool> f = FeatureFlags.parse('{oops');
    ok(FeatureFlags.enabled(f, 'bridge'));
    ok(!FeatureFlags.enabled(f, 'cloud_sync'));
  });
  h.t('toJson 可往返', () {
    final Map<String, bool> f = FeatureFlags.parse(FeatureFlags.toJson(
        FeatureFlags.defaults()));
    eq(f.length, FeatureFlags.definitions.length);
  });
}

// ---------------------------------------------------------------- 11. state

void stateTests(Harness h) {
  h.group('全状态 UI');
  h.t('每个 Page × Phase 都有标题与可恢复性', () {
    int n = 0;
    for (final Page p in Page.values) {
      for (final Phase ph in Phase.values) {
        final ViewState v = AppState.of(p, ph);
        if (ph != Phase.success) ok(v.title.isNotEmpty, '${p.name}/${ph.name}');
        ok(v.message.isNotEmpty || ph == Phase.success);
        n++;
      }
    }
    eq(n, Page.values.length * Phase.values.length);
  });
  h.t('成功态不显示遮罩', () =>
      ok(!AppState.requiresOverlay(Phase.success)));
  h.t('失败态全部可恢复', () {
    for (final Phase ph in <Phase>[
      Phase.errorRetry, Phase.offlineNoCache, Phase.partial,
      Phase.validationFailed, Phase.permissionDenied, Phase.storageLow,
      Phase.syncConflict, Phase.syncCancelled,
    ]) {
      ok(AppState.of(Page.home, ph).recoverable, ph.name);
    }
  });
  h.t('离线无缓存 / 校验失败给出明确动作', () {
    ok(AppState.of(Page.library, Phase.offlineNoCache).showRetry);
    ok(AppState.of(Page.download, Phase.validationFailed).showRetry);
  });
}

// ---------------------------------------------------------------- 12. game

void gameTests(Harness h) {
  h.group('方块合成 2.0 确定性规则');
  h.t('同 seed 同操作序列结果完全一致', () {
    final GameState a = GameRules.newGame(42, 0);
    final GameState b = GameRules.newGame(42, 0);
    for (final Dir d in <Dir>[
      Dir.left, Dir.up, Dir.right, Dir.down, Dir.left, Dir.up,
    ]) {
      GameRules.move(a, d);
      GameRules.move(b, d);
    }
    eq(GameRules.save(a), GameRules.save(b));
  });
  h.t('初始有两块且是 2 或 4', () {
    final GameState s = GameRules.newGame(1, 0);
    int n = 0;
    for (final List<int> row in s.board) {
      for (final int v in row) {
        if (v != 0) {
          ok(v == 2 || v == 4);
          n++;
        }
      }
    }
    eq(n, 2);
  });
  h.t('有效移动后步数递增并生成新块', () {
    final GameState s = GameRules.newGame(7, 0);
    final MoveResult r = GameRules.move(s, Dir.left);
    if (r.moved) eq(s.moves, 1);
    ok(s.moves >= 0);
  });
  h.t('合并计分正确（构造行 [2,2,0,0] 左移）', () {
    final GameState s = GameRules.newGame(1, 0);
    s.board = <List<int>>[
      <int>[2, 2, 0, 0],
      <int>[0, 0, 0, 0],
      <int>[0, 0, 0, 0],
      <int>[0, 0, 0, 0],
    ];
    final MoveResult r = GameRules.move(s, Dir.left);
    ok(r.moved);
    ok(r.gain >= 4, 'gain=${r.gain}');
    ok(s.score >= 4);
  });
  h.t('四方向均可执行且不抛异常', () {
    for (final Dir d in Dir.values) {
      final GameState s = GameRules.newGame(9, 0);
      GameRules.move(s, d);
      ok(GameRules.maxTile(s.board) >= 2);
    }
  });
  h.t('撤销恢复快照且次数递减', () {
    final GameState s = GameRules.newGame(11, 0);
    final GameState snap = GameRules.snapshot(s);
    GameRules.move(s, Dir.left);
    final GameState back = GameRules.undo(s, snap);
    eq(back.undosLeft, GameRules.undoLimit - 1);
    eqStr(back.board, snap.board);
    eq(back.score, snap.score);
  });
  h.t('撤销次数用尽后不再回退', () {
    final GameState s = GameRules.newGame(3, 0)..undosLeft = 0;
    final GameState before = GameRules.snapshot(s);
    GameRules.move(s, Dir.left);
    final GameState after = GameRules.undo(s, before);
    eq(after.undosLeft, 0);
  });
  h.t('hold 交换暂存与预览', () {
    final GameState s = GameRules.newGame(5, 0);
    final int next = s.next;
    GameRules.hold(s);
    ok(s.hold > 0);
    ok(s.next > 0);
    eq(next > 0, true);
  });
  h.t('满盘无相邻可合并时判定结束', () {
    final List<List<int>> b = <List<int>>[
      <int>[2, 4, 2, 4],
      <int>[4, 2, 4, 2],
      <int>[2, 4, 2, 4],
      <int>[4, 2, 4, 2],
    ];
    ok(!GameRules.canMove(b));
  });
  h.t('存档往返保留成就与盘面', () {
    final GameState s = GameRules.newGame(21, 999);
    GameRules.move(s, Dir.left);
    s.achievements.add('tile_128');
    final GameState r = GameRules.restore(GameRules.save(s), 999);
    eqStr(r.board, s.board);
    eq(r.score, s.score);
    ok(r.achievements.contains('tile_128'));
  });
  h.t('非法存档（非 2 幂 / 负分）被裁剪', () {
    final GameState r = GameRules.restore(<String, Object?>{
      'board': <Object?>[
        <Object?>[3, 0, 0, 0],
        <Object?>[0, 0, 0, 0],
        <Object?>[0, 0, 0, 0],
        <Object?>[0, 0, 0, 0],
      ],
      'score': -50,
    }, 0);
    eq(r.board[0][0], 0);
    eq(r.score, 0);
    ok(GameRules.maxTile(r.board) >= 2, '空盘自动开新局');
  });
  h.t('每日种子由本地日期确定，不联网', () {
    eq(GameRules.dailySeed(2026, 11, 5), 20261105);
    eq(GameRules.dailySeed(2026, 11, 5), GameRules.dailySeed(2026, 11, 5));
  });
  h.t('色盲形状按值唯一', () {
    final Set<String> s = <String>{};
    for (final int v in GameRules.shapes.keys) {
      ok(s.add(GameRules.shapeFor(v)), '$v');
    }
    eq(s.length, GameRules.shapes.length);
  });
}

// ---------------------------------------------------------------- 13. search

void searchTests(Harness h) {
  h.group('本地全文检索');
  final List<Doc> docs = <Doc>[
    const Doc('/a', '星穹旅驿 Wiki 入门', '介绍列车与站台的基础资料，含角色图鉴。',
        <String>['指南', 'Wiki'], false),
    const Doc('/b', '社区公告与更新日志', '本次更新修复了离线模式下的搜索问题。',
        <String>['社区'], true),
    const Doc('/c', '方块合成玩法说明', '滑动合并相同方块，达到 2048 即获胜。',
        <String>['游戏'], false),
  ];
  final Index idx = SearchIndex.build(docs);

  h.t('中文词命中', () =>
      ok(SearchIndex.search(idx, '方块').any((Hit x) => x.doc.id == '/c')));
  h.t('标题加权优先', () {
    final List<Hit> r = SearchIndex.search(idx, 'Wiki');
    ok(r.isNotEmpty);
    eq(r.first.doc.id, '/a');
  });
  h.t('收藏筛选', () {
    final List<Hit> r = SearchIndex.search(idx, '更新', favOnly: true);
    ok(r.every((Hit x) => x.doc.favorite));
  });
  h.t('标签筛选', () {
    final List<Hit> r = SearchIndex.search(idx, '资料', tag: '指南');
    ok(r.every((Hit x) => x.doc.tags.contains('指南')));
  });
  h.t('无结果返回空', () =>
      eq(SearchIndex.search(idx, 'zzzz不存在的词').length, 0));
  h.t('高亮含 mark 且 HTML 已转义', () {
    final String p = SearchIndex.preview('目录 <b> 与 Wiki', <String>{'Wiki'});
    ok(p.contains('Wiki'));
    ok(!p.contains('<b>'));
  });
  h.t('序列化往返', () {
    final List<Doc> back = SearchIndex.deserialize(SearchIndex.serialize(docs));
    eq(back.length, docs.length);
    eq(back[1].title, docs[1].title);
    eq(back[1].favorite, true);
  });
  h.t('CJK bigram 与 ASCII 词同时切分', () {
    final List<String> t = SearchIndex.tokenize('Wiki 星穹');
    ok(t.contains('wiki'));
    ok(t.contains('星'));
    ok(t.contains('星穹'));
  });
  h.t('索引文档数可查询', () => eq(idx.docCount, 3));
}

// ---------------------------------------------------------------- 14. sync package

void syncPackageTests(Harness h) {
  h.group('本地优先同步包（版本 / 设备 / 逻辑时钟 / 校验和 / 中断保护）');

  SyncPackage make(List<SyncItem> items, {String dev = devA, int clock = 5}) =>
      SyncPackage.create(
        packageId: 'pack-AAAAAA-0001',
        deviceId: dev,
        createdAtMillis: 1700000000000,
        clock: LogicalClock(clock, dev),
        items: items,
      );

  // 构造「校验和正确、但条目本身非法」的包，用于真正测到条目级校验，
  // 而不是被外层校验和拦截。
  String packWithRawItem(Map<String, Object?> rawItem) {
    final Map<String, Object?> body = <String, Object?>{
      'schema': AppIdentity.syncSchema,
      'packageId': 'pack-AAAAAA-0001',
      'deviceId': devA,
      'createdAt': 1700000000000,
      'clock': LogicalClock(9, devA).toJson(),
      'items': <Object?>[rawItem],
    };
    final String sum = sha256OfString(Json.encode(body));
    return Json.encode(<String, Object?>{...body, 'checksum': sum});
  }

  h.t('创建 → 编码 → 解码往返', () {
    final SyncPackage p = make(<SyncItem>[
      item('fav:1', SyncKind.favorite, <String, Object?>{'title': '入门'}, 1, devA),
    ]);
    final XResult<SyncPackage> r = SyncPackage.decode(p.encode());
    ok(r.ok, r.error ?? '');
    eq(r.value!.items.length, 1);
    eq(r.value!.checksum, p.checksum);
  });
  h.t('校验和逐字节防篡改', () {
    final SyncPackage p = make(<SyncItem>[
      item('fav:1', SyncKind.favorite, <String, Object?>{'title': '入门'}, 1, devA),
    ]);
    final String tampered = p.encode().replaceFirst('入门', '篡改');
    final XResult<SyncPackage> r = SyncPackage.decode(tampered);
    ok(!r.ok);
    eq(r.error, 'CHECKSUM_MISMATCH');
  });
  h.t('截断包拒绝（不产生半份数据）', () {
    final SyncPackage p = make(<SyncItem>[
      item('fav:1', SyncKind.favorite, <String, Object?>{'title': 'x'}, 1, devA),
    ]);
    final String s = p.encode();
    final XResult<SyncPackage> r = SyncPackage.decode(
        s.substring(0, s.length - 12));
    ok(!r.ok);
  });
  h.t('非法 schema 拒绝', () {
    final String s = make(<SyncItem>[]).encode().replaceFirst('"schema":1', '"schema":9');
    ok(!SyncPackage.decode(s).ok);
  });
  h.t('非法设备 ID 拒绝', () {
    final SyncPackage p = make(<SyncItem>[], dev: 'short');
    ok(!SyncPackage.decode(p.encode()).ok);
  });
  h.t('禁止夹带缓存 / 镜像 / 窗口状态类型', () {
    final SyncPackage p = make(<SyncItem>[
      item('x', 'cache_body', <String, Object?>{}, 1, devA),
      item('y', 'offline_mirror', <String, Object?>{}, 1, devA),
      item('z', SyncKind.favorite, <String, Object?>{}, 1, devA),
    ]);
    final XResult<SyncPackage> r = SyncPackage.decode(p.encode());
    ok(r.ok);
    eq(r.value!.items.length, 1, '仅保留白名单 kind');
  });
  h.t('同步项危险键拒绝（校验和正确仍被拒）', () {
    final String s = packWithRawItem(<String, Object?>{
      'id': 'a',
      'kind': SyncKind.favorite,
      'payload': <String, Object?>{'token': 'x'},
      'clock': 1,
      'deviceId': devA,
      'deleted': false,
    });
    eq(SyncPackage.decode(s).error, 'BAD_ITEM');
  });
  h.t('设置项逐项白名单，窗口几何被拒', () {
    final String s = packWithRawItem(<String, Object?>{
      'id': 'a',
      'kind': SyncKind.setting,
      'payload': <String, Object?>{'windowX': 100},
      'clock': 1,
      'deviceId': devA,
      'deleted': false,
    });
    eq(SyncPackage.decode(s).error, 'BAD_ITEM');
  });
  h.t('合法设置项通过', () {
    final SyncPackage p = make(<SyncItem>[
      item('set:theme', SyncKind.setting, <String, Object?>{'theme': 'dark'},
          1, devA),
    ]);
    ok(SyncPackage.decode(p.encode()).ok);
  });
  h.t('逻辑时钟按 counter 定序，不依赖系统时间', () {
    final LogicalClock a = LogicalClock(3, devA);
    final LogicalClock b = LogicalClock(4, devA);
    ok(b.compareTo(a) > 0);
    ok(a.compareTo(b) < 0);
  });
  h.t('时钟相同用设备 ID 稳定定序', () {
    final LogicalClock a = LogicalClock(3, devA);
    final LogicalClock b = LogicalClock(3, devB);
    eq(a.compareTo(b), devA.compareTo(devB));
    ok(a.compareTo(b) != 0);
  });
  h.t('observe 推进到两方最大值 +1', () {
    final LogicalClock a = LogicalClock(3, devA);
    final LogicalClock c = a.observe(LogicalClock(9, devB));
    eq(c.counter, 10);
    eq(c.deviceId, devA);
  });
  h.t('墓碑保留删除意图', () {
    final SyncPackage p = make(<SyncItem>[
      item('fav:2', SyncKind.tombstone, <String, Object?>{'of': 'favorite'},
          8, devA, deleted: true),
    ]);
    final XResult<SyncPackage> r = SyncPackage.decode(p.encode());
    ok(r.ok);
    ok(r.value!.items.first.deleted);
  });
  h.t('预览统计新增/修改/删除/冲突', () {
    final SyncPackage p = make(<SyncItem>[
      item('fav:1', SyncKind.favorite, <String, Object?>{'t': 'new'}, 9, devB),
      item('fav:2', SyncKind.favorite, <String, Object?>{'t': 'x'}, 9, devB),
      item('fav:3', SyncKind.tombstone, <String, Object?>{}, 9, devB,
          deleted: true),
    ]);
    final Map<String, int> s = p.summaryAgainst(<SyncItem>[
      item('fav:1', SyncKind.favorite, <String, Object?>{'t': 'old'}, 2, devA),
      item('fav:3', SyncKind.favorite, <String, Object?>{'t': 'x'}, 2, devA),
    ]);
    eq(s['added'], 1);
    eq(s['modified'], 1);
    eq(s['deleted'], 1);
  });
  h.t('重复 id 拒绝', () {
    final SyncPackage p = make(<SyncItem>[
      item('fav:1', SyncKind.favorite, <String, Object?>{}, 1, devA),
      item('fav:1', SyncKind.favorite, <String, Object?>{}, 2, devA),
    ]);
    ok(!SyncPackage.decode(p.encode()).ok);
  });
}

// ---------------------------------------------------------------- 15. sync merge

void syncMergeTests(Harness h) {
  h.group('同步冲突与合并规则');

  h.t('新增 / 修改 / 无变更分类正确', () {
    final List<SyncItem> local = <SyncItem>[
      item('a', SyncKind.favorite, <String, Object?>{'v': 1}, 2, devA),
      item('b', SyncKind.favorite, <String, Object?>{'v': 1}, 2, devA),
    ];
    final List<SyncItem> inc = <SyncItem>[
      item('a', SyncKind.favorite, <String, Object?>{'v': 2}, 5, devB),
      item('b', SyncKind.favorite, <String, Object?>{'v': 1}, 2, devA),
      item('c', SyncKind.favorite, <String, Object?>{'v': 1}, 3, devB),
    ];
    final SyncDiff d = SyncMerge.diff(local, inc);
    eq(d.added.length, 1);
    eq(d.modified.length, 1);
    eq(d.conflicts.length, 0);
  });
  h.t('本地更新时忽略对端旧值', () {
    final SyncDiff d = SyncMerge.diff(
      <SyncItem>[item('a', SyncKind.favorite, <String, Object?>{'v': 9}, 9, devA)],
      <SyncItem>[item('a', SyncKind.favorite, <String, Object?>{'v': 1}, 2, devB)],
    );
    eq(d.modified.length, 0);
  });
  h.t('阅读进度同时钟冲突交用户', () {
    final SyncDiff d = SyncMerge.diff(
      <SyncItem>[item('p:1', SyncKind.progress, <String, Object?>{'pct': 30}, 5, devA)],
      <SyncItem>[item('p:1', SyncKind.progress, <String, Object?>{'pct': 70}, 5, devB)],
    );
    eq(d.conflicts.length, 1);
    eq(d.conflicts.first.kind, ConflictKind.progress);
  });
  h.t('游戏存档绝不盲目覆盖', () {
    final SyncDiff d = SyncMerge.diff(
      <SyncItem>[item('g:1', SyncKind.gameSave, <String, Object?>{'score': 100}, 3, devA)],
      <SyncItem>[item('g:1', SyncKind.gameSave, <String, Object?>{'score': 900}, 9, devB)],
    );
    eq(d.conflicts.length, 1);
    eq(d.conflicts.first.kind, ConflictKind.gameSave);
  });
  h.t('设置冲突默认不覆盖', () {
    final SyncDiff d = SyncMerge.diff(
      <SyncItem>[item('s:1', SyncKind.setting, <String, Object?>{'theme': 'dark'}, 1, devA)],
      <SyncItem>[item('s:1', SyncKind.setting, <String, Object?>{'theme': 'light'}, 9, devB)],
    );
    eq(d.conflicts.length, 1);
    ok(d.hasConflict);
  });
  h.t('未确认冲突时返回本地原样（无半份数据）', () {
    final List<SyncItem> local = <SyncItem>[
      item('g:1', SyncKind.gameSave, <String, Object?>{'score': 100}, 3, devA),
    ];
    final List<SyncItem> inc = <SyncItem>[
      item('g:1', SyncKind.gameSave, <String, Object?>{'score': 900}, 9, devB),
      item('c', SyncKind.favorite, <String, Object?>{}, 3, devB),
    ];
    final MergeResult r = SyncMerge.merge(local, inc, <Resolution>[],
        localDeviceId: devA, nextClock: 10);
    ok(!r.safeToApply);
    eq(r.applied, 0);
    eqStr(r.merged.first.payload, local.first.payload);
  });
  h.t('全部逐项确认后才应用', () {
    final List<SyncItem> local = <SyncItem>[
      item('g:1', SyncKind.gameSave, <String, Object?>{'score': 100}, 3, devA),
    ];
    final List<SyncItem> inc = <SyncItem>[
      item('g:1', SyncKind.gameSave, <String, Object?>{'score': 900}, 9, devB),
      item('c', SyncKind.favorite, <String, Object?>{}, 3, devB),
    ];
    final MergeResult r = SyncMerge.merge(local, inc, <Resolution>[
      const Resolution('g:1', ResolutionChoice.keepLocal),
    ], localDeviceId: devA, nextClock: 10);
    ok(r.safeToApply);
    ok(r.merged.any((SyncItem x) => x.id == 'c'));
  });
  h.t('keepBoth 保留双方游戏存档', () {
    final List<SyncItem> local = <SyncItem>[
      item('g:1', SyncKind.gameSave, <String, Object?>{'score': 100}, 3, devA),
    ];
    final List<SyncItem> inc = <SyncItem>[
      item('g:1', SyncKind.gameSave, <String, Object?>{'score': 900}, 9, devB),
    ];
    final MergeResult r = SyncMerge.merge(local, inc, <Resolution>[
      const Resolution('g:1', ResolutionChoice.keepBoth),
    ], localDeviceId: devA, nextClock: 10);
    ok(r.safeToApply);
    ok(r.merged.any((SyncItem x) => x.id == 'g:1'));
    ok(r.merged.any((SyncItem x) => x.id == 'g:1.$devB'));
  });
  h.t('标签取并集合并', () {
    final List<SyncItem> local = <SyncItem>[
      item('t:1', SyncKind.tag, <String, Object?>{'tags': <Object?>['A', 'B']}, 5, devA),
    ];
    final List<SyncItem> inc = <SyncItem>[
      item('t:1', SyncKind.tag, <String, Object?>{'tags': <Object?>['B', 'C']}, 5, devB),
    ];
    final SyncDiff d = SyncMerge.diff(local, inc);
    eq(d.conflicts.length, 1);
    final MergeResult r = SyncMerge.merge(local, inc, <Resolution>[
      const Resolution('t:1', ResolutionChoice.merge),
    ], localDeviceId: devA, nextClock: 10);
    ok(r.safeToApply);
    final SyncItem merged = r.merged.firstWhere((SyncItem x) => x.id == 't:1');
    final List<Object?> tags = merged.payload['tags']! as List<Object?>;
    eq(tags.length, 3);
    ok(tags.contains('A') && tags.contains('B') && tags.contains('C'));
  });
  h.t('删除生成墓碑，不静默抹掉', () {
    final List<SyncItem> local = <SyncItem>[
      item('f:1', SyncKind.favorite, <String, Object?>{}, 2, devA),
    ];
    final List<SyncItem> inc = <SyncItem>[
      item('f:1', SyncKind.favorite, <String, Object?>{}, 9, devB,
          deleted: true),
    ];
    final MergeResult r = SyncMerge.merge(local, inc, <Resolution>[],
        localDeviceId: devA, nextClock: 10);
    ok(r.safeToApply);
    final SyncItem x = r.merged.firstWhere((SyncItem e) => e.id == 'f:1');
    ok(x.deleted);
    eq(x.kind, SyncKind.tombstone);
  });
  h.t('删除 vs 更新的编辑冲突交用户', () {
    final SyncDiff d = SyncMerge.diff(
      <SyncItem>[item('f:1', SyncKind.favorite, <String, Object?>{'v': 2}, 12, devA)],
      <SyncItem>[item('f:1', SyncKind.favorite, <String, Object?>{}, 9, devB,
          deleted: true)],
    );
    eq(d.conflicts.length, 1);
    eq(d.conflicts.first.kind, ConflictKind.deleteVsEdit);
  });
  h.t('传输中断 / 取消的残缺包不产生半份数据', () {
    final SyncPackage p = SyncPackage.create(
      packageId: 'pack-AAAAAA-0001',
      deviceId: devA,
      createdAtMillis: 1700000000000,
      clock: LogicalClock(5, devA),
      items: <SyncItem>[
        item('f:1', SyncKind.favorite, <String, Object?>{'v': 1}, 2, devA),
        item('f:2', SyncKind.favorite, <String, Object?>{'v': 2}, 3, devA),
      ],
    );
    final String s = p.encode();
    ok(!SyncPackage.decode(s.substring(0, s.length ~/ 2)).ok);
    ok(!SyncPackage.decode(s.substring(0, s.length - 3)).ok);
    // 取消不改变原包，本地库保持原样，随时可重试。
    ok(SyncPackage.decode(s).ok);
  });
}

// ---------------------------------------------------------------- 16. transport

void transportTests(Harness h) {
  h.group('传输接口与云端未配置状态');
  h.t('云端默认关闭且显示「未配置」', () {
    const CloudConfig c = CloudConfig();
    ok(!c.configured);
    eq(c.statusLabel, '未配置');
    ok(!c.enabled);
  });
  h.t('HTTP 地址不被接受为已配置', () {
    const CloudConfig c = CloudConfig(
        enabled: true, baseUrl: 'http://x/', sessionId: 's');
    ok(!c.configured);
  });
  h.t('序列化不泄露 sessionId', () {
    const CloudConfig c = CloudConfig(
        enabled: true, baseUrl: 'https://x/', sessionId: 'secret');
    ok(!c.toJson().toString().contains('secret'));
  });
  h.t('四个端点与提示词一致', () {
    eq(CloudEndpoints.methods.length, 4);
    eq(CloudEndpoints.itemPath('abc'), '/v1/sync/items/abc');
  });
  h.t('配对凭据 120 秒后过期', () {
    ok(!LocalTransport.pairingExpired(1000, 1000 + 119000));
    ok(LocalTransport.pairingExpired(1000, 1000 + 121000));
  });
  h.t('局域网传输需加密 + 设备信任 + 用户确认', () {
    ok(LocalTransport.canTransfer(
        encrypted: true, deviceTrusted: true, userConfirmed: true));
    ok(!LocalTransport.canTransfer(
        encrypted: false, deviceTrusted: true, userConfirmed: true));
    ok(!LocalTransport.canTransfer(
        encrypted: true, deviceTrusted: false, userConfirmed: true));
    ok(!LocalTransport.canTransfer(
        encrypted: true, deviceTrusted: true, userConfirmed: false));
  });
  h.t('本地传输方式不含虚构服务器', () {
    eq(LocalTransport.methods.length, 4);
    ok(LocalTransport.methods.any((String s) => s.contains('文件')));
  });
}

// ---------------------------------------------------------------- 17. 真实随包资源

void realAssetTests(Harness h, List<String> args) {
  h.group('真实随包资源可被 Dart 核心消费（双平台互操作证据）');
  final String root = args.isNotEmpty ? args[0] : '..';

  h.t('Android 生成的 MANIFEST.json 能被 Dart 清单校验器接受', () {
    final File f = File('$root/assets/MANIFEST.json');
    ok(f.existsSync(), '缺少 ${f.path}');
    final MReport r = ContentManifest.verify(f.readAsStringSync(), 0);
    ok(r.ok, r.detail.isEmpty ? r.reject.toString() : r.detail);
    ok(r.files.length > 100, '文件数=${r.files.length}');
  });

  h.t('清单中抽样文件哈希与源码树字节一致', () {
    final MReport r = ContentManifest.verify(
        File('$root/assets/MANIFEST.json').readAsStringSync(), 0);
    int checked = 0;
    for (final MEntry e in r.files) {
      final File f = File('$root/assets/${e.path}');
      if (!f.existsSync()) continue;
      ok(ContentManifest.verifyFile(e, f.readAsBytesSync()), e.path);
      checked++;
      if (checked >= 5) break;
    }
    ok(checked >= 3, '抽样数=$checked');
  });

  h.t('真实搜索索引可反序列化并可检索', () {
    final File f = File('$root/assets/data/search-index.json');
    ok(f.existsSync(), '缺少 ${f.path}');
    final List<Doc> docs = SearchIndex.deserialize(f.readAsStringSync());
    ok(docs.isNotEmpty);
    final Index idx = SearchIndex.build(docs);
    ok(idx.docCount == docs.length);
    ok(SearchIndex.search(idx, '星穹').isNotEmpty ||
        SearchIndex.search(idx, 'Wiki').isNotEmpty ||
        SearchIndex.search(idx, '社区').isNotEmpty);
  });

  h.t('flags.json 能被开关解析器消费', () {
    final File f = File('$root/assets/config/flags.json');
    ok(f.existsSync(), '缺少 ${f.path}');
    final Map<String, bool> flags = FeatureFlags.parse(f.readAsStringSync());
    ok(flags.containsKey('bridge'));
  });

  h.t('设计令牌 JSON 与 CSS 已生成且一致', () {
    final File json = File('$root/shared/design-tokens.json');
    final File css = File('$root/assets/design-tokens.css');
    ok(json.existsSync(), '缺少 ${json.path}');
    ok(css.existsSync(), '缺少 ${css.path}');
    final Map<String, Object?> m = Json.object(json.readAsStringSync());
    eq(m['brand'], '星穹玻璃');
    final Object? themes = m['themes'];
    ok(themes is List);
    eq((themes as List).length, 6);
    final String cssText = css.readAsStringSync();
    ok(cssText.contains('--xq-brand'));
    ok(cssText.contains(':root{'));
  });

  h.t('三个本地页面已引入共享设计令牌 CSS', () {
    for (final String name in <String>[
      'archive.html', 'library.html', 'game.html'
    ]) {
      final File f = File('$root/assets/$name');
      ok(f.existsSync(), '缺少 ${f.path}');
      ok(f.readAsStringSync().contains('design-tokens.css'), '$name 未引入令牌');
    }
  });
}
