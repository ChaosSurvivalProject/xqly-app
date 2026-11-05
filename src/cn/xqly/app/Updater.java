package cn.xqly.app;

import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/**
 * 自动更新：拉取 GitHub Releases latest，比对版本号，
 * 发现新版本后下载 APK 并通过 XFileProvider 拉起系统安装。
 */
public class Updater {

    public static final String REPO = "ChaosSurvivalProject/xqly-app";
    private static final String API = "https://api.github.com/repos/" + REPO + "/releases/latest";

    private final MainActivity act;

    public Updater(MainActivity a) { act = a; }

    /** 检查更新；verbose=true 时「无更新」也会提示，false 为静默（启动自动检查用） */
    public void checkForUpdates(boolean verbose) {
        new Thread(() -> {
            try {
                JSONObject rel = fetchLatest();
                if (rel == null) {
                    if (verbose) toast("暂时无法获取更新信息");
                    return;
                }
                String tag = rel.optString("tag_name", "");
                String name = rel.optString("name", tag);
                String body = rel.optString("body", "");
                String apkUrl = pickApk(rel);
                if (apkUrl == null) {
                    if (verbose) toast("未找到可用的更新包");
                    return;
                }
                int cmp = compareVersions(tag, MainActivity.VERSION_NAME);
                MainActivity.VersionResult info = new MainActivity.VersionResult(tag, name, body, apkUrl, cmp);
                final MainActivity.VersionResult finalInfo = info;
                act.runOnUiThread(() -> {
                    if (finalInfo.cmp > 0) showUpdateDialog(finalInfo);
                    else if (verbose) toast("已是最新版本 v" + MainActivity.VERSION_NAME);
                });
            } catch (Exception e) {
                if (verbose) toast("检查更新失败：" + e.getMessage());
            }
        }).start();
    }

    private JSONObject fetchLatest() throws Exception {
        URL u = new URL(API);
        HttpURLConnection c = (HttpURLConnection) u.openConnection();
        c.setRequestProperty("User-Agent", "xqly-app/" + MainActivity.VERSION_NAME);
        c.setRequestProperty("Accept", "application/vnd.github+json");
        c.setConnectTimeout(15000);
        c.setReadTimeout(20000);
        c.setInstanceFollowRedirects(true);
        c.connect();
        int code = c.getResponseCode();
        if (code != 200) { c.disconnect(); return null; }
        String json = readAll(c.getInputStream());
        c.disconnect();
        return new JSONObject(json);
    }

    private String pickApk(JSONObject rel) {
        JSONArray arr = rel.optJSONArray("assets");
        if (arr == null) return null;
        // 优先取与版本号对应的 apk（名称含 vX.Y.Z），否则取任一 .apk
        String fallback = null;
        String tag = rel.optString("tag_name", "").replaceAll("^v", "");
        for (int i = 0; i < arr.length(); i++) {
            JSONObject a = arr.optJSONObject(i);
            if (a == null) continue;
            String n = a.optString("name", "");
            if (!n.endsWith(".apk")) continue;
            if (fallback == null) fallback = a.optString("browser_download_url", null);
            if (n.contains(tag) || n.contains("xqly-app-v")) {
                return a.optString("browser_download_url", null);
            }
        }
        return fallback;
    }

    private void showUpdateDialog(MainActivity.VersionResult info) {
        AlertDialog.Builder b = new AlertDialog.Builder(act);
        b.setTitle("发现新版本 v" + info.tag);
        String msg = "";
        if (info.body != null && !info.body.trim().isEmpty()) {
            msg = info.body.replaceAll("#+", "").trim() + "\n\n";
        }
        msg += "当前版本：v" + MainActivity.VERSION_NAME;
        b.setMessage(msg);
        b.setPositiveButton("下载并安装", (d, w) -> downloadAndInstall(info.apkUrl));
        b.setNegativeButton("稍后", null);
        b.setCancelable(true);
        b.show();
    }

