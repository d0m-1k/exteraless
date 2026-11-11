package org.telegram.messenger;

import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProviderInfo;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.HandlerThread;

import java.util.ArrayList;
import java.util.Map;

/**
 * Keeps home screen chat widgets in sync with the dialogs list.
 *
 * Widgets are updated by the launcher only on its own schedule (the provider declares
 * updatePeriodMillis = 1 hour), so a new message could sit unseen on the home screen for
 * a long time. Here the widgets are refreshed right after the app receives new messages
 * or marks them as read.
 */
public final class WidgetsUpdater {

    private static final long DEBOUNCE_MS = 700;
    /** Dialogs are written to SQLite asynchronously, so the first refresh may still see the old row. */
    private static final long SECOND_PASS_MS = 5000;

    private static Handler handler;
    private static Runnable refreshRunnable;
    private static Runnable secondPassRunnable;
    private static final ArrayList<Integer> pendingAccounts = new ArrayList<>();

    private WidgetsUpdater() {
    }

    public static void scheduleUpdate(int account) {
        final Context context = ApplicationLoader.applicationContext;
        if (context == null) {
            return;
        }
        synchronized (WidgetsUpdater.class) {
            if (!pendingAccounts.contains(account)) {
                pendingAccounts.add(account);
            }
            if (handler == null) {
                HandlerThread thread = new HandlerThread("exteraless-widgets");
                thread.start();
                handler = new Handler(thread.getLooper());
            }
            if (refreshRunnable != null) {
                handler.removeCallbacks(refreshRunnable);
            }
            refreshRunnable = () -> {
                final int[] accounts;
                synchronized (WidgetsUpdater.class) {
                    accounts = new int[pendingAccounts.size()];
                    for (int a = 0, n = pendingAccounts.size(); a < n; a++) {
                        accounts[a] = pendingAccounts.get(a);
                    }
                    pendingAccounts.clear();
                }
                for (int accountId : accounts) {
                    updateWidgets(context, accountId);
                }
                if (secondPassRunnable != null) {
                    handler.removeCallbacks(secondPassRunnable);
                }
                secondPassRunnable = () -> {
                    for (int accountId : accounts) {
                        updateWidgets(context, accountId);
                    }
                };
                handler.postDelayed(secondPassRunnable, SECOND_PASS_MS);
            };
            handler.postDelayed(refreshRunnable, DEBOUNCE_MS);
        }
    }

    private static void updateWidgets(Context context, int account) {
        try {
            SharedPreferences preferences = context.getSharedPreferences("shortcut_widget", Context.MODE_PRIVATE);
            AppWidgetManager manager = AppWidgetManager.getInstance(context);
            for (Map.Entry<String, ?> entry : preferences.getAll().entrySet()) {
                String key = entry.getKey();
                if (!key.startsWith("account") || !(entry.getValue() instanceof Integer)) {
                    continue;
                }
                if ((Integer) entry.getValue() != account) {
                    continue;
                }
                int widgetId;
                try {
                    widgetId = Integer.parseInt(key.substring("account".length()));
                } catch (NumberFormatException e) {
                    continue;
                }
                if (preferences.getBoolean("deleted" + widgetId, false)) {
                    continue;
                }
                AppWidgetProviderInfo info = manager.getAppWidgetInfo(widgetId);
                if (info == null || info.provider == null || !ChatsWidgetProvider.class.getName().equals(info.provider.getClassName())) {
                    continue;
                }
                ChatsWidgetProvider.updateWidget(context, manager, widgetId);
            }
        } catch (Throwable e) {
            FileLog.e(e);
        }
    }
}
