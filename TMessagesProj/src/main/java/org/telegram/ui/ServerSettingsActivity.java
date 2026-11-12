package org.telegram.ui;

import static org.telegram.messenger.LocaleController.getString;

import android.text.TextUtils;
import android.view.View;

import org.telegram.messenger.R;
import org.telegram.messenger.ServerConfig;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;
import org.telegram.ui.Components.UniversalFragment;

import java.util.ArrayList;

/**
 * Per-account server menu: production/test/custom backend, MTProto transport and Obfuscated2.
 * Opened both from Settings and from the "add account" screen.
 */
public class ServerSettingsActivity extends UniversalFragment {

    private static final int ID_PROD = 1;
    private static final int ID_TEST = 2;
    private static final int ID_CUSTOM = 3;

    private static final int ID_TRANSPORT_ABRIDGED = 10;
    private static final int ID_TRANSPORT_INTERMEDIATE = 11;
    private static final int ID_TRANSPORT_PADDED = 12;

    private static final int ID_OBFUSCATED2 = 20;

    private final Runnable refresh = () -> {
        if (listView != null && listView.adapter != null) {
            listView.adapter.update(true);
        }
    };

    @Override
    protected boolean useSimpleActionBar() {
        return true;
    }

    @Override
    public boolean isSupportEdgeToEdge() {
        return true;
    }

    @Override
    protected CharSequence getTitle() {
        return getString(R.string.ServerSettings);
    }

    @Override
    protected void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
        final int mode = ServerConfig.getMode(currentAccount);
        final int transport = ServerConfig.getTransport(currentAccount);
        final boolean obfuscated = ServerConfig.isObfuscated2(currentAccount);

        items.add(UItem.asHeader(getString(R.string.ServerSettings)));
        items.add(UItem.asRadio(ID_PROD, getString(R.string.ServerProd)).setChecked(mode == ServerConfig.MODE_PROD));
        items.add(UItem.asRadio(ID_TEST, getString(R.string.ServerTest)).setChecked(mode == ServerConfig.MODE_TEST));
        items.add(UItem.asRadio(ID_CUSTOM, getString(R.string.ServerCustom), getCustomAddress()).setChecked(mode == ServerConfig.MODE_CUSTOM));
        items.add(UItem.asShadow(getString(R.string.ServerSettingsInfo)));

        items.add(UItem.asHeader(getString(R.string.ServerTransport)));
        items.add(UItem.asRadio(ID_TRANSPORT_ABRIDGED, getString(R.string.TransportAbridged)).setChecked(transport == ServerConfig.TRANSPORT_ABRIDGED));
        items.add(UItem.asRadio(ID_TRANSPORT_INTERMEDIATE, getString(R.string.TransportIntermediate)).setChecked(transport == ServerConfig.TRANSPORT_INTERMEDIATE));
        items.add(UItem.asRadio(ID_TRANSPORT_PADDED, getString(R.string.TransportPadded)).setChecked(transport == ServerConfig.TRANSPORT_PADDED));
        items.add(UItem.asShadow(null));

        items.add(UItem.asSwitch(ID_OBFUSCATED2, getString(R.string.Obfuscated2)).setChecked(obfuscated));
        items.add(UItem.asShadow(getString(R.string.Obfuscated2Info)));
    }

    private String getCustomAddress() {
        String host = ServerConfig.getHost(currentAccount);
        return TextUtils.isEmpty(host) ? "" : host + ":" + ServerConfig.getPort(currentAccount);
    }

    @Override
    protected void onClick(UItem item, View view, int position, float x, float y) {
        switch (item.id) {
            case ID_PROD:
                ServerSettingsDialog.requestMode(this, currentAccount, ServerConfig.MODE_PROD, refresh);
                break;
            case ID_TEST:
                ServerSettingsDialog.requestMode(this, currentAccount, ServerConfig.MODE_TEST, refresh);
                break;
            case ID_CUSTOM:
                ServerSettingsDialog.showCustom(this, currentAccount, refresh);
                break;
            case ID_TRANSPORT_ABRIDGED:
            case ID_TRANSPORT_INTERMEDIATE:
            case ID_TRANSPORT_PADDED: {
                if (ServerConfig.getTransport(currentAccount) == item.id - ID_TRANSPORT_ABRIDGED) {
                    return;
                }
                ServerConfig.setTransport(currentAccount, item.id - ID_TRANSPORT_ABRIDGED);
                refresh.run();
                break;
            }
            case ID_OBFUSCATED2:
                ServerConfig.setObfuscated2(currentAccount, !ServerConfig.isObfuscated2(currentAccount));
                refresh.run();
                break;
        }
    }

    @Override
    protected boolean onLongClick(UItem item, View view, int position, float x, float y) {
        return false;
    }
}
