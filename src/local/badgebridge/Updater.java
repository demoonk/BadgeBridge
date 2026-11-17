package local.badgebridge;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.net.Uri;
import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Автообновление: смотрим последний релиз на GitHub, скачиваем APK и
 * передаём его системному установщику. Android (и vivo) в любом случае
 * покажет подтверждение — без него установить нельзя.
 */
public class Updater {

    private static final String API =
            "https://api.github.com/repos/demoonk/BadgeBridge/releases/latest";

    static class Release {
        String version;
        String url;
        long size;
    }

    static String currentVersion(Context context) {
        try {
            PackageInfo info = context.getPackageManager()
                    .getPackageInfo(context.getPackageName(), 0);
            return info.versionName == null ? "0.0.0" : info.versionName;
        } catch (Exception e) {
            return "0.0.0";
        }
    }

    /** Сравнение версий вида 0.4.6. >0 если a новее b. */
    static int compare(String a, String b) {
        String[] pa = a.split("\\.");
        String[] pb = b.split("\\.");
        int n = Math.max(pa.length, pb.length);
        for (int i = 0; i < n; i++) {
            int va = i < pa.length ? parseInt(pa[i]) : 0;
            int vb = i < pb.length ? parseInt(pb[i]) : 0;
            if (va != vb) return va - vb;
        }
        return 0;
    }

    private static int parseInt(String value) {
        int result = 0;
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c < '0' || c > '9') break;
            result = result * 10 + (c - '0');
        }
        return result;
    }

    static Release latest() {
        Release release = new Release();
        try {
            HttpURLConnection conn = (HttpURLConnection) new URL(API).openConnection();
            conn.setRequestProperty("User-Agent", "BadgeBridge " + "updater");
            conn.setRequestProperty("Accept", "application/json");
            conn.setConnectTimeout(8000);
            conn.setReadTimeout(10000);
            if (conn.getResponseCode() != 200) return null;

            StringBuilder body = new StringBuilder();
            byte[] buffer = new byte[4096];
            InputStream in = conn.getInputStream();
            int read;
            while ((read = in.read(buffer)) > 0) {
                for (int i = 0; i < read; i++) body.append((char) buffer[i]);
            }
            in.close();

            Matcher tag = Pattern.compile("\"tag_name\"\\s*:\\s*\"v?([0-9.]+)\"").matcher(body);
            if (!tag.find()) return null;
            release.version = tag.group(1);

            Matcher asset = Pattern.compile(
                    "\"browser_download_url\"\\s*:\\s*\"([^\"]+\\.apk)\"").matcher(body);
            if (!asset.find()) return null;
            release.url = asset.group(1);
            return release;
        } catch (Exception e) {
            return null;
        }
    }

    static File download(Context context, String url) {
        File out = new File(context.getCacheDir(), ApkProvider.FILE);
        for (int attempt = 0; attempt < 3; attempt++) {
            try {
                HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
                conn.setRequestProperty("User-Agent", "BadgeBridge updater");
                conn.setConnectTimeout(8000);
                conn.setReadTimeout(15000);
                conn.setFollowRedirects(true);
                if (conn.getResponseCode() != 200) continue;

                InputStream in = conn.getInputStream();
                OutputStream os = new java.io.FileOutputStream(out);
                byte[] buffer = new byte[8192];
                int read;
                while ((read = in.read(buffer)) > 0) os.write(buffer, 0, read);
                os.flush();
                os.close();
                in.close();
                if (out.length() > 1000) return out;
            } catch (Exception e) {
                // попробуем ещё раз
            }
        }
        return null;
    }

    /** Открывает системный установщик для скачанного APK. */
    static String install(Context context, File apk) {
        try {
            Uri uri = Uri.parse("content://" + ApkProvider.AUTHORITY + "/" + ApkProvider.FILE);
            Intent intent = new Intent(Intent.ACTION_INSTALL_PACKAGE);
            intent.setData(uri);
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            context.startActivity(intent);
            return "ok";
        } catch (Exception e) {
            try {
                Intent fallback = new Intent(Intent.ACTION_VIEW, Uri.parse(API));
                fallback.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(fallback);
                return "установщик недоступен (" + e + "), открыл страницу релиза";
            } catch (Exception ignored) {
                return "установщик не открылся: " + e;
            }
        }
    }
}
