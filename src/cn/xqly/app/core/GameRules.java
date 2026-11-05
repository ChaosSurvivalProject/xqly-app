package cn.xqly.app.core;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * 「方块合成」确定性规则引擎（纯 Java，零 UI 依赖，可完整单元测试）。
 * 与渲染解耦：JS 端只负责把 state 画出来；所有计分、合并、生成、撤销、结束判定在此。
 * 同一 seed + 同一操作序列 → 完全相同棋局（每日挑战 / 复盘 / 回归测试）。
 */
public final class GameRules {
    private GameRules() {}

    public static final int SIZE = 4;
    public static final int UNDO_LIMIT = 3;

    public enum Dir { LEFT, RIGHT, UP, DOWN }

    public static final class State {
        public final int[][] board = new int[SIZE][SIZE];
        public long score;
        public long best;
        public int moves;
        public int combo;          // 连续有合并的步数
        public int undosLeft = UNDO_LIMIT;
        public boolean won;        // 到达 2048
        public boolean over;
        public boolean keepPlaying;
        public long seed;
        public int hold;           // 暂存的方块值，0=空
        public boolean holdUsed;   // 每回合只能 hold 一次
        public int next;           // 下一块预览
        public final List<String> achievements = new ArrayList<String>();
    }

    public static final class MoveResult {
        public final boolean changed;
        public final long gain;
        public final int merges;
        public final int maxTile;
        public final boolean spawned;
        public final boolean wonNow;
        public final boolean overNow;
        MoveResult(boolean c, long g, int m, int mt, boolean s, boolean w, boolean o) {
            changed = c; gain = g; merges = m; maxTile = mt; spawned = s; wonNow = w; overNow = o;
        }
    }

    /** 新局：确定性 seed，起手两块。 */
    public static State newGame(long seed, long best) {
        State s = new State();
        s.seed = seed;
        s.best = Math.max(0, best);
        Random rnd = new Random(seed);
        s.next = tileValue(rnd);
        spawn(s, rnd);
        spawn(s, rnd);
        return s;
    }

    /** 从本地存档恢复（含字段裁剪与非法值防护）。 */
    public static State restore(String json, long best) {
        State s = newGame(0L, best);
        if (json == null) return s;
        try {
            java.util.Map<String, Object> m = MiniJson.object(json);
            List<Object> rows = MiniJson.arr(m, "board");
            if (rows != null && rows.size() == SIZE) {
                for (int y = 0; y < SIZE; y++) {
                    Object row = rows.get(y);
                    if (!(row instanceof List) || ((List<?>) row).size() != SIZE) return s;
                    for (int x = 0; x < SIZE; x++) {
                        Object v = ((List<?>) row).get(x);
                        long n = v instanceof Number ? ((Number) v).longValue() : 0;
                        s.board[y][x] = (n > 0 && isPowerOfTwo(n) && n <= (1L << 30)) ? (int) n : 0;
                    }
                }
            }
            s.score = Math.max(0, MiniJson.num(m, "score", 0));
            s.best = Math.max(s.best, MiniJson.num(m, "best", 0));
            s.moves = (int) Math.max(0, MiniJson.num(m, "moves", 0));
            s.undosLeft = (int) Math.max(0, Math.min(UNDO_LIMIT, MiniJson.num(m, "undosLeft", UNDO_LIMIT)));
            s.won = MiniJson.bool(m, "won", false);
            s.keepPlaying = MiniJson.bool(m, "keepPlaying", false);
            s.seed = MiniJson.num(m, "seed", 0);
            s.hold = clampTile(MiniJson.num(m, "hold", 0));
            s.holdUsed = MiniJson.bool(m, "holdUsed", false);
            s.next = clampTile(MiniJson.num(m, "next", 2));
            if (s.next == 0) s.next = 2;
            List<Object> ach = MiniJson.arr(m, "achievements");
            if (ach != null) {
                for (Object a : ach) {
                    if (a instanceof String && ((String) a).length() <= 32
                            && s.achievements.size() < 32) s.achievements.add((String) a);
                }
            }
        } catch (Exception ignored) { return newGame(0L, best); }
        if (!anyTile(s)) return newGame(s.seed, s.best);
        s.over = !canMove(s);
        return s;
    }

