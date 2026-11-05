#include "win32_app.h"

#include <dwmapi.h>

#include <fstream>
#include <sstream>

#ifndef DWMWA_USE_IMMERSIVE_DARK_MODE
#define DWMWA_USE_IMMERSIVE_DARK_MODE 20
#endif

namespace xqly {

static std::wstring StatePath() {
  return g_app.data_dir + L"\\window-state.json";
}

bool RegisterWindowClass(HINSTANCE h) {
  WNDCLASSEXW wc = {};
  wc.cbSize = sizeof(wc);
  wc.style = CS_HREDRAW | CS_VREDRAW;
  wc.lpfnWndProc = WndProc;
  wc.hInstance = h;
  wc.hIcon = LoadIconW(h, MAKEINTRESOURCEW(101));
  wc.hCursor = LoadCursorW(nullptr, IDC_ARROW);
  wc.hbrBackground = CreateSolidBrush(RGB(10, 14, 24));  // 深空底，不用星空粒子
  wc.lpszClassName = ActiveWindowClass();
  return RegisterClassExW(&wc) != 0;
}

bool CreateMainWindow(HINSTANCE h, int nCmdShow) {
  HWND hwnd = CreateWindowExW(
      WS_EX_NOREDIRECTIONBITMAP, ActiveWindowClass(), kWindowTitle,
      WS_OVERLAPPEDWINDOW, g_app.state.x, g_app.state.y, g_app.state.width,
      g_app.state.height, nullptr, nullptr, h, nullptr);
  if (!hwnd) return false;
  g_app.hwnd = hwnd;
  g_app.dpi = GetDpiForWindow(hwnd);

  // 标题栏跟随系统暗色主题，与内容协调，保持 Windows 操作习惯。
  BOOL dark = TRUE;
  DwmSetWindowAttribute(hwnd, DWMWA_USE_IMMERSIVE_DARK_MODE, &dark,
                        sizeof(dark));

  ShowWindow(hwnd, g_app.state.maximized ? SW_MAXIMIZE : nCmdShow);
  UpdateWindow(hwnd);
  RelayoutContent(hwnd);
  return true;
}

void LoadWindowState() {
  std::ifstream f(StatePath());
  if (!f) return;
  std::stringstream ss;
  ss << f.rdbuf();
  const WindowState s = WindowState::Parse(ss.str());
  if (s.Valid()) g_app.state = s;  // 配置损坏时保留默认值
}

void SaveWindowState() {
  if (!g_app.hwnd) return;
  WINDOWPLACEMENT wp = {};
  wp.length = sizeof(wp);
  if (GetWindowPlacement(g_app.hwnd, &wp)) {
    g_app.state.maximized = wp.showCmd == SW_SHOWMAXIMIZED;
    if (!g_app.state.maximized) {
      RECT r = {};
      if (GetWindowRect(g_app.hwnd, &r)) {
        g_app.state.x = r.left;
        g_app.state.y = r.top;
        g_app.state.width = r.right - r.left;
        g_app.state.height = r.bottom - r.top;
      }
    }
  }
  g_app.state.dpi = static_cast<int>(GetDpiForWindow(g_app.hwnd));
  std::ofstream f(StatePath(), std::ios::trunc);
  if (f) f << g_app.state.Serialize();
}

/// 大屏三栏（列表—正文—目录），中小窗口自动收起右栏；
/// 正文限制最大阅读宽度，工具面板不挤压正文。
void RelayoutContent(HWND hwnd) {
  RECT rc = {};
  if (!GetClientRect(hwnd, &rc)) return;
  const int w = rc.right - rc.left;
  const int scale = static_cast<int>(g_app.dpi) * 100 / 96;
  const int rail_w = 220 * scale / 100;
  const int toc_w = 240 * scale / 100;
  const bool wide = w >= (rail_w + 640 + toc_w) * scale / 100;
  const int content_x = rail_w;
  const int content_w = wide ? w - rail_w - toc_w : w - rail_w;
  // content 是 WebView2 / 原生阅读器的宿主区域；此处仅保存布局意图，
  // 实际子窗口移动由内容承载层在 WM_SIZE 时同步处理。
  if (g_app.content) {
    MoveWindow(g_app.content, content_x, 0, content_w > 0 ? content_w : 1,
               rc.bottom - rc.top, TRUE);
  }
}

LRESULT CALLBACK WndProc(HWND hwnd, UINT msg, WPARAM wp, LPARAM lp) {
  switch (msg) {
    case WM_DPICHANGED: {
      // 多显示器移动 / DPI 变化：按系统建议的新矩形调整，不模糊。
      g_app.dpi = HIWORD(wp);
      const RECT* r = reinterpret_cast<const RECT*>(lp);
      if (r) {
        SetWindowPos(hwnd, nullptr, r->left, r->top, r->right - r->left,
                     r->bottom - r->top, SWP_NOZORDER | SWP_NOACTIVATE);
      }
      RelayoutContent(hwnd);
      return 0;
    }
    case WM_DISPLAYCHANGE: {
      // 显示器拔除：把窗口移回当前虚拟桌面可见区。
      g_app.state.x = g_app.state.ClampToDisplay(
                              GetSystemMetrics(SM_XVIRTUALSCREEN),
                              GetSystemMetrics(SM_YVIRTUALSCREEN),
                              GetSystemMetrics(SM_CXVIRTUALSCREEN),
                              GetSystemMetrics(SM_CYVIRTUALSCREEN))
                          .x;
      SetWindowPos(hwnd, nullptr, g_app.state.x, g_app.state.y, 0, 0,
                   SWP_NOSIZE | SWP_NOZORDER | SWP_NOACTIVATE);
      RelayoutContent(hwnd);
      return 0;
    }
    case WM_SIZE:
      RelayoutContent(hwnd);
      return 0;
    case WM_COMMAND:
      if (HandleCommand(LOWORD(wp))) return 0;
      break;
    case WM_TOUCH:
      if (OnTouch(wp, lp)) return 0;
      break;
    case WM_POINTERDOWN:
    case WM_POINTERUP:
    case WM_POINTERUPDATE:
      if (OnPointer(msg, wp, lp)) return 0;
      break;
    case WM_XQLY_TRAY:
      OnTrayMessage(hwnd, wp, lp);
      return 0;
    case WM_XQLY_ACTIVATE:
      ShowWindow(hwnd, SW_RESTORE);
      SetForegroundWindow(hwnd);
      return 0;
    case WM_COPYDATA: {
      // 单实例：把第二次启动的命令行交给已有窗口处理。
      const COPYDATASTRUCT* c = reinterpret_cast<const COPYDATASTRUCT*>(lp);
      if (c && c->dwData == 1 && c->lpData && c->cbData > 0) {
        const std::wstring path(static_cast<const wchar_t*>(c->lpData),
                                c->cbData / sizeof(wchar_t));
        OpenPackFile(path);
      }
      ShowWindow(hwnd, SW_RESTORE);
      SetForegroundWindow(hwnd);
      return TRUE;
    }
    case WM_ACTIVATE:
      // 页面不可见时暂停媒体、动画、定时器与游戏帧循环。
      g_app.paused = LOWORD(wp) == WA_INACTIVE;
      if (g_app.content) {
        SendMessageW(g_app.content, WM_APP + 10, g_app.paused ? 1 : 0, 0);
      }
      return 0;
    case WM_CLOSE:
      // 关闭按钮只关闭窗口；「退出」由托盘菜单明确触发。
      if (g_app.tray_enabled) {
        ShowWindow(hwnd, SW_HIDE);
        return 0;
      }
      DestroyWindow(hwnd);
      return 0;
    case WM_DESTROY:
      SaveWindowState();
      PostQuitMessage(0);
      return 0;
  }
  return DefWindowProcW(hwnd, msg, wp, lp);
}

}  // namespace xqly
