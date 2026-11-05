#include "win32_app.h"

#include <shellapi.h>

namespace xqly {

namespace {

// 单实例互斥体必须保持句柄存活，否则进程退出前会被系统回收。
HANDLE g_instance_mutex = nullptr;

const wchar_t* kRunKey =
    L"Software\\Microsoft\\Windows\\CurrentVersion\\Run";
const wchar_t* kWebview2Client =
    L"SOFTWARE\\WOW6432Node\\Microsoft\\EdgeUpdate\\Clients\\"
    L"{F3017226-FE2A-4295-8BDF-00C3A9A7E4C5}";
const wchar_t* kWebview2Client64 =
    L"SOFTWARE\\Microsoft\\EdgeUpdate\\Clients\\"
    L"{F3017226-FE2A-4295-8BDF-00C3A9A7E4C5}";

std::wstring ExePathQuoted() {
  wchar_t exe[MAX_PATH] = {0};
  GetModuleFileNameW(nullptr, exe, MAX_PATH);
  return L"\"" + std::wstring(exe) + L"\"";
}

bool SetRegString(HKEY key, const wchar_t* name, const std::wstring& value) {
  return RegSetValueExW(key, name, 0, REG_SZ,
                        reinterpret_cast<const BYTE*>(value.c_str()),
                        static_cast<DWORD>((value.size() + 1) *
                                           sizeof(wchar_t))) == ERROR_SUCCESS;
}

}  // namespace

const wchar_t* ActiveWindowClass() {
  return g_app.portable ? kWindowClassPortable : kWindowClassInstalled;
}

bool EnsureSingleInstance(HWND* existing) {
  const wchar_t* mutex_name = g_app.portable
      ? L"Local\\XQLY_SingleInstance_Portable_2_0_1"
      : L"Local\\XQLY_SingleInstance_Installed_2_0_1";
  g_instance_mutex = CreateMutexW(nullptr, TRUE, mutex_name);
  if (g_instance_mutex && GetLastError() == ERROR_ALREADY_EXISTS) {
    *existing = FindWindowW(ActiveWindowClass(), nullptr);
    return false;
  }
  return g_instance_mutex != nullptr;
}

void ActivateExisting(HWND existing) {
  if (!existing) return;
  // 通过自定义消息激活已有窗口；不重复创建进程。
  SendMessageTimeoutW(existing, WM_XQLY_ACTIVATE, 0, 0, SMTO_ABORTIFHUNG,
                      2000, nullptr);
  ShowWindow(existing, SW_RESTORE);
  SetForegroundWindow(existing);
}

bool AddTrayIcon(HWND hwnd) {
  NOTIFYICONDATAW n = {};
  n.cbSize = sizeof(n);
  n.hWnd = hwnd;
  n.uID = 1;
  n.uFlags = NIF_ICON | NIF_MESSAGE | NIF_TIP;
  n.uCallbackMessage = WM_XQLY_TRAY;
  n.hIcon = LoadIconW(g_app.instance, MAKEINTRESOURCEW(101));
  wcscpy_s(n.szTip, L"星穹旅驿");
  return Shell_NotifyIconW(NIM_ADD, &n) == TRUE;
}

void RemoveTrayIcon(HWND hwnd) {
  NOTIFYICONDATAW n = {};
  n.cbSize = sizeof(n);
  n.hWnd = hwnd;
  n.uID = 1;
  Shell_NotifyIconW(NIM_DELETE, &n);
}

void OnTrayMessage(HWND hwnd, WPARAM, LPARAM lp) {
  switch (LOWORD(lp)) {
    case WM_RBUTTONUP:
    case WM_CONTEXTMENU: {
      HMENU m = CreatePopupMenu();
      if (!m) return;
      AppendMenuW(m, MF_STRING, IDM_TRAY_OPEN, L"打开");
      AppendMenuW(m, MF_STRING, IDM_TRAY_UPDATE, L"检查更新");
      AppendMenuW(m, MF_STRING, IDM_TRAY_SYNC, L"暂停同步");
      AppendMenuW(m, MF_SEPARATOR, 0, nullptr);
      AppendMenuW(m, MF_STRING, IDM_TRAY_EXIT, L"退出");
      POINT p = {};
      GetCursorPos(&p);
      SetForegroundWindow(hwnd);
      TrackPopupMenu(m, TPM_RIGHTALIGN | TPM_BOTTOMALIGN, p.x, p.y, 0, hwnd,
                     nullptr);
      DestroyMenu(m);
      break;
    }
    case WM_LBUTTONDBLCLK:
      ShowWindow(hwnd, SW_RESTORE);
      SetForegroundWindow(hwnd);
      break;
  }
}

/// Windows 通知：集中开关 + 按类型细分；未授权或关闭时静默跳过。
/// 使用气球提示（兼容 Windows 10 1809），下载完成后可通知，不自动打断用户。
void ShowNotification(const std::wstring& title, const std::wstring& text) {
  if (!g_app.notifications || !g_app.tray_enabled || !g_app.hwnd) return;
  NOTIFYICONDATAW n = {};
  n.cbSize = sizeof(n);
  n.hWnd = g_app.hwnd;
  n.uID = 1;
  n.uFlags = NIF_INFO;
  n.dwInfoFlags = NIIF_INFO | NIIF_NOSOUND;
  wcscpy_s(n.szInfoTitle, title.c_str());
  wcscpy_s(n.szInfo, text.c_str());
  Shell_NotifyIconW(NIM_MODIFY, &n);
}

/// 开机启动：写 HKCU（不写系统受保护目录），默认关闭，仅由用户明确同意后调用。
bool SetAutoStart(bool enable) {
  HKEY k = nullptr;
  if (RegOpenKeyExW(HKEY_CURRENT_USER, kRunKey, 0, KEY_SET_VALUE, &k) !=
      ERROR_SUCCESS) {
    return false;
  }
  LONG r;
  if (enable) {
    r = SetRegString(k, L"XQLY", ExePathQuoted()) ? ERROR_SUCCESS
                                                 : ERROR_GEN_FAILURE;
  } else {
    r = RegDeleteValueW(k, L"XQLY");
    if (r == ERROR_FILE_NOT_FOUND) r = ERROR_SUCCESS;  // 本就未启用
  }
  RegCloseKey(k);
  return r == ERROR_SUCCESS;
}

/// 自定义协议：只注册，命令解析由 ParseProtocolUri 的白名单完成，
/// 不执行下载包中的任意脚本或命令行参数。
bool RegisterProtocolHandler() {
  HKEY root = nullptr;
  if (RegCreateKeyExW(HKEY_CURRENT_USER, L"Software\\Classes\\xqly", 0,
                      nullptr, 0, KEY_SET_VALUE, nullptr, &root, nullptr) !=
      ERROR_SUCCESS) {
    return false;
  }
  SetRegString(root, nullptr, L"URL:星穹旅驿 协议");
  SetRegString(root, L"URL Protocol", L"");
  HKEY cmd = nullptr;
  if (RegCreateKeyExW(root, L"shell\\open\\command", 0, nullptr, 0,
                      KEY_SET_VALUE, nullptr, &cmd, nullptr) == ERROR_SUCCESS) {
    SetRegString(cmd, nullptr, ExePathQuoted() + L" \"%1\"");
    RegCloseKey(cmd);
  }
  RegCloseKey(root);
  return true;
}

/// 自有同步包文件关联：HKCU，不写系统受保护目录；打开前验证来源与格式。
bool RegisterFileAssociation() {
  HKEY ext = nullptr;
  if (RegCreateKeyExW(HKEY_CURRENT_USER, L"Software\\Classes\\.xqlypack", 0,
                      nullptr, 0, KEY_SET_VALUE, nullptr, &ext, nullptr) !=
      ERROR_SUCCESS) {
    return false;
  }
  SetRegString(ext, nullptr, L"XQLY.SyncPack");
  RegCloseKey(ext);

  HKEY cmd = nullptr;
  if (RegCreateKeyExW(HKEY_CURRENT_USER,
                      L"Software\\Classes\\XQLY.SyncPack\\shell\\open\\command",
                      0, nullptr, 0, KEY_SET_VALUE, nullptr, &cmd,
                      nullptr) != ERROR_SUCCESS) {
    return false;
  }
  SetRegString(cmd, nullptr, ExePathQuoted() + L" \"%1\"");
  RegCloseKey(cmd);
  return true;
}

bool OpenPackFile(const std::wstring& path) {
  // 最小前置校验：扩展名、可读性、体积上限（与 SyncPackage.maxChars 一致）。
  // 清单与同步包的严格解析（校验和、schema、墓碑、危险键）由 shared/ 的
  // Dart 核心 SyncPackage.decode / ContentManifest.verify 通过版本化通道完成。
  if (path.size() < 10 ||
      path.substr(path.size() - 9) != L".xqlypack") {
    return false;
  }
  const HANDLE h = CreateFileW(path.c_str(), GENERIC_READ, FILE_SHARE_READ,
                               nullptr, OPEN_EXISTING, FILE_ATTRIBUTE_NORMAL,
                               nullptr);
  if (h == INVALID_HANDLE_VALUE) return false;
  LARGE_INTEGER sz = {};
  const bool sized = GetFileSizeEx(h, &sz) == TRUE;
  CloseHandle(h);
  if (!sized || sz.QuadPart <= 0 || sz.QuadPart > 8LL * 1024 * 1024) {
    return false;
  }
  if (g_app.content) {
    SendMessageW(g_app.content, WM_APP + 23, 0,
                 reinterpret_cast<LPARAM>(path.c_str()));
  }
  return true;
}

bool WebView2RuntimeAvailable() {
  const wchar_t* keys[] = {kWebview2Client, kWebview2Client64};
  for (const wchar_t* key : keys) {
    HKEY k = nullptr;
    if (RegOpenKeyExW(HKEY_LOCAL_MACHINE, key, 0, KEY_READ, &k) !=
        ERROR_SUCCESS) {
      continue;
    }
    wchar_t ver[64] = {0};
    DWORD size = sizeof(ver);
    DWORD type = 0;
    const bool ok =
        RegQueryValueExW(k, L"pv", nullptr, &type,
                         reinterpret_cast<LPBYTE>(ver), &size) ==
            ERROR_SUCCESS &&
        ver[0] != L'\0';
    RegCloseKey(k);
    if (ok) return true;
  }
  return false;
}

}  // namespace xqly
