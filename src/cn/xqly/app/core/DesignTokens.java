package cn.xqly.app.core;

/**
 * 「星穹玻璃」设计系统令牌（纯数据，Android 与文档/测试共用）。
 * 三种显示模式共享同一套语义键，仅映射值不同 —— 不做三套孤立页面。
 */
public final class DesignTokens {
    private DesignTokens() {}

    public enum Theme { DARK, LIGHT }
    public enum Mode { GLASS, ECO, CONTRAST }

    /** 语义色键。 */
    public enum ColorKey {
        BACKGROUND, SURFACE, SURFACE_HIGH, OVERLAY, BORDER,
        TEXT, TEXT_SECONDARY, FOCUS, BRAND, SUCCESS, WARNING, ERROR, ON_BRAND
    }

    public static final class Palette {
        public final Theme theme;
        public final Mode mode;
        private final int[] v = new int[ColorKey.values().length];
        Palette(Theme t, Mode m) { theme = t; mode = m; }
        Palette set(ColorKey k, int argb) { v[k.ordinal()] = argb; return this; }
        public int get(ColorKey k) { return v[k.ordinal()]; }
    }

    // 4dp 栅格
    public static final int SPACE_1 = 4, SPACE_2 = 8, SPACE_3 = 12,
            SPACE_4 = 16, SPACE_6 = 24, SPACE_8 = 32;

    // 分级圆角（dp）
    public static final int R_CONTROL = 12, R_CARD = 20, R_DIALOG = 28, R_DOCK = 36, R_PILL = 999;

    // 动效时长（ms）与标准缓动控制点
    public static final int D_SHORT = 120, D_MEDIUM = 260, D_LONG = 420, D_EMPHASIS = 620;
    public static final float[] EASE_STANDARD = {0.2f, 0f, 0f, 1f};
    public static final float[] EASE_DECELERATE = {0f, 0f, 0.2f, 1f};

    // 字号（sp）/ 行高倍率 / 字重 / 字距（em）
    public static final class Type {
        public final float sp, lineHeight, letterSpacing;
        public final int weight;
        Type(float sp, float lh, float ls, int w) { this.sp = sp; lineHeight = lh; letterSpacing = ls; weight = w; }
    }
    public static final Type T_DISPLAY = new Type(28, 1.25f, 0.01f, 700);
    public static final Type T_HEADLINE = new Type(20, 1.3f, 0.01f, 600);
    public static final Type T_TITLE = new Type(16, 1.4f, 0.005f, 600);
    public static final Type T_BODY = new Type(14, 1.6f, 0f, 400);
    public static final Type T_LABEL = new Type(12, 1.4f, 0.02f, 500);
    public static final Type T_CAPTION = new Type(10.5f, 1.4f, 0.02f, 400);

    // 触控目标下限（dp）
    public static final int MIN_TOUCH = 48;

    public static final class Glass {
        public final float blur, saturation, tintAlpha, innerHighlight, outerHighlight, shadow, refraction;
        Glass(float b, float s, float t, float ih, float oh, float sh, float r) {
            blur = b; saturation = s; tintAlpha = t; innerHighlight = ih; outerHighlight = oh; shadow = sh; refraction = r;
        }
    }

    /** 解析某主题+模式下的语义色板。CONTRAST 为增强可读的独立调校值。 */
    public static Palette palette(Theme theme, Mode mode) {
        if (theme == Theme.LIGHT) return light(mode);
        return dark(mode);
    }

