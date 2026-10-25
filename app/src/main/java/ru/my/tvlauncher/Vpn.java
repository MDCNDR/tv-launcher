package ru.my.tvlauncher;

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;

import java.util.HashSet;
import java.util.Set;

/**
 * Состояние VPN берём у системы (любой VPN), а включаем/выключаем через Happ:
 * happ://connect_without_ui и happ://disconnect_without_ui — без открытия его окна.
 */
final class Vpn {

    static final String HAPP = "su.happ.proxyutility";

    interface Listener {
        void onVpnChanged(boolean active);
    }

    private final Context context;
    private final ConnectivityManager cm;
    private final Handler ui = new Handler(Looper.getMainLooper());
    private final Set<Network> vpnNetworks = new HashSet<>();
    private Listener listener;
    private ConnectivityManager.NetworkCallback callback;

    Vpn(Context c) {
        context = c.getApplicationContext();
        cm = context.getSystemService(ConnectivityManager.class);
    }

    static boolean isHappInstalled(Context c) {
        try {
            c.getPackageManager().getPackageInfo(HAPP, 0);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    boolean isActive() {
        synchronized (vpnNetworks) {
            if (!vpnNetworks.isEmpty()) return true;
        }
        Network n = cm.getActiveNetwork();
        NetworkCapabilities caps = n == null ? null : cm.getNetworkCapabilities(n);
        return caps != null && caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN);
    }

    void start(Listener l) {
        listener = l;
        if (callback != null) return;
        NetworkRequest request = new NetworkRequest.Builder()
                .addTransportType(NetworkCapabilities.TRANSPORT_VPN)
                .removeCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN)
                .removeCapability(NetworkCapabilities.NET_CAPABILITY_TRUSTED)
                .removeCapability(NetworkCapabilities.NET_CAPABILITY_NOT_RESTRICTED)
                .build();
        callback = new ConnectivityManager.NetworkCallback() {
            @Override
            public void onAvailable(Network network) {
                synchronized (vpnNetworks) {
                    vpnNetworks.add(network);
                }
                notifyChanged();
            }

            @Override
            public void onLost(Network network) {
                synchronized (vpnNetworks) {
                    vpnNetworks.remove(network);
                }
                notifyChanged();
            }
        };
        try {
            cm.registerNetworkCallback(request, callback);
        } catch (Exception e) {
            Net.warn("vpn callback", e);
            callback = null;
        }
        notifyChanged();
    }

    void stop() {
        listener = null;
        if (callback != null) {
            try {
                cm.unregisterNetworkCallback(callback);
            } catch (Exception ignored) {
            }
            callback = null;
        }
        synchronized (vpnNetworks) {
            vpnNetworks.clear();
        }
    }

    private void notifyChanged() {
        ui.post(() -> {
            if (listener != null) listener.onVpnChanged(isActive());
        });
    }

    /** Отправляет Happ команду включить или выключить VPN. false — если Happ не ответил. */
    boolean request(boolean enable) {
        Uri uri = Uri.parse(enable ? "happ://connect_without_ui" : "happ://disconnect_without_ui");
        Intent i = new Intent(Intent.ACTION_VIEW, uri)
                .setPackage(HAPP)
                .addCategory(Intent.CATEGORY_BROWSABLE)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                        | Intent.FLAG_ACTIVITY_NO_ANIMATION
                        | Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS);
        try {
            context.startActivity(i);
            return true;
        } catch (ActivityNotFoundException | SecurityException e) {
            Net.warn("happ deeplink", e);
            return false;
        }
    }
}
