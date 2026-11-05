#pragma once
// 星穹旅驿 Windows 外壳 · 平台无关核心。
//
// 这一层不 include <windows.h>，只用标准 C++17，因此：
//   1) 可在 Linux 构建机上用 clang++/g++ 真实编译并运行单元测试；
//   2) 在 Windows 上与 Win32 外壳一起由 MSVC 编译，规则不复制第二份。
// 领域规则的唯一真源是 shared/ 的纯 Dart 包；此处只实现窗口/输入/协议/
// 桥校验等桌面外壳自身的逻辑，并通过版本化通道与 Dart 核心协作。
#include <cstdint>
#include <map>
#include <string>
#include <vector>

namespace xqly {

// 极简 JSON 取值器：只用于读取本机可信配置文件，不用于解析网络响应。
// 网络/同步包的严格解析统一由 shared/ 的 Dart 核心负责。
namespace json {
int Int(const std::string& s, const std::string& key, int def);
bool Bool(const std::string& s, const std::string& key, bool def);
std::string Str(const std::string& s, const std::string& key, const std::string& def);
std::string Escape(const std::string& s);
// 提取某个对象（如 "args"）内的键名列表，用于危险键检查。
std::vector<std::string> ObjectKeys(const std::string& s, const std::string& key);
}  // namespace json

// ---------------------------------------------------------------- 窗口状态

struct WindowState {
  int x = 100, y = 100, width = 1280, height = 800;
  bool maximized = false;
  bool snapped = false;  // 分屏贴靠状态
  int dpi = 96;          // 96 = 100%，288 = 300%
  std::string display;   // 显示器标识，用于多显示器恢复

  std::string Serialize() const;
  static WindowState Parse(const std::string& text);
  bool Valid() const;
  /// 显示器拔除 / DPI 变化后把窗口移回可见区域，避免窗口"消失"。
  WindowState ClampToDisplay(int dx, int dy, int dw, int dh) const;
};

// ---------------------------------------------------------------- 多标签

class TabManager {
 public:
  struct Tab {
    std::string id;
    std::string title;
    std::string path;
    bool pinned = false;
  };

  std::string NewTab(const std::string& path, const std::string& title);
  bool Close(const std::string& id);
  bool RestoreLastClosed();
  bool Move(size_t from, size_t to);  // 拖拽重排
  std::string Cycle(bool forward);    // Ctrl+Tab / Ctrl+Shift+Tab
  void Pin(const std::string& id, bool pinned);
  std::string ActiveId() const { return active_; }
  size_t Count() const { return tabs_.size(); }
  size_t ClosedCount() const { return closed_.size(); }
  const std::vector<Tab>& Tabs() const { return tabs_; }

 private:
  std::vector<Tab> tabs_;
  std::vector<Tab> closed_;
  std::string active_;
  uint64_t seq_ = 0;
};

// ---------------------------------------------------------------- 快捷键

enum class Action {
  kNone, kNewTab, kCloseTab, kNextTab, kPrevTab, kRestoreTab,
  kFind, kQuickOpen, kSettings, kRefresh, kEscape, kPauseGame,
};

// 修饰键位：与 Windows MOD_CONTROL/MOD_SHIFT/MOD_ALT 对齐。
enum Mods : uint32_t { kCtrl = 1, kShift = 2, kAlt = 4 };

struct Binding {
  uint32_t mods = 0;
  uint32_t key = 0;
  Action action = Action::kNone;
  std::string label;
  std::string Display() const;
};

class ShortcutRegistry {
 public:
  ShortcutRegistry();
  const std::vector<Binding>& Bindings() const { return bindings_; }
  Action Resolve(uint32_t mods, uint32_t key) const;
  /// 冲突检测：除 except 外已有相同组合则返回 true。
  bool HasConflict(uint32_t mods, uint32_t key, Action except) const;
  /// 重绑失败返回 false（冲突时明确提示，不静默失效）。
  bool Rebind(Action action, uint32_t mods, uint32_t key);

 private:
  std::vector<Binding> bindings_;
};

// ---------------------------------------------------------------- 自定义协议

enum class ProtoReject {
  kOk, kBadScheme, kBadHost, kUnknownCommand, kBadArgs, kTooLong, kBadChars,
};

struct ProtoResult {
  bool ok = false;
  ProtoReject reject = ProtoReject::kBadScheme;
  std::string command;
  std::vector<std::string> args;
};

/// 只接受白名单命令，绝不执行任意参数或拼命令行。
ProtoResult ParseProtocolUri(const std::string& uri);

// ---------------------------------------------------------------- 平台桥

enum class BridgeReject {
  kOk, kTooLarge, kBadVersion, kBadOrigin, kUnknownType,
  kForbiddenKey, kUntrusted, kPermission,
};

struct BridgeResult {
  bool ok = false;
  BridgeReject reject = BridgeReject::kBadVersion;
  std::string type;
};

/// WebView2 → 宿主的 postMessage 最小校验（版本/来源/类型/危险键/权限/长度）。
/// 与 shared/lib/src/bridge_protocol.dart 规则对齐。
BridgeResult ValidateBridgeMessage(const std::string& raw,
                                   const std::string& page_origin,
                                   bool trusted, bool allow_write);

// ---------------------------------------------------------------- 设计令牌

struct Tokens {
  bool loaded = false;
  std::string brand, theme, mode;
  std::map<std::string, std::string> colors;
  int motion_medium_ms = 240;
  double max_reading_width = 760.0;
};

/// 从随包的 design-tokens.json（由 shared/tool/gen_tokens.dart 生成）读取指定主题。
Tokens LoadTokens(const std::string& json_text, const std::string& theme,
                  const std::string& mode);

}  // namespace xqly
