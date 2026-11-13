package org.telegram.ui;

import android.content.Context;
import android.view.Gravity;
import android.widget.EditText;
import android.widget.LinearLayout;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.messenger.ServerConfig;
import org.telegram.messenger.UserConfig;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.LayoutHelper;

/**
 * Server picker shown before an account signs in: production DCs, test DCs or one of the
 * ready-made custom servers. The backend of an activated account cannot be changed because the
 * auth key issued by the previous server would no longer be valid.
 */
public final class ServerSettingsDialog {

    private ServerSettingsDialog() {
    }

    private static boolean isCurrent(int account, int mode) {
        if (ServerConfig.getMode(account) != mode) {
            return false;
        }
        ConnectionsManager connectionsManager = ConnectionsManager.getInstance(account);
        return connectionsManager.isTestBackend() == (mode == ServerConfig.MODE_TEST);
    }

    private static boolean refuseActivated(BaseFragment fragment, int account) {
        if (!UserConfig.getInstance(account).isClientActivated()) {
            return false;
        }
        BulletinFactory.of(fragment).createSimpleBulletin(R.raw.chats_infotip, LocaleController.getString(R.string.ServerOnlyAtLogin)).show();
        return true;
    }

    public static void requestMode(BaseFragment fragment, int account, int mode, Runnable onChanged) {
        Context context = fragment.getParentActivity();
        if (context == null) {
            return;
        }
        if (refuseActivated(fragment, account)) {
            return;
        }
        if (isCurrent(account, mode)) {
            BulletinFactory.of(fragment).createSimpleBulletin(R.raw.chats_infotip, LocaleController.getString(R.string.ServerAlready)).show();
            return;
        }
        ServerConfig.setMode(account, mode);
        ServerConfig.apply(account);
        if (onChanged != null) {
            onChanged.run();
        }
        BulletinFactory.of(fragment).createSimpleBulletin(R.raw.chats_infotip, LocaleController.getString(R.string.ServerApplied)).show();
    }

    /** Points the account at a ready-made custom server together with its pinned RSA key. */
    public static void applyPreset(BaseFragment fragment, int account, String host, int port, String publicKey, long fingerprint, Runnable onChanged) {
        Context context = fragment.getParentActivity();
        if (context == null) {
            return;
        }
        if (refuseActivated(fragment, account)) {
            return;
        }
        if (ServerConfig.isSameCustom(account, host, port) && ServerConfig.isSamePublicKey(account, publicKey, fingerprint)) {
            BulletinFactory.of(fragment).createSimpleBulletin(R.raw.chats_infotip, LocaleController.getString(R.string.ServerAlready)).show();
            return;
        }
        if (!ConnectionsManager.isValidServerKey(account, publicKey)) {
            BulletinFactory.of(fragment).createSimpleBulletin(R.raw.chats_infotip, LocaleController.getString(R.string.PublicKeyInvalid)).show();
            return;
        }
        ServerConfig.setCustom(account, host, port);
        ServerConfig.setPublicKey(account, publicKey, fingerprint);
        ServerConfig.setMode(account, ServerConfig.MODE_CUSTOM);
        ServerConfig.apply(account);
        if (onChanged != null) {
            onChanged.run();
        }
        BulletinFactory.of(fragment).createSimpleBulletin(R.raw.chats_infotip, LocaleController.getString(R.string.ServerApplied)).show();
    }

    /** Picks the RSA key used for the MTProto handshake: built-in Telegram, preset, or a pasted one. */
    public static void showPublicKey(BaseFragment fragment, int account, Runnable onChanged) {
        Context context = fragment.getParentActivity();
        if (context == null) {
            return;
        }
        if (refuseActivated(fragment, account)) {
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
            // Fingerprint is computed natively from the key for keys pasted by the user.
            if (!ConnectionsManager.isValidServerKey(account, publicKey)) {
                BulletinFactory.of(fragment).createSimpleBulletin(R.raw.chats_infotip, LocaleController.getString(R.string.PublicKeyInvalid)).show();
                return;
            }
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
        if (refuseActivated(fragment, account)) {
            return;
        }
        ServerConfig.setPublicKey(account, publicKey, fingerprint);
        if (onChanged != null) {
            onChanged.run();
        }
        BulletinFactory.of(fragment).createSimpleBulletin(R.raw.chats_infotip, LocaleController.getString(R.string.PublicKeyApplied)).show();
    }
}