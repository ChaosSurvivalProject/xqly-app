package cn.xqly.app;

import android.app.AlertDialog;
import android.content.Context;
import android.widget.ScrollView;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import cn.xqly.app.core.AppIdentity;
import cn.xqly.app.core.MiniJson;
import cn.xqly.app.core.UpdateVerdict;
import cn.xqly.app.core.UserPrefs;

/**
 * 更新中心：当前版本 / 通道 / 发布时间 / 更新说明 / 校验状态 / 更新记录 / 回退说明。
 * 记录只保存必要摘要（时间、阶段、错误分类、结论、失败项 key），不记录 URL 全文、
 * 令牌、账号或更新说明全文。
 */
public final class UpdateCenter {
    private UpdateCenter() {}

    public static final String K_RECORDS = "u.update_records";
    public static final int MAX_RECORDS = 30;

    public static final class Record {
        public final long time;
        public final String phase, error, summary, tag;
        public final UpdateVerdict.Verdict verdict;   // 可为 null，不持久化明细对象
        Record(long t, String p, String e, String s, String tag, UpdateVerdict.Verdict v) {
            time = t; phase = p; error = e; summary = s; this.tag = tag; verdict = v;
        }
        String toJson() {
            java.util.LinkedHashMap<String, Object> m = new java.util.LinkedHashMap<String, Object>();
            m.put("t", time); m.put("phase", phase); m.put("error", error);
            m.put("summary", summary == null ? "" : summary);
            m.put("tag", tag == null ? "" : tag);
            if (verdict != null) {
                List<Object> fails = new ArrayList<Object>();
                for (UpdateVerdict.Check c : verdict.failures()) fails.add(c.key);
                m.put("fails", fails);
                m.put("checks", verdict.checks.size());
            }
            return MiniJson.stringify(m);
        }
    }

    public static void append(UserPrefs p, Record r) {
        List<Object> arr = readArr(p);
        arr.add(0, MiniJson.parse(r.toJson()));
        while (arr.size() > MAX_RECORDS) arr.remove(arr.size() - 1);
        p.put(K_RECORDS, MiniJson.stringify(arr));
    }

    public static String render(Context ctx, UserPrefs p, UpdateVerdict.Verdict live) {
        StringBuilder s = new StringBuilder();
        s.append("当前版本：v").append(MainActivity.VERSION_NAME)
                .append("  (versionCode ").append(MainActivity.VERSION_CODE).append(")\n");
        s.append("更新通道：官方 Release\n");
        s.append("发布者：").append(AppIdentity.PUBLISHER).append(" / ").append(AppIdentity.REPO).append("\n");
        String pinned = AppIdentity.EXPECTED_SIGNING_CERT_SHA256;
        s.append("签名指纹钉扎：").append(pinned.isEmpty() ? "未配置（将拒绝一切安装）"
                : pinned.substring(0, 12) + "…").append("\n");

        String lkg = p.get("u.last_known_good", "");
        if (!lkg.isEmpty()) {
            try {
                java.util.Map<String, Object> m = MiniJson.object(lkg);
                s.append("上一个可用版本：v").append(MiniJson.str(m, "version", "?")).append("\n");
            } catch (Exception ignored) { }
        }
        s.append("\n【校验项说明】\n")
                .append("· 官方发布者 / 资产命名 / 下载主机白名单\n")
                .append("· 整包 SHA-256 摘要（Release digest 对比实测）\n")
                .append("· APK 签名证书指纹（与钉扎值比对，防发布者被替换）\n")
                .append("· 版本名与 versionCode 非降级、包内版本与 Release 一致\n")
                .append("· 任一项失败即拒绝安装，仅删除临时文件\n");

        if (live != null) {
            s.append("\n【最近一次校验结果】").append(live.trusted ? "通过" : "未通过").append("\n");
            for (UpdateVerdict.Check c : live.checks) {
                s.append(c.pass ? "  ✓ " : "  ✗ ").append(c.label);
                if (!c.pass && !c.detail.isEmpty()) s.append(" — ").append(safe(c.detail));
                s.append("\n");
            }
        }

        s.append("\n【更新记录（仅摘要）】\n");
        List<Object> arr = readArr(p);
        if (arr.isEmpty()) s.append("  暂无记录\n");
        else {
            SimpleDateFormat f = new SimpleDateFormat("MM-dd HH:mm", Locale.CHINA);
            int n = 0;
            for (Object o : arr) {
                if (!(o instanceof java.util.Map) || n++ >= 12) continue;
                @SuppressWarnings("unchecked") java.util.Map<String, Object> m = (java.util.Map<String, Object>) o;
                s.append("  ").append(f.format(new Date(MiniJson.num(m, "t", 0))))
                        .append("  ").append(safe(MiniJson.str(m, "phase", "")))
                        .append("  ").append(safe(MiniJson.str(m, "error", "")))
                        .append("  ").append(safe(MiniJson.str(m, "summary", "")))
                        .append("\n");
            }
        }

        s.append("\n【回退说明】\n")
                .append("1. 本应用不会自动降级；校验失败时当前版本始终可用。\n")
                .append("2. 若新版本异常，可在系统设置卸载更新，或从官方 Releases 安装上一版本 APK"
                        + "（签名一致可直接覆盖）。\n")
                .append("3. 收藏、历史、阅读进度、游戏存档存于独立用户命名空间，卸载更新不会清除。\n");
        return s.toString();
    }

    public static void show(Context ctx, UserPrefs p, UpdateVerdict.Verdict live) {
        TextView tv = new TextView(ctx);
        tv.setText(render(ctx, p, live));
        tv.setTextColor(0xFFDCE6FF);
        tv.setTextSize(12);
        tv.setTextIsSelectable(true);
        int pad = (int) (18 * ctx.getResources().getDisplayMetrics().density);
        tv.setPadding(pad, pad, pad, pad);
        ScrollView sv = new ScrollView(ctx);
        sv.addView(tv);
        new AlertDialog.Builder(ctx)
                .setTitle("更新中心")
                .setView(sv)
                .setPositiveButton("关闭", null)
                .show();
    }

    private static List<Object> readArr(UserPrefs p) {
        try {
            Object o = MiniJson.parse(p.get(K_RECORDS, "[]"));
            if (o instanceof List) return new ArrayList<Object>((List<Object>) o);
        } catch (Exception ignored) { }
        return new ArrayList<Object>();
    }

    private static String safe(String s) {
        if (s == null) return "";
        // 记录渲染不回显完整 URL / 查询串 / 长文本
        String t = s.replaceAll("https?://\\S+", "[链接]");
        return t.length() > 120 ? t.substring(0, 120) + "…" : t;
    }
}
