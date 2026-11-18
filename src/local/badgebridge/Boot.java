package local.badgebridge;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/**
 * После перезагрузки, обновления пакета или планового пробуждения заново
 * просим систему подключить слушатель уведомлений и перерисовать значки.
 */
public class Boot extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        /* Замер: насколько система задержала плановое пробуждение и был ли
           процесс запущен заново (слушатель уведомлений тогда потерян). */
        Background.measureWake(context);
        Store.scheduleKeepAlive(context);
        Store.widgets(context);
        if (Store.hasAccess(context) && !Store.connected(context)) {
            Listener.forceRebind(context);
        }
    }
}
