package tests;

import cn.xqly.app.core.*;
import cn.xqly.app.core.UpdateVerdict.*;
import cn.xqly.app.core.ContentManifest.*;
import cn.xqly.app.core.DesignTokens.*;
import cn.xqly.app.core.GameRules.*;
import cn.xqly.app.core.VersionPolicy.Decision;
import cn.xqly.app.core.OverlayController.Layer;

import java.util.*;

import static tests.TestHarness.*;

/** 核心逻辑回归套件：游戏规则 / 更新状态机 / 桥协议 / 签名版本校验 / 搜索 / 清单 / 迁移。 */
public final class CoreTestSuite {

    public static void main(String[] args) {
        TestHarness h = new TestHarness();
        jsonTests(h);
        versionTests(h);
        urlTests(h);
        bridgeTests(h);
        updateTests(h);
        manifestTests(h);
        designTests(h);
        flagTests(h);
        gameTests(h);
        searchTests(h);
        userDataTests(h);
        syncTests(h);
        stateTests(h);
        overlayTests(h);
        moduleTests(h);
        workbenchTests(h);
        bundleTests(h);
        System.exit(h.run());
    }

    // ---------------------------------------------------------------- 用户数据 / 迁移
    private static void userDataTests(TestHarness h) {
        h.group("UserData 收藏/历史/进度 与 schema 迁移");
        h.test("收藏切换与去重", () -> {
            UserPrefs p = new UserData.MapPrefs();
            isTrue(UserData.toggleFavorite(p, "a"), "add");
            isTrue(UserData.toggleFavorite(p, "b"), "add2");
            isTrue(UserData.isFavorite(p, "a"), "contains a");
            isFalse(UserData.toggleFavorite(p, "a"), "remove");
            eqInt(UserData.favorites(p).size(), 1, "one left");
        });
        h.test("自定义标签与收藏夹", () -> {
            UserPrefs p = new UserData.MapPrefs();
            UserData.setTags(p, "a", Arrays.asList("阵营", "攻略"));
            eqInt(UserData.tagsOf(p, "a").size(), 2, "tags");
            UserData.addToCollection(p, "想读", "a");
            UserData.addToCollection(p, "想读", "b");
            UserData.addToCollection(p, "想读", "a");
            eqInt(UserData.collections(p).get("想读").size(), 2, "no dup in collection");
        });
        h.test("历史上限与去重置顶", () -> {
            UserPrefs p = new UserData.MapPrefs();
            for (int i = 0; i < 150; i++) UserData.recordHistory(p, "d" + i);
            eqInt(UserData.history(p).size(), UserData.HISTORY_MAX, "capped");
            UserData.recordHistory(p, "d5");
            eq(UserData.history(p).get(0), "d5", "moved to front");
        });
        h.test("阅读进度夹取到 0..1", () -> {
            UserPrefs p = new UserData.MapPrefs();
            UserData.setProgress(p, "a", 2.5f);
            eq(UserData.progressOf(p, "a"), 1.0f, "clamp high");
            UserData.setProgress(p, "a", -1f);
            eq(UserData.progressOf(p, "a"), 0.0f, "clamp low");
        });
        h.test("阅读设置畸形值兜底", () -> {
            UserPrefs p = new UserData.MapPrefs();
            p.put(UserData.K_READING, "{\"fontScale\":999,\"lineSpacing\":\"x\"}");
            java.util.Map<String, Object> r = UserData.reading(p);
            isTrue(((Number) r.get("fontScale")).doubleValue() <= 2.0, "fontScale clamped");
            notNull(r.get("lineSpacing"), "lineSpacing defaulted");
        });
        h.test("schema 0→2 逐级迁移，逗号列表安全转换", () -> {
            UserPrefs p = new UserData.MapPrefs();
            p.put(UserData.K_FAVORITES, "a,b,c");
            eqInt(UserData.migrate(p), 2, "migrated to 2");
            eqInt(UserData.favorites(p).size(), 3, "converted");
            isTrue(p.has(UserData.K_READING), "reading defaults created");
        });
        h.test("迁移幂等且不清空已有 JSON 数组", () -> {
            UserPrefs p = new UserData.MapPrefs();
            p.put(UserData.K_FAVORITES, "[\"a\",\"b\"]");
            UserData.migrate(p);
            UserData.migrate(p);
            eqInt(UserData.favorites(p).size(), 2, "preserved");
        });
        h.test("缓存键与用户键严格分离（清缓存不删收藏）", () -> {
            isTrue(UserData.isUserKey(UserData.K_FAVORITES), "fav is user key");
            isFalse(UserData.isUserKey("cache.site.index"), "cache is not user key");
            for (String k : UserData.cacheKeysToClear()) isFalse(UserData.isUserKey(k), "clear prefix " + k);
        });
        h.test("最近搜索去重与上限", () -> {
            UserPrefs p = new UserData.MapPrefs();
            for (int i = 0; i < 12; i++) UserData.recordSearch(p, "词" + i);
            eqInt(UserData.recentSearch(p).size(), 8, "capped");
            UserData.recordSearch(p, "词11");
            eq(UserData.recentSearch(p).get(0), "词11", "front");
        });
    }

    // ---------------------------------------------------------------- 本地优先同步引擎
    private static void syncTests(TestHarness h) {
        h.group("SyncEngine 本地优先同步（默认不联网）");
        h.test("导出包可解析且校验和自洽", () -> {
            UserPrefs p = new UserData.MapPrefs();
            UserData.toggleFavorite(p, "a");
            UserData.toggleFavorite(p, "b");
            UserData.recordHistory(p, "h1");
            String pkg = SyncEngine.exportPackage(p, "dev-1", 1000L);
            SyncEngine.Parsed q = SyncEngine.parsePackage(pkg);
            eq(q.reject, SyncEngine.Reject.OK, "ok");
            eq(q.deviceId, "dev-1", "device");
            isTrue(q.categories.containsKey(SyncEngine.C_FAVORITES), "has favs");
        });
        h.test("篡改后校验和失败整体拒绝", () -> {
            UserPrefs p = new UserData.MapPrefs();
            UserData.toggleFavorite(p, "a");
            String pkg = SyncEngine.exportPackage(p, "dev-1", 1000L);
            String tampered = pkg.replace("\"a\"", "\"X\"");  // 改了数据但 checksum 未重算
            eq(SyncEngine.parsePackage(tampered).reject, SyncEngine.Reject.BAD_CHECKSUM, "checksum");
        });
        h.test("降 schema / 空设备 / 畸形 JSON 拒绝", () -> {
            eq(SyncEngine.parsePackage("{\"schema\":99,\"deviceId\":\"d\",\"generatedAt\":1,\"categories\":{}}").reject,
                    SyncEngine.Reject.BAD_SCHEMA, "schema");
            eq(SyncEngine.parsePackage("{\"schema\":1,\"deviceId\":\"\",\"generatedAt\":1,\"categories\":{}}").reject,
                    SyncEngine.Reject.NO_DEVICE, "device");
            eq(SyncEngine.parsePackage("{not json").reject, SyncEngine.Reject.BAD_JSON, "json");
        });
        h.test("合并为并集且不删本地项（本地优先）", () -> {
            UserPrefs local = new UserData.MapPrefs();
            UserData.toggleFavorite(local, "keep");          // 本地独有，必须保留
            UserPrefs remoteSrc = new UserData.MapPrefs();
            UserData.toggleFavorite(remoteSrc, "keep");
            UserData.toggleFavorite(remoteSrc, "new");
            String pkg = SyncEngine.exportPackage(remoteSrc, "dev-2", 2000L);
            SyncEngine.Parsed q = SyncEngine.parsePackage(pkg);
            SyncEngine.MergeReport r = SyncEngine.merge(q, local);
            isTrue(r.ok, "merged");
            isTrue(UserData.isFavorite(local, "keep"), "local kept");
            isTrue(UserData.isFavorite(local, "new"), "remote added");
            eqInt(r.setsAdded, 1, "added count");
        });
        h.test("远端更旧时映射类保留本地（不覆盖）", () -> {
            UserPrefs local = new UserData.MapPrefs();
            local.put(UserData.K_READING, "{\"fontScale\":1.0}");
            local.put(SyncEngine.K_BASELINE_AT, "5000");      // 本地基线比远端新
            UserPrefs remoteSrc = new UserData.MapPrefs();
            remoteSrc.put(UserData.K_READING, "{\"fontScale\":2.0}");
            String pkg = SyncEngine.exportPackage(remoteSrc, "dev-3", 1000L);  // generatedAt 更旧
            SyncEngine.MergeReport r = SyncEngine.merge(SyncEngine.parsePackage(pkg), local);
            eq(UserData.reading(local).get("fontScale"), 1.0, "local preserved");
            eqInt(r.skipped, 1, "skipped");
            isTrue(r.mapsUpdated == 0, "no overwrite");
        });
        h.test("默认无云端传输（不联网）", () -> {
            isTrue(!FeatureFlags.enabled(FeatureFlags.defaults(), "cloud_sync"), "cloud_sync off by default");
        });
    }

    // ---------------------------------------------------------------- 状态机
    private static void stateTests(TestHarness h) {
        h.group("AppState 全状态覆盖与可恢复性");
        h.test("每个页面 × 每个状态都有非空文案", () -> {
            for (AppState.Page p : AppState.Page.values()) {
                for (AppState.Phase ph : AppState.Phase.values()) {
                    AppState.ViewState v = AppState.of(p, ph);
                    notNull(v, p + "/" + ph);
                    isTrue(v.phase == ph, "phase preserved");
                    if (ph != AppState.Phase.SUCCESS)
                        isTrue(!v.title.isEmpty() || ph == AppState.Phase.LOADING, p + "/" + ph + " has title");
                }
            }
        });
        h.test("异常态可重试/可恢复", () -> {
            for (AppState.Phase ph : AppState.Phase.values()) {
                isTrue(AppState.canTransition(ph, AppState.Phase.SUCCESS), ph + " -> SUCCESS");
                isTrue(AppState.canTransition(ph, AppState.Phase.LOADING), ph + " -> LOADING");
            }
        });
        h.test("无网无缓存给出明确动作而非无限转圈", () -> {
            AppState.ViewState v = AppState.of(AppState.Page.ARTICLE, AppState.Phase.OFFLINE_NO_CACHE);
            isFalse(v.showContent, "no fake content");
            isFalse(v.spinner, "no infinite spinner");
            isTrue(v.retryable, "action available");
        });
        h.test("校验失败保留可用内容", () -> {
            AppState.ViewState v = AppState.of(AppState.Page.LIBRARY, AppState.Phase.VALIDATION_FAILED);
            isTrue(v.showContent, "keep last good");
            isTrue(v.message.contains("上一个可用版本"), "rollback hint");
        });
        h.test("存储不足不承诺删除用户数据", () -> {
            AppState.ViewState v = AppState.of(AppState.Page.DOWNLOAD, AppState.Phase.STORAGE_LOW);
            isTrue(v.message.contains("收藏与存档不会受影响"), "safe wording");
        });
    }

