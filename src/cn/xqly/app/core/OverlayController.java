package cn.xqly.app.core;

/**
 * 原生浮层互斥控制器（零 Android 依赖，可直接在 JVM 上回归测试）。
 *
 * <p>修复 v2.1.0 的致命 UI bug：当时资料库浮层在构造后没有设为 GONE，且
 * {@code libraryOpen} 标志仍为 false，导致一个透明浮层从启动就叠在主 WebView 上——
 * 下层页面内容全部透出形成「层层叠叠」，上层吃掉触摸使页面「卡住动不了」，
 * 返回键与桥接关闭消息又因标志位不符而关不掉它。
 *
 * <p>本控制器把「谁可见」收敛为唯一真源，并让每次状态变更都返回一份
 * <b>完整迁移</b>：要显示的层 + 要隐藏的层（含所有其它层）。调用方按迁移结果
 * 无条件重设所有浮层的可见性，因此无论之前残留了什么状态、动画是否被取消，
 * 都能收敛到「任意时刻最多一个浮层可见」。
 */
public final class OverlayController {

    public enum Layer {
        /** 无浮层，主 WebView 可见。 */
        NONE,
        ARCHIVE,
        LIBRARY,
        GAME;

        public static final Layer[] OVERLAYS = { ARCHIVE, LIBRARY, GAME };
    }

    /** 一次状态变更的完整迁移结果，调用方据此重设所有浮层可见性。 */
    public static final class Transition {
        /** 需要显示的层；NONE 表示本次不显示任何浮层。 */
        public final Layer show;
        /** 需要隐藏的层，恒包含全部非 show 层（防御历史残留）。 */
        public final Layer[] hide;
        /** 状态是否真的发生变化（用于跳过无谓动画）。 */
        public final boolean changed;

        Transition(Layer show, Layer[] hide, boolean changed) {
            this.show = show;
            this.hide = hide;
            this.changed = changed;
        }
    }

    private Layer current = Layer.NONE;

    public Layer current() { return current; }

    public boolean isOpen(Layer l) { return l != null && l != Layer.NONE && current == l; }

    public boolean anyOpen() { return current != Layer.NONE; }

    /** 打开某层：自动关闭其它层。NONE / null 视为非法参数。 */
    public Transition open(Layer l) {
        if (l == null || l == Layer.NONE) {
            throw new IllegalArgumentException("open() 需要具体的浮层，不能是 NONE");
        }
        boolean changed = current != l;
        current = l;
        return build(l, changed);
    }

    /** 关闭指定层；若它不是当前层，保持现状但仍返回一次完整收敛（清除潜在残留）。 */
    public Transition close(Layer l) {
        if (l != null && l != Layer.NONE && current == l) {
            current = Layer.NONE;
            return build(Layer.NONE, true);
        }
        // 关键：不能因为「关了一个本来就没开的层」就把当前可见层也隐藏掉。
        return build(current, false);
    }

    /** 关闭全部浮层，返回收敛迁移。 */
    public Transition closeAll() {
        boolean changed = current != Layer.NONE;
        current = Layer.NONE;
        return build(Layer.NONE, changed);
    }

    /** 返回键：有浮层则关闭并返回其迁移；无浮层返回 changed=false，交给调用方处理 WebView 返回/退出。 */
    public Transition onBack() { return closeAll(); }

    /**
     * 不变量：任意时刻只有一个 current；构造迁移时 hide 覆盖除 show 外的全部浮层。
     * 供回归测试与 debug 断言使用。
     */
    public boolean invariant(Transition t) {
        if (t == null || t.hide == null) return false;
        if (t.show != current) return false;
        int hideCount = 0;
        for (Layer l : Layer.OVERLAYS) {
            boolean inHide = false;
            for (Layer h : t.hide) if (h == l) { inHide = true; break; }
            if (l == t.show) {
                if (inHide) return false;               // show 层不能被隐藏
            } else if (inHide) {
                hideCount++;
            } else {
                return false;                          // 其余层必须全部被隐藏
            }
        }
        return hideCount == Layer.OVERLAYS.length - (t.show == Layer.NONE ? 0 : 1);
    }

    private Transition build(Layer show, boolean changed) {
        int n = Layer.OVERLAYS.length - (show == Layer.NONE ? 0 : 1);
        Layer[] hide = new Layer[n];
        int i = 0;
        for (Layer l : Layer.OVERLAYS) if (l != show) hide[i++] = l;
        return new Transition(show, hide, changed);
    }
}
