package local.badgebridge;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.os.ParcelFileDescriptor;
import android.database.Cursor;
import android.net.Uri;
import java.io.File;

/**
 * Отдаёт скачанный APK системному установщику через content:// URI.
 * FileProvider отсутствует в compile-time android.jar, поэтому провайдер
 * написан вручную — он нужен только для одного файла в cache.
 */
public class ApkProvider extends ContentProvider {

    static final String FILE = "badgebridge-update.apk";
    static final String AUTHORITY = "local.badgebridge.fileprovider";

    @Override
    public boolean onCreate() {
        return true;
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection,
            String[] selectionArgs, String sortOrder) {
        throw new UnsupportedOperationException("query is not supported");
    }

    @Override
    public String getType(Uri uri) {
        return "application/vnd.android.package-archive";
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        throw new UnsupportedOperationException("insert is not supported");
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        return 0;
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        return 0;
    }

    @Override
    public ParcelFileDescriptor openFile(Uri uri, String mode)
            throws java.io.FileNotFoundException {
        File apk = new File(getContext().getCacheDir(), FILE);
        if (!apk.exists()) {
            throw new IllegalStateException("Обновление не скачано");
        }
        return ParcelFileDescriptor.open(apk, ParcelFileDescriptor.MODE_READ_ONLY);
    }
}
