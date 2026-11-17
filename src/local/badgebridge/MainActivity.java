package local.badgebridge;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.ResolveInfo;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.service.notification.NotificationListenerService;
import android.view.View;
import android.view.WindowInsets;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;

public class MainActivity extends Activity {

    private static final int BG = Color.rgb(13, 22, 34);
    private static final int CARD = Color.rgb(25, 39, 55);
    private static final int INK = Color.rgb(239, 246, 252);
    private static final int MUTED = Color.rgb(162, 180, 199);
    private static final int ACCENT = Color.rgb(112, 225, 200);
    private static final String DEFAULT_TARGET = "org.telegram.messenger.web";

    private ScrollView page;
    private LinearLayout root;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private String stateKey = "";

    /** Результат проверки релизов на GitHub. */
    private String latestVersion = "";
    private String latestUrl = "";
    private String updateNote = "";
    private String updateStatus = "";
    private boolean checkingUpdates = false;

    /** Само-обновляющийся таймер: перерисовываем, если изменились счётчики. */
    private final Runnable redraw = new Runnable() {
        @Override
        public void run() {
            if (!stateKey.equals(signature())) {
                render();
            }
            handler.postDelayed(this, 2000L);
        }
    };

    @Override
    public void onCreate(Bundle bundle) {
        super.onCreate(bundle);
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);
        getWindow().getDecorView().setOnApplyWindowInsetsListener((view, insets) -> {
            view.setPadding(insets.getSystemWindowInsetLeft(), insets.getSystemWindowInsetTop(),
                    insets.getSystemWindowInsetRight(), insets.getSystemWindowInsetBottom());
            return insets.consumeSystemWindowInsets();
        });

        if (!Store.prefs(this).contains("initialized")) {
            if (getPackageManager().getLaunchIntentForPackage(DEFAULT_TARGET) != null) {
                Store.add(this, DEFAULT_TARGET);
            }
            Store.prefs(this).edit().putBoolean("initialized", true).apply();
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        Store.refresh(this);
        render();
        handler.postDelayed(redraw, 2000L);
        checkUpdates();
    }

    @Override
    public void onPause() {
        handler.removeCallbacks(redraw);
        super.onPause();
    }

    private String signature() {
        List<String> targets = new ArrayList<>(Store.targets(this));
        Collections.sort(targets);
        StringBuilder sb = new StringBuilder()
                .append(Store.hasAccess(this))
                .append(Store.connected(this));
        for (String target : targets) {
            sb.append(target).append(Store.count(this, target)).append(Store.dot(this, target));
        }
        return sb.toString();
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private TextView text(String value, int size, int color) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        view.setPadding(0, dp(5), 0, dp(5));
        return view;
    }

