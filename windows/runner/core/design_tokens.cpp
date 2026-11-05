#include "platform_core.h"

namespace xqly {

/// 从 design-tokens.json 的 themes 数组里取出匹配 (theme, mode) 的一套映射。
/// 该文件由 shared/tool/gen_tokens.dart 生成，Android WebView 与 Windows 外壳
/// 消费同一份字节，保证两平台颜色/间距/动效令牌一致。
Tokens LoadTokens(const std::string& text, const std::string& theme,
                  const std::string& mode) {
  Tokens t;
  t.theme = theme;
  t.mode = mode;

  const size_t tp = text.find("\"themes\"");
  if (tp == std::string::npos) return t;
  const size_t arr = text.find('[', tp);
  if (arr == std::string::npos) return t;

  size_t i = arr + 1;
  while (i < text.size()) {
    while (i < text.size() && text[i] != '{' && text[i] != ']') ++i;
    if (i >= text.size() || text[i] == ']') break;

    // 取一个完整的一级对象（按大括号深度，跳过字符串内的大括号）。
    const size_t start = i;
    int depth = 0;
    bool in_str = false;
    size_t end = start;
    for (; i < text.size(); ++i) {
      const char c = text[i];
      if (c == '"' && (i == 0 || text[i - 1] != '\\')) in_str = !in_str;
      if (in_str) continue;
      if (c == '{') {
        ++depth;
      } else if (c == '}') {
        --depth;
        if (depth == 0) {
          end = i + 1;
          ++i;
          break;
        }
      }
    }
    if (end <= start) break;
    const std::string obj = text.substr(start, end - start);

    if (json::Str(obj, "theme", "") != theme ||
        json::Str(obj, "mode", "") != mode) {
      continue;
    }

    t.brand = json::Str(obj, "brand", t.brand);
    // 提取 colors 对象里的 "key":"#rrggbb" 对。
    const size_t cp = obj.find("\"colors\"");
    if (cp != std::string::npos) {
      const size_t cb = obj.find('{', cp);
      const size_t ce = obj.find('}', cb == std::string::npos ? cp : cb);
      if (cb != std::string::npos && ce != std::string::npos && ce > cb) {
        const std::string cobj = obj.substr(cb, ce - cb + 1);
        for (size_t k = 0; k < cobj.size(); ++k) {
          if (cobj[k] != '"') continue;
          const size_t e = cobj.find('"', k + 1);
          if (e == std::string::npos) break;
          const std::string key = cobj.substr(k + 1, e - k - 1);
          const size_t colon = cobj.find(':', e + 1);
          if (colon == std::string::npos) break;
          const size_t v1 = cobj.find('"', colon + 1);
          if (v1 == std::string::npos || v1 > ce - cb) break;
          const size_t v2 = cobj.find('"', v1 + 1);
          if (v2 == std::string::npos) break;
          const std::string val = cobj.substr(v1 + 1, v2 - v1 - 1);
          if (!val.empty() && val[0] == '#') t.colors[key] = val;
          k = v2;
        }
      }
    }
    t.motion_medium_ms = json::Int(obj, "medium", t.motion_medium_ms);
    t.max_reading_width = 760.0;  // 与 DesignTokens.maxReadingWidth 一致
    t.loaded = true;
    break;
  }
  return t;
}

}  // namespace xqly
