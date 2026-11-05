#include "win32_app.h"

#include <commctrl.h>
#include <objbase.h>
#include <shellapi.h>
#include <shellscalingapi.h>
#include <shlobj.h>

#include <fstream>
#include <sstream>

namespace xqly {
App g_app;

std::wstring ExeDirectory() {
  wchar_t buf[MAX_PATH] = {0};
  DWORD n = GetModuleFileNameW(nullptr, buf, MAX_PATH);
  if (n == 0) return L".";
  std::wstring p(buf, n);
  const size_t slash = p.find_last_of(L"\\/");
  return slash == std::wstring::npos ? std::wstring(L".") : p.substr(0, slash);
}

std::wstring ResolveDataDir(bool portable) {
  // 便携版：数据放 EXE 旁的 Data/，不写安装目录之外的全局位置。
  if (portable) return g_app.exe_dir + L"\\Data";
  // 安装版：用户应用数据目录，不需要管理员权限。
  wchar_t* path = nullptr;
  if (SUCCEEDED(SHGetKnownFolderPath(FOLDERID_LocalAppData, 0, nullptr,
                                     &path)) &&
      path != nullptr) {
    const std::wstring d = std::wstring(path) + L"\\XQLY";
    CoTaskMemFree(path);
    return d;
  }
  return g_app.exe_dir + L"\\Data";
}

std::string ReadBundledText(const std::wstring& rel) {
  std::ifstream f(g_app.exe_dir + L"\\data\\" + rel);
  if (!f) {
    std::ifstream f2(g_app.data_dir + L"\\" + rel);
    if (!f2) return std::string();
    std::stringstream ss2;
    ss2 << f2.rdbuf();
    return ss2.str();
  }
  std::stringstream ss;
  ss << f.rdbuf();
  return ss.str();
}

static bool IsPortableLayout() {
  // 便携版判定：EXE 旁存在 portable.flag 或 Data 目录。
  const std::wstring flag = g_app.exe_dir + L"\\portable.flag";
  if (GetFileAttributesW(flag.c_str()) != INVALID_FILE_ATTRIBUTES) return true;
  const DWORD a = GetFileAttributesW((g_app.exe_dir + L"\\Data").c_str());
  return a != INVALID_FILE_ATTRIBUTES && (a & FILE_ATTRIBUTE_DIRECTORY);
}

static bool EnsureWritable(const std::wstring& dir) {
  CreateDirectoryW(dir.c_str(), nullptr);
  const std::wstring probe = dir + L"\\.write-test";
  const HANDLE h = CreateFileW(probe.c_str(), GENERIC_WRITE, 0, nullptr,
                               CREATE_ALWAYS, FILE_ATTRIBUTE_TEMPORARY,
                               nullptr);
  if (h == INVALID_HANDLE_VALUE) return false;
  CloseHandle(h);
  DeleteFileW(probe.c_str());
  return true;
}

}  // namespace xqly

int WINAPI wWinMain(HINSTANCE hInst, HINSTANCE, PWSTR, int nCmdShow) {
  using namespace xqly;

  // 每个显示器 DPI 感知（100%–300%）：按 DPI 自绘，不让系统位图拉伸。
  SetProcessDpiAwarenessContext(DPI_AWARENESS_CONTEXT_PER_MONITOR_AWARE_V2);
  InitCommonControls();

  // 便携版与安装版使用不同的应用标识（窗口类名 / 单实例互斥体 / 数据目录），
  // 避免共享数据造成冲突；因此形态判定必须在单实例检查之前完成。
  g_app.instance = hInst;
  g_app.exe_dir = ExeDirectory();
  g_app.portable = IsPortableLayout();
  g_app.data_dir = ResolveDataDir(g_app.portable);

  // 单实例：再次启动激活已有窗口，而不是开重复进程。
  HWND existing = nullptr;
  if (!EnsureSingleInstance(&existing)) {
    ActivateExisting(existing);
    return 0;
  }

  if (!EnsureWritable(g_app.data_dir)) {
    // 目录只读时明确提示，并允许切换到用户数据目录（此处降级到 LocalAppData）。
    const std::wstring fallback = ResolveDataDir(false);
    if (!EnsureWritable(fallback)) {
      MessageBoxW(nullptr, L"数据目录不可写，且无法切换到用户数据目录。",
                  L"星穹旅驿", MB_ICONERROR);
      return 1;
    }
    g_app.data_dir = fallback;
    g_app.portable = false;
  }

  // 双平台共享设计令牌（由 shared/tool/gen_tokens.dart 生成，与 Android 同一份字节）。
  g_app.tokens = LoadTokens(ReadBundledText(L"design-tokens.json"), "dark", "glass");

  LoadWindowState();
  // 显示器拔除 / 分辨率变化后把窗口移回可见区。
  g_app.state = g_app.state.ClampToDisplay(
      GetSystemMetrics(SM_XVIRTUALSCREEN), GetSystemMetrics(SM_YVIRTUALSCREEN),
      GetSystemMetrics(SM_CXVIRTUALSCREEN), GetSystemMetrics(SM_CYVIRTUALSCREEN));

  if (!RegisterWindowClass(hInst)) return 1;
  if (!CreateMainWindow(hInst, nCmdShow)) return 1;

  const HACCEL accel = BuildAccelerators();
  EnableTouch(g_app.hwnd);

  // 文件关联 / 命令行打开：先验扩展名与格式，再交给共享核心校验清单。
  int argc = 0;
  LPWSTR* argv = CommandLineToArgvW(GetCommandLineW(), &argc);
  if (argv) {
    for (int i = 1; i < argc; ++i) {
      const std::wstring a = argv[i];
      if (a.size() > 9 &&
          a.substr(a.size() - 9) == L".xqlypack") {
        OpenPackFile(a);
      }
    }
    LocalFree(argv);
  }

  // WebView2 为可选内容承载：检测系统运行时，缺失时给出明确提示而非静默下载。
  if (!WebView2RuntimeAvailable()) {
    // 设置页提供引导安装策略；此处不打断首次启动。
    OutputDebugStringW(L"[XQLY] WebView2 Runtime 未检测到，在线网页功能将在设置中提示安装。\n");
  }

  MSG msg = {};
  while (GetMessageW(&msg, nullptr, 0, 0) > 0) {
    if (TranslateAcceleratorW(g_app.hwnd, accel, &msg)) continue;
    TranslateMessage(&msg);
    DispatchMessageW(&msg);
  }

  SaveWindowState();
  RemoveTrayIcon(g_app.hwnd);
  DestroyAcceleratorTable(accel);
  return static_cast<int>(msg.wParam);
}