    // ---------------------------------------------------------------- JSON
    private static void jsonTests(TestHarness h) {
        h.group("MiniJson 严格解析与确定性序列化");
        h.test("基本对象/数组/转义", () -> {
            Map<String, Object> m = MiniJson.object("{\"a\":1,\"b\":[true,null,\"x\\n\"],\"c\":\"\\u4e2d\"}");
            eqInt(MiniJson.num(m, "a", -1), 1, "a");
            eq(MiniJson.str(m, "c", ""), "中", "unicode");
            eq(((List<?>) m.get("b")).size(), 3, "array size");
        });
        h.test("拒绝尾随内容", () -> throwsAny(() -> MiniJson.parse("{\"a\":1} garbage"), "trailing"));
        h.test("拒绝未转义控制字符", () -> throwsAny(() -> MiniJson.parse("{\"a\":\"x\ty\"}"), "control"));
        h.test("拒绝非法字面量/NaN", () -> {
            throwsAny(() -> MiniJson.parse("{\"a\":tru}"), "literal");
            throwsAny(() -> MiniJson.parse("{\"a\":NaN}"), "nan");
        });
        h.test("拒绝超深嵌套", () -> {
            StringBuilder b = new StringBuilder();
            for (int i = 0; i < 40; i++) b.append("[");
            for (int i = 0; i < 40; i++) b.append("]");
            throwsAny(() -> MiniJson.parse(b.toString()), "depth");
        });
        h.test("序列化键序确定性", () -> {
            Map<String, Object> a = new LinkedHashMap<String, Object>();
            a.put("z", 1); a.put("a", 2);
            Map<String, Object> b = new LinkedHashMap<String, Object>();
            b.put("a", 2); b.put("z", 1);
            eq(MiniJson.stringify(a), MiniJson.stringify(b), "key order independent");
        });
        h.test("转义往返一致", () -> {
            String s = "引号\" 反斜杠\\ 换行\n 中文";
            eq(MiniJson.parse(MiniJson.stringify(s)), s, "roundtrip");
        });
    }

    // ---------------------------------------------------------------- 版本
    private static void versionTests(TestHarness h) {
        h.group("VersionPolicy 版本与降级策略");
        h.test("版本号分段比较", () -> {
            eqInt(VersionPolicy.compare("1.2.10", "1.2.9"), 1, "10>9");
            eqInt(VersionPolicy.compare("2.0", "2.0.0"), 0, "补零相等");
            eqInt(VersionPolicy.compare("v1.3.0", "1.2.9"), 1, "v 前缀");
        });
        h.test("畸形版本返回哨兵值", () -> eqInt(VersionPolicy.compare("abc", "1.0"), Integer.MIN_VALUE, "malformed"));
        h.test("拒绝 versionCode 降级", () ->
                eq(VersionPolicy.decide("2.0.0", 3, "1.3.0", 4), Decision.DOWNGRADE, "code downgrade"));
        h.test("拒绝版本名降级", () ->
                eq(VersionPolicy.decide("1.2.0", 5, "1.3.0", 4), Decision.DOWNGRADE, "name downgrade"));
        h.test("相同版本判定 SAME", () ->
                eq(VersionPolicy.decide("1.3.0", 4, "1.3.0", 4), Decision.SAME, "same"));
        h.test("合法升级判定 UPGRADE", () ->
                eq(VersionPolicy.decide("2.0.0", 5, "1.3.0", 4), Decision.UPGRADE, "upgrade"));
        h.test("SHA-256 归一化", () -> {
            isTrue(VersionPolicy.isValidSha256("sha256:" + rep('a', 64)), "prefix form");
            eq(VersionPolicy.normalizeSha256("SHA256:" + rep('A', 64)), rep('a', 64), "normalize");
            isFalse(VersionPolicy.isValidSha256("xyz"), "invalid");
        });
    }

    // ---------------------------------------------------------------- URL
    private static void urlTests(TestHarness h) {
        h.group("UrlPolicy 严格白名单");
        h.test("官方 https 站点内开", () ->
                eq(UrlPolicy.classify("https://xqly.xt91tv.shop/forum"), UrlPolicy.Kind.SITE, "site"));
        h.test("官方 http 明文拒绝", () ->
                eq(UrlPolicy.classify("http://xqly.xt91tv.shop/"), UrlPolicy.Kind.BLOCKED, "cleartext"));
        h.test("危险协议全部拒绝", () -> {
            eq(UrlPolicy.classify("javascript:alert(1)"), UrlPolicy.Kind.BLOCKED, "js");
            eq(UrlPolicy.classify("file:///etc/passwd"), UrlPolicy.Kind.BLOCKED, "file escape");
            eq(UrlPolicy.classify("data:text/html,<script>"), UrlPolicy.Kind.BLOCKED, "data");
            eq(UrlPolicy.classify("content://cn.xqly.app.fileprovider/cache/x"), UrlPolicy.Kind.BLOCKED, "content");
        });
        h.test("userinfo 钓鱼与端口拒绝", () -> {
            eq(UrlPolicy.classify("https://user@xqly.xt91tv.shop/"), UrlPolicy.Kind.BLOCKED, "userinfo");
            eq(UrlPolicy.classify("https://xqly.xt91tv.shop:8443/"), UrlPolicy.Kind.BLOCKED, "port");
        });
        h.test("内置页面白名单", () -> {
            eq(UrlPolicy.classify("file:///android_asset/archive.html"), UrlPolicy.Kind.LOCAL_ASSET, "archive");
            eq(UrlPolicy.classify("file:///android_asset/../../etc/passwd"), UrlPolicy.Kind.BLOCKED, "traversal");
        });
        h.test("外链分流：https 走系统浏览器、http 拒绝", () -> {
            eq(UrlPolicy.classify("https://example.com/"), UrlPolicy.Kind.EXTERNAL, "external browser");
            eq(UrlPolicy.classify("http://example.com/"), UrlPolicy.Kind.BLOCKED, "cleartext external");
        });
        h.test("ZIP 路径穿越防护", () -> {
            isFalse(UrlPolicy.safeAssetPath("../secret"), "dotdot");
            isFalse(UrlPolicy.safeAssetPath("/etc/passwd"), "absolute");
            isFalse(UrlPolicy.safeAssetPath("a\\..\\b"), "backslash");
            isFalse(UrlPolicy.safeAssetPath("C:\\x"), "drive");
            isTrue(UrlPolicy.safeAssetPath("site/css/a.css"), "valid");
        });
        h.test("桥注入仅可信页面", () -> {
            isTrue(UrlPolicy.bridgeAllowed("https://xqly.xt91tv.shop/wiki"), "site");
            isFalse(UrlPolicy.bridgeAllowed("https://evil.com/"), "external");
        });
    }

    // ---------------------------------------------------------------- 桥协议
    private static void bridgeTests(TestHarness h) {
        h.group("BridgeProtocol 版本化 postMessage");
        h.test("合法 openPage 通过", () -> {
            BridgeProtocol.Result r = BridgeProtocol.validate(
                    msg("openPage", "{\"path\":\"/forum\"}", "https://xqly.xt91tv.shop/"),
                    true, new HashSet<String>(), false);
            isTrue(r.ok, r.reason());
            eq(r.type, "openPage", "type");
        });
        h.test("旧版本消息拒绝", () -> {
            String s = "{\"v\":1,\"id\":\"i1\",\"type\":\"ping\",\"args\":{},\"origin\":\"https://xqly.xt91tv.shop/\"}";
            eq(BridgeProtocol.validate(s, true, new HashSet<String>(), false).reject,
                    BridgeProtocol.Reject.BAD_VERSION, "v1");
        });
        h.test("未知类型拒绝", () -> {
            String s = "{\"v\":2,\"id\":\"i1\",\"type\":\"exec\",\"args\":{},\"origin\":\"https://xqly.xt91tv.shop/\"}";
            eq(BridgeProtocol.validate(s, true, new HashSet<String>(), false).reject,
                    BridgeProtocol.Reject.UNKNOWN_TYPE, "exec");
        });
        h.test("危险能力键拒绝（file/token/install/cmd）", () -> {
            for (String k : new String[]{"filePath", "token", "install", "cmd", "script"}) {
                String s = "{\"v\":2,\"id\":\"i1\",\"type\":\"ping\",\"args\":{\"" + k + "\":\"x\"},\"origin\":\"https://xqly.xt91tv.shop/\"}";
                eq(BridgeProtocol.validate(s, true, new HashSet<String>(), false).reject,
                        BridgeProtocol.Reject.FORBIDDEN_CAPABILITY, k);
            }
        });
        h.test("openPage 站外路径拒绝", () -> {
            BridgeProtocol.Result r = BridgeProtocol.validate(
                    msg("openPage", "{\"path\":\"https://evil.com\"}", "https://xqly.xt91tv.shop/"),
                    true, new HashSet<String>(), false);
            eq(r.reject, BridgeProtocol.Reject.FORBIDDEN_CAPABILITY, "external path");
        });
        h.test("不可信页面拒绝", () -> {
            BridgeProtocol.Result r = BridgeProtocol.validate(
                    msg("ping", "{}", "https://xqly.xt91tv.shop/"), false, new HashSet<String>(), false);
            eq(r.reject, BridgeProtocol.Reject.UNTRUSTED_ORIGIN, "untrusted");
        });
        h.test("origin 必须可信", () -> {
            BridgeProtocol.Result r = BridgeProtocol.validate(
                    msg("ping", "{}", "https://evil.com/"), true, new HashSet<String>(), false);
            eq(r.reject, BridgeProtocol.Reject.BAD_ORIGIN, "origin");
        });
        h.test("幂等 id 去重", () -> {
            Set<String> seen = new HashSet<String>();
            String s = msg("ping", "{}", "https://xqly.xt91tv.shop/");
            isTrue(BridgeProtocol.validate(s, true, seen, false).ok, "first");
            eq(BridgeProtocol.validate(s, true, seen, false).reject,
                    BridgeProtocol.Reject.DUPLICATE_ID, "duplicate");
        });
        h.test("超长消息与超长参数拒绝", () -> {
            // 直接引用协议常量，避免实现调整限额后断言漂移
            eq(BridgeProtocol.validate(rep('x', BridgeProtocol.MAX_MESSAGE + 1),
                            true, new HashSet<String>(), false).reject,
                    BridgeProtocol.Reject.TOO_LARGE, "size");
            String s = msg("ping", "{\"k\":\"" + rep('a', BridgeProtocol.MAX_ARG_LEN + 1) + "\"}",
                    "https://xqly.xt91tv.shop/");
            eq(BridgeProtocol.validate(s, true, new HashSet<String>(), false).reject,
                    BridgeProtocol.Reject.BAD_ARGS, "arg len");
        });
        h.test("来源绑定：伪造 origin 不得通过", () -> {
            String forged = "{\"v\":2,\"id\":\"f1\",\"type\":\"getState\",\"args\":{},"
                    + "\"origin\":\"https://xqly.xt91tv.shop/\"}";
            eq(BridgeProtocol.validate(forged, true, new HashSet<String>(), false,
                            "file:///android_asset/game.html").reject,
                    BridgeProtocol.Reject.BAD_ORIGIN, "cross origin");
            String ok = "{\"v\":2,\"id\":\"f2\",\"type\":\"getState\",\"args\":{},"
                    + "\"origin\":\"https://xqly.xt91tv.shop/wiki\"}";
            isTrue(BridgeProtocol.validate(ok, true, new HashSet<String>(), false,
                    "https://xqly.xt91tv.shop/").ok, "same host");
        });
        h.test("写能力受 allowWrite 控制", () -> {
            String s = msg("favorite", "{\"id\":\"p1\"}", "https://xqly.xt91tv.shop/");
            eq(BridgeProtocol.validate(s, true, new HashSet<String>(), false).reject,
                    BridgeProtocol.Reject.FORBIDDEN_CAPABILITY, "readonly");
            isTrue(BridgeProtocol.validate(s, true, new HashSet<String>(), true).ok, "writable");
        });
        h.test("畸形 JSON 拒绝", () ->
                eq(BridgeProtocol.validate("{oops", true, new HashSet<String>(), false).reject,
                        BridgeProtocol.Reject.BAD_JSON, "bad json"));
    }

