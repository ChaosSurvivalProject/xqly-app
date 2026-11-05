package cn.xqly.app;

import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.content.pm.SigningInfo;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;

import cn.xqly.app.core.AppIdentity;
import cn.xqly.app.core.MiniJson;
import cn.xqly.app.core.SigningCert;
import cn.xqly.app.core.UpdateVerdict;
import cn.xqly.app.core.VersionPolicy;

/**
 * 可信更新链（安全 P0）。
 *
 * - 固定官方发布者 (AppIdentity.REPO) 与更新来源 (api.github.com + 白名单下载主机)
 * - 查询：连接/读取超时、可取消、错误分类（不吞异常根因）
 * - 下载：进度 / 速度 / 剩余时间 / 取消 / 断点续传（Range）/ 临时文件
 * - 安装前：整包 SHA-256、APK 签名证书指纹钉扎、versionName、versionCode 全校验
 * - 拒绝：签名变化、摘要不符、版本降级、未知来源、空钉扎配置
 * - 系统不允许安装：一次性引导 + 安全查看/稍后安装，不反复弹窗
 * - 校验结果全部写入「更新中心」记录；保留上一个可用版本与回退说明
 *
 * 关键原则：绝不因 URL 来自 GitHub 就信任 APK；自签名成功 ≠ 发布者可信。
 */
public class Updater {

    private static final int CONNECT_TIMEOUT = 12000;
    private static final int READ_TIMEOUT = 20000;
    private static final String CACHE_APK = "xqly-update.apk";

    private final MainActivity act;
    private final PrefsStore userPrefs;   // 用户命名空间（更新记录/上次可用版本）
    private volatile boolean cancelled = false;
    private volatile HttpURLConnection activeConn;

    public Updater(MainActivity a) {
        act = a;
        userPrefs = new PrefsStore(a, true);
    }

    public void cancel() {
        cancelled = true;
        HttpURLConnection c = activeConn;
        if (c != null) try { c.disconnect(); } catch (Exception ignored) { }
    }

    // ---------------------------------------------------------------- 查询
    public void checkForUpdates(boolean verbose) {
        cancelled = false;
        new Thread(() -> {
            HttpURLConnection c = null;
            try {
                URL u = new URL(AppIdentity.RELEASES_LATEST);
                c = (HttpURLConnection) u.openConnection();
                activeConn = c;
                c.setRequestProperty("User-Agent", "xqly-app/" + MainActivity.VERSION_NAME);
                c.setRequestProperty("Accept", "application/vnd.github+json");
                c.setConnectTimeout(CONNECT_TIMEOUT);
                c.setReadTimeout(READ_TIMEOUT);
                c.setInstanceFollowRedirects(false);   // 只允许白名单主机，禁止被重定向到任意域
                int code = c.getResponseCode();
                if (cancelled) return;
                if (code != 200) {
                    record("check", UpdateVerdict.NetError.HTTP, "HTTP " + code, null);
                    if (verbose) toast("更新源返回 HTTP " + code);
                    return;
                }
                String json = readAll(c.getInputStream());
                UpdateVerdict.ParseResult pr = UpdateVerdict.parseRelease(json);
                if (pr.error != UpdateVerdict.ParseError.OK) {
                    record("check", UpdateVerdict.NetError.PARSE, pr.error.name(), null);
                    if (verbose) toast("未发现合规的官方发布版本");
                    return;
                }
                VersionPolicy.Decision d = VersionPolicy.decide(
                        pr.release.tag, 0, MainActivity.VERSION_NAME, MainActivity.VERSION_CODE);
                // 查询阶段 versionCode 未知，用 0 占位只看版本名；最终以 evaluate 为准
                if (d == VersionPolicy.Decision.DOWNGRADE || d == VersionPolicy.Decision.MALFORMED) {
                    if (verbose) toast("已是最新版本 v" + MainActivity.VERSION_NAME);
                    return;
                }
                if (d == VersionPolicy.Decision.SAME) {
                    if (verbose) toast("已是最新版本 v" + MainActivity.VERSION_NAME);
                    return;
                }
                record("check", UpdateVerdict.NetError.NONE, "发现 " + pr.release.tag, null);
                act.runOnUiThread(() -> showUpdateDialog(pr.release));
            } catch (java.net.SocketTimeoutException e) {
                record("check", UpdateVerdict.NetError.TIMEOUT, "查询超时", null);
                if (verbose) toast("检查更新超时，请稍后重试");
            } catch (Exception e) {
                UpdateVerdict.NetError ne = UpdateVerdict.classify(e, null);
                record("check", ne, ne.name(), null);
                if (verbose) toast("检查更新失败（" + ne.name() + "）");
            } finally {
                if (c != null) c.disconnect();
                activeConn = null;
            }
        }, "xqly-update-check").start();
    }

