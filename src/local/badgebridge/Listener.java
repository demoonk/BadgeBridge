package local.badgebridge;

import android.content.ComponentName;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.os.Process;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Считает активные уведомления для выбранных приложений.
 * Тексты уведомлений не сохраняются — используется только пакет и флаги.
 */
public class Listener extends NotificationListenerService {

    /* Флаги служебных записей, которые в счёт не идут:
       512  — GROUP_SUMMARY: системная «шапка» склеенной группы;
       2    — ONGOING_EVENT: постоянные (фоновые) уведомления;
       1024 — FILTERED: отфильтрованные/отложенные, их в ленте нет. */
    private static final int GROUP_SUMMARY = 512;
    private static final int ONGOING_EVENT = 2;
    private static final int FILTERED = 1024;

    /* Раз в 45 секунд пересчитываем ленту, даже если система не прислала событий. */
    private static final long RESCAN_MS = 45_000L;

    static volatile Listener current;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable rescan = new Runnable() {
        @Override
        public void run() {
            if (current == Listener.this) {
                scan();
                handler.postDelayed(this, RESCAN_MS);
            }
        }
    };

    /**
     * vivo/OriginOS не переподключает слушателя, когда процесс приложения был
     * убит оптимизатором: система считает, что связь уже есть. Отвязка и
     * повторная привязка заставляют сервис подключиться заново.
     */
    static void forceRebind(Context context) {
        ComponentName name = new ComponentName(context, Listener.class);
        try {
            requestUnbind(name);
        } catch (Exception e) {
            // уже отвязан — не важно
        }
        try {
            requestRebind(name);
        } catch (Exception e) {
            // доступ не подтверждён — подключимся при следующем разрешении
        }
    }

    @Override
    public void onListenerConnected() {
        current = this;
        scan();
        handler.removeCallbacks(rescan);
        handler.postDelayed(rescan, RESCAN_MS);
    }

    @Override
    public void onListenerDisconnected() {
        if (current == this) {
            current = null;
        }
        handler.removeCallbacks(rescan);
        Store.widgets(this);
        forceRebind(this);
    }

    @Override
    public void onDestroy() {
        if (current == this) {
            current = null;
        }
        handler.removeCallbacks(rescan);
        Store.widgets(this);
        super.onDestroy();
    }

    @Override
    public void onNotificationPosted(StatusBarNotification notification) {
        scan();
    }

    @Override
    public void onNotificationRemoved(StatusBarNotification notification) {
        scan();
    }

    public synchronized void scan() {
        Set<String> targets = Store.targets(this);
        Map<String, Integer> counts = new HashMap<>();
        for (String target : targets) {
            counts.put(target, 0);
        }

        StatusBarNotification[] active;
        try {
            active = getActiveNotifications();
        } catch (SecurityException e) {
            current = null;
            Store.widgets(this);
            forceRebind(this);
            return;
        }
        if (active == null) {
            return;
        }

        /* Склеенные группы: система добавляет «шапку» группы (GROUP_SUMMARY), а сами
           уведомления остаются отдельными записями — их и считаем. */
        for (StatusBarNotification notification : active) {
            String pkg = notification.getPackageName();
            if (!targets.contains(pkg)) {
                continue;
            }
            try {
                if (!notification.getUser().equals(Process.myUserHandle())) {
                    continue;
                }
            } catch (Exception e) {
                // не удалось проверить пользователя — считаем уведомление своим
            }

            int flags = notification.getNotification().flags;
            if ((flags & (GROUP_SUMMARY | ONGOING_EVENT | FILTERED)) != 0) {
                continue;
            }

            counts.put(pkg, counts.getOrDefault(pkg, 0) + 1);
        }

        SharedPreferences.Editor editor = Store.prefs(this).edit();
        for (Map.Entry<String, Integer> entry : counts.entrySet()) {
            editor.putInt("count:" + entry.getKey(), entry.getValue());
        }
        editor.putLong("updated", System.currentTimeMillis()).apply();
        Store.widgets(this);
    }
}
