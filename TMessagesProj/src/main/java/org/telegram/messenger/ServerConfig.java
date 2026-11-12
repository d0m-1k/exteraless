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

    /** Ready-made custom backend: host, port and the RSA key it uses for the MTProto handshake. */
    public static final String PRESET_TONPX_HOST = "tonpx.fun";
    public static final int PRESET_TONPX_PORT = 7443;
    public static final long PRESET_TONPX_FINGERPRINT = 0xc5b80f7207736c16L;
    public static final String PRESET_TONPX_KEY = "-----BEGIN PUBLIC KEY-----\n"
            + "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAhSSDPOcRqGzdhM3tS6Vr\n"
            + "GH+0pKKCVacMCyP3+UDqGZIHYg33aFUZpJP6I8eqOiWxkjCJe8FNrTjw7J9bBQmc\n"
            + "MS5FtUnC15C/gpCPNdGRGGm2iFjyFdr0a7SWQrK7f5yVsI81kEUbxcokP55aDkKw\n"
            + "MUydtlm9fkCyyOyX9KLzYGDvOPn89WprMx9Gjzt5hpL9wSlS4ucfUlJlKGkK3iXO\n"
            + "y7ZkQsxktEFAILgC/ef6pKHfulWYH0g61kPS7HZgVCfvBjJelgiyl7EeZPpEKkwm\n"
            + "43zuey7rHW6PCUetkBckfWfp5MfqhRWRWQdHS9VXiVMkHmRdX06a0FIF4gF59l1O\n"
            + "yQIDAQAB\n"
            + "-----END PUBLIC KEY-----";

    public static final int TRANSPORT_ABRIDGED = 0;
    public static final int TRANSPORT_INTERMEDIATE = 1;
    public static final int TRANSPORT_PADDED = 2;

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

    public static String getPublicKey(int account) {
        SharedPreferences preferences = prefs();
        return preferences == null ? "" : preferences.getString("publicKey" + account, "");
    }

    public static long getPublicKeyFingerprint(int account) {
        SharedPreferences preferences = prefs();
        return preferences == null ? 0 : preferences.getLong("publicKeyFingerprint" + account, 0);
    }

    public static void setPublicKey(int account, String publicKey, long fingerprint) {
        SharedPreferences preferences = prefs();
        if (preferences != null) {
            preferences.edit()
                    .putString("publicKey" + account, publicKey == null ? "" : publicKey)
                    .putLong("publicKeyFingerprint" + account, fingerprint)
                    .apply();
        }
        ConnectionsManager connectionsManager = ConnectionsManager.getInstance(account);
        applyPublicKey(connectionsManager, account);
        discardGenericConnections(account);
    }

    public static void applyPublicKey(ConnectionsManager connectionsManager, int account) {
        connectionsManager.setServerPublicKey(getPublicKey(account), getPublicKeyFingerprint(account));
    }

    public static boolean isTonpxPreset(int account) {
        return getMode(account) == MODE_CUSTOM
                && PRESET_TONPX_HOST.equals(getHost(account))
                && getPort(account) == PRESET_TONPX_PORT
                && PRESET_TONPX_KEY.equals(getPublicKey(account));
    }

    public static String getPublicKeyName(int account) {
        String key = getPublicKey(account);
        if (TextUtils.isEmpty(key)) {
            return LocaleController.getString(R.string.PublicKeyDefault);
        }
        if (PRESET_TONPX_KEY.equals(key)) {
            return LocaleController.getString(R.string.ServerTonpx);
        }
        return LocaleController.getString(R.string.PublicKeyCustom);
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
        int transport = preferences == null ? TRANSPORT_ABRIDGED : preferences.getInt("transport" + account, TRANSPORT_ABRIDGED);
        // Full was an option in earlier builds; fall back to Abridged for those accounts.
        return transport > TRANSPORT_PADDED ? TRANSPORT_ABRIDGED : transport;
    }

    public static void setTransport(int account, int transport) {
        SharedPreferences preferences = prefs();
        if (preferences != null) {
            preferences.edit().putInt("transport" + account, transport).apply();
        }
        applyTransport(ConnectionsManager.getInstance(account), account);
        // Drop live connections so the new transport takes effect immediately instead of only on
        // the next reconnect.
        discardGenericConnections(account);
    }

    public static boolean isObfuscated2(int account) {
        SharedPreferences preferences = prefs();
        return preferences != null && preferences.getBoolean("obfuscated" + account, false);
    }

    public static void setObfuscated2(int account, boolean obfuscated) {
        SharedPreferences preferences = prefs();
        if (preferences != null) {
            preferences.edit().putBoolean("obfuscated" + account, obfuscated).apply();
        }
        applyTransport(ConnectionsManager.getInstance(account), account);
        discardGenericConnections(account);
    }

    private static void discardGenericConnections(int account) {
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
            default:
                return LocaleController.getString(R.string.TransportAbridged);
        }
    }

    /** Re-applies the stored transport, e.g. when the account's native connection is (re)created. */
    public static void applyTransport(int account) {
        applyTransport(ConnectionsManager.getInstance(account), account);
    }

    public static void applyTransport(ConnectionsManager connectionsManager, int account) {
        connectionsManager.setTransport(getTransport(account), isObfuscated2(account));
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
        } else {
            if (wasCustom) {
                connectionsManager.clearCustomServer();
                if (preferences != null) {
                    preferences.edit().putBoolean("custom" + account, false).apply();
                }
            }
            connectionsManager.setServerPublicKey("", 0);
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
        applyPublicKey(connectionsManager, account);
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
