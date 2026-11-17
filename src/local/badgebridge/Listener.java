package local.badgebridge;

import android.content.ComponentName;
import android.content.SharedPreferences;
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

    static volatile Listener current;

    @Override
    public void onListenerConnected() {
        current = this;
        scan();
    }

    @Override
    public void onListenerDisconnected() {
        current = null;
        Store.widgets(this);
        requestRebind(new ComponentName(this, Listener.class));
    }

    @Override
    public void onDestroy() {
        if (current == this) {
            current = null;
        }
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
            return;
        }
        if (active == null) {
            return;
        }

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
            // FLAG_SKIP (512) и FLAG_AUTOMATIC (2) — служебные, их не считаем
            int flags = notification.getNotification().flags;
            if ((flags & 512) == 0 && (flags & 2) == 0) {
                counts.put(pkg, counts.getOrDefault(pkg, 0) + 1);
            }
        }

        SharedPreferences.Editor editor = Store.prefs(this).edit();
        for (Map.Entry<String, Integer> entry : counts.entrySet()) {
            editor.putInt("count:" + entry.getKey(), entry.getValue());
        }
        editor.putLong("updated", System.currentTimeMillis()).apply();
        Store.widgets(this);
    }
}
