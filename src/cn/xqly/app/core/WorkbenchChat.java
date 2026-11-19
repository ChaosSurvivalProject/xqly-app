package cn.xqly.app.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/** 本地会话仓库（Java 侧镜像 shared/lib/src/workbench_chat.dart）。 */
public final class WorkbenchChat {
    public WorkbenchChat() {}

    public static final int MAX_SESSIONS = 32;
    public static final int MAX_MESSAGES = 200;
    public static final int MAX_TEXT = 2048;
    private static final Pattern ID = Pattern.compile("^[A-Za-z0-9_-]{1,48}$");

    public enum Role { USER, DRAFT, NOTE }

    public static final class Message {
        public final Role role;
        public final String text;
        public final long atMs;
        Message(Role role, String text, long atMs) {
            this.role = role; this.text = WorkbenchLog.redact(text); this.atMs = atMs;
        }
        public Map<String, Object> toJson() {
            Map<String, Object> m = new LinkedHashMap<String, Object>();
            m.put("role", role.name().toLowerCase(java.util.Locale.US));
            m.put("text", text); m.put("atMs", atMs);
            return m;
        }
    }

    public static final class Session {
        public final String id;
        public String title;
        public long updatedAtMs;
        private final List<Message> messages = new ArrayList<Message>();
        Session(String id, String title, long atMs) {
            this.id = id; this.title = title; this.updatedAtMs = atMs;
        }
        public List<Message> messages() { return Collections.unmodifiableList(messages); }
        public int messageCount() { return messages.size(); }

        boolean append(Role role, String text, long atMs) {
            if (text == null || text.isEmpty() || text.length() > MAX_TEXT) return false;
            if (messages.size() >= MAX_MESSAGES) return false;
            messages.add(new Message(role, text, atMs));
            updatedAtMs = atMs;
            return true;
        }
        public Map<String, Object> toJson() {
            Map<String, Object> m = new LinkedHashMap<String, Object>();
            m.put("id", id); m.put("title", title);
            m.put("messageCount", messages.size()); m.put("updatedAtMs", updatedAtMs);
            return m;
        }
    }

    private final Map<String, Session> sessions = new LinkedHashMap<String, Session>();

    public int count() { return sessions.size(); }

    public boolean create(String id, String title, long atMs) {
        if (id == null || !ID.matcher(id).matches()) return false;
        if (title == null || title.isEmpty() || title.length() > 80) return false;
        if (sessions.containsKey(id) || sessions.size() >= MAX_SESSIONS) return false;
        sessions.put(id, new Session(id, title, atMs));
        return true;
    }

    public boolean append(String id, Role role, String text, long atMs) {
        Session s = sessions.get(id);
        return s != null && s.append(role, text, atMs);
    }

    public boolean remove(String id) { return sessions.remove(id) != null; }
    public Session get(String id) { return sessions.get(id); }

    public List<Map<String, Object>> list() {
        List<Session> all = new ArrayList<Session>(sessions.values());
        Collections.sort(all, new Comparator<Session>() {
            @Override public int compare(Session a, Session b) {
                return Long.compare(b.updatedAtMs, a.updatedAtMs);
            }
        });
        List<Map<String, Object>> out = new ArrayList<Map<String, Object>>();
        for (Session s : all) out.add(s.toJson());
        return Collections.unmodifiableList(out);
    }

    public void clear() { sessions.clear(); }
}
