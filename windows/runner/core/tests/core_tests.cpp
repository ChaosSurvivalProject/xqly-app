// Windows 外壳平台无关核心的单元测试。
// 零第三方依赖，Linux 与 Windows 均可编译运行：
//   cmake -S windows/runner/core -B /tmp/xqly-core-build && cmake --build /tmp/xqly-core-build
//   /tmp/xqly-core-build/xqly_core_tests windows/runner/resources/design-tokens.json
#include "platform_core.h"

#include <fstream>
#include <functional>
#include <iostream>
#include <sstream>
#include <stdexcept>

static int g_passed = 0;
static int g_failed = 0;
static std::string g_tokens_path;

static void T(const std::string& name, const std::function<void()>& fn) {
  try {
    fn();
    ++g_passed;
    std::cout << "  PASS  " << name << "\n";
  } catch (const std::exception& e) {
    ++g_failed;
    std::cout << "  FAIL  " << name << "  ->  " << e.what() << "\n";
  }
}

static void OK(bool c, const std::string& m = "") {
  if (!c) throw std::runtime_error(m.empty() ? "断言失败" : m);
}

static void EQI(long long a, long long b, const std::string& m = "") {
  if (a != b) {
    throw std::runtime_error(m + " 期望=" + std::to_string(b) +
                             " 实际=" + std::to_string(a));
  }
}

static void EQS(const std::string& a, const std::string& b,
                const std::string& m = "") {
  if (a != b) throw std::runtime_error(m + " 期望=" + b + " 实际=" + a);
}

static std::string Msg(const std::string& type, const std::string& args,
                       const std::string& origin, int v = 2) {
  return "{\"v\":" + std::to_string(v) + ",\"type\":\"" + type +
         "\",\"args\":" + args + ",\"origin\":\"" + origin +
         "\",\"id\":\"m1\"}";
}

static std::string ReadFile(const std::string& p) {
  std::ifstream f(p);
  if (!f) throw std::runtime_error("无法读取 " + p);
  std::stringstream ss;
  ss << f.rdbuf();
  return ss.str();
}

