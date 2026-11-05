package cn.xqly.app.core;

/**
 * 固定发布者与可信来源标识（安全边界的唯一真源）。
 *
 * 说明：这里记录的是「期望」发布者身份，不是密钥。真正的信任来自
 * ① GitHub Release 所属仓库；② APK 签名证书 SHA-256 指纹钉扎；③ 摘要校验。
 * 三者任一不符即拒装。指纹由维护者在发布指南中用 keytool 取得后填入，
 * 本地占位值指向当前真实发布证书，可在构建期用 -D 覆盖（不入库任何私钥）。
 */
public final class AppIdentity {
    private AppIdentity() {}

    public static final String PACKAGE = "cn.xqly.app";
    public static final String PUBLISHER = "ChaosSurvivalProject";
    public static final String REPO = "ChaosSurvivalProject/xqly-app";

    /** 官方站点主机（WebView 白名单与内容来源）。 */
    public static final String SITE_HOST = "xqly.xt91tv.shop";
    public static final String SITE_BASE = "https://" + SITE_HOST;

    /** 允许的更新 API 主机（严格匹配，不含端口/子域）。 */
    public static final String API_HOST = "api.github.com";
    /** 允许下载 APK 资产的重定向主机（GitHub 对象存储）。 */
    public static final String[] DOWNLOAD_HOSTS = {
            "github.com", "objects.githubusercontent.com", "release-assets.githubusercontent.com"
    };

    public static final String RELEASES_LATEST =
            "https://" + API_HOST + "/repos/" + REPO + "/releases/latest";

    /**
     * 官方 APK 签名证书 SHA-256 指纹（小写无分隔，64 hex）。
     * 这是「钉扎」信任根：下载 APK 后必须重新计算签名证书指纹并与之比对。
     * 为空 = 未配置 → 更新中心将拒绝一切安装并给出人工补齐指引。
     */
    public static final String EXPECTED_SIGNING_CERT_SHA256 =
            "e1e8edc56e9700a27ab2cd4ba5f45fa0ba1bd9a88e4a261de7ef3684b3c21c05";

    /** 桥协议版本：任何消息必须携带并匹配，旧版本一律拒绝。 */
    public static final int BRIDGE_VERSION = 2;
    public static final String BRIDGE_NAME = "XQLYBridge";

    /** 客户端资产 APK 命名规范：xqly-app-vX.Y.Z.apk（ASCII，固定前缀）。 */
    public static final String APK_ASSET_PREFIX = "xqly-app-v";
    public static final String APK_ASSET_SUFFIX = ".apk";

    /** 下载体积红线：小于 1KB 或大于 120MB 视为异常。 */
    public static final long APK_MIN_BYTES = 1024L;
    public static final long APK_MAX_BYTES = 120L * 1024 * 1024;

    /** 内容包上限，防止超大解压比 / 磁盘耗尽。 */
    public static final long PACK_MAX_FILES = 4096;
    public static final long PACK_MAX_TOTAL_BYTES = 256L * 1024 * 1024;
    public static final long PACK_MAX_SINGLE_BYTES = 32L * 1024 * 1024;
    public static final double PACK_MAX_EXPAND_RATIO = 200.0;
}
