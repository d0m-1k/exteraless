package org.telegram.ui;

import android.content.Context;
import android.text.InputType;
import android.view.Gravity;
import android.text.TextUtils;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.R;
import org.telegram.messenger.ServerConfig;
import org.telegram.messenger.UserConfig;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.LayoutHelper;

/**
 * Per-account server picker: production DCs, test DCs or a custom MTProto server.
 */
public final class ServerSettingsDialog {

    private ServerSettingsDialog() {
    }

    private static boolean isCurrent(int account, int mode) {
        if (ServerConfig.getMode(account) != mode) {
            return false;
        }
        if (mode == ServerConfig.MODE_CUSTOM) {
            return true;
        }
        ConnectionsManager connectionsManager = ConnectionsManager.getInstance(account);
        return connectionsManager.isTestBackend() == (mode == ServerConfig.MODE_TEST);
    }

    public static void requestMode(BaseFragment fragment, int account, int mode, Runnable onChanged) {
        Context context = fragment.getParentActivity();
        if (context == null) {
            return;
        }
        if (isCurrent(account, mode)) {
            BulletinFactory.of(fragment).createSimpleBulletin(R.raw.chats_infotip, LocaleController.getString(R.string.ServerAlready)).show();
            return;
        }
        // Auth keys are bound to a datacenter and to the backend that issued them, so any switch
        // to another backend invalidates the current session.
        if (UserConfig.getInstance(account).isClientActivated()) {
            confirmLogout(fragment, account, mode, null, onChanged);
        } else {
            ServerConfig.setMode(account, mode);
            ServerConfig.apply(account);
            if (onChanged != null) {
                onChanged.run();
            }
            BulletinFactory.of(fragment).createSimpleBulletin(R.raw.chats_infotip, LocaleController.getString(R.string.ServerApplied)).show();
        }
    }

    private static void confirmLogout(BaseFragment fragment, int account, int mode, Runnable applyBeforeLogout, Runnable onChanged) {
        Context context = fragment.getParentActivity();
        if (context == null) {
            return;
        }
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle(LocaleController.getString(R.string.ServerSettings));
        builder.setMessage(LocaleController.getString(R.string.ServerChangeLogout));
        builder.setPositiveButton(LocaleController.getString(R.string.LogOut), (dialog, which) -> {
            if (applyBeforeLogout != null) {
                applyBeforeLogout.run();
            }
            ServerConfig.setPending(mode);
            ServerConfig.setMode(account, mode);
            if (onChanged != null) {
                onChanged.run();
            }
            MessagesController.getInstance(account).performLogout(0);
        });
        builder.setNegativeButton(LocaleController.getString(R.string.Cancel), null);
        fragment.showDialog(builder.create());
    }

    /** Points the account at a ready-made custom server together with its pinned RSA key. */
    public static void applyPreset(BaseFragment fragment, int account, String host, int port, String publicKey, long fingerprint, Runnable onChanged) {
        Context context = fragment.getParentActivity();
        if (context == null) {
            return;
        }
        if (ServerConfig.isSameCustom(account, host, port) && ServerConfig.isSamePublicKey(account, publicKey, fingerprint)) {
            BulletinFactory.of(fragment).createSimpleBulletin(R.raw.chats_infotip, LocaleController.getString(R.string.ServerAlready)).show();
            return;
        }
        Runnable apply = () -> {
            ServerConfig.setCustom(account, host, port);
            ServerConfig.setPublicKey(account, publicKey, fingerprint);
        };
        if (UserConfig.getInstance(account).isClientActivated()) {
            // The auth key issued by the previous backend is not valid on this server.
            confirmLogout(fragment, account, ServerConfig.MODE_CUSTOM, apply, onChanged);
        } else {
            apply.run();
            ServerConfig.setMode(account, ServerConfig.MODE_CUSTOM);
            ServerConfig.apply(account);
            if (onChanged != null) {
                onChanged.run();
            }
            BulletinFactory.of(fragment).createSimpleBulletin(R.raw.chats_infotip, LocaleController.getString(R.string.ServerApplied)).show();
        }
    }

    /** Picks the RSA key used for the MTProto handshake: built-in, the tonpx.fun preset, or a pasted one. */
    public static void showPublicKey(BaseFragment fragment, int account, Runnable onChanged) {
        Context context = fragment.getParentActivity();
        if (context == null) {
            return;
        }
        CharSequence[] items = new CharSequence[]{
                LocaleController.getString(R.string.PublicKeyDefault),
                LocaleController.getString(R.string.ServerTonpx),
                LocaleController.getString(R.string.PublicKeyPaste)
        };
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle(LocaleController.formatString("PublicKeyRow", R.string.PublicKeyRow, ServerConfig.getPublicKeyName(account)));
        builder.setItems(items, (dialog, which) -> {
            if (which == 0) {
                applyPublicKeyChange(fragment, account, "", 0, onChanged);
            } else if (which == 1) {
                applyPublicKeyChange(fragment, account, ServerConfig.PRESET_TONPX_KEY, ServerConfig.PRESET_TONPX_FINGERPRINT, onChanged);
            } else {
                showPublicKeyInput(fragment, account, onChanged);
            }
        });
        builder.setNegativeButton(LocaleController.getString(R.string.Cancel), null);
        fragment.showDialog(builder.create());
    }

