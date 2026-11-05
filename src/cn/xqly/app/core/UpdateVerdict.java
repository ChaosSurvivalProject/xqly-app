package cn.xqly.app.core;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 可信更新链的「判定」核心（纯逻辑，可测）：
 * 解析 GitHub Release JSON → 选出合规资产 → 结合下载后的实测值
 * （文件 SHA-256、APK 签名证书指纹、versionCode、versionName）
 * 给出逐项可展示的校验结论。任何一项失败都不得安装。
 *
 * 关键：不因 URL 来自 GitHub 就信任；自签名成功 ≠ 发布者可信。
 */
public final class UpdateVerdict {
    private UpdateVerdict() {}

    public static final class Check {
        public final String key, label;
        public final boolean pass;
        public final String detail;
        Check(String k, String l, boolean p, String d) { key = k; label = l; pass = p; detail = d; }
    }

    public static final class Asset {
        public final String name, url, digest;
        public final long size;
        Asset(String n, String u, String d, long s) { name = n; url = u; digest = d; size = s; }
    }

    /** Release 元数据（从 API 精选，不保留全文/敏感字段）。 */
    public static final class Release {
        public final String tag, name, body, htmlUrl;
        public final boolean draft, prerelease;
        public final Asset asset;
        Release(String tag, String name, String body, String htmlUrl,
                boolean draft, boolean prerelease, Asset asset) {
            this.tag = tag; this.name = name; this.body = body; this.htmlUrl = htmlUrl;
            this.draft = draft; this.prerelease = prerelease; this.asset = asset;
        }
    }

    /** 下载完成后由 Android 层实测填入。 */
    public static final class Measured {
        public final long bytes;
        public final String fileSha256;      // 整包摘要
        public final String signingCertSha256; // APK 签名证书指纹
        public final long apkVersionCode;
        public final String apkVersionName;
        public Measured(long b, String f, String c, long vc, String vn) {
            bytes = b; fileSha256 = f; signingCertSha256 = c; apkVersionCode = vc; apkVersionName = vn;
        }
    }

    public static final class Verdict {
        public final boolean trusted;
        public final List<Check> checks;
        Verdict(boolean t, List<Check> c) { trusted = t; checks = c; }
        public List<Check> failures() {
            List<Check> out = new ArrayList<Check>();
            for (Check c : checks) if (!c.pass) out.add(c);
            return out;
        }
    }

    public enum ParseError { OK, BAD_JSON, NO_TAG, NOT_OFFICIAL, DRAFT_OR_PRERELEASE, NO_VALID_ASSET, BAD_ASSET_HOST }

    public static final class ParseResult {
        public final ParseError error;
        public final Release release;
        ParseResult(ParseError e, Release r) { error = e; release = r; }
    }

    /** 从 GitHub API JSON 中严格提取，只接受官方仓库、非草稿、命名合规的资产。 */
    public static ParseResult parseRelease(String json) {
        final Map<String, Object> root;
        try { root = MiniJson.object(json); }
        catch (Exception e) { return new ParseResult(ParseError.BAD_JSON, null); }

        String htmlUrl = MiniJson.str(root, "html_url", "");
        // 钉扎发布者：html_url 必须指向官方仓库的 release
        if (!htmlUrl.startsWith("https://github.com/" + AppIdentity.REPO + "/releases/tag/"))
            return new ParseResult(ParseError.NOT_OFFICIAL, null);

        boolean draft = MiniJson.bool(root, "draft", true);
        boolean pre = MiniJson.bool(root, "prerelease", true);
        if (draft || pre) return new ParseResult(ParseError.DRAFT_OR_PRERELEASE, null);

        String tag = MiniJson.str(root, "tag_name", "");
        if (tag.isEmpty() || VersionPolicy.parse(tag) == null)
            return new ParseResult(ParseError.NO_TAG, null);

        String name = MiniJson.str(root, "name", tag);
        String body = MiniJson.str(root, "body", "");
        if (body.length() > 8000) body = body.substring(0, 8000);

        List<Object> assets = MiniJson.arr(root, "assets");
        Asset chosen = null;
        if (assets != null) {
            for (Object o : assets) {
                if (!(o instanceof Map)) continue;
                @SuppressWarnings("unchecked") Map<String, Object> a = (Map<String, Object>) o;
                String an = MiniJson.str(a, "name", "");
                String url = MiniJson.str(a, "browser_download_url", "");
                String dig = MiniJson.str(a, "digest", "");
                long sz = MiniJson.num(a, "size", 0);
                if (!validAssetName(an, tag)) continue;
                if (!downloadHostAllowed(url)) return new ParseResult(ParseError.BAD_ASSET_HOST, null);
                chosen = new Asset(an, url, dig, sz);
                break;
            }
        }
        if (chosen == null) return new ParseResult(ParseError.NO_VALID_ASSET, null);
        return new ParseResult(ParseError.OK,
                new Release(tag, name, body, htmlUrl, draft, pre, chosen));
    }

