package local.badgebridge;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.widget.RemoteViews;
import java.util.HashSet;
import java.util.Set;

/**
 * Хранилище целей, счётчиков и отрисовка значка-бейджа.
 * Всё живёт в SharedPreferences "bridge".
 */
public final class Store {

    /* Композиция «иконка + бейдж» целиком вписывается в ячейку виджета,
       поэтому SIZE = ICON + BADGE_R: выступ бейджа равен его радиусу.
       Замер системных значков OriginOS: иконка 206 px, бейдж Ø72 px,
       выступ 36 px -> иконка занимает 85% композиции.
       Здесь иконка = 174/200 = 87% ячейки (как было до 0.4.3),
       бейдж Ø52 сидит на углу иконки и наполовину выступает наружу. */
    private static final int SIZE = 200;
    private static final int ICON = 174;          // иконка: x 0..174, y 26..200
    private static final int ICON_TOP = 26;
    private static final int RADIUS = 50;
    private static final float BADGE_R = 26.0f;   // Ø52 в холсте -> ~65 px на экране
    private static final float BADGE_X = ICON;    // 174 — правый край иконки
    private static final float BADGE_Y = ICON_TOP; // 26 — верхний край иконки
    private static final int BADGE_COLOR = Color.rgb(245, 74, 42);
    private static final int FALLBACK_BG = Color.rgb(45, 65, 85);
    private static final int WHITE = 0xFFFFFFFF;

    private Store() {
    }

