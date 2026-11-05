import 'dart:math' as math;

enum XTheme { dark, light }
enum XMode { glass, eco, contrast }

/// 「星穹玻璃」双平台设计令牌唯一真源。
///
/// Windows（Win32 自绘 + WebView2）与 Android（原生 View + WebView）
/// 都从 `all()` 生成各自的映射；正文与重要操作满足 WCAG AA。
/// 不引用任何 Flutter / Win32 / Android 类型，保证可在纯 Dart VM 上测试。
class DesignTokens {
  DesignTokens._();

  static const String brandName = '星穹玻璃';

  /// 语义色键。
  static const List<String> colorKeys = <String>[
    'background', 'surface', 'surfaceAlt', 'overlay', 'border',
    'focus', 'brand', 'text', 'textDim', 'success', 'warning', 'error',
  ];

  /// 0xAARRGGBB。深空背景靠层次与微光，不堆粒子。
  static const Map<String, int> _dark = <String, int>{
    'background': 0xff0a0e18,
    'surface': 0xff121826,
    'surfaceAlt': 0xff1a2233,
    'overlay': 0xff202b40,
    'border': 0x33a9c2ff,
    'focus': 0xff8ab6ff,
    'brand': 0xffa87cff,
    'text': 0xfff2f6ff,
    'textDim': 0xffb3c0d9,
    'success': 0xff6fd8a6,
    'warning': 0xffffce86,
    'error': 0xffff8a96,
  };

  static const Map<String, int> _light = <String, int>{
    'background': 0xfff6f8fd,
    'surface': 0xffffffff,
    'surfaceAlt': 0xffeef2fa,
    'overlay': 0xffffffff,
    'border': 0x330f2347,
    'focus': 0xff2f66d0,
    'brand': 0xff6d3fd4,
    'text': 0xff141c2c,
    'textDim': 0xff4f5c73,
    'success': 0xff1b7f52,
    'warning': 0xff96610a,
    'error': 0xffb3263a,
  };

  /// 三种显示模式对透明/模糊/对比的调整系数。
  static const Map<XMode, Map<String, double>> _mode = <XMode, Map<String, double>>{
    XMode.glass: <String, double>{
      'glassAlpha': 0.16, 'blur': 22.0, 'saturation': 1.35,
      'contrastBoost': 1.0, 'animScale': 1.0, 'touchScale': 1.0,
    },
    XMode.eco: <String, double>{
      'glassAlpha': 0.0, 'blur': 0.0, 'saturation': 1.0,
      'contrastBoost': 1.0, 'animScale': 0.35, 'touchScale': 1.0,
    },
    XMode.contrast: <String, double>{
      'glassAlpha': 0.0, 'blur': 0.0, 'saturation': 1.0,
      'contrastBoost': 1.25, 'animScale': 0.2, 'touchScale': 1.25,
    },
  };

  /// 4dp 栅格。
  static double spacing(int step) => step * 4.0;
  static const Map<String, double> gaps = <String, double>{
    'xs': 8, 'sm': 12, 'md': 16, 'lg': 24, 'xl': 32,
  };

  /// 同心圆角：外层容器更大，内层控件更小，差值恒定。
  static const Map<String, double> radii = <String, double>{
    'control': 12, 'card': 16, 'sheet': 20, 'dialog': 24, 'dock': 28,
  };

  static const Map<String, int> motion = <String, int>{
    'short': 120, 'medium': 240, 'long': 400, 'emphasis': 600,
  };

  static const Map<String, double> type = <String, double>{
    'caption': 12, 'body': 15, 'title': 20, 'heading': 26, 'display': 34,
    'lineHeight': 1.55, 'letterSpacing': 0.1,
  };

  /// 推荐触控目标：Android 48dp、Windows 44 逻辑像素，取并集下限。
  static const double minTouchTarget = 48.0;
  static const double desktopTouchTarget = 44.0;

  /// 正文最大阅读宽度，避免大屏被拉伸。
  static const double maxReadingWidth = 760.0;

  static Map<String, int> colors(XTheme theme, XMode mode) {
    final Map<String, int> base = theme == XTheme.dark ? _dark : _light;
    if (mode != XMode.contrast) return Map<String, int>.from(base);
    // 增强可读：提高前景/背景分离，移除低对比边框。
    final Map<String, int> out = Map<String, int>.from(base);
    out['text'] = theme == XTheme.dark ? 0xffffffff : 0xff000000;
    out['textDim'] = theme == XTheme.dark ? 0xffd8e2f5 : 0xff2b3446;
    out['border'] = theme == XTheme.dark ? 0x66cfe0ff : 0x660d1b33;
    return out;
  }

  static Map<String, double> glass(XMode mode) => _mode[mode]!;

  /// 统一光源方向：左上 135°，所有高光/阴影共用。
  static const Map<String, double> light = <String, double>{
    'angleDeg': 135.0,
    'highlightAlpha': 0.26,
    'shadowAlpha': 0.30,
    'shadowBlur': 28.0,
    'shadowDy': 10.0,
    'edgeAlpha': 0.42,
  };

