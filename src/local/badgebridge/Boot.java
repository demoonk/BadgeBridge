package local.badgebridge;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.service.notification.NotificationListenerService;

/**
 * После перезагрузки или обновления пакета заново просим систему
 * подключить слушатель уведомлений и перерисовать значки.
 */
public class Boot extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        Store.widgets(context);
        NotificationListenerService.requestRebind(new ComponentName(context, Listener.class));
    }
}