    static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences("bridge", 0);
    }

    static Set<String> targets(Context context) {
        return new HashSet<>(prefs(context).getStringSet("targets", new HashSet<>()));
    }

    static void add(Context context, String pkg) {
        Set<String> targets = targets(context);
        targets.add(pkg);
        prefs(context).edit().putStringSet("targets", targets).apply();
        refresh(context);
    }

    static String label(Context context, String pkg) {
        try {
            return context.getPackageManager()
                    .getApplicationLabel(context.getPackageManager().getApplicationInfo(pkg, 0))
                    .toString();
        } catch (Exception e) {
            return pkg.isEmpty() ? "Выбрать приложение" : pkg;
        }
    }

    static boolean hasAccess(Context context) {
        NotificationManager manager = context.getSystemService(NotificationManager.class);
        return manager.isNotificationListenerAccessGranted(new ComponentName(context, Listener.class));
    }

    static boolean connected(Context context) {
        return hasAccess(context) && Listener.current != null;
    }

    /** Число видимых уведомлений выбранного приложения прямо сейчас. */
    static int count(Context context, String pkg) {
        return connected(context) ? prefs(context).getInt("count:" + pkg, 0) : 0;
    }

    /** Тестовая точка: горит 10 секунд после «Проверить значок». */
    static boolean flagged(Context context, String pkg) {
        return prefs(context).getLong("test:" + pkg, 0L) > System.currentTimeMillis();
    }

    static boolean dot(Context context, String pkg) {
        return flagged(context, pkg) || count(context, pkg) > 0;
    }

    static String plural(int n) {
        if (n <= 0) {
            return "Новых уведомлений нет";
        }
        if (n == 1) {
            return "1 новое уведомление";
        }
        if (n < 5) {
            return n + " новых уведомления";
        }
        return n + " новых уведомлений";
    }

    /** Иконка приложения со скруглением и красным бейджем с числом. */
    static Bitmap icon(Context context, String pkg, int count, boolean flag) {
        Bitmap bitmap = Bitmap.createBitmap(SIZE, SIZE, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);

        try {
            Drawable icon = context.getPackageManager().getApplicationIcon(pkg);
            canvas.save();
            Path clip = new Path();
            clip.addRoundRect(0, ICON_TOP, ICON, SIZE, RADIUS, RADIUS, Path.Direction.CW);
            canvas.clipPath(clip);
            icon.setBounds(0, ICON_TOP, ICON, SIZE);
            icon.draw(canvas);
            canvas.restore();
        } catch (Exception e) {
            Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
            paint.setColor(FALLBACK_BG);
            canvas.drawRoundRect(0, ICON_TOP, ICON, SIZE, RADIUS, RADIUS, paint);
            paint.setColor(WHITE);
            paint.setTextSize(72.0f);
            paint.setTextAlign(Paint.Align.CENTER);
            canvas.drawText("+", ICON / 2.0f, ICON_TOP + ICON / 2.0f + 25.0f, paint);
        }

        if (count > 0 || flag) {
            Paint badge = new Paint(Paint.ANTI_ALIAS_FLAG);
            badge.setColor(BADGE_COLOR);
            canvas.drawCircle(BADGE_X, BADGE_Y, BADGE_R, badge);
            if (count > 0) {
                String text = count > 99 ? "99+" : String.valueOf(count);
                float size = text.length() == 1 ? 30.0f : (text.length() == 2 ? 24.0f : 19.0f);
                badge.setColor(WHITE);
                badge.setTextSize(size);
                badge.setTextAlign(Paint.Align.CENTER);
                canvas.drawText(text, BADGE_X, BADGE_Y + size * 0.36f, badge);
            }
        }
        return bitmap;
    }

    static void refresh(Context context) {
        Listener listener = Listener.current;
        if (listener != null) {
            listener.scan();
        } else {
            widgets(context);
        }
    }

    public static void widgets(Context context) {
        Context app = context.getApplicationContext();
        AppWidgetManager manager = AppWidgetManager.getInstance(app);
        ComponentName self = new ComponentName(app, Widgets.class);
        for (int id : manager.getAppWidgetIds(self)) {
            updateWidget(app, id);
        }
    }

    static void updateWidget(Context context, int widgetId) {
        String pkg = prefs(context).getString("widget:" + widgetId, "");
        boolean bound = targets(context).contains(pkg);
        int count = bound ? count(context, pkg) : 0;
        boolean flag = bound && flagged(context, pkg);
        String label = label(context, pkg);

        RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.widget);
        views.setImageViewBitmap(R.id.widget_icon, icon(context, pkg, count, flag));
        views.setTextViewText(R.id.widget_label, count > 0 ? label + " · " + count : label);

        String description = label;
        if (count > 0) {
            description += ", " + count + " уведомлений";
        } else if (flag) {
            description += ", есть уведомление";
        }
        views.setContentDescription(R.id.widget_icon, description);

        Intent open = pkg.isEmpty()
                ? new Intent(context, ConfigureActivity.class).putExtra("appWidgetId", widgetId)
                : new Intent(context, OpenActivity.class).putExtra("pkg", pkg);
        open.setData(Uri.parse("badgebridge://widget/" + widgetId));
        views.setOnClickPendingIntent(R.id.widget_root,
                PendingIntent.getActivity(context, widgetId, open,
                        PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT));

        AppWidgetManager.getInstance(context).updateAppWidget(widgetId, views);
    }

    static void bind(Context context, int widgetId, String pkg) {
        add(context, pkg);
        prefs(context).edit().putString("widget:" + widgetId, pkg).apply();
        updateWidget(context, widgetId);
    }

    static void test(Context context, String pkg) {
        prefs(context).edit()
                .putLong("test:" + pkg, System.currentTimeMillis() + 10_000L)
                .apply();
        widgets(context);
        new Handler(Looper.getMainLooper()).postDelayed(
                () -> widgets(context), 10_200L);
    }

    /** Попытка закрепить значок на рабочем столе. */
    static void requestPin(Activity activity, String pkg) {
        prefs(activity).edit().putString("pendingWidget", pkg).apply();
        AppWidgetManager manager = AppWidgetManager.getInstance(activity);

        if (Build.MANUFACTURER.equalsIgnoreCase("vivo")) {
            // vivo не поддерживает requestPinAppWidget, но есть собственный intent
            try {
                Uri uri = new Uri.Builder()
                        .scheme("vivo")
                        .authority("com.bbk.launcher2")
                        .appendPath("origin")
                        .appendQueryParameter("mainPkg", activity.getPackageName())
                        .appendQueryParameter("pkg", activity.getPackageName())
                        .appendQueryParameter("classname", Widgets.class.getName())
                        .appendQueryParameter("cmpType", "0")
                        .appendQueryParameter("locType", "1")
                        .build();
                Intent intent = new Intent(Intent.ACTION_VIEW, uri)
                        .setComponent(new ComponentName("com.bbk.launcher2",
                                "com.bbk.launcher2.bubblet.JumpOriginWidgetActivity"));
                activity.startActivity(intent);
            } catch (ActivityNotFoundException | SecurityException e) {
                guide(activity, pkg);
            }
            return;
        }

        if (!manager.isRequestPinAppWidgetSupported()) {
            guide(activity, pkg);
            return;
        }

        Intent result = new Intent(activity, PinResult.class)
                .putExtra("pkg", pkg)
                .setData(Uri.parse("badgebridge://pin/" + pkg + "/" + System.currentTimeMillis()));
        PendingIntent callback = PendingIntent.getBroadcast(
                activity, 0, result, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);

        if (!manager.requestPinAppWidget(new ComponentName(activity, Widgets.class), null, callback)) {
            guide(activity, pkg);
        }
    }

    static void guide(final Activity activity, String pkg) {
        new AlertDialog.Builder(activity)
                .setTitle("Значок «" + label(activity, pkg) + "»")
                .setMessage("На vivo добавление выполняется через рабочий стол:\n\n"
                        + "1. Удерживайте свободное место.\n"
                        + "2. Откройте «Комплект».\n"
                        + "3. Прокрутите вниз → «Виджеты приложений».\n"
                        + "4. Выберите «Точки уведомлений» → «Добавить виджет».\n\n"
                        + "Новый значок настроится на выбранное приложение. "
                        + "Для следующих приложений повторите эти шаги.")
                .setPositiveButton("На рабочий стол", (dialog, which) ->
                        activity.startActivity(new Intent(Intent.ACTION_MAIN)
                                .addCategory(Intent.CATEGORY_HOME)))
                .setNegativeButton("Позже", null)
                .show();
    }
}
