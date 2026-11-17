package local.badgebridge;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/**
 * Ответ лаунчера о том, что виджет был закреплён: привязываем его к цели.
 */
public class PinResult extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        int widgetId = intent.getIntExtra("appWidgetId", -1);
        String pkg = intent.getStringExtra("pkg");
        if (widgetId == -1 || pkg == null || !Store.targets(context).contains(pkg)) {
            return;
        }
        Store.bind(context, widgetId, pkg);
    }
}
