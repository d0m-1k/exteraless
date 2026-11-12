package org.telegram.messenger;

import android.content.Context;
import android.content.SharedPreferences;
import android.text.TextUtils;

import org.telegram.tgnet.ConnectionsManager;

/**
 * Per-account choice of the Telegram backend: the production DCs, the test DCs, or one of the
 * ready-made custom servers.
 *
 * The choice belongs to the account slot and is only meant to be made before signing in. Auth keys
 * are issued per backend and are not tied to a host, so switching between servers invalidates the
 * current authorization; the UI therefore refuses to change the server of an activated account.
 */
public final class ServerConfig {

    public static final int MODE_PROD = 0;
    public static final int MODE_TEST = 1;
    public static final int MODE_CUSTOM = 2;

    /** Ready-made custom backends: host, port and the RSA key used for the MTProto handshake. */
    public static final String PRESET_TONPX_HOST = "tonpx.fun";
    public static final int PRESET_TONPX_PORT = 7443;
    public static final String PRESET_TONPX_KEY = "-----BEGIN PUBLIC KEY-----\n"
            + "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAhSSDPOcRqGzdhM3tS6Vr\n"
            + "GH+0pKKCVacMCyP3+UDqGZIHYg33aFUZpJP6I8eqOiWxkjCJe8FNrTjw7J9bBQmc\n"
            + "MS5FtUnC15C/gpCPNdGRGGm2iFjyFdr0a7SWQrK7f5yVsI81kEUbxcokP55aDkKw\n"
            + "MUydtlm9fkCyyOyX9KLzYGDvOPn89WprMx9Gjzt5hpL9wSlS4ucfUlJlKGkK3iXO\n"
            + "y7ZkQsxktEFAILgC/ef6pKHfulWYH0g61kPS7HZgVCfvBjJelgiyl7EeZPpEKkwm\n"
            + "43zuey7rHW6PCUetkBckfWfp5MfqhRWRWQdHS9VXiVMkHmRdX06a0FIF4gF59l1O\n"
            + "yQIDAQAB\n"
            + "-----END PUBLIC KEY-----";
    /** A zero fingerprint means "compute it from the key" natively. */
    public static final long PRESET_TONPX_FINGERPRINT = 0;

    public static final String PRESET_LOCAL_HOST = "192.168.0.121";
    public static final int PRESET_LOCAL_PORT = 1111;
    public static final String PRESET_LOCAL_KEY = PRESET_TONPX_KEY;
    public static final long PRESET_LOCAL_FINGERPRINT = 0;

    public static final int TRANSPORT_ABRIDGED = 0;
    public static final int TRANSPORT_INTERMEDIATE = 1;
    public static final int TRANSPORT_PADDED = 2;

    private static final String PREFS = "exteraless_server";

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
        applyPublicKey(ConnectionsManager.getInstance(account), account);
        discardGenericConnections(account);
    }

    public static void applyPublicKey(ConnectionsManager connectionsManager, int account) {
        connectionsManager.setServerPublicKey(getPublicKey(account), getPublicKeyFingerprint(account));
    }

    /** True when the account already points at this exact custom backend. */
    public static boolean isSameCustom(int account, String host, int port) {
        return getMode(account) == MODE_CUSTOM
                && !TextUtils.isEmpty(host)
                && host.equals(getHost(account))
                && port == getPort(account);
    }

    /** Compares a key with the stored one; a zero fingerprint means "compute it from the key". */
    public static boolean isSamePublicKey(int account, String publicKey, long fingerprint) {
        String current = getPublicKey(account);
        String value = publicKey == null ? "" : publicKey;
        if (!current.equals(value)) {
            return false;
        }
        return fingerprint == 0 || getPublicKeyFingerprint(account) == fingerprint;
    }

    public static boolean isTonpxPreset(int account) {
        return isSameCustom(account, PRESET_TONPX_HOST, PRESET_TONPX_PORT);
    }

    public static boolean isLocalPreset(int account) {
        return isSameCustom(account, PRESET_LOCAL_HOST, PRESET_LOCAL_PORT);
    }

    public static String getPublicKeyName(int account) {
        String key = getPublicKey(account);
        if (TextUtils.isEmpty(key)) {
            return LocaleController.getString(R.string.PublicKeyDefault);
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
        // Official DCs use Obfuscated2 by default, like stock Telegram. Custom servers usually
        // expect the plain transport unless the user turns the layer on explicitly.
        boolean defaultValue = getMode(account) != MODE_CUSTOM;
        return preferences != null && preferences.getBoolean("obfuscated" + account, defaultValue);
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
        apply(ConnectionsManager.getInstance(account), account);
    }

    /**
     * Applies the stored choice to an already created native manager. Used from
     * {@link ConnectionsManager#init} so calling {@link ConnectionsManager#getInstance} there
     * cannot recurse.
     */
    public static void apply(ConnectionsManager connectionsManager, int account) {
        applyTransport(connectionsManager, account);

        final int mode = getMode(account);
        final boolean wantTest = mode == MODE_TEST;
        if (connectionsManager.isTestBackend() != wantTest) {
            connectionsManager.setTestBackend(wantTest);
        }

        if (mode == MODE_CUSTOM) {
            String host = getHost(account);
            int port = getPort(account);
            if (!TextUtils.isEmpty(host) && port > 0 && port <= 65535) {
                connectionsManager.setCustomServer(host, port);
                applyPublicKey(connectionsManager, account);
            } else {
                if (connectionsManager.hasCustomServer()) {
                    connectionsManager.clearCustomServer();
                }
                connectionsManager.setServerPublicKey("", 0);
            }
        } else {
            if (connectionsManager.hasCustomServer()) {
                connectionsManager.clearCustomServer();
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
}