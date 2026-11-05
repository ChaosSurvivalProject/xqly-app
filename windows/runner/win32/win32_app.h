#pragma once
// 星穹旅驿 · 原生 Win32 外壳
//
// 这是真正的 Win32 窗口程序（wWinMain + HWND + 消息循环），不是浏览器窗口壳。
// 网页内容（如启用）由 WebView2 承载在原生窗口的 content 区域，导航、工具栏、
// 多标签、托盘与窗口管理全部由本外壳实现。
//
// 平台无关逻辑（窗口状态、标签、快捷键、协议、桥、令牌）在 ../core/ 中，
// 已在 Linux 上用 CMake 真实编译并跑过单元测试。
#ifndef UNICODE
#define UNICODE
#endif
#ifndef _UNICODE
#define _UNICODE
#endif
#ifndef NOMINMAX
#define NOMINMAX
#endif
#ifndef WIN32_LEAN_AND_MEAN
#define WIN32_LEAN_AND_MEAN
#endif
#include <windows.h>

#include <string>

#include "../core/platform_core.h"

namespace xqly {

// 菜单 / 加速键命令 ID
enum : WORD {
  IDM_NEWTAB = 40001,
  IDM_CLOSETAB,
  IDM_NEXTTAB,
  IDM_PREVTAB,
  IDM_RESTORETAB,
  IDM_FIND,
  IDM_QUICKOPEN,
  IDM_SETTINGS,
  IDM_REFRESH,
  IDM_ESCAPE,
  IDM_PAUSEGAME,
  IDM_TRAY_OPEN,
  IDM_TRAY_UPDATE,
  IDM_TRAY_SYNC,
  IDM_TRAY_EXIT,
  IDM_TOGGLE_NOTIFY,
  IDM_TOGGLE_AUTOSTART,
  IDM_TOGGLE_TRAY,
  IDM_EXPORT_SYNC,
  IDM_IMPORT_SYNC,
  IDM_CLEAR_CACHE,
};

constexpr UINT WM_XQLY_TRAY = WM_APP + 1;
constexpr UINT WM_XQLY_ACTIVATE = WM_APP + 2;
constexpr const wchar_t* kWindowClassInstalled = L"XQLY_Win32_Window";
constexpr const wchar_t* kWindowClassPortable = L"XQLY_Win32_Window_Portable";
constexpr const wchar_t* kWindowTitle = L"星穹旅驿";

/// 按当前分发形态返回窗口类名：安装版与便携版互不干扰，可同时运行。
const wchar_t* ActiveWindowClass();

/// 单进程应用状态。多窗口时各自持有外壳状态，共享数据通过单进程仓库协调
/// （本工程的数据层由 shared/ 的 Dart 核心提供，不在此复制一份业务状态）。
struct App {
  HINSTANCE instance = nullptr;
  HWND hwnd = nullptr;
  HWND content = nullptr;
  WindowState state;
  TabManager tabs;
  ShortcutRegistry shortcuts;
  Tokens tokens;
  bool tray_enabled = false;   // 最小化到托盘：默认关闭
  bool autostart = false;      // 开机启动：默认关闭，需用户明确同意
  bool notifications = true;   // 通知集中开关
  bool portable = false;
  bool paused = false;         // 窗口不可见时暂停媒体/动画/游戏帧循环
  UINT dpi = 96;
  std::wstring exe_dir;
  std::wstring data_dir;
};

extern App g_app;

std::wstring ExeDirectory();
std::wstring ResolveDataDir(bool portable);
std::string ReadBundledText(const std::wstring& rel);

// ---- window.cpp ----
bool RegisterWindowClass(HINSTANCE h);
bool CreateMainWindow(HINSTANCE h, int nCmdShow);
void LoadWindowState();
void SaveWindowState();
void RelayoutContent(HWND hwnd);
LRESULT CALLBACK WndProc(HWND, UINT, WPARAM, LPARAM);

// ---- input.cpp ----
HACCEL BuildAccelerators();
bool HandleCommand(WORD id);
void HandleGameKey(WPARAM vk, bool down);
void EnableTouch(HWND hwnd);
bool OnTouch(WPARAM wp, LPARAM lp);
bool OnPointer(UINT msg, WPARAM wp, LPARAM lp);

// ---- system_integration.cpp ----
bool EnsureSingleInstance(HWND* existing);
void ActivateExisting(HWND existing);
bool AddTrayIcon(HWND hwnd);
void RemoveTrayIcon(HWND hwnd);
void OnTrayMessage(HWND hwnd, WPARAM wp, LPARAM lp);
void ShowNotification(const std::wstring& title, const std::wstring& text);
bool SetAutoStart(bool enable);
bool RegisterProtocolHandler();
bool RegisterFileAssociation();
bool OpenPackFile(const std::wstring& path);
bool WebView2RuntimeAvailable();

}  // namespace xqly
