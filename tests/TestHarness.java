package tests;

import java.util.ArrayList;
import java.util.List;

/** 零依赖 JVM 测试框架：无 JUnit、无 Android、无网络，标准 javac/java 即可运行。 */
public final class TestHarness {
    public static final class Case {
        final String group, name;
        final Runnable body;
        Case(String g, String n, Runnable b) { group = g; name = n; body = b; }
    }

    private final List<Case> cases = new ArrayList<Case>();
    private int passed, failed;
    private final List<String> failures = new ArrayList<String>();
    private String currentGroup = "";

    public void group(String g) { currentGroup = g; }

    public void test(String name, Runnable body) { cases.add(new Case(currentGroup, name, body)); }

    public int run() {
        System.out.println("==========================================================");
        System.out.println(" 星穹旅驿 · core 逻辑 JVM 单元测试");
        System.out.println(" JVM: " + System.getProperty("java.version")
                + "  |  用例总数: " + cases.size());
        System.out.println("==========================================================");
        String g = null;
        for (Case c : cases) {
            if (!c.group.equals(g)) { g = c.group; System.out.println("\n[" + g + "]"); }
            try {
                c.body.run();
                passed++;
                System.out.println("  PASS  " + c.name);
            } catch (Throwable t) {
                failed++;
                String msg = "  FAIL  " + c.name + "  ->  " + t;
                failures.add("[" + g + "] " + c.name + " : " + t);
                System.out.println(msg);
            }
        }
        System.out.println("\n----------------------------------------------------------");
        System.out.println(" 结果: " + passed + " passed, " + failed + " failed, " + cases.size() + " total");
        if (failed > 0) {
            System.out.println(" 失败明细:");
            for (String f : failures) System.out.println("   - " + f);
        }
        System.out.println("==========================================================");
        return failed == 0 ? 0 : 1;
    }

    // ---- 断言 ----
    public static void ok(boolean cond, String msg) { if (!cond) throw new AssertionError(msg); }
    public static void eq(Object a, Object b, String msg) {
        if (a == null ? b != null : !a.equals(b)) throw new AssertionError(msg + " (expected=" + b + " actual=" + a + ")");
    }
    public static void eqInt(long a, long b, String msg) {
        if (a != b) throw new AssertionError(msg + " (expected=" + b + " actual=" + a + ")");
    }
    public static void isTrue(boolean v, String msg) { ok(v, msg); }
    public static void isFalse(boolean v, String msg) { ok(!v, msg); }
    public static void notNull(Object o, String msg) { ok(o != null, msg); }
    public static void throwsAny(Runnable r, String msg) {
        try { r.run(); } catch (Throwable t) { return; }
        throw new AssertionError(msg + " (expected exception)");
    }
}