    // ---------------------------------------------------------------- 更新校验
    private static void updateTests(TestHarness h) {
        h.group("UpdateVerdict 可信更新链 / 状态机");
        final String goodJson = releaseJson("v2.0.0", "xqly-app-v2.0.0.apk",
                rep('a', 64), "https://objects.githubusercontent.com/x.apk", 5000000);
        h.test("解析官方 Release", () -> {
            ParseResult p = UpdateVerdict.parseRelease(goodJson);
            eq(p.error, ParseError.OK, p.error.name());
            notNull(p.release, "release");
            eq(p.release.tag, "v2.0.0", "tag");
        });
        h.test("拒绝非官方仓库", () -> {
            String j = goodJson.replace("https://github.com/ChaosSurvivalProject/xqly-app/releases/tag/v2.0.0",
                    "https://github.com/evil/xqly-app/releases/tag/v2.0.0");
            eq(UpdateVerdict.parseRelease(j).error, ParseError.NOT_OFFICIAL, "publisher");
        });
        h.test("拒绝草稿/预发布", () -> {
            String j = goodJson.replace("\"draft\":false", "\"draft\":true");
            eq(UpdateVerdict.parseRelease(j).error, ParseError.DRAFT_OR_PRERELEASE, "draft");
        });
        h.test("资产名与 tag 必须一致", () -> {
            isTrue(UpdateVerdict.validAssetName("xqly-app-v2.0.0.apk", "v2.0.0"), "match");
            isFalse(UpdateVerdict.validAssetName("xqly-app-v1.0.0.apk", "v2.0.0"), "mismatch");
            isFalse(UpdateVerdict.validAssetName("default.apk", "v2.0.0"), "bad prefix");
        });
        h.test("非白名单下载主机拒绝", () -> {
            isFalse(UpdateVerdict.downloadHostAllowed("https://evil.com/x.apk"), "evil");
            isTrue(UpdateVerdict.downloadHostAllowed("https://github.com/a/b/x.apk"), "github");
        });
        h.test("全部校验通过才 trusted", () -> {
            ParseResult p = UpdateVerdict.parseRelease(goodJson);
            Measured m = new Measured(5000000, rep('a', 64),
                    AppIdentity.EXPECTED_SIGNING_CERT_SHA256, 5, "v2.0.0");
            Verdict v = UpdateVerdict.evaluate(p.release, m, "1.3.0", 4,
                    AppIdentity.EXPECTED_SIGNING_CERT_SHA256);
            isTrue(v.trusted, "trusted; failures=" + v.failures().size());
            eqInt(v.failures().size(), 0, "no failures");
        });
        h.test("SHA-256 摘要不符 → 拒装", () -> {
            ParseResult p = UpdateVerdict.parseRelease(goodJson);
            Measured m = new Measured(5000000, rep('b', 64),
                    AppIdentity.EXPECTED_SIGNING_CERT_SHA256, 5, "v2.0.0");
            Verdict v = UpdateVerdict.evaluate(p.release, m, "1.3.0", 4,
                    AppIdentity.EXPECTED_SIGNING_CERT_SHA256);
            isFalse(v.trusted, "reject digest");
            isTrue(hasFailure(v, "sha256"), "sha256 failure");
        });
        h.test("签名证书指纹变化 → 拒装（防发布者被替换）", () -> {
            ParseResult p = UpdateVerdict.parseRelease(goodJson);
            Measured m = new Measured(5000000, rep('a', 64), rep('c', 64), 5, "v2.0.0");
            Verdict v = UpdateVerdict.evaluate(p.release, m, "1.3.0", 4,
                    AppIdentity.EXPECTED_SIGNING_CERT_SHA256);
            isFalse(v.trusted, "reject signature change");
            isTrue(hasFailure(v, "signature"), "signature failure");
        });
        h.test("未配置钉扎指纹 → 一律拒装", () -> {
            ParseResult p = UpdateVerdict.parseRelease(goodJson);
            Measured m = new Measured(5000000, rep('a', 64),
                    AppIdentity.EXPECTED_SIGNING_CERT_SHA256, 5, "v2.0.0");
            isFalse(UpdateVerdict.evaluate(p.release, m, "1.3.0", 4, "").trusted, "empty pin");
        });
        h.test("版本降级 → 拒装", () -> {
            ParseResult p = UpdateVerdict.parseRelease(goodJson);
            Measured m = new Measured(5000000, rep('a', 64),
                    AppIdentity.EXPECTED_SIGNING_CERT_SHA256, 3, "v2.0.0");
            Verdict v = UpdateVerdict.evaluate(p.release, m, "1.3.0", 4,
                    AppIdentity.EXPECTED_SIGNING_CERT_SHA256);
            isFalse(v.trusted, "reject downgrade");
            isTrue(hasFailure(v, "version"), "version failure");
        });
        h.test("包内版本与 Release 不符 → 拒装", () -> {
            ParseResult p = UpdateVerdict.parseRelease(goodJson);
            Measured m = new Measured(5000000, rep('a', 64),
                    AppIdentity.EXPECTED_SIGNING_CERT_SHA256, 5, "v1.0.0");
            isFalse(UpdateVerdict.evaluate(p.release, m, "1.3.0", 4,
                    AppIdentity.EXPECTED_SIGNING_CERT_SHA256).trusted, "apk version mismatch");
        });
        h.test("声明缺失摘要 → 拒装", () -> {
            String j = releaseJson("v2.0.0", "xqly-app-v2.0.0.apk", "",
                    "https://objects.githubusercontent.com/x.apk", 5000000);
            ParseResult p = UpdateVerdict.parseRelease(j);
            Measured m = new Measured(5000000, rep('a', 64),
                    AppIdentity.EXPECTED_SIGNING_CERT_SHA256, 5, "v2.0.0");
            isFalse(UpdateVerdict.evaluate(p.release, m, "1.3.0", 4,
                    AppIdentity.EXPECTED_SIGNING_CERT_SHA256).trusted, "missing digest");
        });
        h.test("网络错误分类", () -> {
            eq(UpdateVerdict.classify(new java.net.SocketTimeoutException("Read timed out"), null),
                    UpdateVerdict.NetError.TIMEOUT, "timeout");
            eq(UpdateVerdict.classify(new javax.net.ssl.SSLException("cert"), null),
                    UpdateVerdict.NetError.TLS, "tls");
            eq(UpdateVerdict.classify(null, 503), UpdateVerdict.NetError.HTTP, "http");
            eq(UpdateVerdict.classify(new java.io.IOException("ENOSPC: no space left"), null),
                    UpdateVerdict.NetError.DISK_FULL, "disk");
        });
    }

    // ---------------------------------------------------------------- 内容清单
    private static void manifestTests(TestHarness h) {
        h.group("ContentManifest 内容包攻击面");
        final String good = manifest(2, "site/index.html", rep('a', 64), 100);
        h.test("合法清单通过", () -> isTrue(ContentManifest.verify(good, 1).ok, "valid"));
        h.test("版本回退攻击拒绝", () ->
                eq(ContentManifest.verify(good, 5).reject, Reject.ROLLBACK, "rollback"));
        h.test("路径穿越条目拒绝", () -> {
            String j = manifest(2, "../etc/passwd", rep('a', 64), 100);
            eq(ContentManifest.verify(j, 1).reject, Reject.UNSAFE_PATH, "zip slip");
        });
        h.test("绝对路径/反斜杠/驱动符拒绝", () -> {
            eq(ContentManifest.verify(manifest(2, "/etc/x", rep('a', 64), 100), 1).reject, Reject.UNSAFE_PATH, "abs");
            eq(ContentManifest.verify(manifest(2, "a\\..\\b", rep('a', 64), 100), 1).reject, Reject.UNSAFE_PATH, "bs");
            eq(ContentManifest.verify(manifest(2, "C:\\x", rep('a', 64), 100), 1).reject, Reject.UNSAFE_PATH, "drive");
        });
        h.test("非法哈希/非法体积拒绝", () -> {
            eq(ContentManifest.verify(manifest(2, "a", "zz", 100), 1).reject, Reject.BAD_HASH, "hash");
            eq(ContentManifest.verify(manifest(2, "a", rep('a', 64), -1), 1).reject, Reject.BAD_SIZE, "negative");
            eq(ContentManifest.verify(manifest(2, "a", rep('a', 64),
                    AppIdentity.PACK_MAX_SINGLE_BYTES + 1), 1).reject, Reject.BAD_SIZE, "too large");
        });
        h.test("空文件条目合法（离线镜像存在空占位文件）", () ->
                isTrue(ContentManifest.verify(manifest(2, "site/empty.js", rep('a', 64), 0), 1).ok,
                        "zero-byte entry"));
        h.test("重复路径拒绝", () -> {
            String j = "{\"schema\":1,\"version\":2,\"files\":["
                    + "{\"p\":\"a\",\"sha256\":\"" + rep('a', 64) + "\",\"size\":1},"
                    + "{\"p\":\"a\",\"sha256\":\"" + rep('b', 64) + "\",\"size\":1}]}";
            eq(ContentManifest.verify(j, 1).reject, Reject.DUPLICATE_PATH, "dup");
        });
        h.test("超大解压比 / 截断检测", () -> {
            isTrue(ContentManifest.badExpandRatio(1000, 1000L * 1000), "zip bomb");
            isFalse(ContentManifest.badExpandRatio(1000, 2000), "normal");
            isTrue(ContentManifest.truncated(1000, 900), "truncated");
            isFalse(ContentManifest.truncated(1000, 1000), "complete");
        });
        h.test("文件哈希校验与 SHA-256 实现", () -> {
            String hash = ContentManifest.sha256("hello".getBytes(java.nio.charset.StandardCharsets.UTF_8));
            eq(hash, "2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824", "sha256(hello)");
            Entry e = new Entry("a", hash, 5);
            isTrue(ContentManifest.verifyFile(e, "hello".getBytes(java.nio.charset.StandardCharsets.UTF_8)), "match");
            isFalse(ContentManifest.verifyFile(e, "hellx".getBytes(java.nio.charset.StandardCharsets.UTF_8)), "mismatch");
        });
    }

