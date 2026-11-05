#include "platform_core.h"

#include <algorithm>
#include <cctype>
#include <cstdio>
#include <sstream>

namespace xqly {
namespace json {

static void SkipWs(const std::string& s, size_t& p) {
  while (p < s.size() &&
         (s[p] == ' ' || s[p] == '\t' || s[p] == '\n' || s[p] == '\r')) {
    ++p;
  }
}

int Int(const std::string& s, const std::string& key, int def) {
  const std::string pat = "\"" + key + "\"";
  size_t p = s.find(pat);
  if (p == std::string::npos) return def;
  p = s.find(':', p + pat.size());
  if (p == std::string::npos) return def;
  ++p;
  SkipWs(s, p);
  bool neg = false;
  if (p < s.size() && (s[p] == '-' || s[p] == '+')) {
    neg = s[p] == '-';
    ++p;
  }
  if (p >= s.size() || !std::isdigit(static_cast<unsigned char>(s[p]))) {
    return def;
  }
  long long v = 0;
  while (p < s.size() && std::isdigit(static_cast<unsigned char>(s[p]))) {
    v = v * 10 + (s[p] - '0');
    ++p;
    if (v > 1000000000LL) return def;
  }
  return static_cast<int>(neg ? -v : v);
}

bool Bool(const std::string& s, const std::string& key, bool def) {
  const std::string pat = "\"" + key + "\"";
  size_t p = s.find(pat);
  if (p == std::string::npos) return def;
  p = s.find(':', p + pat.size());
  if (p == std::string::npos) return def;
  ++p;
  SkipWs(s, p);
  if (s.compare(p, 4, "true") == 0) return true;
  if (s.compare(p, 5, "false") == 0) return false;
  return def;
}

std::string Str(const std::string& s, const std::string& key,
                const std::string& def) {
  const std::string pat = "\"" + key + "\"";
  size_t p = s.find(pat);
  if (p == std::string::npos) return def;
  p = s.find(':', p + pat.size());
  if (p == std::string::npos) return def;
  ++p;
  SkipWs(s, p);
  if (p >= s.size() || s[p] != '"') return def;
  ++p;
  std::string out;
  while (p < s.size() && s[p] != '"') {
    if (s[p] == '\\' && p + 1 < s.size()) {
      ++p;
      switch (s[p]) {
        case 'n': out += '\n'; break;
        case 'r': out += '\r'; break;
        case 't': out += '\t'; break;
        case '"': out += '"'; break;
        case '\\': out += '\\'; break;
        default: out += s[p]; break;
      }
      ++p;
    } else {
      out += s[p++];
    }
  }
  return out;
}

std::string Escape(const std::string& s) {
  std::string o;
  char buf[8];
  for (char c : s) {
    switch (c) {
      case '"': o += "\\\""; break;
      case '\\': o += "\\\\"; break;
      case '\n': o += "\\n"; break;
      case '\r': o += "\\r"; break;
      case '\t': o += "\\t"; break;
      default:
        if (static_cast<unsigned char>(c) < 0x20) {
          std::snprintf(buf, sizeof(buf), "\\u%04x",
                        static_cast<unsigned char>(c));
          o += buf;
        } else {
          o += c;
        }
    }
  }
  return o;
}

std::vector<std::string> ObjectKeys(const std::string& s,
                                    const std::string& key) {
  std::vector<std::string> out;
  const std::string pat = "\"" + key + "\"";
  size_t kp = s.find(pat);
  if (kp == std::string::npos) return out;
  size_t brace = s.find('{', kp + pat.size());
  if (brace == std::string::npos) return out;

  int depth = 0;
  size_t i = brace;
  while (i < s.size()) {
    const char c = s[i];
    if (c == '{') {
      ++depth;
      ++i;
      continue;
    }
    if (c == '}') {
      --depth;
      ++i;
      if (depth == 0) break;
      continue;
    }
    if (c == '"') {
      // 完整读完一个字符串（键或值），跳过转义引号。
      const size_t start = i + 1;
      size_t e = start;
      while (e < s.size() && s[e] != '"') {
        if (s[e] == '\\') ++e;
        ++e;
      }
      if (e >= s.size()) break;
      size_t q = e + 1;
      SkipWs(s, q);
      if (depth == 1 && q < s.size() && s[q] == ':') {
        out.push_back(s.substr(start, e - start));
      }
      i = e + 1;
      continue;
    }
    ++i;
  }
  return out;
}

}  // namespace json

// ---------------------------------------------------------------- 窗口状态

std::string WindowState::Serialize() const {
  std::ostringstream os;
  os << "{\"x\":" << x << ",\"y\":" << y
     << ",\"width\":" << width << ",\"height\":" << height
     << ",\"maximized\":" << (maximized ? "true" : "false")
     << ",\"snapped\":" << (snapped ? "true" : "false")
     << ",\"dpi\":" << dpi
     << ",\"display\":\"" << json::Escape(display) << "\"}";
  return os.str();
}

WindowState WindowState::Parse(const std::string& text) {
  WindowState s;
  s.x = json::Int(text, "x", s.x);
  s.y = json::Int(text, "y", s.y);
  s.width = json::Int(text, "width", s.width);
  s.height = json::Int(text, "height", s.height);
  s.maximized = json::Bool(text, "maximized", s.maximized);
  s.snapped = json::Bool(text, "snapped", s.snapped);
  s.dpi = json::Int(text, "dpi", s.dpi);
  s.display = json::Str(text, "display", "");
  return s;
}

bool WindowState::Valid() const {
  return width >= 320 && width <= 16384 && height >= 240 && height <= 16384 &&
         dpi >= 96 && dpi <= 288;  // 100%–300% 系统缩放
}

WindowState WindowState::ClampToDisplay(int dx, int dy, int dw, int dh) const {
  WindowState o = *this;
  if (dw <= 0 || dh <= 0) return o;
  o.width = std::min(std::max(o.width, 320), dw);
  o.height = std::min(std::max(o.height, 240), dh);
  const bool offscreen = o.x + o.width < dx || o.x > dx + dw ||
                         o.y + o.height < dy || o.y > dy + dh;
  if (offscreen) {
    // 显示器拔除 / DPI 剧变：窗口完全不可见时居中恢复到当前显示器。
    o.x = dx + (dw - o.width) / 2;
    o.y = dy + (dh - o.height) / 2;
    o.snapped = false;
  } else {
    o.x = std::max(dx, std::min(o.x, dx + dw - o.width));
    o.y = std::max(dy, std::min(o.y, dy + dh - o.height));
  }
  return o;
}

// ---------------------------------------------------------------- 多标签

std::string TabManager::NewTab(const std::string& path,
                               const std::string& title) {
  const std::string id = "tab-" + std::to_string(++seq_);
  tabs_.push_back(Tab{id, title, path, false});
  active_ = id;
  return id;
}

bool TabManager::Close(const std::string& id) {
  for (auto it = tabs_.begin(); it != tabs_.end(); ++it) {
    if (it->id != id) continue;
    if (it->pinned) return false;
    closed_.push_back(*it);
    if (closed_.size() > 20) closed_.erase(closed_.begin());
    tabs_.erase(it);
    if (active_ == id) active_ = tabs_.empty() ? "" : tabs_.back().id;
    return true;
  }
  return false;
}

bool TabManager::RestoreLastClosed() {
  if (closed_.empty()) return false;
  const Tab t = closed_.back();
  closed_.pop_back();
  tabs_.push_back(t);
  active_ = t.id;
  return true;
}

bool TabManager::Move(size_t from, size_t to) {
  if (from >= tabs_.size() || to >= tabs_.size() || from == to) return false;
  const Tab t = tabs_[from];
  tabs_.erase(tabs_.begin() + static_cast<long>(from));
  tabs_.insert(tabs_.begin() + static_cast<long>(to), t);
  return true;
}

std::string TabManager::Cycle(bool forward) {
  if (tabs_.empty()) return "";
  size_t i = 0;
  for (; i < tabs_.size(); ++i) {
    if (tabs_[i].id == active_) break;
  }
  if (i >= tabs_.size()) i = 0;
  i = (i + (forward ? 1 : tabs_.size() - 1)) % tabs_.size();
  active_ = tabs_[i].id;
  return active_;
}

void TabManager::Pin(const std::string& id, bool pinned) {
  for (auto& t : tabs_) {
    if (t.id == id) t.pinned = pinned;
  }
}

// ---------------------------------------------------------------- 快捷键

std::string Binding::Display() const {
  std::string s;
  if (mods & kCtrl) s += "Ctrl+";
  if (mods & kShift) s += "Shift+";
  if (mods & kAlt) s += "Alt+";
  if (key >= 'A' && key <= 'Z') {
    s += static_cast<char>(key);
  } else {
    switch (key) {
      case 9: s += "Tab"; break;
      case 27: s += "Esc"; break;
      case 32: s += "Space"; break;
      case 116: s += "F5"; break;
      case 188: s += ","; break;
      default: s += "VK" + std::to_string(key); break;
    }
  }
  return s;
}

ShortcutRegistry::ShortcutRegistry() {
  bindings_ = {
      {kCtrl, 'T', Action::kNewTab, "新标签"},
      {kCtrl, 'W', Action::kCloseTab, "关闭标签"},
      {kCtrl, 9, Action::kNextTab, "下一个标签"},
      {kCtrl | kShift, 9, Action::kPrevTab, "上一个标签"},
      {kCtrl | kShift, 'T', Action::kRestoreTab, "恢复最近关闭的标签"},
      {kCtrl, 'F', Action::kFind, "搜索"},
      {kCtrl, 'K', Action::kQuickOpen, "快速打开"},
      {kCtrl, 188, Action::kSettings, "设置"},
      {0, 116, Action::kRefresh, "刷新"},
      {kCtrl, 'R', Action::kRefresh, "刷新"},
      {0, 27, Action::kEscape, "关闭弹层 / 取消"},
      {0, 32, Action::kPauseGame, "暂停 / 继续游戏"},
  };
}

Action ShortcutRegistry::Resolve(uint32_t mods, uint32_t key) const {
  for (const auto& b : bindings_) {
    if (b.mods == mods && b.key == key) return b.action;
  }
  return Action::kNone;
}

bool ShortcutRegistry::HasConflict(uint32_t mods, uint32_t key,
                                   Action except) const {
  for (const auto& b : bindings_) {
    if (b.mods == mods && b.key == key && b.action != except) return true;
  }
  return false;
}

bool ShortcutRegistry::Rebind(Action action, uint32_t mods, uint32_t key) {
  // 冲突时明确失败，绝不静默覆盖已有绑定。
  if (HasConflict(mods, key, action)) return false;
  for (auto& b : bindings_) {
    if (b.action == action) {
      b.mods = mods;
      b.key = key;
      return true;
    }
  }
  return false;
}

}  // namespace xqly
