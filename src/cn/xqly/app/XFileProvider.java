package cn.xqly.app;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;

import java.io.File;
import java.io.FileNotFoundException;

/**
 * 极简 FileProvider：仅用于把应用私有缓存目录里的 APK 暴露给系统安装器。
 * 路径约定：content://cn.xqly.app.fileprovider/cache/<name>  ->  getCacheDir()/<name>
 * 不读取任何外部配置，足够支撑「下载 APK → 拉起安装」这一条链路。
 */
public class XFileProvider extends ContentProvider {

    public static final String AUTHORITY = "cn.xqly.app.fileprovider";

    @Override public boolean onCreate() { return true; }

    @Override public String getType(Uri uri) {
        return "application/vnd.android.package-archive";
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder) {
        File f = resolve(uri);
        MatrixCursor c = new MatrixCursor(new String[]{OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE});
        if (f != null && f.exists()) {
            c.addRow(new Object[]{f.getName(), f.length()});
        }
        return c;
    }

    @Override public Uri insert(Uri uri, ContentValues values) { return null; }
    @Override public int delete(Uri uri, String selection, String[] selectionArgs) { return 0; }
    @Override public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) { return 0; }

    private File resolve(Uri uri) {
        String seg = uri.getLastPathSegment();
        if (seg == null) return null;
        // 简单防目录穿越
        if (seg.contains("/") || seg.contains("..")) return null;
        return new File(getContext().getCacheDir(), seg);
    }

    @Override
    public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        File f = resolve(uri);
        if (f == null || !f.exists()) throw new FileNotFoundException(uri.toString());
        return ParcelFileDescriptor.open(f, ParcelFileDescriptor.MODE_READ_ONLY);
    }
}