    private static Palette dark(Mode mode) {
        boolean contrast = mode == Mode.CONTRAST;
        Palette p = new Palette(Theme.DARK, mode);
        p.set(ColorKey.BACKGROUND, contrast ? 0xFF000000 : 0xFF050713);
        p.set(ColorKey.SURFACE, contrast ? 0xFF0A0D18 : 0xFF121736);
        p.set(ColorKey.SURFACE_HIGH, contrast ? 0xFF141A2C : 0xFF1C2348);
        p.set(ColorKey.OVERLAY, contrast ? 0xE6000000 : 0xD8050713);
        p.set(ColorKey.BORDER, contrast ? 0xFF8FA0C8 : 0x40C9D6FF);
        p.set(ColorKey.TEXT, contrast ? 0xFFFFFFFF : 0xFFF3F6FF);
        p.set(ColorKey.TEXT_SECONDARY, contrast ? 0xFFE6ECFF : 0xB4DCE6FF);
        p.set(ColorKey.FOCUS, 0xFFFFCE86);
        p.set(ColorKey.BRAND, 0xFFFFCE86);
        p.set(ColorKey.SUCCESS, 0xFF59E6A8);
        p.set(ColorKey.WARNING, 0xFFFFB84D);
        p.set(ColorKey.ERROR, 0xFFFF6B6B);
        p.set(ColorKey.ON_BRAND, 0xFF241500);
        return p;
    }

    private static Palette light(Mode mode) {
        boolean contrast = mode == Mode.CONTRAST;
        Palette p = new Palette(Theme.LIGHT, mode);
        p.set(ColorKey.BACKGROUND, contrast ? 0xFFFFFFFF : 0xFFF5F7FC);
        p.set(ColorKey.SURFACE, contrast ? 0xFFFFFFFF : 0xFFEFF2FA);
        p.set(ColorKey.SURFACE_HIGH, contrast ? 0xFFF0F0F0 : 0xFFE3E9F6);
        p.set(ColorKey.OVERLAY, contrast ? 0xF2FFFFFF : 0xCCF5F7FC);
        p.set(ColorKey.BORDER, contrast ? 0xFF3A465F : 0x80304060);
        p.set(ColorKey.TEXT, contrast ? 0xFF000000 : 0xFF141A2E);
        p.set(ColorKey.TEXT_SECONDARY, contrast ? 0xFF1A2236 : 0xC8202A40);
        p.set(ColorKey.FOCUS, contrast ? 0xFF5A3D00 : 0xFF8A5A00);
        p.set(ColorKey.BRAND, contrast ? 0xFF6B4A00 : 0xFF9A6B00);
        p.set(ColorKey.SUCCESS, 0xFF0E7A4B);
        p.set(ColorKey.WARNING, 0xFF8A4B00);
        p.set(ColorKey.ERROR, 0xFFB3261E);
        p.set(ColorKey.ON_BRAND, 0xFFFFFFFF);
        return p;
    }

    public static Glass glass(Mode mode) {
        switch (mode) {
            case ECO:      return new Glass(0f, 1f, 0.92f, 0.10f, 0.04f, 0.10f, 0f);
            case CONTRAST: return new Glass(4f, 1f, 0.98f, 0.16f, 0.06f, 0.12f, 0f);
            case GLASS:
            default:       return new Glass(18f, 1.45f, 0.34f, 0.42f, 0.22f, 0.35f, 0.55f);
        }
    }

    /** WCAG 相对亮度与对比度，用于自动验证正文/焦点是否达 AA。 */
    public static double luminance(int argb) {
        double r = lin((argb >> 16) & 0xFF), g = lin((argb >> 8) & 0xFF), b = lin(argb & 0xFF);
        return 0.2126 * r + 0.7152 * g + 0.0722 * b;
    }
    private static double lin(int c) {
        double s = c / 255.0;
        return s <= 0.04045 ? s / 12.92 : Math.pow((s + 0.055) / 1.055, 2.4);
    }
    public static double contrastRatio(int a, int b) {
        double l1 = luminance(a), l2 = luminance(b);
        double hi = Math.max(l1, l2), lo = Math.min(l1, l2);
        return (hi + 0.05) / (lo + 0.05);
    }
    /** 正文 AA=4.5，大字/焦点 AA=3.0；返回是否满足。 */
    public static boolean meetsAA(int fg, int bg, boolean largeText) {
        return contrastRatio(fg, bg) >= (largeText ? 3.0 : 4.5);
    }
}