    // ---------------------------------------------------------------- 设计系统
    private static void designTests(TestHarness h) {
        h.group("DesignTokens 设计令牌 / WCAG 对比度");
        h.test("三模式玻璃参数递减动效", () -> {
            isTrue(DesignTokens.glass(Mode.GLASS).blur > DesignTokens.glass(Mode.ECO).blur, "glass>eco");
            eqInt((long) DesignTokens.glass(Mode.ECO).blur, 0L, "eco no blur");
            isTrue(DesignTokens.glass(Mode.CONTRAST).tintAlpha >= 0.95f, "contrast opaque");
        });
        h.test("亮/暗主题语义色均完整", () -> {
            for (Theme t : Theme.values()) for (Mode m : Mode.values()) {
                Palette p = DesignTokens.palette(t, m);
                for (ColorKey k : ColorKey.values()) ok(p.get(k) != 0, t + "/" + m + "/" + k);
            }
        });
        h.test("深色正文对比度达 AA", () -> {
            Palette d = DesignTokens.palette(Theme.DARK, Mode.GLASS);
            isTrue(DesignTokens.meetsAA(d.get(ColorKey.TEXT), d.get(ColorKey.SURFACE), false), "dark body AA");
        });
        h.test("增强可读模式对比度更高", () -> {
            double normal = DesignTokens.contrastRatio(
                    DesignTokens.palette(Theme.DARK, Mode.GLASS).get(ColorKey.TEXT),
                    DesignTokens.palette(Theme.DARK, Mode.GLASS).get(ColorKey.BACKGROUND));
            double contrast = DesignTokens.contrastRatio(
                    DesignTokens.palette(Theme.DARK, Mode.CONTRAST).get(ColorKey.TEXT),
                    DesignTokens.palette(Theme.DARK, Mode.CONTRAST).get(ColorKey.BACKGROUND));
            isTrue(contrast >= normal, "contrast >= normal");
        });
        h.test("对比度参考值正确", () -> {
            isTrue(Math.abs(DesignTokens.contrastRatio(0xFF000000, 0xFFFFFFFF) - 21.0) < 0.01, "black/white=21");
            eqInt(DesignTokens.SPACE_1, 4, "grid");
            eqInt(DesignTokens.MIN_TOUCH, 48, "touch target");
        });
    }

    // ---------------------------------------------------------------- 功能开关
    private static void flagTests(TestHarness h) {
        h.group("FeatureFlags / Kill Switch");
        h.test("安全默认值", () -> {
            Map<String, Boolean> d = FeatureFlags.defaults();
            isTrue(d.get("auto_update"), "auto_update on");
            isFalse(d.get("scroll_compact"), "scroll compact OFF by default");
            isFalse(d.get("sound"), "sound OFF by default");
            isTrue(d.get("haptics"), "haptics on");
        });
        h.test("未知键忽略 + 非布尔忽略", () -> {
            Map<String, Boolean> f = FeatureFlags.parse("{\"sound\":\"yes\",\"unknown\":true,\"game\":false}");
            isFalse(f.get("game"), "game off");
            eq(f.get("sound"), Boolean.FALSE, "sound stays default");
            isFalse(f.containsKey("unknown"), "unknown dropped");
        });
        h.test("全局 Kill Switch 未知能力默认关闭", () ->
                isFalse(FeatureFlags.enabled(FeatureFlags.defaults(), "not_a_flag"), "unknown off"));
        h.test("KILL 后不发网络的能力判定", () -> {
            Map<String, Boolean> f = FeatureFlags.parse("{\"auto_update\":false,\"update_download\":false}");
            isFalse(FeatureFlags.enabled(f, "auto_update"), "update off");
            isFalse(FeatureFlags.enabled(f, "update_download"), "download off");
        });
        h.test("畸形配置回退到全默认", () ->
                eq(FeatureFlags.parse("{broken").get("auto_update"), Boolean.TRUE, "fallback"));
    }

    // ---------------------------------------------------------------- 游戏规则
    private static void gameTests(TestHarness h) {
        h.group("GameRules 确定性引擎");
        h.test("同 seed 同操作序列结果完全一致", () -> {
            State a = GameRules.newGame(42L, 0), b = GameRules.newGame(42L, 0);
            Dir[] seq = {Dir.LEFT, Dir.UP, Dir.RIGHT, Dir.DOWN, Dir.LEFT, Dir.UP};
            for (Dir d : seq) { GameRules.move(a, d); GameRules.move(b, d); }
            eq(GameRules.save(a), GameRules.save(b), "deterministic");
        });
        h.test("不同 seed 起手不同（多数情况）", () -> {
            boolean diff = false;
            for (long s = 1; s < 20; s++) {
                if (!GameRules.save(GameRules.newGame(s, 0)).equals(GameRules.save(GameRules.newGame(1000, 0)))) { diff = true; break; }
            }
            isTrue(diff, "seed affects layout");
        });
        h.test("合并计分正确（2+2=4 得 4 分）", () -> {
            State s = blank();
            s.board[0][0] = 2; s.board[0][1] = 2;
            s.next = 2;
            MoveResult r = GameRules.move(s, Dir.LEFT);
            isTrue(r.changed, "changed");
            eqInt(r.gain, 4, "gain");
            eqInt(s.score, 4, "score");
        });
        h.test("无效移动不改变棋盘且不生成", () -> {
            State s = blank();
            s.board[0][0] = 2;
            MoveResult r = GameRules.move(s, Dir.LEFT);
            isFalse(r.changed, "no change");
            eqInt(GameRules.maxTile(s), 2, "board unchanged");
        });
        h.test("撤销恢复快照且次数递减", () -> {
            State s = GameRules.newGame(7L, 0);
            State snap = GameRules.snapshot(s);
            GameRules.move(s, Dir.LEFT);
            State u = GameRules.undo(s, snap, s.best);
            eqInt(u.undosLeft, GameRules.UNDO_LIMIT - 1, "undo count");
            // 棋盘与分数应完全恢复（undosLeft 按规则递减，故只比业务状态）
            eq(java.util.Arrays.deepToString(u.board), java.util.Arrays.deepToString(snap.board), "board restored");
            eqInt(u.score, snap.score, "score restored");
            eqInt(u.moves, snap.moves, "moves restored");
        });
        h.test("撤销次数耗尽后拒绝", () -> {
            State s = GameRules.newGame(7L, 0);
            State snap = GameRules.snapshot(s);
            s.undosLeft = 0;
            isTrue(GameRules.undo(s, snap, 0) == s, "no undo left");
        });
        h.test("hold 每回合限一次并保留预览", () -> {
            State s = GameRules.newGame(9L, 0);
            int first = s.hold;
            isTrue(GameRules.hold(s), "first hold");
            isFalse(GameRules.hold(s), "second hold blocked");
            ok(s.hold != 0 && s.next != 0, "hold and next set");
        });
        h.test("满盘无相邻可合并 → over", () -> {
            State s = blank();
            int[][] v = {{2,4,2,4},{4,2,4,2},{2,4,2,4},{4,2,4,2}};
            for (int y = 0; y < 4; y++) for (int x = 0; x < 4; x++) s.board[y][x] = v[y][x];
            isFalse(GameRules.canMove(s), "no moves");
        });
        h.test("等级随最大方块提升", () -> {
            State s = blank();
            s.board[0][0] = 2; eqInt(GameRules.level(s), 0, "lv0");
            s.board[0][0] = 2048; eqInt(GameRules.level(s), 10, "lv10");
        });
        h.test("每日挑战种子确定性且按日期变化", () -> {
            eq(GameRules.dailySeed(2026, 11, 5), GameRules.dailySeed(2026, 11, 5), "same day");
            ok(GameRules.dailySeed(2026, 11, 5) != GameRules.dailySeed(2026, 11, 6), "next day differs");
        });
        h.test("色盲友好形状按值唯一", () -> {
            isFalse(GameRules.shapeFor(2).equals(GameRules.shapeFor(4)), "2 vs 4");
            isFalse(GameRules.shapeFor(8).equals(GameRules.shapeFor(16)), "8 vs 16");
        });
        h.test("存档往返 + 非法存档安全回退", () -> {
            State s = GameRules.newGame(3L, 100);
            GameRules.move(s, Dir.UP);
            State r = GameRules.restore(GameRules.save(s), 100);
            eq(GameRules.save(r), GameRules.save(s), "roundtrip");
            State bad = GameRules.restore("{not json", 100);
            notNull(bad, "fallback not null");
            ok(GameRules.maxTile(bad) > 0, "fallback playable");
        });
        h.test("成就解锁（首次合成）", () -> {
            State s = blank();
            s.board[0][0] = 2; s.board[0][1] = 2;
            GameRules.move(s, Dir.LEFT);
            isTrue(s.achievements.contains("初次合成"), "achievement");
        });
    }

