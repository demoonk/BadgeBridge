package local.badgebridge;

import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.Context;
import android.os.Bundle;

/**
 * Провайдер виджета 1×1. Первый созданный виджет автоматически
 * привязывается к приложению, выбранному через «Добавить значок на экран».
 */
public class Widgets extends AppWidgetProvider {

    @Override
    public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] widgetIds) {
        String pending = Store.prefs(context).getString("pendingWidget", "");
        for (int id : widgetIds) {
            String bound = Store.prefs(context).getString("widget:" + id, "");
            if (bound.isEmpty() && Store.targets(context).contains(pending)) {
                Store.bind(context, id, pending);
                Store.prefs(context).edit().remove("pendingWidget").apply();
            } else {
                Store.updateWidget(context, id);
            }
        }
        Store.refresh(context);
    }

    @Override
    public void onDeleted(Context context, int[] widgetIds) {
        for (int id : widgetIds) {
            Store.prefs(context).edit().remove("widget:" + id).apply();
        }
    }

    @Override
    public void onAppWidgetOptionsChanged(Context context, AppWidgetManager appWidgetManager,
                                         int widgetId, Bundle options) {
        Store.updateWidget(context, widgetId);
    }
}