    public static String save(State s) {
        java.util.Map<String, Object> m = new java.util.LinkedHashMap<String, Object>();
        List<Object> rows = new ArrayList<Object>();
        for (int y = 0; y < SIZE; y++) {
            List<Object> row = new ArrayList<Object>();
            for (int x = 0; x < SIZE; x++) row.add(s.board[y][x]);
            rows.add(row);
        }
        m.put("board", rows);
        m.put("score", s.score);
        m.put("best", s.best);
        m.put("moves", s.moves);
        m.put("undosLeft", s.undosLeft);
        m.put("won", s.won);
        m.put("keepPlaying", s.keepPlaying);
        m.put("seed", s.seed);
        m.put("hold", s.hold);
        m.put("holdUsed", s.holdUsed);
        m.put("next", s.next);
        m.put("achievements", new ArrayList<Object>(s.achievements));
        return MiniJson.stringify(m);
    }

    /** 一次移动。snapshot 由调用方在 move 前保存以便撤销。 */
    public static MoveResult move(State s, Dir dir) {
        if (s.over) return new MoveResult(false, 0, 0, maxTile(s), false, false, true);
        int[][] before = copy(s.board);
        long gain = 0;
        int merges = 0;

        for (int i = 0; i < SIZE; i++) {
            int[] line = new int[SIZE];
            for (int j = 0; j < SIZE; j++) {
                switch (dir) {
                    case LEFT:  line[j] = s.board[i][j]; break;
                    case RIGHT: line[j] = s.board[i][SIZE - 1 - j]; break;
                    case UP:    line[j] = s.board[j][i]; break;
                    case DOWN:  line[j] = s.board[SIZE - 1 - j][i]; break;
                }
            }
            int[] a = new int[SIZE];
            int k = 0;
            for (int j = 0; j < SIZE; j++) if (line[j] != 0) a[k++] = line[j];
            int[] out = new int[SIZE];
            int w = 0;
            for (int j = 0; j < k; ) {
                if (j + 1 < k && a[j] == a[j + 1]) {
                    int v = a[j] * 2;
                    out[w++] = v;
                    gain += v;
                    merges++;
                    j += 2;
                } else {
                    out[w++] = a[j++];
                }
            }
            for (int j = 0; j < SIZE; j++) {
                switch (dir) {
                    case LEFT:  s.board[i][j] = out[j]; break;
                    case RIGHT: s.board[i][SIZE - 1 - j] = out[j]; break;
                    case UP:    s.board[j][i] = out[j]; break;
                    case DOWN:  s.board[SIZE - 1 - j][i] = out[j]; break;
                }
            }
        }

        if (!changed(before, s.board)) return new MoveResult(false, 0, 0, maxTile(s), false, false, false);

        s.score += gain;
        s.moves++;
        s.combo = merges > 0 ? s.combo + 1 : 0;
        if (s.score > s.best) s.best = s.score;
        s.holdUsed = false;

        Random rnd = new Random(s.seed + s.moves * 0x9E3779B97F4A7C15L + gain);
        boolean spawned = spawn(s, rnd);

        int mt = maxTile(s);
        boolean wonNow = false;
        if (mt >= 2048 && !s.won && !s.keepPlaying) { s.won = true; wonNow = true; }
        unlock(s, mt);
        boolean overNow = !canMove(s);
        s.over = overNow;
        return new MoveResult(true, gain, merges, mt, spawned, wonNow, overNow);
    }

    /** 暂存：把「下一块」与 hold 交换，每回合限一次，不改变棋盘。 */
    public static boolean hold(State s) {
        if (s.holdUsed || s.over) return false;
        int incoming = s.next;
        s.next = s.hold == 0 ? drawValue(s) : s.hold;
        s.hold = incoming;
        s.holdUsed = true;
        return true;
    }

    /** 撤销：返回快照状态；次数规则明确，无付费。 */
    public static State undo(State current, State snapshot, long best) {
        if (snapshot == null || current.undosLeft <= 0) return current;
        State s = restore(save(snapshot), best);
        s.undosLeft = current.undosLeft - 1;
        s.best = Math.max(best, current.best);
        s.over = !canMove(s);
        return s;
    }

