/// 所有页面的完整状态（禁止「只有成功界面 + 无限旋转」）。
enum Phase {
  loading,
  success,
  empty,
  offlineCached,
  offlineNoCache,
  errorRetry,
  partial,
  validationFailed,
  permissionDenied,
  storageLow,
  pendingSwitch,
  syncConflict,
  syncCancelled,
}

enum Page {
  home,
  library,
  article,
  search,
  favorite,
  download,
  update,
  game,
  sync,
}

class ViewState {
  final Phase phase;
  final String title;
  final String message;
  final bool showSpinner;
  final bool showRetry;
  final String actionLabel;
  final bool recoverable;

  const ViewState(this.phase, this.title, this.message, this.showSpinner,
      this.showRetry, this.actionLabel, this.recoverable);
}

class AppState {
  AppState._();

  static ViewState of(Page page, Phase phase) {
    switch (phase) {
      case Phase.loading:
        return ViewState(phase, '正在准备内容',
            '首次打开会读取本地索引，无需联网。', true, false, '', true);
      case Phase.success:
        return ViewState(phase, '', '', false, false, '', true);
      case Phase.empty:
        return ViewState(phase, _emptyTitle(page),
            '换个关键词，或清除筛选条件后再试。', false, false, '', true);
      case Phase.offlineCached:
        return ViewState(phase, '当前离线 · 使用本机内容',
            '展示的是随应用内置的离线镜像，联网后可在设置中检查更新。',
            false, false, '', true);
      case Phase.offlineNoCache:
        return ViewState(phase, '暂无离线内容',
            '本机还没有保存该内容，请联网后重试或先下载离线包。',
            false, true, '去下载', true);
      case Phase.errorRetry:
        return ViewState(phase, '加载失败',
            '内容读取出错，可以重试；已保存的内容不受影响。',
            false, true, '重试', true);
      case Phase.partial:
        return ViewState(phase, '部分内容可用',
            '部分条目暂时无法读取，其余内容仍可正常浏览。',
            false, true, '刷新', true);
      case Phase.validationFailed:
        return ViewState(phase, '内容校验未通过',
            '已保留当前可用版本，临时内容不会被启用。',
            false, true, '查看详情', true);
      case Phase.permissionDenied:
        return ViewState(phase, '权限被拒绝',
            '请在系统设置中授予所需权限后重试；基础离线阅读仍可使用。',
            false, true, '去设置', true);
      case Phase.storageLow:
        return ViewState(phase, '存储空间不足',
            '请清理空间后重试；不会删除收藏、同步库与游戏存档。',
            false, true, '清理缓存', true);
      case Phase.pendingSwitch:
        return ViewState(phase, '新内容待切换',
            '新版本已校验完成，将在下次启动时生效。',
            false, false, '', true);
      case Phase.syncConflict:
        return ViewState(phase, '存在同步冲突',
            '请逐项选择保留哪一份，未确认前不会改动本地数据。',
            false, true, '处理冲突', true);
      case Phase.syncCancelled:
        return ViewState(phase, '同步已取消',
            '未生成半份数据，本地库保持原状。',
            false, true, '重新开始', true);
    }
  }

  static bool requiresOverlay(Phase p) => p != Phase.success;

  static String _emptyTitle(Page p) {
    switch (p) {
      case Page.favorite:
        return '还没有收藏';
      case Page.library:
        return '还没有阅读记录';
      case Page.search:
        return '没有找到匹配内容';
      case Page.download:
        return '没有已下载的内容';
      case Page.game:
        return '还没有游戏记录';
      case Page.sync:
        return '还没有同步记录';
      default:
        return '这里还是空的';
    }
  }
}
