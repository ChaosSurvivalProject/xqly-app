#include "win32_app.h"

#include <cmath>
#include <new>

namespace xqly {

namespace {
constexpr WORD MapAction(Action a) {
  switch (a) {
    case Action::kNewTab: return IDM_NEWTAB;
    case Action::kCloseTab: return IDM_CLOSETAB;
    case Action::kNextTab: return IDM_NEXTTAB;
    case Action::kPrevTab: return IDM_PREVTAB;
    case Action::kRestoreTab: return IDM_RESTORETAB;
    case Action::kFind: return IDM_FIND;
    case Action::kQuickOpen: return IDM_QUICKOPEN;
    case Action::kSettings: return IDM_SETTINGS;
    case Action::kRefresh: return IDM_REFRESH;
    case Action::kEscape: return IDM_ESCAPE;
    case Action::kPauseGame: return IDM_PAUSEGAME;
    default: return 0;
  }
}
}  // namespace

/// 由 ShortcutRegistry 的唯一真源生成加速键表；改动绑定不会两处漂移。
/// 冲突在注册表层已拒绝（见 core_tests），此处不再静默覆盖。
HACCEL BuildAccelerators() {
  ACCEL items[16] = {};
  size_t n = 0;
  for (const Binding& b : g_app.shortcuts.Bindings()) {
    if (n >= 16) break;
    const WORD cmd = MapAction(b.action);
    if (cmd == 0) continue;
    BYTE virt = FVIRTKEY | FNOINVERT;
    if (b.mods & kCtrl) virt |= FCONTROL;
    if (b.mods & kShift) virt |= FSHIFT;
    if (b.mods & kAlt) virt |= FALT;
    items[n].fVirt = virt;
    items[n].key = static_cast<WORD>(b.key);
    items[n].cmd = cmd;
    ++n;
  }
  return CreateAcceleratorTableW(items, static_cast<int>(n));
}

bool HandleCommand(WORD id) {
  switch (id) {
    case IDM_NEWTAB:
      g_app.tabs.NewTab("/", "新标签");
      break;
    case IDM_CLOSETAB:
      if (!g_app.tabs.Close(g_app.tabs.ActiveId())) {
        g_app.tabs.RestoreLastClosed();  // 固定标签不可关时保持可玩/可读
      }
      break;
    case IDM_NEXTTAB: g_app.tabs.Cycle(true); break;
    case IDM_PREVTAB: g_app.tabs.Cycle(false); break;
    case IDM_RESTORETAB: g_app.tabs.RestoreLastClosed(); break;
    case IDM_FIND:
      // 聚焦搜索框：鼠标悬停不能成为获取功能的唯一方式，Ctrl+F 始终可用。
      if (g_app.content) SendMessageW(g_app.content, WM_APP + 11, 0, 0);
      break;
    case IDM_QUICKOPEN:
      if (g_app.content) SendMessageW(g_app.content, WM_APP + 12, 0, 0);
      break;
    case IDM_SETTINGS:
      if (g_app.content) SendMessageW(g_app.content, WM_APP + 13, 0, 0);
      break;
    case IDM_REFRESH:
      if (g_app.content) SendMessageW(g_app.content, WM_APP + 14, 0, 0);
      break;
    case IDM_ESCAPE:
      if (g_app.content) SendMessageW(g_app.content, WM_APP + 15, 0, 0);
      break;
    case IDM_PAUSEGAME:
      g_app.paused = !g_app.paused;
      if (g_app.content)
        SendMessageW(g_app.content, WM_APP + 10, g_app.paused ? 1 : 0, 0);
      break;
    case IDM_TOGGLE_NOTIFY:
      g_app.notifications = !g_app.notifications;
      break;
    case IDM_TOGGLE_AUTOSTART:
      // 必须由用户在设置中显式同意，绝不默认开启。
      g_app.autostart = SetAutoStart(!g_app.autostart);
      break;
    case IDM_TOGGLE_TRAY:
      g_app.tray_enabled = !g_app.tray_enabled;
      if (g_app.tray_enabled) AddTrayIcon(g_app.hwnd);
      else RemoveTrayIcon(g_app.hwnd);
      break;
    case IDM_EXPORT_SYNC:
      if (g_app.content) SendMessageW(g_app.content, WM_APP + 16, 0, 0);
      break;
    case IDM_IMPORT_SYNC:
      if (g_app.content) SendMessageW(g_app.content, WM_APP + 17, 0, 0);
      break;
    case IDM_CLEAR_CACHE:
      // 清理前列出影响；不得删除收藏、同步库与游戏存档。
      if (g_app.content) SendMessageW(g_app.content, WM_APP + 18, 0, 0);
      break;
    case IDM_TRAY_OPEN:
      ShowWindow(g_app.hwnd, SW_RESTORE);
      SetForegroundWindow(g_app.hwnd);
      break;
    case IDM_TRAY_UPDATE:
      if (g_app.content) SendMessageW(g_app.content, WM_APP + 19, 0, 0);
      break;
    case IDM_TRAY_SYNC:
      if (g_app.content) SendMessageW(g_app.content, WM_APP + 16, 0, 0);
      break;
    case IDM_TRAY_EXIT:
      // 明确的「退出」动作，关闭窗口本身不触发。
      SaveWindowState();
      RemoveTrayIcon(g_app.hwnd);
      DestroyWindow(g_app.hwnd);
      break;
    default:
      return false;
  }
  InvalidateRect(g_app.hwnd, nullptr, FALSE);
  return true;
}

/// 游戏键鼠映射：方向键移动/旋转、空格硬降、Shift 暂存、Esc 暂停。
/// 输入缓冲与「重复按键不异常加速」由内容层的固定逻辑步长处理。
void HandleGameKey(WPARAM vk, bool down) {
  if (!g_app.content || g_app.paused) return;
  WPARAM code = 0;
  switch (vk) {
    case VK_LEFT: code = 1; break;
    case VK_RIGHT: code = 2; break;
    case VK_UP: code = 3; break;
    case VK_DOWN: code = 4; break;
    case VK_SPACE: code = 5; break;
    case VK_SHIFT: code = 6; break;
    case VK_ESCAPE: code = 7; break;
    default: return;
  }
  SendMessageW(g_app.content, WM_APP + 20, code, down ? 1 : 0);
}

void EnableTouch(HWND hwnd) {
  // Windows 触屏 / 平板：接受触摸与手掌抑制，提供惯性滚动。
  RegisterTouchWindow(hwnd, TWF_WANTPALM);
}

bool OnTouch(WPARAM wp, LPARAM lp) {
  const UINT count = LOWORD(wp);
  if (count == 0) return false;
  HTOUCHINPUT h = reinterpret_cast<HTOUCHINPUT>(lp);
  TOUCHINPUT* ti = new (std::nothrow) TOUCHINPUT[count];
  if (!ti) return false;
  bool handled = false;
  if (GetTouchInputInfo(h, count, ti, sizeof(TOUCHINPUT))) {
    // 单指滑动 → 滚动/方向输入；长按 → 交给内容层弹出上下文菜单。
    const TOUCHINPUT& t = ti[0];
    if ((t.dwFlags & TOUCHEVENTF_MOVE) && g_app.content) {
      SendMessageW(g_app.content, WM_APP + 21,
                   static_cast<WPARAM>(t.x / 100),
                   static_cast<LPARAM>(t.y / 100));
      handled = true;
    }
  }
  delete[] ti;
  CloseTouchInputHandle(h);
  return handled;
}

bool OnPointer(UINT msg, WPARAM wp, LPARAM lp) {
  // 触控笔：悬停反馈、按压、擦除/选择由内容层按 Pointer 类型区分。
  const UINT id = GET_POINTERID_WPARAM(wp);
  POINTER_INFO pi = {};
  if (!GetPointerInfo(id, &pi)) return false;
  if (g_app.content) {
    SendMessageW(g_app.content, WM_APP + 22,
                 static_cast<WPARAM>(msg),
                 static_cast<LPARAM>(pi.pointerType));
  }
  (void)lp;
  return true;
}

}  // namespace xqly