  static double relativeLuminance(int argb) {
    double channel(int v) {
      final double s = v / 255.0;
      return s <= 0.03928 ? s / 12.92 : math.pow((s + 0.055) / 1.055, 2.4) as double;
    }

    final double r = channel((argb >> 16) & 0xff);
    final double g = channel((argb >> 8) & 0xff);
    final double b = channel(argb & 0xff);
    return 0.2126 * r + 0.7152 * g + 0.0722 * b;
  }

  static double contrastRatio(int fg, int bg) {
    final double a = relativeLuminance(fg);
    final double b = relativeLuminance(bg);
    final double hi = a > b ? a : b;
    final double lo = a > b ? b : a;
    return (hi + 0.05) / (lo + 0.05);
  }

  static bool meetsAA(int fg, int bg, {bool largeText = false}) =>
      contrastRatio(fg, bg) >= (largeText ? 3.0 : 4.5);

  /// 供 `tool/gen_tokens.dart` 生成 CSS / JSON，落到 WebView 与 Win32 外壳。
  static Map<String, Object?> all(XTheme theme, XMode mode) {
    final Map<String, int> c = colors(theme, mode);
    final Map<String, double> g = glass(mode);
    return <String, Object?>{
      'brand': brandName,
      'theme': theme.name,
      'mode': mode.name,
      'colors': <String, Object?>{
        for (final String k in colorKeys) k: _hex(c[k]!),
      },
      'spacing': <String, Object?>{for (final String k in gaps.keys) k: gaps[k]},
      'radii': <String, Object?>{for (final String k in radii.keys) k: radii[k]},
      'motion': <String, Object?>{for (final String k in motion.keys) k: motion[k]},
      'type': <String, Object?>{for (final String k in type.keys) k: type[k]},
      'glass': <String, Object?>{for (final String k in g.keys) k: g[k]},
      'light': <String, Object?>{for (final String k in light.keys) k: light[k]},
      'minTouchTarget': minTouchTarget,
      'desktopTouchTarget': desktopTouchTarget,
      'maxReadingWidth': maxReadingWidth,
    };
  }

  static String _hex(int argb) =>
      '#${(argb & 0xffffff).toRadixString(16).padLeft(6, '0')}';

  /// 生成 CSS 自定义属性（亮/暗 × 三模式）。
  static String toCss() {
    final StringBuffer sb = StringBuffer();
    sb.writeln('/* 由 shared/tool/gen_tokens.dart 生成，请勿手改。 */');
    sb.writeln('/* 星穹玻璃设计令牌 · 双平台共享唯一真源 */');
    // 默认层：即使页面未设置 data-theme / data-mode，也能拿到全部 --xq-* 变量。
    // 只定义 --xq-* 前缀，不覆盖页面已有变量名，因此引入本文件不改变既有视觉。
    sb.writeln(':root{');
    final Map<String, int> def = colors(XTheme.dark, XMode.glass);
    for (final String k in colorKeys) {
      sb.writeln('  --xq-$k:${_hex(def[k]!)};');
    }
    for (final String k in gaps.keys) {
      sb.writeln('  --xq-gap-$k:${gaps[k]!}px;');
    }
    for (final String k in radii.keys) {
      sb.writeln('  --xq-radius-$k:${radii[k]!}px;');
    }
    for (final String k in motion.keys) {
      sb.writeln('  --xq-motion-$k:${motion[k]!}ms;');
    }
    sb.writeln('  --xq-glass-alpha:${glass(XMode.glass)['glassAlpha']};');
    sb.writeln('  --xq-glass-blur:${glass(XMode.glass)['blur']}px;');
    sb.writeln('  --xq-touch-scale:1;');
    sb.writeln('  --xq-max-reading:${maxReadingWidth}px;');
    sb.writeln('}');
    for (final XTheme t in XTheme.values) {
      for (final XMode m in XMode.values) {
        final String sel = t == XTheme.dark
            ? '[data-theme="dark"][data-mode="${m.name}"]'
            : '[data-theme="light"][data-mode="${m.name}"]';
        sb.writeln('$sel{');
        final Map<String, int> c = colors(t, m);
        for (final String k in colorKeys) {
          sb.writeln('  --xq-$k:${_hex(c[k]!)};');
        }
        for (final String k in gaps.keys) {
          sb.writeln('  --xq-gap-$k:${gaps[k]}px;');
        }
        for (final String k in radii.keys) {
          sb.writeln('  --xq-radius-$k:${radii[k]}px;');
        }
        for (final String k in motion.keys) {
          sb.writeln('  --xq-motion-$k:${motion[k]}ms;');
        }
        final Map<String, double> g = glass(m);
        sb.writeln('  --xq-glass-alpha:${g['glassAlpha']};');
        sb.writeln('  --xq-glass-blur:${g['blur']}px;');
        sb.writeln('  --xq-touch-scale:${g['touchScale']};');
        sb.writeln('  --xq-max-reading:${maxReadingWidth}px;');
        sb.writeln('}');
      }
    }
    sb.writeln('@media (prefers-reduced-motion: reduce){');
    sb.writeln('  *{--xq-motion-short:0ms;--xq-motion-medium:0ms;');
    sb.writeln('    --xq-motion-long:0ms;--xq-motion-emphasis:0ms;}');
    sb.writeln('}');
    return sb.toString();
  }
}
