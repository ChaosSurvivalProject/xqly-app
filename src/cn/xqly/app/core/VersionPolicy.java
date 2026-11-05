package cn.xqly.app.core;

/** 版本号与 versionCode 的升级策略：拒绝降级、拒绝畸形、拒绝无法确认的版本。 */
public final class VersionPolicy {
    private VersionPolicy() {}

    public enum Decision { UPGRADE, SAME, DOWNGRADE, MALFORMED }

    /** 解析 "v1.2.3" / "1.2" / "2.0.0-beta" → 数字段；无法解析返回 null。 */
    public static int[] parse(String v) {
        if (v == null) return null;
        String s = v.trim();
        if (s.startsWith("v") || s.startsWith("V")) s = s.substring(1);
        int cut = s.length();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (!(c == '.' || (c >= '0' && c <= '9'))) { cut = i; break; }
        }
        s = s.substring(0, cut);
        if (s.isEmpty()) return null;
        String[] parts = s.split("\\.");
        int[] out = new int[parts.length];
        for (int i = 0; i < parts.length; i++) {
            if (parts[i].isEmpty()) return null;
            try { out[i] = Integer.parseInt(parts[i]); }
            catch (NumberFormatException e) { return null; }
            if (out[i] < 0) return null;
        }
        return out;
    }

    /** a>b 返回 1，等于 0，小于 -1；任一畸形返回 Integer.MIN_VALUE。 */
    public static int compare(String a, String b) {
        int[] va = parse(a), vb = parse(b);
        if (va == null || vb == null) return Integer.MIN_VALUE;
        int len = Math.max(va.length, vb.length);
        for (int i = 0; i < len; i++) {
            int x = i < va.length ? va[i] : 0;
            int y = i < vb.length ? vb[i] : 0;
            if (x != y) return x > y ? 1 : -1;
        }
        return 0;
    }

    /** 综合判定：版本名与 versionCode 必须同时不降级，且至少一项升级。 */
    public static Decision decide(String candidateTag, long candidateCode,
                                  String currentName, long currentCode) {
        int[] c = parse(candidateTag);
        int[] u = parse(currentName);
        if (c == null || u == null || candidateCode < 0) return Decision.MALFORMED;
        int nameCmp = compare(candidateTag, currentName);
        if (nameCmp == Integer.MIN_VALUE) return Decision.MALFORMED;
        if (candidateCode < currentCode) return Decision.DOWNGRADE;
        if (nameCmp < 0) return Decision.DOWNGRADE;
        if (candidateCode == currentCode && nameCmp == 0) return Decision.SAME;
        return Decision.UPGRADE;
    }

    public static boolean isValidSha256(String hex) {
        if (hex == null) return false;
        String s = hex.trim().toLowerCase();
        if (s.startsWith("sha256:")) s = s.substring(7);
        if (s.length() != 64) return false;
        for (int i = 0; i < 64; i++) {
            char c = s.charAt(i);
            if (!((c >= '0' && c <= '9') || (c >= 'a' && c <= 'f'))) return false;
        }
        return true;
    }

    public static String normalizeSha256(String hex) {
        if (!isValidSha256(hex)) return null;
        String s = hex.trim().toLowerCase();
        if (s.startsWith("sha256:")) s = s.substring(7);
        return s;
    }
}
