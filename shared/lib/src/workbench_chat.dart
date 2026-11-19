import 'workbench_log.dart';

/// 消息角色。仅本地记录，不代表真实 AI 响应。
enum ChatRole { user, draft, note }

/// 一条本地会话消息：用户草稿、占位说明、离线笔记。
/// 明确不标记为 assistant，避免伪装成真实模型输出。
class ChatMessage {
  final ChatRole role;
  final String text;
  final int atMs;

  const ChatMessage(this.role, this.text, this.atMs);

  Map<String, Object?> toJson() => <String, Object?>{
        'role': role.name,
        'text': workbenchRedact(text),
        'atMs': atMs,
      };
}

/// 本地会话：对标 harness 的「会话管理」交互，但只存本地草稿与笔记。
class ChatSession {
  static const int maxSessions = 32;
  static const int maxMessages = 200;
  static const int maxText = 2048;

  final String id;
  String title;
  final List<ChatMessage> _messages = <ChatMessage>[];
  int updatedAtMs;

  ChatSession(this.id, this.title, this.updatedAtMs);

  List<ChatMessage> get messages => List<ChatMessage>.unmodifiable(_messages);
  int get messageCount => _messages.length;

  bool append(ChatRole role, String text, int atMs) {
    if (text.isEmpty || text.length > maxText) return false;
    if (_messages.length >= maxMessages) return false;
    _messages.add(ChatMessage(role, text, atMs));
    updatedAtMs = atMs;
    return true;
  }

  Map<String, Object?> toJson() => <String, Object?>{
        'id': id,
        'title': title,
        'messageCount': _messages.length,
        'updatedAtMs': updatedAtMs,
      };
}

/// 会话仓库（纯内存，零落盘）：上限保护 + 最近更新排序。
class ChatStore {
  final Map<String, ChatSession> _sessions = <String, ChatSession>{};
  static final RegExp _idPattern = RegExp(r'^[A-Za-z0-9_-]{1,48}$');

  int get count => _sessions.length;

  bool create(String id, String title, int atMs) {
    if (!_idPattern.hasMatch(id)) return false;
    if (title.isEmpty || title.length > 80) return false;
    if (_sessions.containsKey(id)) return false;
    if (_sessions.length >= ChatSession.maxSessions) return false;
    _sessions[id] = ChatSession(id, title, atMs);
    return true;
  }

  bool append(String id, ChatRole role, String text, int atMs) {
    final ChatSession? s = _sessions[id];
    return s != null && s.append(role, text, atMs);
  }

  bool remove(String id) => _sessions.remove(id) != null;

  ChatSession? get(String id) => _sessions[id];

  /// 最近更新优先；返回摘要而非正文，避免把内容带进列表桥响应。
  List<Map<String, Object?>> list() {
    final List<ChatSession> all = _sessions.values.toList()
      ..sort((ChatSession a, ChatSession b) => b.updatedAtMs.compareTo(a.updatedAtMs));
    return List<Map<String, Object?>>.unmodifiable(
        all.map((ChatSession s) => s.toJson()).toList());
  }

  void clear() => _sessions.clear();
}