    // ---------------------------------------------------------------- 搜索
    private static void searchTests(TestHarness h) {
        h.group("SearchIndex 本地全文检索");
        final List<SearchIndex.Doc> docs = Arrays.asList(
                new SearchIndex.Doc("p1", "阵营内测", "黎明阵营与暮夜阵营的对抗玩法说明", Arrays.asList("阵营", "玩法"), true),
                new SearchIndex.Doc("p2", "玩家社区", "发布帖子、评论与收藏功能", Arrays.asList("社区"), false),
                new SearchIndex.Doc("p3", "Wiki 知识库", "服务器规则与方块合成指南", Arrays.asList("wiki", "指南"), false)
        );
        h.test("索引建立与规模", () -> {
            SearchIndex.Index idx = SearchIndex.build(docs);
            eqInt(idx.docCount(), 3, "docs");
            isTrue(idx.termCount() > 10, "terms");
        });
        h.test("中文检索命中且标题加权", () -> {
            SearchIndex.Index idx = SearchIndex.build(docs);
            List<SearchIndex.Hit> r = SearchIndex.search(idx, "阵营", null, false, 10);
            isTrue(!r.isEmpty(), "has hits");
            eq(r.get(0).id, "p1", "top hit");
        });
        h.test("高亮预览含 <mark>", () -> {
            SearchIndex.Index idx = SearchIndex.build(docs);
            List<SearchIndex.Hit> r = SearchIndex.search(idx, "阵营", null, false, 10);
            isTrue(r.get(0).snippet.contains("<mark>"), "highlight");
        });
        h.test("仅收藏筛选", () -> {
            SearchIndex.Index idx = SearchIndex.build(docs);
            List<SearchIndex.Hit> r = SearchIndex.search(idx, "功能", null, true, 10);
            for (SearchIndex.Hit hit : r) eq(hit.id, "p1", "fav only");
        });
        h.test("标签筛选", () -> {
            SearchIndex.Index idx = SearchIndex.build(docs);
            List<SearchIndex.Hit> r = SearchIndex.search(idx, "指南", "wiki", false, 10);
            isTrue(r.size() == 1 && r.get(0).id.equals("p3"), "tag filter");
        });
        h.test("无结果返回空", () -> {
            SearchIndex.Index idx = SearchIndex.build(docs);
            isTrue(SearchIndex.search(idx, "zzzz不存在的词", null, false, 10).isEmpty(), "empty");
        });
        h.test("HTML 转义防注入", () ->
                eq(SearchIndex.esc("<script>"), "&lt;script&gt;", "escape"));
        h.test("索引序列化往返", () -> {
            String json = SearchIndex.serialize(docs);
            eqInt(SearchIndex.deserialize(json).size(), 3, "roundtrip");
            eqInt(SearchIndex.deserialize("{bad").size(), 0, "bad json safe");
        });
        h.test("最近搜索去重与上限", () -> {
            SearchIndex.Index idx = SearchIndex.build(docs);
            for (int i = 0; i < 12; i++) SearchIndex.search(idx, "词" + i, null, false, 1);
            isTrue(idx.recentCount() <= 8, "recent capped");
        });
    }

    // ---------------------------------------------------------------- 随包资源真实验证
    private static void bundleTests(TestHarness h) {
        h.group("随包资源真实验证（搜索索引 / 内容清单）");
        h.test("真实搜索索引可反序列化且可检索", () -> {
            List<SearchIndex.Doc> ds = SearchIndex.deserialize(readFile("assets/data/search-index.json"));
            isTrue(ds.size() >= 10, "docs=" + ds.size());
            for (SearchIndex.Doc d : ds)
                isTrue(UrlPolicy.sitePathAllowed(d.id), "safe id " + d.id);
            SearchIndex.Index idx = SearchIndex.build(ds);
            isTrue(!SearchIndex.search(idx, "上线", null, false, 10).isEmpty(), "命中 上线");
            isTrue(!SearchIndex.search(idx, "监控", null, false, 10).isEmpty(), "命中 监控");
        });
        h.test("MANIFEST.json 结构合法且抽样文件哈希一致", () -> {
            ContentManifest.Report r = ContentManifest.verify(readFile("assets/MANIFEST.json"), 0);
            isTrue(r.ok, r.ok ? "valid" : r.reason());
            isTrue(r.manifest.files.size() >= 100, "files=" + r.manifest.files.size());
            List<ContentManifest.Entry> f = r.manifest.files;
            int[] pick = {0, f.size() / 3, f.size() / 2, f.size() - 1};
            for (int i : pick) {
                ContentManifest.Entry e = f.get(i);
                byte[] b = readFileBytes("assets/" + e.path);
                isTrue(b != null && ContentManifest.verifyFile(e, b), "hash " + e.path);
            }
        });
    }

