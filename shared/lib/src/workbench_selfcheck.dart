
/// 自检项状态：pass / warn / fail / unavailable（本客户端未提供该运行时）。
enum CheckState { pass, warn, fail, unavailable }

/// 自检条目：对标 harness 的 18 项自检 UI，但如实标注"未提供运行时"。
class CheckItem {
  final String id;
  final String label;
  final CheckState state;
  final String note;

  const CheckItem(this.id, this.label, this.state, this.note);

  Map<String, Object?> toJson() => <String, Object?>{
        'id': id,
        'label': label,
        'state': state.name,
        'note': note,
      };
}

/// 环境自检：只对**本客户端真实具备**的能力报 pass；
/// PRoot / Node / Ubuntu / Landlock 等运行时一律 unavailable，不伪造。
class WorkbenchSelfCheck {
  static List<CheckItem> run({
    required bool manifestOk,
    required bool tokensOk,
    required bool bridgeOk,
    required bool signedOk,
    required bool offlineAssetsOk,
    int freeMb = 0,
  }) {
    final List<CheckItem> r = <CheckItem>[
      CheckItem('manifest', '内置资源清单',
          manifestOk ? CheckState.pass : CheckState.fail,
          manifestOk ? '清单哈希校验通过' : '清单校验失败，禁止继续'),
      CheckItem('tokens', '共享设计令牌',
          tokensOk ? CheckState.pass : CheckState.fail,
          tokensOk ? '三端令牌逐字节一致' : '令牌不一致'),
      CheckItem('bridge', 'xqly-bridge/1 协议',
          bridgeOk ? CheckState.pass : CheckState.fail,
          bridgeOk ? '16+N 条方法 schema 已加载' : '协议未就绪'),
      CheckItem('signature', 'APK 签名证书',
          signedOk ? CheckState.pass : CheckState.warn,
          signedOk ? '正式签名，可覆盖升级' : '测试签名，请勿作为正式版'),
      CheckItem('offline', '离线随包资源',
          offlineAssetsOk ? CheckState.pass : CheckState.warn,
          offlineAssetsOk ? '档案/游戏可离线使用' : '部分离线资源缺失'),
      CheckItem('storage', '可用空间',
          freeMb >= 512 ? CheckState.pass : CheckState.warn,
          freeMb >= 512 ? '$freeMb MB，充足' : '$freeMb MB，建议清理至 512 MB 以上'),
    ];
    // 对标 harness 的运行时自检项：本客户端明确不内置，逐项 unavailable。
    const List<String> runtimeItems = <String>[
      'shell', 'node', 'proot', 'landlock', 'pty', 'ripgrep',
      'compiler', 'plugin_exec', 'shell_tools', 'shizuku',
      'a11y', 'virtual_display', 'webview_runtime',
    ];
    for (final String id in runtimeItems) {
      r.add(CheckItem(id, _labelOf(id), CheckState.unavailable,
          '本客户端零依赖内置，不提供该运行时；见模组说明'));
    }
    return List<CheckItem>.unmodifiable(r);
  }

  static String _labelOf(String id) {
    const Map<String, String> labels = <String, String>{
      'shell': 'Shell (bash)',
      'node': 'Node.js',
      'proot': 'PRoot 沙箱',
      'landlock': '内核 Landlock',
      'pty': 'PTY 终端',
      'ripgrep': 'ripgrep',
      'compiler': 'C 编译器 / make / Python',
      'plugin_exec': '第三方插件执行',
      'shell_tools': '设备 Shell 工具',
      'shizuku': 'Shizuku 授权',
      'a11y': '无障碍自动化',
      'virtual_display': '目标应用副屏',
      'webview_runtime': '运行时控制台',
    };
    return labels[id] ?? id;
  }

  /// 汇总：只有 fail 阻断；unavailable 是预期边界，不算失败。
  static Map<String, Object?> summary(List<CheckItem> items) {
    int pass = 0, warn = 0, fail = 0, unavailable = 0;
    for (final CheckItem i in items) {
      switch (i.state) {
        case CheckState.pass: pass++; break;
        case CheckState.warn: warn++; break;
        case CheckState.fail: fail++; break;
        case CheckState.unavailable: unavailable++; break;
      }
    }
    return <String, Object?>{
      'total': items.length,
      'pass': pass,
      'warn': warn,
      'fail': fail,
      'unavailable': unavailable,
      'healthy': fail == 0,
    };
  }
}
