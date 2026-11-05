# 星穹旅驿 · 原生 Windows 外壳
#
# 构建（必须在 Windows + Visual Studio 2022「使用 C++ 的桌面开发」）：
#   cmake -S windows -B windows/build -A x64
#   cmake --build windows/build --config Release
#
# 本机为 Linux，没有 MSVC / Windows SDK，因此这里只在 [runner/core] 上做
# 平台无关核心的真实编译与测试；Win32 目标仅在 WIN32 为真时参与构建，
# 由 .github/workflows 的 windows-latest runner 生成真正的 EXE。
cmake_minimum_required(VERSION 3.15)
project(xqly_windows LANGUAGES CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# 必须在顶层调用，子目录里的 enable_testing() 不会在顶层构建树生成
# CTestTestfile.cmake；缺了这一行时 `cmake -S windows && ctest` 会报
# "No tests were found"（本机验证时真实复现过）。
enable_testing()

# 平台无关核心 + 单元测试（Linux / Windows 均可编译运行）
add_subdirectory(runner/core)

if(WIN32)
  add_executable(xqly WIN32
    runner/win32/main.cpp
    runner/win32/window.cpp
    runner/win32/system_integration.cpp
    runner/win32/input.cpp
    runner/resources/version.rc
  )
  target_include_directories(xqly PRIVATE runner/core runner/win32)
  target_link_libraries(xqly PRIVATE
    xqly_platform_core
    comctl32 dwmapi shell32 shcore user32 gdi32 ole32
  )
  target_compile_definitions(xqly PRIVATE
    UNICODE _UNICODE NOMINMAX WIN32_LEAN_AND_MEAN
  )
  # 源码中的中文文案按 UTF-8 编译，避免 MSVC 默认代码页导致乱码。
  target_compile_options(xqly PRIVATE /utf-8 /W4 /EHsc)

  # 随包设计令牌与共享数据放到 EXE 旁的 data/ 目录
  add_custom_command(TARGET xqly POST_BUILD
    COMMAND ${CMAKE_COMMAND} -E make_directory "$<TARGET_FILE_DIR:xqly>/data"
    COMMAND ${CMAKE_COMMAND} -E copy_if_different
      "${CMAKE_CURRENT_SOURCE_DIR}/runner/resources/design-tokens.json"
      "$<TARGET_FILE_DIR:xqly>/data/design-tokens.json"
  )
endif()