    private void showUpdateDialog(UpdateVerdict.Release rel) {
        AlertDialog.Builder b = new AlertDialog.Builder(act);
        b.setTitle("发现新版本 " + rel.tag);
        String msg = rel.body == null ? "" : rel.body.replaceAll("#+", "").trim();
        if (msg.length() > 600) msg = msg.substring(0, 600) + "…";
        String full = (msg.isEmpty() ? "" : msg + "\n\n")
                + "当前版本：v" + MainActivity.VERSION_NAME + " (code " + MainActivity.VERSION_CODE + ")\n"
                + "发布者：" + AppIdentity.PUBLISHER + "\n"
                + "安装前将校验：整包 SHA-256、签名证书指纹、版本号、versionCode。";
        b.setMessage(full);
        b.setPositiveButton("下载并验证", (d, w) -> downloadAndInstall(rel));
        b.setNeutralButton("更新中心", (d, w) -> UpdateCenter.show(act, userPrefs, null));
        b.setNegativeButton("稍后", null);
        b.setCancelable(true);
        b.show();
    }

    // ---------------------------------------------------------------- 下载
    private void downloadAndInstall(UpdateVerdict.Release rel) {
        if (!FeatureGate.enabled(act, "update_download")) {
            toast("更新下载已被开关停用");
            return;
        }
        if (!UpdateVerdict.downloadHostAllowed(rel.asset.url)) {
            toast("下载来源不在白名单，已取消");
            record("download", UpdateVerdict.NetError.HTTP, "BAD_ASSET_HOST", rel.tag);
            return;
        }
        cancelled = false;

        LinearLayout lay = new LinearLayout(act);
        lay.setOrientation(LinearLayout.VERTICAL);
        lay.setPadding(dp(20), dp(16), dp(20), dp(8));
        TextView info = new TextView(act);
        info.setTextColor(0xFFDCE6FF);
        info.setTextSize(13);
        lay.addView(info);
        ProgressBar bar = new ProgressBar(act, null, android.R.attr.progressBarStyleHorizontal);
        bar.setMax(100);
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(-1, -2);
        blp.topMargin = dp(12);
        lay.addView(bar, blp);

        AlertDialog dlg = new AlertDialog.Builder(act)
                .setTitle("正在下载 " + rel.tag)
                .setView(lay)
                .setCancelable(true)
                .setNegativeButton("取消", (d, w) -> cancel())
                .setOnDismissListener(d -> cancel())
                .show();

        new Thread(() -> {
            File out = new File(act.getCacheDir(), CACHE_APK);
            try {
                long total = download(rel.asset.url, out, (done, max, speed, eta) ->
                        act.runOnUiThread(() -> {
                            if (max > 0) bar.setProgress((int) (done * 100 / max));
                            info.setText(String.format("%.1f / %.1f MB  ·  %.0f KB/s  ·  剩余 %s",
                                    done / 1048576.0, max / 1048576.0, speed / 1024.0, etaText(eta)));
                        }));
                if (cancelled) {
                    record("download", UpdateVerdict.NetError.CANCELLED, "用户取消", rel.tag);
                    return;   // 保留临时文件用于断点续传
                }
                if (total < AppIdentity.APK_MIN_BYTES || total > AppIdentity.APK_MAX_BYTES) {
                    act.runOnUiThread(dlg::dismiss);
                    safeDelete(out);
                    record("download", UpdateVerdict.NetError.IO, "BAD_SIZE " + total, rel.tag);
                    toast("更新包体积异常，已丢弃");
                    return;
                }
                String fileHash = sha256Of(out);
                PackageMeta pm = readApkMeta(out);
                UpdateVerdict.Measured m = new UpdateVerdict.Measured(
                        out.length(), fileHash, pm.certSha256, pm.versionCode, pm.versionName);
                UpdateVerdict.Verdict v = UpdateVerdict.evaluate(
                        rel, m, MainActivity.VERSION_NAME, MainActivity.VERSION_CODE,
                        AppIdentity.EXPECTED_SIGNING_CERT_SHA256);
                record("verify", v.trusted ? UpdateVerdict.NetError.NONE : UpdateVerdict.NetError.HTTP,
                        verdictSummary(v), rel.tag, v);

                act.runOnUiThread(() -> {
                    dlg.dismiss();
                    if (!v.trusted) {
                        safeDelete(out);   // 校验失败只删临时内容，当前版本完好
                        showVerificationFailure(rel, v);
                    } else {
                        saveLastKnownGood();
                        installOrGuide(out, rel, v);
                    }
                });
            } catch (java.net.SocketTimeoutException e) {
                act.runOnUiThread(dlg::dismiss);
                record("download", UpdateVerdict.NetError.TIMEOUT, "下载超时", rel.tag);
                toast("下载超时，已保留进度，可重试续传");
            } catch (Exception e) {
                act.runOnUiThread(dlg::dismiss);
                UpdateVerdict.NetError ne = UpdateVerdict.classify(e, null);
                record("download", ne, ne.name(), rel.tag);
                toast("下载失败（" + ne.name() + "），进度已保留");
            }
        }, "xqly-update-download").start();
    }