int main(int argc, char** argv) {
  using namespace xqly;
  if (argc > 1) g_tokens_path = argv[1];

  std::cout << "── 窗口状态（尺寸 / 位置 / DPI / 多显示器）──\n";
  T("序列化 → 解析往返保留全部字段", [] {
    WindowState s;
    s.x = 320; s.y = 180; s.width = 1600; s.height = 900;
    s.maximized = true; s.snapped = true; s.dpi = 192;
    s.display = "DISPLAY2";
    const WindowState b = WindowState::Parse(s.Serialize());
    EQI(b.x, 320); EQI(b.y, 180); EQI(b.width, 1600); EQI(b.height, 900);
    OK(b.maximized); OK(b.snapped); EQI(b.dpi, 192); EQS(b.display, "DISPLAY2");
  });
  T("非法几何 / 越界 DPI 被拒绝", [] {
    WindowState s; s.width = 10; OK(!s.Valid());
    s.width = 1280; s.dpi = 400; OK(!s.Valid());
    s.dpi = 96; OK(s.Valid());
  });
  T("显示器拔除后窗口移回可见区域", [] {
    WindowState s; s.x = 4000; s.y = 3000; s.width = 1280; s.height = 800;
    const WindowState b = s.ClampToDisplay(0, 0, 1920, 1080);
    OK(b.x >= 0 && b.x + b.width <= 1920, "x 越界");
    OK(b.y >= 0 && b.y + b.height <= 1080, "y 越界");
    OK(!b.snapped, "失效的分屏状态应清除");
  });
  T("300% 缩放仍在合法范围", [] {
    WindowState s; s.dpi = 288; OK(s.Valid());
  });
  T("损坏配置回退到默认尺寸", [] {
    const WindowState b = WindowState::Parse("not-json");
    EQI(b.width, 1280); EQI(b.height, 800); OK(b.Valid());
  });

  std::cout << "── 多标签（新建 / 关闭 / 恢复 / 拖拽）──\n";
  T("新建三个标签并激活最后一个", [] {
    TabManager m;
    m.NewTab("/a", "A"); m.NewTab("/b", "B");
    const std::string c = m.NewTab("/c", "C");
    EQI(m.Count(), 3); EQS(m.ActiveId(), c);
  });
  T("Ctrl+Tab / Ctrl+Shift+Tab 循环切换", [] {
    TabManager m;
    const std::string a = m.NewTab("/a", "A");
    m.NewTab("/b", "B");
    m.Cycle(true);
    EQS(m.ActiveId(), a, "向前循环应回到首个");
    m.Cycle(false);
    OK(m.ActiveId() != a, "向后循环应切到另一个");
  });
  T("关闭标签进入最近关闭栈", [] {
    TabManager m;
    const std::string a = m.NewTab("/a", "A");
    m.NewTab("/b", "B");
    OK(m.Close(a)); EQI(m.Count(), 1); EQI(m.ClosedCount(), 1);
  });
  T("恢复最近关闭的标签", [] {
    TabManager m;
    const std::string a = m.NewTab("/a", "A");
    m.Close(a);
    OK(m.RestoreLastClosed()); EQI(m.Count(), 1);
    EQS(m.ActiveId(), "tab-1");
  });
  T("固定标签不可关闭", [] {
    TabManager m;
    const std::string a = m.NewTab("/a", "A");
    m.Pin(a, true);
    OK(!m.Close(a)); EQI(m.Count(), 1);
  });
  T("拖拽重排改变顺序", [] {
    TabManager m;
    m.NewTab("/a", "A"); m.NewTab("/b", "B"); m.NewTab("/c", "C");
    OK(m.Move(0, 2));
    EQS(m.Tabs()[2].path, "/a");
  });
  T("越界拖拽被拒绝", [] {
    TabManager m; m.NewTab("/a", "A");
    OK(!m.Move(0, 9)); OK(!m.Move(9, 0));
  });

  std::cout << "── 快捷键（默认表 / 冲突 / 重绑）──\n";
  T("默认绑定覆盖全部要求的动作", [] {
    ShortcutRegistry r;
    OK(r.Bindings().size() >= 12);
    OK(r.Resolve(kCtrl, 'T') == Action::kNewTab);
    OK(r.Resolve(kCtrl, 'W') == Action::kCloseTab);
    OK(r.Resolve(kCtrl, 9) == Action::kNextTab);
    OK(r.Resolve(kCtrl | kShift, 9) == Action::kPrevTab);
    OK(r.Resolve(kCtrl | kShift, 'T') == Action::kRestoreTab);
    OK(r.Resolve(kCtrl, 'F') == Action::kFind);
    OK(r.Resolve(kCtrl, 'K') == Action::kQuickOpen);
    OK(r.Resolve(kCtrl, 188) == Action::kSettings);
    OK(r.Resolve(0, 116) == Action::kRefresh);
    OK(r.Resolve(kCtrl, 'R') == Action::kRefresh);
    OK(r.Resolve(0, 27) == Action::kEscape);
  });
  T("冲突重绑明确失败，不静默失效", [] {
    ShortcutRegistry r;
    OK(r.HasConflict(kCtrl, 'T', Action::kFind));
    OK(!r.Rebind(Action::kFind, kCtrl, 'T'), "与 Ctrl+T 冲突应失败");
    OK(r.Resolve(kCtrl, 'T') == Action::kNewTab, "原绑定不受影响");
  });
  T("无冲突重绑成功并生效", [] {
    ShortcutRegistry r;
    OK(r.Rebind(Action::kFind, kCtrl | kAlt, 'F'));
    OK(r.Resolve(kCtrl | kAlt, 'F') == Action::kFind);
  });
  T("Display 生成可读文本", [] {
    ShortcutRegistry r;
    bool found = false;
    for (const auto& b : r.Bindings()) {
      if (b.action == Action::kPrevTab) {
        EQS(b.Display(), "Ctrl+Shift+Tab");
        found = true;
      }
    }
    OK(found);
  });

  std::cout << "── 自定义协议（白名单命令）──\n";
  T("合法 xqly://app/open 通过", [] {
    const ProtoResult r = ParseProtocolUri("xqly://app/open/wiki/intro");
    OK(r.ok); EQS(r.command, "open"); EQI(r.args.size(), 2);
    EQS(r.args[0], "wiki");
  });
  T("javascript: / file: 协议拒绝", [] {
    OK(!ParseProtocolUri("javascript:alert(1)").ok);
    OK(!ParseProtocolUri("file:///C:/Windows/system32").ok);
    OK(!ParseProtocolUri("https://evil.com/app/open").ok);
  });
  T("非 app 主机拒绝", [] {
    OK(ParseProtocolUri("xqly://evil/open").reject == ProtoReject::kBadHost);
  });
  T("未知命令拒绝", [] {
    OK(ParseProtocolUri("xqly://app/format").reject ==
       ProtoReject::kUnknownCommand);
  });
  T("路径穿越与特殊字符参数拒绝", [] {
    OK(!ParseProtocolUri("xqly://app/open/../secret").ok);
    OK(!ParseProtocolUri("xqly://app/open/a;calc.exe").ok);
    OK(!ParseProtocolUri("xqly://app/open/%2e%2e").ok);
  });
  T("超长 URI 拒绝", [] {
    OK(ParseProtocolUri("xqly://app/open/" + std::string(3000, 'a')).reject ==
       ProtoReject::kTooLong);
  });
  T("参数过多拒绝", [] {
    OK(ParseProtocolUri("xqly://app/open/a/b/c/d/e/f").reject ==
       ProtoReject::kBadArgs);
  });

  std::cout << "── 平台桥（版本 / 来源 / 类型 / 危险键 / 权限）──\n";
  const std::string kOrigin = "https://xqly.xt91tv.shop/";
  T("合法消息通过", [&] {
    const BridgeResult r = ValidateBridgeMessage(
        Msg("getState", "{}", kOrigin), kOrigin, true, true);
    OK(r.ok); EQS(r.type, "getState");
  });
  T("旧版本拒绝", [&] {
    OK(ValidateBridgeMessage(Msg("getState", "{}", kOrigin, 1),
                             kOrigin, true, true).reject ==
       BridgeReject::kBadVersion);
  });
  T("伪造 origin 与真实页面不符拒绝", [&] {
    OK(ValidateBridgeMessage(Msg("getState", "{}", "https://evil.com/"),
                             kOrigin, true, true).reject ==
       BridgeReject::kBadOrigin);
  });
  T("不可信页面拒绝", [&] {
    OK(ValidateBridgeMessage(Msg("getState", "{}", kOrigin),
                             kOrigin, false, true).reject ==
       BridgeReject::kUntrusted);
  });
  T("未知类型拒绝", [&] {
    OK(ValidateBridgeMessage(Msg("exec", "{}", kOrigin),
                             kOrigin, true, true).reject ==
       BridgeReject::kUnknownType);
  });
  T("危险键 token 拒绝", [&] {
    OK(ValidateBridgeMessage(Msg("getState", "{\"token\":\"x\"}", kOrigin),
                             kOrigin, true, true).reject ==
       BridgeReject::kForbiddenKey);
  });
  T("写操作缺少权限拒绝", [&] {
    OK(ValidateBridgeMessage(Msg("favorite", "{}", kOrigin),
                             kOrigin, true, false).reject ==
       BridgeReject::kPermission);
  });
  T("超长消息拒绝", [&] {
    OK(ValidateBridgeMessage(std::string(9000, 'x'), kOrigin, true, true)
           .reject == BridgeReject::kTooLarge);
  });

  std::cout << "── 设计令牌（读取随包 JSON）──\n";
  T("从真实 design-tokens.json 加载 dark/glass", [&] {
    OK(!g_tokens_path.empty(), "未提供令牌文件路径");
    const Tokens t = LoadTokens(ReadFile(g_tokens_path), "dark", "glass");
    OK(t.loaded, "未找到 dark/glass 映射");
    EQS(t.brand, "星穹玻璃");
    EQI(t.motion_medium_ms, 240);
  });
  T("语义色齐全（12 个）", [&] {
    const Tokens t = LoadTokens(ReadFile(g_tokens_path), "dark", "glass");
    EQI(t.colors.size(), 12);
    OK(t.colors.count("background") == 1);
    OK(t.colors.count("brand") == 1);
    OK(t.colors.count("focus") == 1);
    OK(t.colors.at("brand") == "#a87cff");
  });
  T("浅色 / 增强可读主题可加载且颜色不同", [&] {
    const Tokens light = LoadTokens(ReadFile(g_tokens_path), "light", "contrast");
    OK(light.loaded);
    const Tokens dark = LoadTokens(ReadFile(g_tokens_path), "dark", "glass");
    OK(light.colors.at("text") != dark.colors.at("text"));
  });

  std::cout << "\n" << std::string(58, '=') << "\n";
  std::cout << " 结果: " << g_passed << " passed, " << g_failed
            << " failed, " << (g_passed + g_failed) << " total\n";
  std::cout << std::string(58, '=') << "\n";
  return g_failed == 0 ? 0 : 1;
}
