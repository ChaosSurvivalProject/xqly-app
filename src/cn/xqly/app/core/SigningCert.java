package cn.xqly.app.core;

/** 签名证书指纹计算辅助（把 Android 取到的证书字节转成可比对的小写 hex）。 */
public final class SigningCert {
    private SigningCert() {}

    public static String sha256(byte[] certBytes) {
        if (certBytes == null || certBytes.length == 0) return "";
        byte[] d;
        try {
            d = java.security.MessageDigest.getInstance("SHA-256").digest(certBytes);
        } catch (Exception e) { return ""; }
        StringBuilder b = new StringBuilder(64);
        for (byte x : d) b.append(String.format("%02x", x & 0xFF));
        return b.toString();
    }

    /** 把 keytool 的 "AA:BB:..." 形式归一化为 64 位小写 hex。 */
    public static String normalize(String s) {
        if (s == null) return null;
        String t = s.replace(":", "").replace(" ", "").trim().toLowerCase();
        return VersionPolicy.isValidSha256(t) ? t : null;
    }
}
