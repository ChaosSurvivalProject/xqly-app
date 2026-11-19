package cn.xqly.app.core;

import java.util.regex.Pattern;

/** 投递区路径安全策略（Java 侧镜像）。不读写任意文件，只做判定 + 稳定标识。 */
public final class WorkbenchDropzone {
    private WorkbenchDropzone() {}

    public static final String WORKSPACE = "root/1";
    public static final int MAX_LEN = 192;
    private static final Pattern SEG = Pattern.compile("^[\\w\\u4e00-\\u9fa5][\\w.\\-\\u4e00-\\u9fa5]{0,126}$");

    public static final class Verdict {
        public final boolean allowed;
        public final String reason, safeId;
        private Verdict(boolean allowed, String reason, String safeId) {
            this.allowed = allowed; this.reason = reason; this.safeId = safeId;
        }
        static Verdict allow(String id) { return new Verdict(true, "NONE", id); }
        static Verdict deny(String reason) { return new Verdict(false, reason, ""); }
    }

    public static Verdict check(String raw) {
        if (raw == null || raw.isEmpty() || raw.length() > MAX_LEN) return Verdict.deny("TOO_LONG");
        if (raw.indexOf('\0') >= 0 || raw.contains("\\")) return Verdict.deny("BAD_CHAR");
        String low = raw.toLowerCase(java.util.Locale.US);
        if (low.contains("%2f") || low.contains("%5c") || low.contains(".."))
            return Verdict.deny("TRAVERSAL");
        if (raw.startsWith("/") || low.contains("://")) return Verdict.deny("ABSOLUTE");
        String prefix = WORKSPACE + "/";
        if (!raw.startsWith(prefix)) return Verdict.deny("OUTSIDE_WORKSPACE");
        String rel = raw.substring(prefix.length());
        if (rel.isEmpty() || rel.startsWith("/") || rel.endsWith("/")) return Verdict.deny("BAD_PATH");
        for (String seg : rel.split("/", -1)) {
            if (seg.isEmpty() || seg.equals(".") || seg.equals("..")) return Verdict.deny("BAD_SEGMENT");
            if (!SEG.matcher(seg).matches()) return Verdict.deny("BAD_SEGMENT");
        }
        return Verdict.allow("dz_" + stableId(rel).substring(0, 16));
    }

    /** FNV-1a 双路 64bit 摘要，与 Dart dropzoneStableId 逐字节一致。 */
    public static String stableId(String input) {
        long h1 = 0xcbf29ce484222325L;
        long h2 = 0x9e3779b97f4a7c15L;
        for (int i = 0; i < input.length(); i++) {
            int c = input.charAt(i);
            h1 = (h1 ^ c) * 0x100000001b3L;
            h2 = (h2 + c + (h1 >>> 8)) * 0x100000001b3L;
        }
        return pad(h1) + pad(h2);
    }

    private static String pad(long v) {
        String s = Long.toHexString(v);
        while (s.length() < 16) s = "0" + s;
        return s;
    }
}