    public static boolean validAssetName(String name, String tag) {
        if (name == null || tag == null) return false;
        if (!name.startsWith(AppIdentity.APK_ASSET_PREFIX) || !name.endsWith(AppIdentity.APK_ASSET_SUFFIX))
            return false;
        String version = name.substring(AppIdentity.APK_ASSET_PREFIX.length(),
                name.length() - AppIdentity.APK_ASSET_SUFFIX.length());
        return VersionPolicy.compare(version, tag) == 0;
    }

    public static boolean downloadHostAllowed(String url) {
        if (url == null) return false;
        for (String h : AppIdentity.DOWNLOAD_HOSTS) {
            if (url.startsWith("https://" + h + "/")) return true;
        }
        return false;
    }

    /** 安装前最终校验（所有项必须 pass 才 trusted）。 */
    public static Verdict evaluate(Release rel, Measured m,
                                   String currentName, long currentCode,
                                   String pinnedCert) {
        List<Check> checks = new ArrayList<Check>();
        add(checks, "publisher", "官方发布者", rel != null && rel.htmlUrl != null
                && rel.htmlUrl.startsWith("https://github.com/" + AppIdentity.REPO + "/"), rel == null ? "无 Release" : rel.htmlUrl);
        if (rel == null || m == null) {
            add(checks, "download", "下载完整", false, "无实测值");
            return new Verdict(false, checks);
        }

        add(checks, "asset_name", "资产命名与版本匹配",
                validAssetName(rel.asset.name, rel.tag), rel.asset.name);
        add(checks, "asset_host", "下载来源主机",
                downloadHostAllowed(rel.asset.url), rel.asset.url);

        boolean sizeOk = m.bytes >= AppIdentity.APK_MIN_BYTES
                && m.bytes <= AppIdentity.APK_MAX_BYTES
                && (rel.asset.size <= 0 || rel.asset.size == m.bytes);
        add(checks, "size", "文件大小合理", sizeOk, "实测=" + m.bytes + " 声明=" + rel.asset.size);

        String declared = VersionPolicy.normalizeSha256(rel.asset.digest);
        String actual = VersionPolicy.normalizeSha256(m.fileSha256);
        boolean digestOk = declared != null && actual != null && declared.equals(actual);
        add(checks, "sha256", "整包 SHA-256 摘要", digestOk,
                "声明=" + (declared == null ? "缺失" : declared.substring(0, 8))
                        + " 实测=" + (actual == null ? "缺失" : actual.substring(0, 8)));

        String pin = VersionPolicy.normalizeSha256(pinnedCert);
        String cert = VersionPolicy.normalizeSha256(m.signingCertSha256);
        boolean certOk = pin != null && cert != null && pin.equals(cert);
        add(checks, "signature", "APK 签名证书指纹钉扎", certOk,
                cert == null ? "未取到签名证书" : (certOk ? "匹配" : "不匹配/签名变化"));

        VersionPolicy.Decision d = VersionPolicy.decide(rel.tag, m.apkVersionCode, currentName, currentCode);
        add(checks, "version", "版本与 versionCode 非降级", d == VersionPolicy.Decision.UPGRADE,
                "decision=" + d + " tag=" + rel.tag + " code=" + m.apkVersionCode);

        add(checks, "apk_version", "包内版本与 Release 一致",
                VersionPolicy.compare(m.apkVersionName, rel.tag) == 0
                        && m.apkVersionCode > 0,
                "包内=" + m.apkVersionName + "/" + m.apkVersionCode);

        boolean all = true;
        for (Check c : checks) if (!c.pass) all = false;
        return new Verdict(all, checks);
    }

    private static void add(List<Check> l, String k, String label, boolean p, String d) {
        l.add(new Check(k, label, p, d == null ? "" : d));
    }

    /** 错误分类：用于 UI 呈现与埋点（不含敏感信息）。 */
    public enum NetError { NONE, TIMEOUT, NO_NETWORK, HTTP, TLS, PARSE, CANCELLED, IO, DISK_FULL, UNKNOWN }

    public static NetError classify(Throwable t, Integer httpCode) {
        if (t == null && httpCode == null) return NetError.NONE;
        if (httpCode != null) {
            if (httpCode == 401 || httpCode == 403) return NetError.HTTP;
            if (httpCode >= 500) return NetError.HTTP;
            if (httpCode == 404) return NetError.HTTP;
            return NetError.HTTP;
        }
        String n = t == null ? "" : t.getClass().getSimpleName().toLowerCase();
        String msg = t == null ? "" : String.valueOf(t.getMessage()).toLowerCase();
        if (n.contains("timeout") || msg.contains("timed out") || msg.contains("timeout")) return NetError.TIMEOUT;
        if (n.contains("ssl") || n.contains("certificate") || msg.contains("ssl") || msg.contains("cert")) return NetError.TLS;
        if (n.contains("unknownhost") || n.contains("connect") || msg.contains("unreachable") || msg.contains("network")) return NetError.NO_NETWORK;
        if (n.contains("json") || n.contains("parse")) return NetError.PARSE;
        if (msg.contains("no space") || msg.contains("enospc")) return NetError.DISK_FULL;
        if (n.contains("interrupted") || msg.contains("cancel")) return NetError.CANCELLED;
        if (n.contains("io")) return NetError.IO;
        return NetError.UNKNOWN;
    }
}
