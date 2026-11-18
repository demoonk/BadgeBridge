package local.badgebridge;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.PowerManager;
import android.provider.Settings;

/**
 * Проверка, разрешена ли приложению фоновая работа.
 *
 * vivo/OriginOS не отдаёт переключатель «Фоновое ограничение» наружу,
 * поэтому приложение измеряет последствия само:
 *   1. насколько поздно приходит плановое пробуждение (AlarmManager);
 *   2. запускается ли процесс «с нуля» (слушатель уведомлений уже потерян);
 *   3. включена ли глобальная экономия энергии в системе.
 * Если признаки ограничения есть — показываем карточку и открываем
 * страницу настроек, где можно разрешить фоновую работу.
 */
public final class Background {

    /* Пробуждение позже, чем на 1.5 минуты, = система притормаживает приложение. */
    private static final long LATE_MS = 90_000L;
    /* Замер считается значимым, если он свежий. */
    private static final long FRESH_MS = 60L * 60_000L;

    private Background() {
    }

    /** Вызывается при каждом пробуждении по будильнику (Boot). */
    static void measureWake(Context context) {
        SharedPreferences prefs = Store.prefs(context);
        long now = System.currentTimeMillis();
        long due = prefs.getLong("keepAlive", 0L);
        if (due <= 0L) {
            return;
        }
        long late = now - due;
        SharedPreferences.Editor editor = prefs.edit();
        if (late > LATE_MS) {
            editor.putLong("lateWake", late);
            editor.putLong("lateWakeAt", now);
            editor.putInt("lateWakes", prefs.getInt("lateWakes", 0) + 1);
        } else if (late > -60_000L) {
            /* Пробуждение произошло или должно произойти в ближайшие минуту —
               ограничения нет. */
            editor.putLong("onTimeWake", now);
            /* Ограничение снято — разрешим подсказку снова, если оно вернётся. */
            if (prefs.getBoolean("bgGuideShown", false)) {
                editor.putBoolean("bgGuideShown", false);
            }
        }
        if (Listener.current == null) {
            editor.putInt("coldWakes", prefs.getInt("coldWakes", 0) + 1);
        }
        editor.apply();
    }

    private static int secure(Context context, String key) {
        try {
            return Settings.Secure.getInt(context.getContentResolver(), key, 0);
        } catch (Exception e) {
            return 0;
        }
    }

    /** Приложение внесено в список «не оптимизировать батарею». */
    static boolean exemptFromOptimization(Context context) {
        try {
            PowerManager power = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
            return power != null && power.isIgnoringBatteryOptimizations(context.getPackageName());
        } catch (Exception e) {
            return false;
        }
    }

    /** OriginOS: глобальная приостановка приложений ради экономии энергии. */
    static boolean systemPowerSave(Context context) {
        return secure(context, "power_save_ap_status") == 1
                || secure(context, "sup_power_save_type") > 0;
    }

    /** Есть прямые доказательства, что систему приложение останавливает. */
    static boolean restricted(Context context) {
        SharedPreferences prefs = Store.prefs(context);
        long lateAt = prefs.getLong("lateWakeAt", 0L);
        boolean fresh = lateAt > 0L && System.currentTimeMillis() - lateAt < FRESH_MS;
        boolean late = fresh && prefs.getLong("lateWake", 0L) > LATE_MS;
        boolean cold = prefs.getInt("coldWakes", 0) > 0;
        return late || cold;
    }

    /** "restricted" — точно ограничено, "check" — стоит проверить, "ok" — всё хорошо. */
    static String status(Context context) {
        if (restricted(context)) {
            return "restricted";
        }
        long onTime = Store.prefs(context).getLong("onTimeWake", 0L);
        if (onTime > 0L && System.currentTimeMillis() - onTime < FRESH_MS) {
            return "ok";
        }
        return !exemptFromOptimization(context) || systemPowerSave(context) ? "check" : "ok";
    }

    static String delayText(Context context) {
        long late = Store.prefs(context).getLong("lateWake", 0L);
        if (late <= 0L) {
            return "";
        }
        long minutes = late / 60_000L;
        long seconds = (late % 60_000L) / 1000L;
        if (minutes == 0L) {
            return seconds + " с";
        }
        return minutes + " мин " + seconds + " с";
    }

    /** Страница приложения: там переключатель «Приостанавливать работу приложения…». */
    static boolean openAppSettings(Context context) {
        Uri target = Uri.fromParts("package", context.getPackageName(), null);
        try {
            context.startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                    .setData(target));
            return true;
        } catch (Exception e) {
            try {
                context.startActivity(new Intent("android.settings.APP_SETTINGS").setData(target));
                return true;
            } catch (Exception ignored) {
                return false;
            }
        }
    }

    /** Системный диалог «не оптимизировать работу в фоне» для этого приложения. */
    static boolean requestIgnoreOptimization(Context context) {
        try {
            context.startActivity(new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                    .setData(Uri.fromParts("package", context.getPackageName(), null)));
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /** Страница «Батарея» OriginOS, откуда открывается «Фоновое управление питанием». */
    static boolean openBatterySettings(Context context) {
        try {
            context.startActivity(new Intent()
                    .setComponent(new ComponentName("com.iqoo.powersaving",
                            "com.iqoo.powersaving.PowerSavingManagerActivity")));
            return true;
        } catch (Exception e) {
            try {
                context.startActivity(new Intent(Settings.ACTION_BATTERY_SAVER_SETTINGS));
                return true;
            } catch (Exception ignored) {
                return false;
            }
        }
    }
}
