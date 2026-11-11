package org.telegram.messenger;

import android.content.Context;
import android.content.SharedPreferences;
import android.text.TextUtils;

import org.telegram.tgnet.ConnectionsManager;

/**
 * Per-account choice of the Telegram backend: production DCs, the test DCs, or a custom server.
 *
 * The choice is keyed by the account slot and survives logout, so the same slot can be pointed at
 * another server before signing in again. Switching between production and test invalidates the
 * existing authorization (the auth key lives on the old backend), so the UI signs the account out
 * first and lets {@link #consumePending()} replay the choice on the login screen.
 */
public final class ServerConfig {

    public static final int MODE_PROD = 0;
    public static final int MODE_TEST = 1;
    public static final int MODE_CUSTOM = 2;

    public static final int TRANSPORT_ABRIDGED = 0;
    public static final int TRANSPORT_INTERMEDIATE = 1;
    public static final int TRANSPORT_PADDED = 2;
    public static final int TRANSPORT_FULL = 3;

    private static final String PREFS = "exteraless_server";
    private static final long PENDING_TTL_MS = 10 * 60 * 1000L;

    private ServerConfig() {
    }

    private static SharedPreferences prefs() {
        Context context = ApplicationLoader.applicationContext;
        if (context == null) {
            return null;
        }
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public static int getMode(int account) {
        SharedPreferences preferences = prefs();
        return preferences == null ? MODE_PROD : preferences.getInt("mode" + account, MODE_PROD);
    }

    public static void setMode(int account, int mode) {
        SharedPreferences preferences = prefs();
        if (preferences != null) {
            preferences.edit().putInt("mode" + account, mode).apply();
        }
    }

    public static String getHost(int account) {
        SharedPreferences preferences = prefs();
        return preferences == null ? "" : preferences.getString("host" + account, "");
    }

    public static int getPort(int account) {
        SharedPreferences preferences = prefs();
        return preferences == null ? 443 : preferences.getInt("port" + account, 443);
    }

    public static void setCustom(int account, String host, int port) {
        SharedPreferences preferences = prefs();
        if (preferences != null) {
            preferences.edit()
                    .putString("host" + account, host)
                    .putInt("port" + account, port)
                    .apply();
        }
    }

    public static String getDisplay(int account) {
        switch (getMode(account)) {
            case MODE_TEST:
                return "Test";
            case MODE_CUSTOM: {
                String host = getHost(account);
                return TextUtils.isEmpty(host) ? "Custom" : host + ":" + getPort(account);
            }
            default:
                return "Prod";
        }
    }

    public static int getTransport(int account) {
        SharedPreferences preferences = prefs();
        return preferences == null ? TRANSPORT_ABRIDGED : preferences.getInt("transport" + account, TRANSPORT_ABRIDGED);
    }

    public static void setTransport(int account, int transport) {
        SharedPreferences preferences = prefs();
        if (preferences != null) {
            preferences.edit().putInt("transport" + account, transport).apply();
        }
        applyTransport(ConnectionsManager.getInstance(account), account);
        // Drop live connections so the new transport takes effect immediately instead of only on
        // the next reconnect.
        for (int dcId = 1; dcId <= 5; dcId++) {
            ConnectionsManager.getInstance(account).discardConnection(dcId, ConnectionsManager.ConnectionTypeGeneric);
        }
    }

    public static String getTransportName(int transport) {
        switch (transport) {
            case TRANSPORT_INTERMEDIATE:
                return LocaleController.getString(R.string.TransportIntermediate);
            case TRANSPORT_PADDED:
                return LocaleController.getString(R.string.TransportPadded);
            case TRANSPORT_FULL:
                return LocaleController.getString(R.string.TransportFull);
            default:
                return LocaleController.getString(R.string.TransportAbridged);
        }
    }

    /** Re-applies the stored transport, e.g. when the account's native connection is (re)created. */
    public static void applyTransport(int account) {
        applyTransport(ConnectionsManager.getInstance(account), account);
    }

    public static void applyTransport(ConnectionsManager connectionsManager, int account) {
        connectionsManager.setTransport(getTransport(account));
    }

    /** Applies the stored choice to the native connection of the given account. */
    public static void apply(int account) {
        SharedPreferences preferences = prefs();
        ConnectionsManager connectionsManager = ConnectionsManager.getInstance(account);
        final int mode = getMode(account);
        applyTransport(connectionsManager, account);
        final boolean wantTest = mode == MODE_TEST;
        boolean wasCustom = preferences != null && preferences.getBoolean("custom" + account, false);
        if (connectionsManager.isTestBackend() != wantTest) {
            connectionsManager.switchBackend(false);
            wasCustom = false;
        }
        if (mode == MODE_CUSTOM) {
            applyCustom(connectionsManager, account);
            if (preferences != null) {
                preferences.edit().putBoolean("custom" + account, true).apply();
            }
        } else if (wasCustom) {
            connectionsManager.clearCustomServer();
            if (preferences != null) {
                preferences.edit().putBoolean("custom" + account, false).apply();
            }
        }
    }

    /** Re-applies the custom address, e.g. after the server pushed a new config with official DC IPs. */
    public static void applyCustom(int account) {
        applyCustom(ConnectionsManager.getInstance(account), account);
    }

    public static void applyCustom(ConnectionsManager connectionsManager, int account) {
        String host = getHost(account);
        int port = getPort(account);
        if (TextUtils.isEmpty(host) || port <= 0 || port > 65535) {
            return;
        }
        connectionsManager.setCustomServer(host, port);
    }

    public static void setPending(int mode) {
        SharedPreferences preferences = prefs();
        if (preferences != null) {
            preferences.edit()
                    .putInt("pendingMode", mode)
                    .putLong("pendingTime", System.currentTimeMillis())
                    .apply();
        }
    }

    /** Returns the backend chosen right before logout, or -1 when there is nothing fresh to apply. */
    public static int consumePending() {
        SharedPreferences preferences = prefs();
        if (preferences == null) {
            return -1;
        }
        long time = preferences.getLong("pendingTime", 0);
        int mode = preferences.getInt("pendingMode", -1);
        preferences.edit().remove("pendingMode").remove("pendingTime").apply();
        if (mode < 0 || System.currentTimeMillis() - time > PENDING_TTL_MS) {
            return -1;
        }
        return mode;
    }
}
