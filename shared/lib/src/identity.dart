/// 发布者、更新来源、桥版本、签名钉扎与产物命名的**唯一真源**。
///
/// Windows 外壳与 Android 客户端都从这里取值，避免两平台规则漂移。
/// 对应 Android 侧 `src/cn/xqly/app/core/AppIdentity.java`，两侧常量必须逐项一致，
/// 测试 `identity 与 Android 常量一致` 会校验关键项。
class AppIdentity {
  AppIdentity._();

  static const String appName = '星穹旅驿';
  static const String androidPackage = 'cn.xqly.app';

  /// 当前业务版本：Android 与 Windows 使用同一个业务版本，不复制规则实现。
  static const String versionName = '2.1.1';
  static const int versionCode = 8;

  /// Windows 四段版本（MSIX / VERSIONINFO 要求）。
  static const String windowsVersion = '2.1.1.0';

  static const String publisher = 'ChaosSurvivalProject';
  static const String repo = 'ChaosSurvivalProject/xqly-app';
  static const String siteHost = 'xqly.xt91tv.shop';
  static const String siteBase = 'https://$siteHost';
  static const String apiHost = 'api.github.com';

  static const List<String> downloadHosts = <String>[
    'github.com',
    'objects.githubusercontent.com',
    'release-assets.githubusercontent.com',
  ];

  static const String latestReleaseApi =
      'https://$apiHost/repos/$repo/releases/latest';

  /// 正式发布签名证书 SHA-256（小写 hex，无冒号）。
  /// 与 Android 侧 `EXPECTED_SIGNING_CERT_SHA256` 一致；改密钥必须双平台同步。
  static const String expectedSigningCertSha256 =
      'e1e8edc56e9700a27ab2cd4ba5f45fa0ba1bd9a88e4a261de7ef3684b3c21c05';

  static const int bridgeVersion = 2;
  static const String bridgeName = 'XQLYBridge';

  /// **不要改**：已发布客户端的 `UpdateVerdict.validAssetName` 要求
  /// 资产名 = 前缀 + 与 tag 相同的版本 + 后缀，否则 OTA 会发现不到新版本。
  static const String apkAssetPrefix = 'xqly-app-v';
  static const String apkAssetSuffix = '.apk';

  static const int apkMinBytes = 1024;
  static const int apkMaxBytes = 120 * 1024 * 1024;

  static const int packMaxFiles = 4096;
  static const int packMaxSingleBytes = 32 * 1024 * 1024;
  static const int packMaxTotalBytes = 256 * 1024 * 1024;
  static const int packMaxExpandRatio = 200;

  /// 内容包清单 schema 版本。
  static const int contentManifestSchema = 1;

  /// 同步包 schema 版本（见 sync_package.dart）。
  static const int syncSchema = 1;

  static String androidReleaseAsset(String version) =>
      '$apkAssetPrefix$version$apkAssetSuffix';

  /// 用户友好的中文别名资产。仅作为附加下载项上传，不参与 OTA 解析。
  static const String userFacingAndroidAsset =
      '星穹旅驿-2.1.1-android-universal-release.apk';
}
