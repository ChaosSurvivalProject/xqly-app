import 'foundation.dart';

/// 内置应用离线能力。与「可玩的小游戏 / 完整站点」严格区分：
/// dev-portal 首版只登记 readOnly，离线仅提供外壳与帮助，不承诺远程执行。
enum OfflineCapability { full, readOnly, none }

/// 应用注册表中的一条应用定义。
///
/// 这是「单一 WebView 池 + 多应用路由 + 独立沙箱」的**唯一真源**：
/// Android 原生层、Windows 外壳与 Web 层都从这里取 appId / 路由 / 作用域，
/// 不允许任何一端自行另建应用标识或第二套容器。
class AppSpec {
  final String id;
  final String displayName;
  final String subtitle;
  final String route;
  final String bridgeScope;
  final String storagePartition;
  final OfflineCapability offline;
  final int maxNavDepth;

  /// UI 上常驻的社区标识（非官方内容不藏在关于页里）。
  final String communityLabel;

  /// 图标用的中文短标签；不复制任何上游 logo / 商标词。
  final String iconLabel;

  const AppSpec({
    required this.id,
    required this.displayName,
    required this.subtitle,
    required this.route,
    required this.bridgeScope,
    required this.storagePartition,
    required this.offline,
    required this.maxNavDepth,
    required this.communityLabel,
    required this.iconLabel,
  });

  Map<String, Object?> toJson() => <String, Object?>{
        'id': id,
        'displayName': displayName,
        'subtitle': subtitle,
        'route': route,
        'bridgeScope': bridgeScope,
        'storagePartition': storagePartition,
        'offlineCapability': offline.name,
        'maxNavDepth': maxNavDepth,
        'communityLabel': communityLabel,
        'iconLabel': iconLabel,
      };
}

/// 导航四层：应用抽屉(1) → 内置应用首页(2) → 详情/会话(3) → 模态/审批(4)。
/// 抽屉是全局导航，不得被 WebView 浏览器历史吞掉。
class AppNav {
  AppNav._();

  static const int drawer = 1;
  static const int appHome = 2;
  static const int detail = 3;
  static const int modal = 4;
}

class AppRegistry {
  AppRegistry._();

  static const String bridgeVersion = 'xqly-bridge/1';
  static const String routeScheme = 'xqly-app';
  static const String deepLinkScheme = 'xqly';
  static const String devPortalId = 'dev-portal';
  static const String aiWorkbenchId = 'ai-workbench';

  static const List<AppSpec> apps = <AppSpec>[
    AppSpec(
      id: 'web',
      displayName: '星穹旅驿',
      subtitle: '官网 · 社区 · 档案',
      route: 'xqly-app://apps/web',
      bridgeScope: 'site:*',
      storagePartition: 'web',
      offline: OfflineCapability.full,
      maxNavDepth: AppNav.detail,
      communityLabel: '',
      iconLabel: '首页',
    ),
    AppSpec(
      id: 'cube-craft',
      displayName: '方块合成',
      subtitle: '离线小游戏',
      route: 'xqly-app://apps/cube-craft',
      bridgeScope: 'game:*',
      storagePartition: 'cube-craft',
      offline: OfflineCapability.full,
      maxNavDepth: AppNav.detail,
      communityLabel: '',
      iconLabel: '游戏',
    ),
    AppSpec(
      id: devPortalId,
      displayName: '星穹旅驿开发者平台',
      subtitle: '开发者工具·远程门户',
      route: 'xqly-app://apps/dev-portal',
      bridgeScope: 'devportal:*',
      storagePartition: 'dev-portal',
      offline: OfflineCapability.readOnly,
      maxNavDepth: AppNav.modal,
      communityLabel: '社区非官方 · 自研远程门户',
      iconLabel: '开发',
    ),
    AppSpec(
      id: aiWorkbenchId,
      displayName: '星穹旅驿 AI 工作台',
      subtitle: '本地交互对标 · 不内置模型与运行时',
      route: 'xqly-app://apps/ai-workbench',
      bridgeScope: 'workbench:*',
      storagePartition: 'ai-workbench',
      offline: OfflineCapability.full,
      maxNavDepth: AppNav.detail,
      communityLabel: '社区非官方 · 零依赖功能对标',
      iconLabel: '工作台',
    ),
  ];

  static AppSpec? byId(String id) {
    for (final AppSpec a in apps) {
      if (a.id == id) return a;
    }
    return null;
  }

  static bool registered(String id) => byId(id) != null;

  static String? routeFor(String id) => byId(id)?.route;

  /// 应用内路由只允许 `xqly-app://apps/<已注册 id>`，禁路径穿越、禁查询注入。
  static bool routeAllowed(String route) {
    if (route.isEmpty || route.length > 128) return false;
    if (!route.startsWith('$routeScheme://apps/')) return false;
    if (route.contains('..') || route.contains('\\') || route.contains('?') ||
        route.contains('#') || route.contains('\u0000')) {
      return false;
    }
    final String id = route.substring('$routeScheme://apps/'.length);
    return registered(id);
  }

  /// 桥作用域：dev-portal 只能拿到 `devportal:*`，不得跨应用调用。
  static bool scopeAllows(String appId, String scope) {
    final AppSpec? a = byId(appId);
    if (a == null) return false;
    if (scope.isEmpty || scope.length > 64) return false;
    return scope == a.bridgeScope;
  }

  /// 每应用独立 Web 存储分区；不同应用绝不共用一个分区。
  static bool storageIsolated(String a, String b) {
    if (!registered(a) || !registered(b)) return false;
    return byId(a)!.storagePartition != byId(b)!.storagePartition;
  }

  static OfflineCapability offlineOf(String id) =>
      byId(id)?.offline ?? OfflineCapability.none;

  /// 离线包中该应用是否有可展示内容；none 时显示「本模块在离线档案中不可用」。
  static bool offlineAvailable(String id) {
    final OfflineCapability c = offlineOf(id);
    return c == OfflineCapability.full || c == OfflineCapability.readOnly;
  }

  static bool navDepthAllowed(String id, int depth) {
    final AppSpec? a = byId(id);
    return a != null && depth >= AppNav.drawer && depth <= a.maxNavDepth;
  }

  static Map<String, Object?> toJson() => <String, Object?>{
        'bridgeVersion': bridgeVersion,
        'navLevels': <String, int>{
          'drawer': AppNav.drawer,
          'appHome': AppNav.appHome,
          'detail': AppNav.detail,
          'modal': AppNav.modal,
        },
        'apps': <Object?>[for (final AppSpec a in apps) a.toJson()],
      };

  static String describe() => Json.encode(toJson());
}