    public static State snapshot(State s) { return restore(save(s), s.best); }

    public static boolean canMove(State s) {
        for (int y = 0; y < SIZE; y++) for (int x = 0; x < SIZE; x++) {
            if (s.board[y][x] == 0) return true;
            if (x + 1 < SIZE && s.board[y][x] == s.board[y][x + 1]) return true;
            if (y + 1 < SIZE && s.board[y][x] == s.board[y + 1][x]) return true;
        }
        return false;
    }

    public static int maxTile(State s) {
        int m = 0;
        for (int y = 0; y < SIZE; y++) for (int x = 0; x < SIZE; x++) m = Math.max(m, s.board[y][x]);
        return m;
    }

    /** 等级：由最大方块取 log2，用于 UI 显示与升级反馈。 */
    public static int level(State s) {
        int m = maxTile(s);
        int lv = 0;
        while (m > 2) { m >>= 1; lv++; }
        return lv;
    }

    /** 每日挑战：本地日期 → 确定性种子，无需联网。 */
    public static long dailySeed(int year, int month, int day) {
        long h = 1469598103934665603L;
        long[] parts = {year, month, day, 0x58514C59L};
        for (long p : parts) { h ^= p; h *= 1099511628211L; }
        return h & Long.MAX_VALUE;
    }

    /** 色盲友好：返回与数值绑定的形状标识（不依赖颜色区分）。 */
    public static String shapeFor(int value) {
        switch (value) {
            case 2: return "●";
            case 4: return "■";
            case 8: return "▲";
            case 16: return "◆";
            case 32: return "★";
            case 64: return "⬟";
            case 128: return "✚";
            case 256: return "⬢";
            case 512: return "✦";
            case 1024: return "❖";
            case 2048: return "✧";
            default: return "◈";
        }
    }

    // ---- internals ----
    private static boolean spawn(State s, Random rnd) {
        List<int[]> empty = new ArrayList<int[]>();
        for (int y = 0; y < SIZE; y++) for (int x = 0; x < SIZE; x++)
            if (s.board[y][x] == 0) empty.add(new int[]{y, x});
        if (empty.isEmpty()) return false;
        int[] p = empty.get(rnd.nextInt(empty.size()));
        s.board[p[0]][p[1]] = s.next;
        s.next = tileValue(rnd);
        return true;
    }

    private static int drawValue(State s) {
        Random r = new Random(s.seed + s.moves * 31L + s.score);
        return tileValue(r);
    }

    private static int tileValue(Random r) { return r.nextDouble() < 0.9 ? 2 : 4; }

    private static void unlock(State s, int mt) {
        addAchievement(s, "first_merge", s.moves >= 1, "初次合成");
        addAchievement(s, "combo5", s.combo >= 5, "五连合成");
        addAchievement(s, "tile128", mt >= 128, "抵达 128");
        addAchievement(s, "tile1024", mt >= 1024, "抵达 1024");
        addAchievement(s, "astral", mt >= 2048, "合成星穹方块");
    }
    private static void addAchievement(State s, String id, boolean cond, String label) {
        if (cond && !s.achievements.contains(id)) s.achievements.add(label);
    }

    private static int[][] copy(int[][] a) {
        int[][] b = new int[SIZE][SIZE];
        for (int i = 0; i < SIZE; i++) System.arraycopy(a[i], 0, b[i], 0, SIZE);
        return b;
    }
    private static boolean changed(int[][] a, int[][] b) {
        for (int i = 0; i < SIZE; i++) for (int j = 0; j < SIZE; j++) if (a[i][j] != b[i][j]) return true;
        return false;
    }
    private static boolean anyTile(State s) {
        for (int y = 0; y < SIZE; y++) for (int x = 0; x < SIZE; x++) if (s.board[y][x] != 0) return true;
        return false;
    }
    private static boolean isPowerOfTwo(long n) { return n > 0 && (n & (n - 1)) == 0; }
    private static int clampTile(long n) {
        return (n > 0 && isPowerOfTwo(n) && n <= (1L << 30)) ? (int) n : 0;
    }
}