    private static void showPublicKeyInput(BaseFragment fragment, int account, Runnable onChanged) {
        Context context = fragment.getParentActivity();
        if (context == null) {
            return;
        }
        LinearLayout layout = new LinearLayout(context);
        layout.setOrientation(LinearLayout.VERTICAL);
        int padding = AndroidUtilities.dp(24);
        layout.setPadding(padding, AndroidUtilities.dp(8), padding, 0);

        EditText keyEdit = new EditText(context);
        keyEdit.setHint(LocaleController.getString(R.string.PublicKeyHint));
        keyEdit.setSingleLine(false);
        keyEdit.setMinLines(4);
        keyEdit.setGravity(Gravity.TOP);
        keyEdit.setText(ServerConfig.getPublicKey(account));
        layout.addView(keyEdit, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle(LocaleController.getString(R.string.PublicKey));
        builder.setView(layout);
        builder.setPositiveButton(LocaleController.getString(R.string.Save), (dialog, which) -> {
            String publicKey = keyEdit.getText().toString().trim();
            if (!publicKey.contains("-----BEGIN") || !publicKey.contains("KEY-----")) {
                BulletinFactory.of(fragment).createSimpleBulletin(R.raw.chats_infotip, LocaleController.getString(R.string.PublicKeyInvalid)).show();
                return;
            }
            // Fingerprint is computed natively from the key for keys pasted by the user.
            applyPublicKeyChange(fragment, account, publicKey, 0, onChanged);
        });
        builder.setNegativeButton(LocaleController.getString(R.string.Cancel), null);
        fragment.showDialog(builder.create());
    }

    private static void applyPublicKeyChange(BaseFragment fragment, int account, String publicKey, long fingerprint, Runnable onChanged) {
        if (ServerConfig.isSamePublicKey(account, publicKey, fingerprint)) {
            BulletinFactory.of(fragment).createSimpleBulletin(R.raw.chats_infotip, LocaleController.getString(R.string.NothingChanged)).show();
            return;
        }
        Runnable apply = () -> ServerConfig.setPublicKey(account, publicKey, fingerprint);
        boolean custom = ServerConfig.getMode(account) == ServerConfig.MODE_CUSTOM;
        if (custom && UserConfig.getInstance(account).isClientActivated()) {
            // A new handshake key means a new backend, so the old auth key cannot be reused.
            confirmLogout(fragment, account, ServerConfig.MODE_CUSTOM, apply, onChanged);
        } else {
            apply.run();
            if (onChanged != null) {
                onChanged.run();
            }
            BulletinFactory.of(fragment).createSimpleBulletin(R.raw.chats_infotip, LocaleController.getString(R.string.PublicKeyApplied)).show();
        }
    }

    public static void showCustom(BaseFragment fragment, int account, Runnable onChanged) {
        Context context = fragment.getParentActivity();
        if (context == null) {
            return;
        }
        LinearLayout layout = new LinearLayout(context);
        layout.setOrientation(LinearLayout.VERTICAL);
        int padding = AndroidUtilities.dp(24);
        layout.setPadding(padding, AndroidUtilities.dp(8), padding, 0);

        EditText hostEdit = new EditText(context);
        hostEdit.setHint(LocaleController.getString(R.string.ServerHost));
        hostEdit.setSingleLine(true);
        hostEdit.setText(ServerConfig.getHost(account));
        layout.addView(hostEdit, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        EditText portEdit = new EditText(context);
        portEdit.setHint(LocaleController.getString(R.string.ServerPort));
        portEdit.setInputType(InputType.TYPE_CLASS_NUMBER);
        portEdit.setSingleLine(true);
        portEdit.setText(String.valueOf(ServerConfig.getPort(account)));
        layout.addView(portEdit, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        TextView hintView = new TextView(context);
        hintView.setText(LocaleController.getString(R.string.ServerCustomHint));
        hintView.setTextSize(13);
        hintView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText4));
        layout.addView(hintView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 8, 0));

        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle(LocaleController.getString(R.string.ServerCustom));
        builder.setView(layout);
        builder.setPositiveButton(LocaleController.getString(R.string.Save), (dialog, which) -> {
            String host = hostEdit.getText().toString().trim();
            int port;
            try {
                port = Integer.parseInt(portEdit.getText().toString().trim());
            } catch (NumberFormatException e) {
                port = -1;
            }
            if (TextUtils.isEmpty(host) || port <= 0 || port > 65535) {
                BulletinFactory.of(fragment).createSimpleBulletin(R.raw.chats_infotip, LocaleController.getString(R.string.ServerInvalid)).show();
                return;
            }
            if (ServerConfig.isSameCustom(account, host, port)) {
                BulletinFactory.of(fragment).createSimpleBulletin(R.raw.chats_infotip, LocaleController.getString(R.string.ServerAlready)).show();
                return;
            }
            Runnable apply = () -> ServerConfig.setCustom(account, host, port);
            if (UserConfig.getInstance(account).isClientActivated()) {
                // The current auth key belongs to another host and cannot be reused here.
                confirmLogout(fragment, account, ServerConfig.MODE_CUSTOM, apply, onChanged);
            } else {
                apply.run();
                ServerConfig.setMode(account, ServerConfig.MODE_CUSTOM);
                ServerConfig.apply(account);
                if (onChanged != null) {
                    onChanged.run();
                }
                BulletinFactory.of(fragment).createSimpleBulletin(R.raw.chats_infotip, LocaleController.getString(R.string.ServerApplied)).show();
            }
        });
        builder.setNegativeButton(LocaleController.getString(R.string.Cancel), null);
        fragment.showDialog(builder.create());
    }
}
