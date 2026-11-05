# 平台无关核心：只用标准 C++17，Linux / Windows 均可编译并运行单元测试。
# 既可作为 windows/CMakeLists.txt 的子目录，也可独立配置：
#   cmake -S windows/runner/core -B build-wincore
#   cmake --build build-wincore
#   ./build-wincore/xqly_core_tests ../resources/design-tokens.json
if(CMAKE_SOURCE_DIR STREQUAL CMAKE_CURRENT_SOURCE_DIR)
  cmake_minimum_required(VERSION 3.15)
  project(xqly_platform_core LANGUAGES CXX)
endif()

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

add_library(xqly_platform_core STATIC
  platform_core.cpp
  security_core.cpp
  design_tokens.cpp
)
target_include_directories(xqly_platform_core PUBLIC ${CMAKE_CURRENT_SOURCE_DIR})

add_executable(xqly_core_tests tests/core_tests.cpp)
target_link_libraries(xqly_core_tests PRIVATE xqly_platform_core)

enable_testing()
add_test(NAME xqly_core_tests
  COMMAND xqly_core_tests
          "${CMAKE_CURRENT_SOURCE_DIR}/../resources/design-tokens.json")