    private void downloadAndInstall(String apkUrl) {
        // Android 8+ 需要「安装未知应用」授权
        if (Build.VERSION.SDK_INT >= 26 && !act.getPackageManager().canRequestPackageInstalls()) {
            Intent i = new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:" + act.getPackageName()));
            act.startActivity(i);
            act.toast("请允许「星穹旅驿」安装未知应用后，再次点击更新");
            return;
        }
        ProgressDialog pd = new ProgressDialog(act);
        pd.setMessage("正在下载更新…");
        pd.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
        pd.setCancelable(false);
        pd.setMax(100);
        pd.show();

        new Thread(() -> {
            try {
                File out = new File(act.getCacheDir(), "update.apk");
                long total = download(apkUrl, out, (done, max) -> act.runOnUiThread(() -> {
                    if (max > 0) {
                        pd.setMax(100);
                        pd.setProgress((int) (done * 100 / max));
                        pd.setMessage("正在下载更新… " + (done * 100 / max) + "%");
                    }
                }));
                act.runOnUiThread(() -> {
                    pd.dismiss();
                    if (total > 0) installApk(out);
                    else act.toast("下载失败：文件为空");
                });
            } catch (Exception e) {
                act.runOnUiThread(() -> {
                    pd.dismiss();
                    act.toast("下载失败：" + e.getMessage());
                });
            }
        }).start();
    }

    private long download(String urlStr, File out, Progress cb) throws Exception {
        URL u = new URL(urlStr);
        HttpURLConnection c = (HttpURLConnection) u.openConnection();
        c.setRequestProperty("User-Agent", "xqly-app/" + MainActivity.VERSION_NAME);
        c.setConnectTimeout(15000);
        c.setReadTimeout(30000);
        c.setInstanceFollowRedirects(true);
        c.connect();
        int code = c.getResponseCode();
        if (code != 200 && code != 206) { c.disconnect(); throw new Exception("HTTP " + code); }
        long total = c.getContentLength();
        InputStream in = c.getInputStream();
        FileOutputStream fos = new FileOutputStream(out);
        byte[] buf = new byte[16384];
        long done = 0;
        int n;
        while ((n = in.read(buf)) > 0) {
            fos.write(buf, 0, n);
            done += n;
            if (total > 0 && (done % (256 * 1024) < buf.length)) cb.on(done, total);
        }
        fos.close();
        in.close();
        c.disconnect();
        return done;
    }

    private void installApk(File f) {
        if (!f.exists() || f.length() == 0) { act.toast("安装包缺失"); return; }
        Uri uri = Uri.parse("content://" + XFileProvider.AUTHORITY + "/cache/" + f.getName());
        Intent i = new Intent(Intent.ACTION_VIEW);
        i.setDataAndType(uri, "application/vnd.android.package-archive");
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            act.startActivity(i);
        } catch (Exception e) {
            act.toast("无法启动安装：" + e.getMessage());
        }
    }

    private static String readAll(InputStream in) throws Exception {
        java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = in.read(buf)) > 0) bos.write(buf, 0, n);
        in.close();
        return new String(bos.toByteArray(), StandardCharsets.UTF_8);
    }

    private void toast(String s) { act.runOnUiThread(() -> Toast.makeText(act, s, Toast.LENGTH_SHORT).show()); }

    interface Progress { void on(long done, long max); }

    /** 版本号比较：a > b 返回 1，等于返回 0，否则 -1（按 . 分段比较整数） */
    static int compareVersions(String a, String b) {
        int[] va = parse(a), vb = parse(b);
        int len = Math.max(va.length, vb.length);
        for (int i = 0; i < len; i++) {
            int x = i < va.length ? va[i] : 0;
            int y = i < vb.length ? vb[i] : 0;
            if (x != y) return x > y ? 1 : -1;
        }
        return 0;
    }

    private static int[] parse(String v) {
        if (v == null) return new int[]{0};
        String s = v.trim().replaceAll("^v", "").replaceAll("[^0-9.].*$", "");
        String[] parts = s.split("\\.");
        int[] out = new int[parts.length];
        for (int i = 0; i < parts.length; i++) {
            try { out[i] = Integer.parseInt(parts[i]); } catch (Exception e) { out[i] = 0; }
        }
        return out;
    }
}
