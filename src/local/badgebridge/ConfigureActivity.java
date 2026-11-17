package local.badgebridge;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Диалог выбора приложения для уже закреплённого значка
 * (долгое нажатие на виджет → «Настроить»).
 */
public class ConfigureActivity extends Activity {

    @Override
    public void onCreate(Bundle bundle) {
        super.onCreate(bundle);
        setResult(0);

        final int widgetId = getIntent().getIntExtra("appWidgetId", -1);
        if (widgetId == -1) {
            finish();
            return;
        }

        List<String> targets = new ArrayList<>(Store.targets(this));
        targets.sort((a, b) -> Store.label(this, a).compareToIgnoreCase(Store.label(this, b)));

        if (targets.isEmpty()) {
            new AlertDialog.Builder(this)
                    .setTitle("Сначала добавьте приложение")
                    .setMessage("Откройте «Точки уведомлений» и выберите приложения для значков.")
                    .setPositiveButton("Открыть", (dialog, which) -> {
                        startActivity(new Intent(this, MainActivity.class));
                        finish();
                    })
                    .setOnCancelListener(dialog -> finish())
                    .show();
            return;
        }

        String[] names = new String[targets.size()];
        for (int i = 0; i < targets.size(); i++) {
            names[i] = Store.label(this, targets.get(i));
        }

        new AlertDialog.Builder(this)
                .setTitle("Приложение для значка")
                .setItems(names, (dialog, which) -> {
                    Store.bind(this, widgetId, targets.get(which));
                    setResult(-1, new Intent().putExtra("appWidgetId", widgetId));
                    finish();
                })
                .setOnCancelListener(dialog -> finish())
                .show();
    }
}
