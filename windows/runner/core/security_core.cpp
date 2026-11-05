#include "platform_core.h"

#include <cctype>
#include <set>

namespace xqly {

// ---------------------------------------------------------------- 自定义协议

ProtoResult ParseProtocolUri(const std::string& uri) {
  ProtoResult r;
  if (uri.size() > 2048) {
    r.reject = ProtoReject::kTooLong;
    return r;
  }
  const std::string prefix = "xqly://";
  if (uri.compare(0, prefix.size(), prefix) != 0) {
    r.reject = ProtoReject::kBadScheme;  // 也拒绝 javascript: / file: / data:
    return r;
  }
  std::string rest = uri.substr(prefix.size());
  if (rest.compare(0, 4, "app/") != 0 && rest != "app") {
    r.reject = ProtoReject::kBadHost;
    return r;
  }
  rest = rest.size() >= 4 ? rest.substr(4) : std::string();

  std::vector<std::string> parts;
  std::string cur;
  for (char c : rest) {
    if (c == '/') {
      parts.push_back(cur);
      cur.clear();
    } else {
      cur += c;
    }
  }
  if (!cur.empty()) parts.push_back(cur);
  if (parts.empty()) {
    r.reject = ProtoReject::kUnknownCommand;
    return r;
  }

  // 只接受固定命令集，绝不拼接命令行或执行任意参数。
  static const std::set<std::string> kCommands = {
      "open", "search", "sync", "update", "settings"};
  if (kCommands.find(parts[0]) == kCommands.end()) {
    r.reject = ProtoReject::kUnknownCommand;
    return r;
  }
  if (parts.size() > 5) {
    r.reject = ProtoReject::kBadArgs;
    return r;
  }
  for (size_t i = 1; i < parts.size(); ++i) {
    const std::string& a = parts[i];
    if (a.empty() || a.size() > 256 || a.find("..") != std::string::npos) {
      r.reject = ProtoReject::kBadArgs;
      return r;
    }
    for (char c : a) {
      const bool ok = std::isalnum(static_cast<unsigned char>(c)) || c == '_' ||
                      c == '-' || c == '.';
      if (!ok) {
        r.reject = ProtoReject::kBadChars;
        return r;
      }
    }
  }
  r.ok = true;
  r.reject = ProtoReject::kOk;
  r.command = parts[0];
  r.args.assign(parts.begin() + 1, parts.end());
  return r;
}

// ---------------------------------------------------------------- 平台桥

BridgeResult ValidateBridgeMessage(const std::string& raw,
                                   const std::string& page_origin,
                                   bool trusted, bool allow_write) {
  BridgeResult r;
  if (raw.empty() || raw.size() > 8192) {
    r.reject = BridgeReject::kTooLarge;
    return r;
  }
  // 版本必须与 shared/ 的 AppIdentity.bridgeVersion 一致。
  if (json::Int(raw, "v", -1) != 2) {
    r.reject = BridgeReject::kBadVersion;
    return r;
  }
  if (!trusted) {
    r.reject = BridgeReject::kUntrusted;
    return r;
  }
  const std::string origin = json::Str(raw, "origin", "");
  // 页面自称的 origin 必须与宿主记录的真实页面 URL 一致。
  if (origin.empty() || origin.size() > 256 || origin != page_origin) {
    r.reject = BridgeReject::kBadOrigin;
    return r;
  }
  const std::string type = json::Str(raw, "type", "");
  static const std::set<std::string> kTypes = {
      "openPage", "closeOverlay", "scroll", "search", "favorite", "progress",
      "getState", "gameSave", "gameLoad", "syncPreview", "syncApply",
      "syncCancel", "windowCommand"};
  if (kTypes.find(type) == kTypes.end()) {
    r.reject = BridgeReject::kUnknownType;
    return r;
  }

  static const std::set<std::string> kForbidden = {
      "token", "password", "cookie", "file", "url", "cmd", "exec", "install",
      "script", "registry", "process", "keystore"};
  for (const auto& k : json::ObjectKeys(raw, "args")) {
    if (kForbidden.find(k) != kForbidden.end()) {
      r.reject = BridgeReject::kForbiddenKey;
      return r;
    }
  }

  static const std::set<std::string> kWriteTypes = {
      "favorite", "progress", "gameSave", "syncApply", "syncCancel",
      "windowCommand"};
  if (kWriteTypes.find(type) != kWriteTypes.end() && !allow_write) {
    r.reject = BridgeReject::kPermission;
    return r;
  }
  r.ok = true;
  r.reject = BridgeReject::kOk;
  r.type = type;
  return r;
}

}  // namespace xqly