    private LinearLayout card() {
        GradientDrawable background = new GradientDrawable();
        background.setColor(CARD);
        background.setCornerRadius(dp(20));

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(18), dp(14), dp(18), dp(14));
        card.setBackground(background);

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.bottomMargin = dp(16);
        root.addView(card, params);
        return card;
    }

    private Button button(String value, Runnable action) {
        return button(value, action, ACCENT);
    }

    private Button button(String value, Runnable action, int color) {
        Button button = new Button(this);
        button.setText(value);
        button.setAllCaps(false);
        button.setTextColor(BG);
        button.setBackgroundTintList(ColorStateList.valueOf(color));
        button.setOnClickListener(v -> action.run());
        return button;
    }

    /**
     * Карточка обновлений — в самом верху экрана. Проверка выполняется
     * каждый раз при открытии приложения (onResume).
     */
    private void renderUpdateCard() {
        String current = Updater.currentVersion(this);
        boolean newer = !latestVersion.isEmpty() && Updater.compare(latestVersion, current) > 0;

        GradientDrawable background = new GradientDrawable();
        background.setColor(newer ? Color.rgb(44, 33, 29) : CARD);
        background.setCornerRadius(dp(20));

        LinearLayout update = new LinearLayout(this);
        update.setOrientation(LinearLayout.VERTICAL);
        update.setPadding(dp(18), dp(14), dp(18), dp(14));
        update.setBackground(background);

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.bottomMargin = dp(16);
        root.addView(update, params);

        if (newer) {
            update.addView(text("Есть обновление — " + latestVersion, 18, Color.rgb(255, 184, 150)));
            update.addView(text("У вас " + current + ". Скачаем APK с GitHub и откроем установщик, "
                    + "vivo попросит подтвердить.", 13, MUTED));
            update.addView(button("Обновиться до " + latestVersion, this::updateNow,
                    Color.rgb(245, 110, 80)));
            if (!updateStatus.isEmpty()) {
                update.addView(text(updateStatus, 12, MUTED));
            }
        } else {
            update.addView(text(latestVersion.isEmpty()
                    ? (updateNote.isEmpty() ? "Проверяю обновления на GitHub…" : updateNote)
                    : "Версия " + current + " — последняя на GitHub", 13, MUTED));
            TextView recheck = text("Проверить снова", 13, ACCENT);
            recheck.setPadding(dp(4), dp(10), dp(10), dp(10));
            recheck.setOnClickListener(v -> checkUpdates());
            update.addView(recheck);
        }
    }

    private void render() {
        int scrollY = page == null ? 0 : page.getScrollY();
        ScrollView scroll = new ScrollView(this);
        page = scroll;
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(BG);

        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(22), dp(18), dp(22), dp(24));
        scroll.addView(root);
        setContentView(scroll);

        stateKey = signature();
        scroll.post(() -> scroll.scrollTo(0, scrollY));

        TextView eyebrow = text("ЛИЧНЫЕ ЗНАЧКИ", 12, ACCENT);
        eyebrow.setLetterSpacing(0.16f);
        root.addView(eyebrow);

        TextView title = text("Уведомления\nна виду", 32, INK);
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        root.addView(title);

        root.addView(text("Отдельный значок для каждого приложения: на нём написано число "
                + "уведомлений. Нажатие открывает приложение.", 16, MUTED));

        renderUpdateCard();

        LinearLayout status = card();
        boolean connected = Store.connected(this);
        status.addView(text(connected ? "●  Уведомления подключены"
                : Store.hasAccess(this) ? "◌  Подключаем уведомления"
                : "○  Нужен доступ к уведомлениям", 17, connected ? ACCENT : INK));
        status.addView(text("Приложение проверяет только источник и наличие уведомлений. "
                + "Тексты не сохраняются и никуда не отправляются. Разрешение Android открывает "
                + "доступ ко всем уведомлениям — отслеживаются только выбранные ниже.", 13, MUTED));
        status.addView(button(connected ? "Настройки доступа" : "Разрешить доступ",
                () -> startActivity(new Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS"))));
        if (Store.hasAccess(this) && !connected) {
            status.addView(button("Переподключить", () -> {
                NotificationListenerService.requestRebind(new android.content.ComponentName(this, Listener.class));
                Store.refresh(this);
            }));
        }

        TextView heading = text("Мои приложения", 20, INK);
        heading.setTypeface(null, android.graphics.Typeface.BOLD);
        root.addView(heading);

        List<String> targets = new ArrayList<>(Store.targets(this));
        targets.sort((a, b) -> Store.label(this, a).compareToIgnoreCase(Store.label(this, b)));

        for (String pkg : targets) {
            LinearLayout item = card();

            LinearLayout row = new LinearLayout(this);
            row.setGravity(android.view.Gravity.CENTER_VERTICAL);

            ImageView preview = new ImageView(this);
            preview.setImageBitmap(Store.icon(this, pkg, Store.count(this, pkg), Store.flagged(this, pkg)));
            row.addView(preview, new LinearLayout.LayoutParams(dp(54), dp(54)));

            LinearLayout texts = new LinearLayout(this);
            texts.setOrientation(LinearLayout.VERTICAL);
            texts.setPadding(dp(14), 0, 0, 0);
            texts.addView(text(Store.label(this, pkg), 18, INK));
            texts.addView(text(connected ? Store.plural(Store.count(this, pkg))
                    : "Ожидаем доступ к уведомлениям", 13, MUTED));
            row.addView(texts, new LinearLayout.LayoutParams(-2, -1, 1.0f));
            item.addView(row);

            item.addView(button("Добавить значок на экран", () -> Store.requestPin(this, pkg)));

            LinearLayout actions = new LinearLayout(this);
            TextView check = text("Проверить значок", 13, ACCENT);
            check.setPadding(dp(4), dp(12), dp(20), dp(12));
            check.setOnClickListener(v -> {
                Store.test(this, pkg);
                Toast.makeText(this, "Значок появится на 10 секунд", Toast.LENGTH_SHORT).show();
                render();
            });
            actions.addView(check);

            TextView remove = text("Удалить", 13, MUTED);
            remove.setPadding(dp(12), dp(12), dp(12), dp(12));
            remove.setOnClickListener(v -> confirmRemove(pkg));
            actions.addView(remove);
            item.addView(actions);
        }

        root.addView(button("+ Добавить приложение", this::pick));

        root.addView(text("Как пользоваться", 18, INK));
        root.addView(text("1. Разрешите доступ к уведомлениям.\n"
                + "2. Добавьте значок выбранного приложения на экран.\n"
                + "3. По желанию уберите рядом обычную иконку.\n\n"
                + "Это виджеты 1×1: приложение можно менять долгим нажатием → Настроить. "
                + "Цифра на значке — число видимых уведомлений прямо сейчас. Постоянные служебные "
                + "уведомления не учитываются. При запрете доступа цифра исчезает.", 13, MUTED));
    }

    /** Проверка последнего релиза. Делается при открытии приложения. */
    private void checkUpdates() {
        if (checkingUpdates) return;
        checkingUpdates = true;
        new Thread(() -> {
            Updater.Release release = Updater.latest();
            checkingUpdates = false;
            handler.post(() -> {
                if (release == null) {
                    updateNote = "Не удалось связаться с GitHub — проверьте интернет";
                    latestVersion = "";
                    latestUrl = "";
                } else {
                    latestVersion = release.version;
                    latestUrl = release.url;
                    updateNote = "";
                }
                render();
            });
        }).start();
    }

    /** Скачиваем APK и открываем системный установщик. */
    private void updateNow() {
        if (latestUrl.isEmpty()) {
            Toast.makeText(this, "Сначала проверьте обновления", Toast.LENGTH_SHORT).show();
            return;
        }
        handler.removeCallbacks(redraw);
        new Thread(() -> {
            java.io.File apk = Updater.download(this, latestUrl);
            handler.post(() -> {
                if (apk == null) {
                    updateStatus = "Скачивание не удалось — проверьте интернет";
                    Toast.makeText(this, updateStatus, Toast.LENGTH_LONG).show();
                    render();
                    handler.postDelayed(redraw, 2000L);
                    return;
                }
                String result = Updater.install(this, apk);
                updateStatus = result == "ok"
                        ? "Скачан APK " + apk.length() + " Б, открыт установщик"
                        : "Скачан " + apk.length() + " Б, но: " + result;
                Toast.makeText(this, updateStatus, Toast.LENGTH_LONG).show();
                render();
                handler.postDelayed(redraw, 2000L);
            });
        }).start();
    }

    private void confirmRemove(final String pkg) {
        new AlertDialog.Builder(this)
                .setTitle("Перестать отслеживать?")
                .setMessage("Точка исчезнет. Сам значок можно убрать с рабочего стола обычным способом.")
                .setPositiveButton("Удалить", (dialog, which) -> {
                    java.util.Set<String> targets = Store.targets(this);
                    targets.remove(pkg);
                    Store.prefs(this).edit()
                            .putStringSet("targets", targets)
                            .remove("test:" + pkg)
                            .apply();
                    Store.widgets(this);
                    render();
                })
                .setNegativeButton("Отмена", null)
                .show();
    }

    private void pick() {
        handler.removeCallbacks(redraw);

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(dp(16), dp(8), dp(16), dp(8));

        EditText search = new EditText(this);
        search.setSingleLine();
        search.setHint("Название приложения");
        layout.addView(search);

        ListView list = new ListView(this);
        layout.addView(list, new LinearLayout.LayoutParams(-1, dp(380)));

        List<ResolveInfo> installed = getPackageManager().queryIntentActivities(
                new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0);
        installed.sort((a, b) -> a.loadLabel(getPackageManager()).toString()
                .compareToIgnoreCase(b.loadLabel(getPackageManager()).toString()));

        List<String> packages = new ArrayList<>();
        List<String> labels = new ArrayList<>();
        HashSet<String> seen = new HashSet<>();
        for (ResolveInfo info : installed) {
            String pkg = info.activityInfo.packageName;
            if (!pkg.equals(getPackageName()) && seen.add(pkg)) {
                packages.add(pkg);
                labels.add(info.loadLabel(getPackageManager()).toString());
            }
        }

        final List<String> shownPackages = new ArrayList<>();
        final List<String> shownLabels = new ArrayList<>();
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, shownPackages);
        list.setAdapter(adapter);

        Runnable filter = () -> {
            shownPackages.clear();
            shownLabels.clear();
            String query = search.getText().toString().toLowerCase(Locale.ROOT);
            for (int i = 0; i < packages.size(); i++) {
                if (labels.get(i).toLowerCase(Locale.ROOT).contains(query)
                        || packages.get(i).contains(query)) {
                    shownPackages.add(packages.get(i));
                    shownLabels.add(labels.get(i));
                }
            }
            adapter.notifyDataSetChanged();
        };
        filter.run();

        search.addTextChangedListener(new android.text.TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int a, int b, int c) {
            }

            @Override
            public void onTextChanged(CharSequence s, int a, int b, int c) {
                filter.run();
            }

            @Override
            public void afterTextChanged(android.text.Editable s) {
            }
        });

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Добавить приложение")
                .setView(layout)
                .setNegativeButton("Отмена", null)
                .create();

        dialog.setOnDismissListener(d -> {
            render();
            handler.postDelayed(redraw, 2000L);
        });

        list.setOnItemClickListener((parent, view, position, id) -> {
            Store.add(this, shownPackages.get(position));
            dialog.dismiss();
        });

        dialog.show();
    }
}