    /** 断点续传下载（Range）+ 进度/速度/ETA + 取消。返回最终文件长度。 */
    private long download(String urlStr, File out, Progress cb) throws Exception {
        long existing = out.exists() ? out.length() : 0;
        if (existing > AppIdentity.APK_MAX_BYTES) { safeDelete(out); existing = 0; }
        HttpURLConnection c = (HttpURLConnection) new URL(urlStr).openConnection();
        activeConn = c;
        c.setRequestProperty("User-Agent", "xqly-app/" + MainActivity.VERSION_NAME);
        c.setConnectTimeout(CONNECT_TIMEOUT);
        c.setReadTimeout(READ_TIMEOUT);
        c.setInstanceFollowRedirects(false);
        if (existing > 0) c.setRequestProperty("Range", "bytes=" + existing + "-");
        int code = c.getResponseCode();
        if (code == 302 || code == 301 || code == 307 || code == 308) {
            String loc = c.getHeaderField("Location");
            c.disconnect();
            if (!UpdateVerdict.downloadHostAllowed(loc)) throw new java.io.IOException("redirect to untrusted host");
            return download(loc, out, cb);   // 白名单内重定向
        }
        boolean append = (code == 206 && existing > 0);
        if (code != 200 && code != 206) { c.disconnect(); throw new java.io.IOException("HTTP " + code); }

        long partLen = c.getContentLengthLong();
        long total = append ? existing + Math.max(0, partLen) : Math.max(0, partLen);
        try (InputStream in = c.getInputStream();
             RandomAccessFile raf = new RandomAccessFile(out, "rw")) {
            if (append) raf.seek(existing); else { raf.setLength(0); }
            byte[] buf = new byte[16384];
            long done = existing, lastCb = 0;
            long start = System.currentTimeMillis();
            int n;
            while ((n = in.read(buf)) > 0) {
                if (cancelled) throw new java.io.InterruptedIOException("cancelled");
                raf.write(buf, 0, n);
                done += n;
                long now = System.currentTimeMillis();
                if (now - lastCb > 250) {
                    long elapsed = Math.max(1, now - start);
                    long speed = (done - existing) * 1000L / elapsed;
                    long remain = total > done && speed > 0 ? (total - done) * 1000L / speed : -1;
                    cb.on(done, total, speed, remain);
                    lastCb = now;
                }
            }
        } finally { c.disconnect(); activeConn = null; }
        return out.length();
    }

