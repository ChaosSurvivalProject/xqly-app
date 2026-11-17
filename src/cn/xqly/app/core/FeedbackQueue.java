package cn.xqly.app.core;

import java.util.ArrayList;
import java.util.List;

/**
 * 由 Dart 核心维护的**单一**临时反馈队列（Java 侧镜像）。
 * 同一时刻最多展示一条（高优先级抢占）；危险提示 6s、普通 4s、长文本可手动关闭。
 * Android / Windows 用底部固定条，WebView 用顶部安全区下固定条。
 */
public final class FeedbackQueue {
    private FeedbackQueue() {}

    public enum Level { INFO, SUCCESS, WARNING, ERROR }

    public static final int DANGER_MS = 6000;
    public static final int NORMAL_MS = 4000;
    public static final int LONG_TEXT_THRESHOLD = 120;
    public static final int MAX_QUEUE = 16;
    public static final String PLACEMENT_ANDROID = "bottom-bar";
    public static final String PLACEMENT_WEBVIEW = "top-safe-bar";

    public static final class Feedback {
        public final String id, text, action;
        public final Level level;
        public final int durationMs;
        public final boolean dismissible;
        Feedback(String id, Level level, String text, int durationMs,
                 boolean dismissible, String action) {
            this.id = id; this.level = level; this.text = text;
            this.durationMs = durationMs; this.dismissible = dismissible;
            this.action = action;
        }
        public int priority() {
            switch (level) {
                case ERROR: return 4;
                case WARNING: return 3;
                case SUCCESS: return 2;
                default: return 1;
            }
        }
    }

    public static int durationFor(Level level, String text) {
        return (level == Level.ERROR || level == Level.WARNING) ? DANGER_MS : NORMAL_MS;
    }

    public static Feedback make(String id, Level level, String text, String action,
                                Integer durationMs) {
        boolean longText = text.length() > LONG_TEXT_THRESHOLD;
        return new Feedback(id, level, text,
                durationMs == null ? durationFor(level, text) : durationMs,
                longText, action.length() <= 2 ? action : "");
    }

    public static final class State {
        private final List<Feedback> pending = new ArrayList<Feedback>();
        private Feedback current = null;
        private long startedAt = 0;

        public Feedback current() { return current; }
        public int pendingCount() { return pending.size(); }

        public Feedback push(Feedback f, long nowMs) {
            if (current == null) {
                current = f; startedAt = nowMs; return f;
            }
            if (f.priority() > current.priority()) {
                if (nowMs - startedAt < current.durationMs) pending.add(0, current);
                current = f; startedAt = nowMs; return f;
            }
            if (pending.size() >= MAX_QUEUE) pending.remove(0);
            pending.add(f);
            return current;
        }

        public Feedback tick(long nowMs) {
            if (current == null) return null;
            if (current.dismissible && pending.isEmpty()) return current;
            if (nowMs - startedAt < current.durationMs) return current;
            return next(nowMs);
        }

        public Feedback dismiss(String id, long nowMs) {
            if (current != null && current.id.equals(id) && current.dismissible)
                return next(nowMs);
            for (int i = pending.size() - 1; i >= 0; i--)
                if (pending.get(i).id.equals(id)) pending.remove(i);
            return current;
        }

        private Feedback next(long nowMs) {
            if (pending.isEmpty()) { current = null; startedAt = 0; return null; }
            int best = 0;
            for (int i = 1; i < pending.size(); i++)
                if (pending.get(i).priority() > pending.get(best).priority()) best = i;
            current = pending.remove(best);
            startedAt = nowMs;
            return current;
        }

        public void clear() {
            pending.clear(); current = null; startedAt = 0;
        }
    }
}