    // ---------------------------------------------------------------- helpers
    private static String readFile(String p) {
        byte[] b = readFileBytes(p);
        return b == null ? "" : new String(b, java.nio.charset.StandardCharsets.UTF_8);
    }
    private static byte[] readFileBytes(String p) {
        try {
            java.io.InputStream in = new java.io.FileInputStream(p);
            java.io.ByteArrayOutputStream o = new java.io.ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) o.write(buf, 0, n);
            in.close();
            return o.toByteArray();
        } catch (Exception e) { return null; }
    }
    private static String msg(String type, String args, String origin) {
        return "{\"v\":2,\"id\":\"id1\",\"type\":\"" + type + "\",\"args\":" + args
                + ",\"origin\":\"" + origin + "\"}";
    }
    private static String releaseJson(String tag, String asset, String digest, String url, long size) {
        return "{\"tag_name\":\"" + tag + "\",\"name\":\"" + tag + "\",\"body\":\"更新说明\","
                + "\"draft\":false,\"prerelease\":false,"
                + "\"html_url\":\"https://github.com/ChaosSurvivalProject/xqly-app/releases/tag/" + tag + "\","
                + "\"assets\":[{\"name\":\"" + asset + "\",\"browser_download_url\":\"" + url + "\","
                + "\"digest\":\"sha256:" + digest + "\",\"size\":" + size + "}]}";
    }
    private static String manifest(long ver, String path, String hash, long size) {
        // path 用 MiniJson.stringify 正确转义，保证反斜杠等字符真正到达校验逻辑
        return "{\"schema\":1,\"version\":" + ver + ",\"files\":[{\"p\":"
                + MiniJson.stringify(path) + ",\"sha256\":\"" + hash + "\",\"size\":" + size + "}]}";
    }
    private static boolean hasFailure(Verdict v, String key) {
        for (Check c : v.failures()) if (c.key.equals(key)) return true;
        return false;
    }
    private static String rep(char c, int n) {
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < n; i++) b.append(c);
        return b.toString();
    }
    private static State blank() {
        State s = GameRules.newGame(1L, 0);
        for (int y = 0; y < 4; y++) for (int x = 0; x < 4; x++) s.board[y][x] = 0;
        s.next = 2; s.hold = 0; s.holdUsed = false; s.over = false;
        return s;
    }

    // ---------------------------------------------------------------- 浮层互斥（v2.1.1 UI bug 回归）
    /**
     * 回归 v2.1.0 的致命 bug：资料库浮层构造后未设 GONE、标志位却为 false，
     * 造成透明浮层常驻叠加、触摸被吃、返回键与关闭消息都关不掉。
     * 这里模拟调用方「按迁移结果无条件重设所有层可见性」，验证不变量恒成立。
     */
    private static void overlayTests(TestHarness h) {
        h.group("浮层互斥控制器 / UI 层叠回归");

        h.test("初始无任何浮层可见", () -> {
            OverlayController c = new OverlayController();
            eq(c.current(), Layer.NONE, "initial NONE");
            isFalse(c.anyOpen(), "nothing open");
            OverlayController.Transition t = c.closeAll();
            eqInt(visibleCount(t), 0, "closeAll 收敛到 0 层");
            isTrue(c.invariant(t), "invariant");
        });

        h.test("打开资料库：显示 LIBRARY 且隐藏其余全部", () -> {
            OverlayController c = new OverlayController();
            OverlayController.Transition t = c.open(Layer.LIBRARY);
            eq(t.show, Layer.LIBRARY, "show LIBRARY");
            // 层数随应用增加时自动适配，断言语义仍是「隐藏其余全部」。
            eqInt(t.hide.length, Layer.OVERLAYS.length - 1, "hide 其余全部浮层");
            isTrue(contains(t.hide, Layer.ARCHIVE), "ARCHIVE hidden");
            isTrue(contains(t.hide, Layer.GAME), "GAME hidden");
            isTrue(c.invariant(t), "invariant");
            eqInt(visibleCount(t), 1, "恰好 1 层可见");
        });

        h.test("浮层互斥：后开的自动顶掉先开的", () -> {
            OverlayController c = new OverlayController();
            c.open(Layer.LIBRARY);
            OverlayController.Transition t = c.open(Layer.GAME);
            eq(c.current(), Layer.GAME, "current GAME");
            eq(t.show, Layer.GAME, "show GAME");
            isTrue(contains(t.hide, Layer.LIBRARY), "LIBRARY 必须被隐藏");
            eqInt(visibleCount(t), 1, "仍恰好 1 层可见");
            isTrue(c.invariant(t), "invariant");
        });

        h.test("跳转页面时 closeAll 清空所有浮层（不再残留无法关闭的层）", () -> {
            OverlayController c = new OverlayController();
            c.open(Layer.ARCHIVE);
            c.open(Layer.GAME);
            OverlayController.Transition t = c.closeAll();
            eq(t.show, Layer.NONE, "show NONE");
            eqInt(t.hide.length, Layer.OVERLAYS.length, "全部浮层要求隐藏");
            eqInt(visibleCount(t), 0, "收敛到 0 层");
            isFalse(c.anyOpen(), "nothing open");
            isTrue(c.invariant(t), "invariant");
        });

        h.test("返回键：有浮层时关闭它，无浮层时不下发变更", () -> {
            OverlayController c = new OverlayController();
            c.open(Layer.ARCHIVE);
            OverlayController.Transition t1 = c.onBack();
            isTrue(t1.changed, "关闭了浮层");
            eq(t1.show, Layer.NONE, "收敛到主界面");
            OverlayController.Transition t2 = c.onBack();
            isFalse(t2.changed, "已无浮层，交给 WebView 返回/退出");
            isTrue(c.invariant(t2), "invariant");
        });

        h.test("重复关闭 / 关闭非当前层幂等，且仍下发完整收敛", () -> {
            OverlayController c = new OverlayController();
            c.open(Layer.LIBRARY);
            c.close(Layer.GAME);                       // 非当前层
            eq(c.current(), Layer.LIBRARY, "不受影响");
            OverlayController.Transition t = c.close(Layer.LIBRARY);
            isTrue(t.changed, "真正关闭");
            OverlayController.Transition t2 = c.close(Layer.LIBRARY);
            isFalse(t2.changed, "重复关闭无变更");
            eqInt(visibleCount(t2), 0, "仍然收敛到 0 层");
            isTrue(c.invariant(t2), "invariant");
        });

        h.test("open(NONE) 抛异常，杜绝用 NONE 打开", () -> {
            OverlayController c = new OverlayController();
            throwsAny(() -> c.open(Layer.NONE), "NONE 非法");
            throwsAny(() -> c.open(null), "null 非法");
        });

        h.test("回归不变量：200 步随机操作后可见浮层数恒 <= 1", () -> {
            OverlayController c = new OverlayController();
            // 模拟调用方维护的「实际可见集合」：每次按迁移结果无条件重设。
            java.util.Set<Layer> visible = new java.util.HashSet<Layer>();
            java.util.Random r = new java.util.Random(20261108L);
            // 直接使用枚举全集：新增应用（如 DEV_PORTAL）自动纳入 fuzz 覆盖。
            Layer[] layers = Layer.OVERLAYS;
            for (int i = 0; i < 200; i++) {
                OverlayController.Transition t;
                int op = r.nextInt(4);
                if (op == 0) t = c.open(layers[r.nextInt(layers.length)]);
                else if (op == 1) t = c.close(layers[r.nextInt(layers.length)]);
                else if (op == 2) t = c.closeAll();
                else t = c.onBack();
                // 调用方按迁移结果重设所有浮层可见性（BUG 修复后的正确行为）
                visible.clear();
                if (t.show != Layer.NONE) visible.add(t.show);
                isTrue(c.invariant(t), "invariant @step " + i);
                isTrue(visible.size() <= 1, "最多 1 层可见 @step " + i);
                eq(visible.contains(c.current()) || c.current() == Layer.NONE,
                        true, "可见集合与 current 一致 @step " + i);
            }
        });
    }

    // ---------------------------------------------------------------- 模组：dev-portal / xqly-bridge/1
    private static String env(String method, Map<String, Object> params, String id) {
        Map<String, Object> m = new LinkedHashMap<String, Object>();
        m.put("bridge", BridgeV1.VERSION);
        m.put("appId", AppRegistry.DEV_PORTAL_ID);
        m.put("id", id);
        m.put("method", method);
        m.put("params", params);
        return MiniJson.stringify(m);
    }

    private static BridgeV1.CallResult vcall(String raw) {
        return BridgeV1.validate(raw, BridgeV1.DEFAULT_PERMISSIONS, null, 1000L,
                new HashMap<String, Long>(), true, false, false,
                Collections.<String>emptySet());
    }

    private static BridgeV1.CallResult vcall(String raw, Set<String> perms, long nowMs,
            Map<String, Long> lastCallAt, boolean online, boolean telemetry,
            boolean approval, Set<String> allowlist) {
        return BridgeV1.validate(raw, perms, null, nowMs, lastCallAt,
                online, telemetry, approval, allowlist);
    }

    private static void moduleTests(TestHarness h) {
        h.group("应用注册表 · 应用类模组与独立沙箱");
        h.test("应用登记齐全，dev-portal 为离线只读", () -> {
            isTrue(AppRegistry.APPS.size() >= 3, "应用数");
            AppRegistry.AppSpec p = AppRegistry.byId(AppRegistry.DEV_PORTAL_ID);
            notNull(p, "dev-portal 应已注册");
            eq(p.offline, AppRegistry.Offline.READ_ONLY, "离线能力");
            eq(p.bridgeScope, "devportal:*", "桥作用域");
            isTrue(!p.communityLabel.isEmpty(), "社区标识常驻");
            isTrue(AppRegistry.offlineAvailable(AppRegistry.DEV_PORTAL_ID), "离线可用");
            isFalse(AppRegistry.offlineAvailable("unknown"), "未知应用不可用");
        });
        h.test("路由 / 作用域 / 存储分区 / 导航深度", () -> {
            isTrue(AppRegistry.routeAllowed("xqly-app://apps/dev-portal"), "注册路由");
            isFalse(AppRegistry.routeAllowed("xqly-app://apps/../admin"), "路径穿越");
            isFalse(AppRegistry.routeAllowed("xqly-app://apps/unknown"), "未注册应用");
            isTrue(AppRegistry.scopeAllows("dev-portal", "devportal:*"), "作用域匹配");
            isFalse(AppRegistry.scopeAllows("dev-portal", "site:*"), "跨应用作用域");
            isTrue(AppRegistry.storageIsolated("dev-portal", "web"), "存储隔离");
            isFalse(AppRegistry.storageIsolated("web", "web"), "同分区不隔离");
            isTrue(AppRegistry.navDepthAllowed("dev-portal", AppRegistry.NAV_MODAL), "模态层");
            isFalse(AppRegistry.navDepthAllowed("dev-portal", 5), "超深度");
        });

        h.group("端点策略 · 私网封锁 / Deep Link 白名单 / 零遥测");
        h.test("私网 / 环回 / 链路本地识别", () -> {
            String[] hosts = {"10.0.0.5", "192.168.1.9", "172.16.0.1", "127.0.0.1",
                    "169.254.1.1", "localhost", "box.local", "x.internal", "[::1]"};
            for (String x : hosts) isTrue(EndpointPolicy.privateNetwork(x), "私网 " + x);
            isFalse(EndpointPolicy.privateNetwork("relay.example.com"), "公网域名");
        });
        h.test("端点校验：未登记 / 私网 / 非法端口 / 非法 pin 逐一拒绝", () -> {
            Set<String> allow = new HashSet<String>(Arrays.asList("ep1"));
            EndpointPolicy.Endpoint good = new EndpointPolicy.Endpoint(
                    "ep1", "relay.example.com", 443,
                    EndpointPolicy.Transport.WSS, "主端点", "");
            eq(good.id, "ep1", "构造器");
            isTrue(EndpointPolicy.validate(good, allow).ok, "合法端点");
            eq(EndpointPolicy.validate(good, Collections.<String>emptySet()).reject,
                    EndpointPolicy.Reject.NOT_ALLOWLISTED, "未登记");
            eq(EndpointPolicy.validate(new EndpointPolicy.Endpoint(
                    "ep1", "10.0.0.5", 443, EndpointPolicy.Transport.WSS, "", ""), allow).reject,
                    EndpointPolicy.Reject.PRIVATE_NETWORK, "私网原因准确");
            eq(EndpointPolicy.validate(new EndpointPolicy.Endpoint(
                    "ep1", "relay.example.com", 22, EndpointPolicy.Transport.WSS, "", ""), allow).reject,
                    EndpointPolicy.Reject.BAD_PORT, "非法端口");
            eq(EndpointPolicy.validate(new EndpointPolicy.Endpoint(
                    "ep1", "relay.example.com", 443, EndpointPolicy.Transport.WSS, "", "abc"), allow).reject,
                    EndpointPolicy.Reject.BAD_PIN, "非法 pin");
        });
        h.test("normalizePath 保留 scheme 的 //，Deep Link 不再被误拒", () -> {
            eq(EndpointPolicy.normalizePath("xqly://app/dev-portal"),
                    "xqly://app/dev-portal", "scheme 不被折叠");
            eq(EndpointPolicy.normalizePath("xqly://app//dev-portal"),
                    "xqly://app/dev-portal", "路径重复斜杠归一化");
            isTrue(EndpointPolicy.deepLinkAllowed("xqly://app/dev-portal"), "抽屉深链");
            isTrue(EndpointPolicy.deepLinkAllowed("xqly://app/dev-portal?view=session"), "带查询");
            isTrue(EndpointPolicy.deepLinkAllowed("xqly://app/dev-portal/session/abc_1"), "会话深链");
            eq(EndpointPolicy.sessionIdFrom("xqly://app/dev-portal/session/abc_1"),
                    "abc_1", "会话 id 提取");
            for (String bad : new String[]{"xqly://app/admin", "xqly://app/dev-portal/../admin",
                    "xqly://app/dev-portal/%2e%2e/admin", "xqly://app/dev-portal?evil=1",
                    "xqly://app/dev-portal/session/bad.id"}) {
                isFalse(EndpointPolicy.deepLinkAllowed(bad), "错误放行 " + bad);
            }
            isTrue(EndpointPolicy.queryAllowed("view=session&focus=input"), "合法查询");
            isFalse(EndpointPolicy.queryAllowed("token=abc"), "未知查询键");
            for (String bad : new String[]{"javascript:alert(1)", "file:///etc/passwd",
                    "data:text/html,x", "http://relay.example.com", "https://10.0.0.5/x"}) {
                isFalse(EndpointPolicy.linkAllowed(bad), "错误放行链接 " + bad);
            }
            isTrue(EndpointPolicy.linkAllowed("https://relay.example.com/session"), "合法 HTTPS");
            isFalse(EndpointPolicy.TELEMETRY_DEFAULT, "默认遥测关闭");
        });

        h.group("xqly-bridge/1 · 方法 / 越权拒绝 / 幂等限流 / 脱敏");
        h.test("方法与默认权限仅 6 项，版本与注册表一致", () -> {
            isTrue(BridgeV1.METHODS.size() >= 16, "方法数");
            eqInt(BridgeV1.permissionMatrix().size(), BridgeV1.METHODS.size(), "权限矩阵");
            eqInt(BridgeV1.DEFAULT_PERMISSIONS.size(), 6, "默认权限数");
            isTrue(BridgeV1.DEFAULT_PERMISSIONS.contains("public"), "public 默认授予");
            isFalse(BridgeV1.DEFAULT_PERMISSIONS.contains("credential"), "credential 不授予");
            isFalse(BridgeV1.DEFAULT_PERMISSIONS.contains("telemetry:optional"), "遥测不授予");
            eq(BridgeV1.VERSION, AppRegistry.BRIDGE_VERSION, "桥版本一致");
        });
        h.test("未知方法 / 越界 appId / 版本 / 畸形 JSON / 超长消息 / 非法 id", () -> {
            eq(vcall(env("xqly.unknown", Collections.<String, Object>emptyMap(), "c1")).reject,
                    BridgeV1.Reject.UNKNOWN_METHOD, "未知方法");
            Map<String, Object> foreign = new LinkedHashMap<String, Object>();
            foreign.put("bridge", BridgeV1.VERSION);
            foreign.put("appId", "web");
            foreign.put("id", "c1");
            foreign.put("method", "xqly.env.getInfo");
            foreign.put("params", Collections.<String, Object>emptyMap());
            eq(vcall(MiniJson.stringify(foreign)).reject, BridgeV1.Reject.FOREIGN_APP, "越界应用");
            eq(vcall("{bad json").reject, BridgeV1.Reject.BAD_JSON, "畸形 JSON");
            eq(vcall(repeat("x", 9000)).reject, BridgeV1.Reject.TOO_LARGE, "超长消息");
            eq(vcall(env("xqly.env.getInfo", Collections.<String, Object>emptyMap(), "bad.id")).reject,
                    BridgeV1.Reject.BAD_ID, "非法 id");
        });
        h.test("越权能力 100% 拒绝：凭据 / 遥测 / 工具审批 / 网络 / 导出", () -> {
            String[] methods = {"xqly.credential.getEndpoint", "xqly.credential.unlock",
                    "xqly.telemetry.report", "xqly.devportal.approveTool",
                    "xqly.devportal.connect", "xqly.file.pickSave", "xqly.error.report"};
            Set<String> allow = new HashSet<String>(Arrays.asList("ep1"));
            for (String m : methods) {
                Map<String, Object> p = new LinkedHashMap<String, Object>();
                if (m.endsWith("getEndpoint") || m.endsWith("connect")) p.put("endpointId", "ep1");
                if (m.endsWith("approveTool")) { p.put("toolId", "demo.tool"); p.put("scope", "workspace"); }
                BridgeV1.CallResult r = BridgeV1.validate(env(m, p, "c1"),
                        BridgeV1.DEFAULT_PERMISSIONS, null, 1000L,
                        new HashMap<String, Long>(), true, false, false, allow);
                isFalse(r.ok, m + " 不应放行");
                eq(r.reject, BridgeV1.Reject.PERMISSION_DENIED, m + " 拒绝原因");
            }
        });
        h.test("重复 id 去重与 120ms 同方法限流", () -> {
            Set<String> seen = new HashSet<String>();
            Map<String, Long> last = new HashMap<String, Long>();
            String raw = env("xqly.env.getInfo", Collections.<String, Object>emptyMap(), "x1");
            isTrue(BridgeV1.validate(raw, BridgeV1.DEFAULT_PERMISSIONS, seen, 1000L,
                    last, true, false, false, Collections.<String>emptySet()).ok, "首次");
            eq(BridgeV1.validate(raw, BridgeV1.DEFAULT_PERMISSIONS, seen, 5000L,
                    last, true, false, false, Collections.<String>emptySet()).reject,
                    BridgeV1.Reject.DUPLICATE_ID, "重复 id");
            Map<String, Long> l2 = new HashMap<String, Long>();
            isTrue(vcall(raw, BridgeV1.DEFAULT_PERMISSIONS, 1000L, l2, true, false, false,
                    Collections.<String>emptySet()).ok, "首次调用");
            eq(vcall(raw, BridgeV1.DEFAULT_PERMISSIONS, 1050L, l2, true, false, false,
                    Collections.<String>emptySet()).reject, BridgeV1.Reject.RATE_LIMITED, "限流");
            isTrue(vcall(raw, BridgeV1.DEFAULT_PERMISSIONS, 1200L, l2, true, false, false,
                    Collections.<String>emptySet()).ok, "间隔后放行");
        });
        h.test("参数 fuzzing：超长 / 缺必填 / 危险键 / 受限键 / 合法链接", () -> {
            Map<String, Object> longTitle = new LinkedHashMap<String, Object>();
            longTitle.put("title", repeat("x", 2100));
            eq(vcall(env("xqly.ui.setTitle", longTitle, "c1")).reject,
                    BridgeV1.Reject.BAD_PARAMS, "超长参数");
            Map<String, Object> missing = new LinkedHashMap<String, Object>();
            missing.put("subtitle", "s");
            eq(vcall(env("xqly.ui.setTitle", missing, "c1")).reject,
                    BridgeV1.Reject.BAD_PARAMS, "缺必填");
            Map<String, Object> tokens = new LinkedHashMap<String, Object>();
            tokens.put("tokens", new ArrayList<Object>());
            eq(vcall(env("xqly.theme.subscribe", tokens, "c1")).reject,
                    BridgeV1.Reject.FORBIDDEN_KEY, "危险键");
            Map<String, Object> badUrl = new LinkedHashMap<String, Object>();
            badUrl.put("url", "javascript:x");
            eq(vcall(env("xqly.link.open", badUrl, "c1")).reject,
                    BridgeV1.Reject.BAD_PARAMS, "危险 URL");
            Map<String, Object> goodUrl = new LinkedHashMap<String, Object>();
            goodUrl.put("url", "https://relay.example.com/kb");
            isTrue(vcall(env("xqly.link.open", goodUrl, "c1")).ok, "合法 URL");
        });
        h.test("approveTool 的 scope 与 grant 各司其职", () -> {
            Set<String> wide = new HashSet<String>(BridgeV1.DEFAULT_PERMISSIONS);
            wide.add("user_approval");
            Map<String, Object> ok = new LinkedHashMap<String, Object>();
            ok.put("toolId", "demo.tool"); ok.put("scope", "workspace"); ok.put("grant", "once");
            isTrue(vcall(env("xqly.devportal.approveTool", ok, "c1"), wide, 1000L,
                    new HashMap<String, Long>(), true, false, true,
                    Collections.<String>emptySet()).ok, "合法审批");
            Map<String, Object> bad = new LinkedHashMap<String, Object>();
            bad.put("toolId", "demo.tool"); bad.put("scope", "once"); bad.put("grant", "bogus");
            eq(vcall(env("xqly.devportal.approveTool", bad, "c1"), wide, 1000L,
                    new HashMap<String, Long>(), true, false, true,
                    Collections.<String>emptySet()).reject,
                    BridgeV1.Reject.BAD_PARAMS, "非法 grant");
        });
        h.test("离线降级 / 遥测关闭 / 未审批 三条红线", () -> {
            Set<String> wide = new HashSet<String>(BridgeV1.DEFAULT_PERMISSIONS);
            wide.add("network:devportal"); wide.add("telemetry:optional"); wide.add("user_approval");
            Set<String> allow = new HashSet<String>(Arrays.asList("ep1"));
            Map<String, Object> conn = new LinkedHashMap<String, Object>();
            conn.put("endpointId", "ep1");
            eq(vcall(env("xqly.devportal.connect", conn, "c1"), wide, 1000L,
                    new HashMap<String, Long>(), false, false, false, allow).reject,
                    BridgeV1.Reject.OFFLINE_UNAVAILABLE, "离线降级");
            eq(vcall(env("xqly.telemetry.report", Collections.<String, Object>emptyMap(), "c1"),
                    wide, 1000L, new HashMap<String, Long>(), true, false, false,
                    Collections.<String>emptySet()).reject,
                    BridgeV1.Reject.TELEMETRY_OFF, "遥测关闭");
            Map<String, Object> ap = new LinkedHashMap<String, Object>();
            ap.put("toolId", "demo.tool"); ap.put("scope", "workspace"); ap.put("grant", "once");
            eq(vcall(env("xqly.devportal.approveTool", ap, "c1"), wide, 1000L,
                    new HashMap<String, Long>(), true, false, false,
                    Collections.<String>emptySet()).reject,
                    BridgeV1.Reject.APPROVAL_REQUIRED, "未审批");
        });
        h.test("响应安全与日志脱敏", () -> {
            isTrue(BridgeV1.responseSafe("{\"ok\":true}"), "普通响应安全");
            for (String bad : new String[]{"{\"imei\":\"x\"}", "{\"authorization\":\"Bearer x\"}",
                    "{\"path\":\"file:///data/user/0/x\"}", "{\"api_key\":\"x\"}"}) {
                isFalse(BridgeV1.responseSafe(bad), "错误放行 " + bad);
            }
            isTrue(BridgeV1.redact("{\"token\":\"abc123\"}").contains("\"***\""), "密钥抹除");
            isFalse(BridgeV1.redact("{\"token\":\"abc123\"}").contains("abc123"), "值不残留");
            isTrue(BridgeV1.redact("file:///data/user/0/secret").contains("***"), "路径抹除");
        });

        h.group("反馈队列 / 门户状态机 / URL 策略 / 浮层");
        h.test("反馈时长、位置与有界优先级队列", () -> {
            eqInt(FeedbackQueue.durationFor(FeedbackQueue.Level.ERROR, "x"), 6000, "错误时长");
            eqInt(FeedbackQueue.durationFor(FeedbackQueue.Level.INFO, "x"), 4000, "普通时长");
            eq(FeedbackQueue.PLACEMENT_WEBVIEW, "top-safe-bar", "WebView 位置");
            eq(FeedbackQueue.PLACEMENT_ANDROID, "bottom-bar", "Android 位置");
            FeedbackQueue.State q = new FeedbackQueue.State();
            FeedbackQueue.Feedback info = FeedbackQueue.make("i1",
                    FeedbackQueue.Level.INFO, "普通", "", null);
            FeedbackQueue.Feedback err = FeedbackQueue.make("e1",
                    FeedbackQueue.Level.ERROR, "错误", "", null);
            eq(q.push(info, 0L).id, "i1", "首个提示");
            eq(q.push(err, 10L).id, "e1", "高优先级抢占");
            eqInt(q.pendingCount(), 1, "被抢占者入队");
            notNull(q.tick(7000L), "超时后切到下一条");
        });
        h.test("离线只读 / 连接状态流转 / 断开清空", () -> {
            DevPortalState s = new DevPortalState();
            eq(s.mode(), DevPortalState.Mode.OFFLINE_READ_ONLY, "默认离线只读");
            isFalse(s.canConnect(), "离线不可连");
            isFalse(s.canExecute(), "v1 不执行工具");
            isFalse(s.connect("ep1", new HashSet<String>(Arrays.asList("ep1")), 1000L), "离线连接被拒");
            s.setOnline(true);
            isTrue(s.connect("ep1", new HashSet<String>(Arrays.asList("ep1")), 1000L), "在线可连");
            eq(s.link(), DevPortalState.Link.CHECKING, "检查中");
            s.confirmConnected();
            eq(s.link(), DevPortalState.Link.CONNECTED, "已连接");
            isTrue(s.addSession("s1"), "会话登记");
            s.onChunk();
            eqInt(s.streamedChunks(), 1, "流式块计数");
            s.setOnline(false);
            eq(s.link(), DevPortalState.Link.DISCONNECTED, "回离线断开");
            isTrue(s.sessions().isEmpty(), "会话清空");
            eqInt(s.streamedChunks(), 0, "计数清零");
        });
        h.test("解锁失败三次锁定 60 秒；snapshot 不泄露敏感字段", () -> {
            DevPortalState u = new DevPortalState();
            isTrue(u.recordUnlock(false, 1000L), "第 1 次");
            isTrue(u.recordUnlock(false, 1100L), "第 2 次");
            isFalse(u.recordUnlock(false, 1200L), "第 3 次触发锁定");
            isTrue(u.isLocked(1300L), "锁定中");
            isFalse(u.recordUnlock(true, 1300L), "锁定期间拒绝");
            isFalse(u.isLocked(61200L), "60 秒后解锁");
            String snap = MiniJson.stringify(u.snapshot()).toLowerCase(Locale.US);
            for (String f : new String[]{"token", "secret", "imei", "deviceid", "password", "apikey"})
                isFalse(snap.contains(f), "snapshot 泄露 " + f);
            eq(u.snapshot().get("canExecute"), Boolean.FALSE, "不暴露执行能力");
            eq(u.snapshot().get("telemetry"), Boolean.FALSE, "零遥测");
            isTrue(DevPortalState.offlineEmptyState().containsKey("next"), "离线态含下一步");
        });
        h.test("URL 策略：dev-portal 子目录精确放行，其它子目录拒绝", () -> {
            eq(UrlPolicy.classify("file:///android_asset/apps/dev-portal/index.html"),
                    UrlPolicy.Kind.LOCAL_ASSET, "内置页");
            eq(UrlPolicy.classify("file:///android_asset/apps/evil/index.html"),
                    UrlPolicy.Kind.BLOCKED, "任意子目录");
            isTrue(UrlPolicy.bridgeAllowed("file:///android_asset/apps/dev-portal/index.html"), "可注入桥");
            isFalse(UrlPolicy.bridgeAllowed("file:///android_asset/apps/evil/index.html"), "不可注入桥");
            isTrue(UrlPolicy.localResourceAllowed("file:///android_asset/apps/dev-portal/app.js"), "资源白名单");
            isFalse(UrlPolicy.localResourceAllowed("file:///android_asset/apps/dev-portal/secret.js"), "未登记资源");
            eq(UrlPolicy.DEV_PORTAL_PAGE, "apps/dev-portal/index.html", "页面常量");
        });
        h.test("OverlayController 已纳入 DEV_PORTAL 且仍满足互斥不变量", () -> {
            isTrue(OverlayController.Layer.OVERLAYS.length >= 4, "浮层数");
            OverlayController c = new OverlayController();
            OverlayController.Transition t = c.open(OverlayController.Layer.DEV_PORTAL);
            eq(t.show, OverlayController.Layer.DEV_PORTAL, "显示开发者平台");
            isTrue(c.invariant(t), "互斥不变量");
            isTrue(contains(t.hide, OverlayController.Layer.LIBRARY), "资料库被隐藏");
            isTrue(contains(t.hide, OverlayController.Layer.GAME), "游戏被隐藏");
            eq(c.onBack().show, OverlayController.Layer.NONE, "返回键收敛");
        });
    }

    private static String repeat(String c, int n) {
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < n; i++) b.append(c);
        return b.toString();
    }

    /** 按迁移结果计算可见层数（模拟调用方无条件重设可见性）。 */
    private static int visibleCount(OverlayController.Transition t) {
        return t.show == Layer.NONE ? 0 : 1;
    }

    private static boolean contains(Layer[] arr, Layer x) {
        for (Layer l : arr) if (l == x) return true;
        return false;
    }

    // ---------------------------------------------------------------- AI 工作台 · Java 镜像
    private static void workbenchTests(TestHarness h) {
        h.group("AI 工作台 · 会话 / 供应商 / 插件 / 投递区 / 自检 / 日志");
        h.test("应用注册表含 ai-workbench：独立分区 / workbench 作用域 / 路由", () -> {
            AppRegistry.AppSpec a = AppRegistry.byId(AppRegistry.AI_WORKBENCH_ID);
            notNull(a, "工作台已注册");
            eq(a.offline, AppRegistry.Offline.FULL, "离线可用");
            eq(a.bridgeScope, "workbench:*", "作用域");
            isTrue(AppRegistry.storageIsolated("ai-workbench", "dev-portal"), "存储隔离");
            isTrue(AppRegistry.routeAllowed("xqly-app://apps/ai-workbench"), "路由");
            isFalse(AppRegistry.routeAllowed("xqly-app://apps/ai-workbench/../evil"), "穿越拒绝");
        });
        h.test("桥作用域按 appId 匹配；工作台方法零网络/审批/遥测/密钥参数", () -> {
            eq(BridgeV1.scopeFor("dev-portal"), "devportal:*", "门户作用域");
            eq(BridgeV1.scopeFor("ai-workbench"), "workbench:*", "工作台作用域");
            eq(BridgeV1.scopeFor("evil"), null, "外来 appId");
            isTrue(BridgeV1.METHODS.size() >= 31, "方法数");
            int n = 0;
            for (BridgeV1.MethodSpec s : BridgeV1.METHODS.values()) {
                if (!s.name.startsWith("xqly.workbench.")) continue;
                n++;
                isFalse(s.network, s.name + " 不联网");
                isFalse(s.requiresApproval, s.name + " 不需审批");
                isFalse(s.permission.equals("telemetry:optional"), s.name + " 不遥测");
                for (String k : s.allowedKeys) {
                    isFalse(k.equals("apiKey") || k.equals("key") || k.equals("secret")
                        || k.equals("token") || k.equals("api_key"), s.name + " 参数 " + k);
                }
            }
            eqInt(n, 15, "工作台方法数");
        });
        h.test("会话仓库：新建 / 追加 / 排序 / 上限保护", () -> {
            WorkbenchChat c = new WorkbenchChat();
            isTrue(c.create("s1", "会话一", 100L), "建 s1");
            isTrue(c.create("s2", "会话二", 200L), "建 s2");
            isFalse(c.create("s1", "重复", 300L), "重复拒绝");
            isFalse(c.create("../bad", "穿越", 300L), "非法 id");
            isTrue(c.append("s1", WorkbenchChat.Role.USER, "草稿", 300L), "追加");
            isFalse(c.append("s1", WorkbenchChat.Role.USER, "", 300L), "空文本拒绝");
            eq(c.list().get(0).get("id"), "s1", "最近更新优先");
            isTrue(c.remove("s2"), "删除");
            eqInt(c.count(), 1, "剩余数");
        });
        h.test("供应商目录：9 模板 / 只登记布尔配置态 / 自建主机校验", () -> {
            eqInt(WorkbenchProvider.CATALOG.size(), 9, "模板数");
            isTrue(WorkbenchProvider.validId("deepseek"), "合法 id");
            isFalse(WorkbenchProvider.validId("evil"), "非法 id");
            WorkbenchProvider p = new WorkbenchProvider();
            isTrue(p.markConfigured("deepseek", true), "标记配置");
            isTrue(p.isConfigured("deepseek"), "已配置");
            isTrue(WorkbenchProvider.customHostAllowed("api.example.com"), "公网主机");
            isFalse(WorkbenchProvider.customHostAllowed("127.0.0.1"), "私网拒绝");
            isFalse(WorkbenchProvider.customHostAllowed("localhost"), "环回拒绝");
            for (Map<String, Object> e : p.list()) {
                isTrue(e.containsKey("configured"), "有配置态");
                isFalse(e.toString().toLowerCase(java.util.Locale.US).contains("apikey"), "无密钥");
            }
        });
        h.test("插件清单：注册 / 启停 / 更新回滚（回滚后禁用）", () -> {
            WorkbenchPlugin p = new WorkbenchPlugin();
            isTrue(p.register("npm.pkg", "示例", "npm", "1.0.0"), "注册");
            isFalse(p.register("npm.pkg", "重复", "npm", "1.0.1"), "重复拒绝");
            isTrue(p.toggle("npm.pkg", true), "启用");
            eq(p.list().get(0).get("enabled"), true, "启用态");
            isTrue(p.update("npm.pkg", "1.1.0"), "更新");
            isFalse(p.update("npm.pkg", "1.0.9"), "禁止降级");
            isTrue(p.rollback("npm.pkg"), "回滚");
            eq(p.list().get(0).get("version"), "1.0.0", "版本回退");
            eq(p.list().get(0).get("enabled"), false, "回滚后禁用");
        });
        h.test("投递区：工作区约束 / 穿越与编码绕过拒绝 / 稳定标识", () -> {
            isTrue(WorkbenchDropzone.check("root/1/a.txt").allowed, "合法相对路径");
            isFalse(WorkbenchDropzone.check("root/1/../evil").allowed, "穿越拒绝");
            isFalse(WorkbenchDropzone.check("/etc/passwd").allowed, "绝对拒绝");
            isFalse(WorkbenchDropzone.check("root/1/%2e%2e/x").allowed, "编码拒绝");
            isFalse(WorkbenchDropzone.check("other/1/a.txt").allowed, "工作区外拒绝");
            eqInt(WorkbenchDropzone.check("root/1/a.txt").safeId.length(), 19, "标识长度");
        });
        h.test("自检：运行时项 UNAVAILABLE 不算失败；诊断日志统一脱敏与窗口", () -> {
            java.util.List<WorkbenchSelfCheck.Item> items = WorkbenchSelfCheck.run(
                true, true, true, true, true, 1024);
            eqInt(items.size(), 19, "自检项数");
            eq(WorkbenchSelfCheck.summary(items).get("healthy"), true, "健康");
            eqInt(((Number) WorkbenchSelfCheck.summary(items).get("unavailable")).intValue(), 13, "运行时不可用数");
            WorkbenchLog l = new WorkbenchLog(16);
            l.append(WorkbenchLog.Level.INFO, "UI", "apiKey=\"sk-secret-1234567890\" file:///data/x", 1L);
            isFalse(l.query(null, "", 8).get(0).text.contains("sk-secret"), "密钥脱敏");
            isFalse(l.query(null, "", 8).get(0).text.contains("file:///data"), "路径脱敏");
            for (int i = 0; i < 100; i++) l.append(WorkbenchLog.Level.INFO, "UI", "msg " + i, i);
            eqInt(l.query(null, "", 5).size(), 5, "尾部窗口");
            isTrue(WorkbenchLog.verdict("credential missing").contains("凭据"), "错误判读");
        });
        h.test("聚合 snapshot 不泄露密钥/令牌，不提供执行与模型能力", () -> {
            WorkbenchState w = new WorkbenchState();
            Map<String, Object> s = w.snapshot(true, true, true, true, true, 1024);
            eq(s.get("canExecute"), false, "不执行工具");
            eq(s.get("canCallModel"), false, "不发模型请求");
            eq(s.get("credentialStored"), false, "无密钥落盘");
            eq(s.get("telemetry"), false, "默认零遥测");
            isFalse(s.toString().toLowerCase(java.util.Locale.US).contains("apikey"), "无密钥字段");
        });
    }

}