    // ---------------------------------------------------------------- 校验结果
    private void showVerificationFailure(UpdateVerdict.Release rel, UpdateVerdict.Verdict v) {
        StringBuilder s = new StringBuilder();
        s.append("更新包未通过校验，未安装。当前版本保持可用。\n\n失败项：\n");
        for (UpdateVerdict.Check c : v.failures()) s.append("· ").append(c.label).append("：").append(c.detail).append("\n");
        s.append("\n已删除临时下载，未改动现有内容。");
        new AlertDialog.Builder(act)
                .setTitle("校验未通过")
                .setMessage(s.toString())
                .setPositiveButton("更新中心", (d, w) -> UpdateCenter.show(act, userPrefs, v))
                .setNegativeButton("关闭", null)
                .show();
    }

    private static String verdictSummary(UpdateVerdict.Verdict v) {
        if (v.trusted) return "全部 " + v.checks.size() + " 项校验通过";
        StringBuilder s = new StringBuilder("未通过：");
        for (UpdateVerdict.Check c : v.failures()) s.append(c.key).append(" ");
        return s.toString().trim();
    }

    // ---------------------------------------------------------------- 安装 / 引导
    private void installOrGuide(File apk, UpdateVerdict.Release rel, UpdateVerdict.Verdict v) {
        if (Build.VERSION.SDK_INT >= 26 && !act.getPackageManager().canRequestPackageInstalls()) {
            // 一次性引导：记录已提示，避免每次检查反复弹窗
            boolean prompted = Boolean.parseBoolean(userPrefs.get("u.install_prompted", "false"));
            new AlertDialog.Builder(act)
                    .setTitle("系统需要授权")
                    .setMessage("Android 要求先允许「安装未知应用」才能更新。\n\n"
                            + "你也可以选择「稍后安装」，已验证的更新包会保留在本机缓存，不会丢失。")
                    .setPositiveButton(prompted ? "重新授权" : "去授权", (d, w) -> {
                        userPrefs.put("u.install_prompted", "true");
                        Intent i = new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                                Uri.parse("package:" + act.getPackageName()));
                        act.startActivity(i);
                    })
                    .setNeutralButton("查看校验结果", (d, w) -> UpdateCenter.show(act, userPrefs, v))
                    .setNegativeButton("稍后安装", (d, w) -> toast("已保留更新包，可在更新中心继续"))
                    .show();
            return;
        }
        Uri uri = Uri.parse("content://" + XFileProvider.AUTHORITY + "/cache/" + CACHE_APK);
        Intent i = new Intent(Intent.ACTION_VIEW);
        i.setDataAndType(uri, "application/vnd.android.package-archive");
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            act.startActivity(i);
            userPrefs.put("u.pending_update", rel.tag);
        } catch (Exception e) {
            // 系统限制：提供安全的文件查看，不反复弹窗
            new AlertDialog.Builder(act)
                    .setTitle("无法直接安装")
                    .setMessage("当前系统未允许应用内安装。更新包已通过全部校验并保存在本机缓存。\n\n"
                            + "可稍后在「更新中心」重试，或由维护者从官方 Releases 手动安装。")
                    .setPositiveButton("更新中心", (d, w) -> UpdateCenter.show(act, userPrefs, v))
                    .setNegativeButton("关闭", null)
                    .show();
        }
    }

    // ---------------------------------------------------------------- APK 元信息
    private static final class PackageMeta {
        String certSha256 = "";
        String versionName = "";
        long versionCode = 0;
    }

    private PackageMeta readApkMeta(File apk) {
        PackageMeta m = new PackageMeta();
        try {
            PackageManager pm = act.getPackageManager();
            int flags = Build.VERSION.SDK_INT >= 28
                    ? PackageManager.GET_SIGNING_CERTIFICATES : PackageManager.GET_SIGNATURES;
            PackageInfo pi = pm.getPackageArchiveInfo(apk.getAbsolutePath(), flags);
            if (pi == null) return m;
            m.versionName = pi.versionName == null ? "" : pi.versionName;
            if (Build.VERSION.SDK_INT >= 28) {
                m.versionCode = pi.getLongVersionCode();
                SigningInfo si = pi.signingInfo;
                if (si != null) {
                    Signature[] sigs = si.getApkContentsSigners();
                    if (sigs == null || sigs.length == 0) sigs = si.getSigningCertificateHistory();
                    if (sigs != null && sigs.length > 0) m.certSha256 = SigningCert.sha256(sigs[0].toByteArray());
                }
            } else {
                m.versionCode = pi.versionCode;
                if (pi.signatures != null && pi.signatures.length > 0)
                    m.certSha256 = SigningCert.sha256(pi.signatures[0].toByteArray());
            }
        } catch (Exception ignored) { }
        return m;
    }

    private static String sha256Of(File f) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        try (InputStream in = new java.io.FileInputStream(f)) {
            byte[] buf = new byte[16384];
            int n;
            while ((n = in.read(buf)) > 0) md.update(buf, 0, n);
        }
        StringBuilder b = new StringBuilder(64);
        for (byte x : md.digest()) b.append(String.format("%02x", x & 0xFF));
        return b.toString();
    }

    // ---------------------------------------------------------------- 记录 / 回退信息
    private void record(String phase, UpdateVerdict.NetError err, String summary, String tag) {
        record(phase, err, summary, tag, null);
    }

    private void record(String phase, UpdateVerdict.NetError err, String summary,
                        String tag, UpdateVerdict.Verdict v) {
        UpdateCenter.Record r = new UpdateCenter.Record(
                System.currentTimeMillis(), phase, err.name(), summary,
                tag == null ? "" : tag, v);
        UpdateCenter.append(userPrefs, r);
    }

    private void saveLastKnownGood() {
        // 安装前保存当前可用版本，供回退说明使用
        StringBuilder m = new StringBuilder();
        m.append("{\"version\":\"").append(MainActivity.VERSION_NAME).append("\",")
                .append("\"code\":").append(MainActivity.VERSION_CODE).append(",")
                .append("\"time\":").append(System.currentTimeMillis()).append("}");
        userPrefs.put("u.last_known_good", m.toString());
    }

    // ---------------------------------------------------------------- helpers
    private interface Progress { void on(long done, long max, long speed, long etaMs); }

    private static String etaText(long ms) {
        if (ms < 0) return "计算中";
        long s = ms / 1000;
        if (s < 60) return s + " 秒";
        return (s / 60) + " 分 " + (s % 60) + " 秒";
    }

    private static String readAll(InputStream in) throws Exception {
        java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = in.read(buf)) > 0) bos.write(buf, 0, n);
        in.close();
        return new String(bos.toByteArray(), StandardCharsets.UTF_8);
    }

    private static void safeDelete(File f) { try { if (f.exists()) f.delete(); } catch (Exception ignored) { } }

    private void toast(String s) { act.runOnUiThread(() -> Toast.makeText(act, s, Toast.LENGTH_SHORT).show()); }

    private int dp(float v) { return (int) (v * act.getResources().getDisplayMetrics().density + 0.5f); }
}
