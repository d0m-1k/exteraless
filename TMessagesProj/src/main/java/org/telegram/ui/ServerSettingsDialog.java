package org.telegram.ui;

import android.content.Context;
import android.text.InputType;
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

    public static void show(BaseFragment fragment, int account) {
        show(fragment, account, null);
    }

    public static void show(BaseFragment fragment, int account, Runnable onChanged) {
        Context context = fragment.getParentActivity();
        if (context == null) {
            return;
        }
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle(LocaleController.getString(R.string.ServerSettings));
        // Transport and Obfuscated2 have their own rows on the login screen, so this dialog only
        // picks the backend mode.
        CharSequence[] items = new CharSequence[]{
                LocaleController.getString(R.string.ServerProd),
                LocaleController.getString(R.string.ServerTest),
                LocaleController.getString(R.string.ServerCustom)
        };
        builder.setItems(items, (dialog, which) -> {
            if (which == 2) {
                showCustom(fragment, account, onChanged);
            } else {
                requestMode(fragment, account, which == 1 ? ServerConfig.MODE_TEST : ServerConfig.MODE_PROD, onChanged);
            }
        });
        fragment.showDialog(builder.create());
    }

    /** Transport picker shared by the login screen and Settings. */
    public static void showTransport(BaseFragment fragment, int account, Runnable onChanged) {
        Context context = fragment.getParentActivity();
        if (context == null) {
            return;
        }
        int current = ServerConfig.getTransport(account);
        CharSequence[] items = new CharSequence[]{
                transportItem(ServerConfig.TRANSPORT_ABRIDGED, current),
                transportItem(ServerConfig.TRANSPORT_INTERMEDIATE, current),
                transportItem(ServerConfig.TRANSPORT_PADDED, current),
                transportItem(ServerConfig.TRANSPORT_FULL, current)
        };
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle(LocaleController.getString(R.string.ServerTransport));
        builder.setItems(items, (dialog, which) -> {
            if (which == current) {
                return;
            }
            ServerConfig.setTransport(account, which);
            if (onChanged != null) {
                onChanged.run();
            }
            if (which == ServerConfig.TRANSPORT_FULL && ServerConfig.isObfuscated2(account)) {
                BulletinFactory.of(fragment).createSimpleBulletin(R.raw.chats_infotip, LocaleController.getString(R.string.Obfuscated2FullHint)).show();
            } else {
                BulletinFactory.of(fragment).createSimpleBulletin(R.raw.chats_infotip, LocaleController.getString(R.string.ServerApplied)).show();
            }
        });
        builder.setNegativeButton(LocaleController.getString(R.string.Cancel), null);
        fragment.showDialog(builder.create());
    }

    public static void toggleObfuscated2(BaseFragment fragment, int account, Runnable onChanged) {
        boolean obfuscated = !ServerConfig.isObfuscated2(account);
        ServerConfig.setObfuscated2(account, obfuscated);
        if (onChanged != null) {
            onChanged.run();
        }
        BulletinFactory.of(fragment).createSimpleBulletin(R.raw.chats_infotip,
                LocaleController.formatString("Obfuscated2Row", R.string.Obfuscated2Row,
                        LocaleController.getString(obfuscated ? R.string.Obfuscated2On : R.string.Obfuscated2Off))).show();
    }

    private static CharSequence transportItem(int transport, int current) {
        String name = ServerConfig.getTransportName(transport);
        return transport == current ? name + "  ✓" : name;
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
        boolean activated = UserConfig.getInstance(account).isClientActivated();
        ConnectionsManager connectionsManager = ConnectionsManager.getInstance(account);
        boolean customNow = ServerConfig.getMode(account) == ServerConfig.MODE_CUSTOM;
        // Going back from a custom server to production keeps the existing prod auth key, no relogin needed.
        boolean keepSession = mode == ServerConfig.MODE_PROD && !connectionsManager.isTestBackend() && customNow;
        if (activated && !keepSession) {
            confirmLogout(fragment, account, mode, onChanged);
        } else {
            ServerConfig.setMode(account, mode);
            ServerConfig.apply(account);
            if (onChanged != null) {
                onChanged.run();
            }
            BulletinFactory.of(fragment).createSimpleBulletin(R.raw.chats_infotip, LocaleController.getString(R.string.ServerApplied)).show();
        }
    }

    private static void confirmLogout(BaseFragment fragment, int account, int mode, Runnable onChanged) {
        Context context = fragment.getParentActivity();
        if (context == null) {
            return;
        }
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle(LocaleController.getString(R.string.ServerSettings));
        builder.setMessage(LocaleController.getString(R.string.ServerChangeLogout));
        builder.setPositiveButton(LocaleController.getString(R.string.LogOut), (dialog, which) -> {
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
            ServerConfig.setCustom(account, host, port);
            boolean activated = UserConfig.getInstance(account).isClientActivated();
            ConnectionsManager connectionsManager = ConnectionsManager.getInstance(account);
            if (activated && connectionsManager.isTestBackend()) {
                // The test auth key is not valid on a production-based custom server.
                confirmLogout(fragment, account, ServerConfig.MODE_CUSTOM, onChanged);
            } else {
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
