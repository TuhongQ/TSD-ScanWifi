package cn.scanwifi.app;

import android.content.Context;
import android.net.*;
import android.net.wifi.WifiInfo;
import android.net.wifi.SupplicantState;
import android.net.wifi.WifiManager;
import android.os.Handler;
import java.util.HashMap;
import java.util.Map;

/** SSID and IP must belong to the same live Wi-Fi Network, including IPv6-only LANs. */
final class WifiConnectionStatus {
    private final ConnectivityManager connectivity;
    private final Handler handler;
    private final Map<Network,String> names=new HashMap<>();
    private final Map<Network,Boolean> addresses=new HashMap<>();
    private ConnectivityManager.NetworkCallback callback;
    WifiConnectionStatus(Context context,Handler h) {connectivity=context.getSystemService(ConnectivityManager.class);handler=h;}
    void start() {
        if(callback!=null || connectivity==null) return;
        callback=android.os.Build.VERSION.SDK_INT>=31 ? new Listener(ConnectivityManager.NetworkCallback.FLAG_INCLUDE_LOCATION_INFO) : new Listener();
        try {connectivity.registerNetworkCallback(new NetworkRequest.Builder().addTransportType(NetworkCapabilities.TRANSPORT_WIFI).build(),callback,handler);}
        catch(SecurityException e) {callback=null;}
    }
    void stop() {
        if(callback!=null) {try {connectivity.unregisterNetworkCallback(callback);} catch(IllegalArgumentException ignored) {} callback=null;}
        names.clear();addresses.clear();
    }
    boolean connected(String target) {
        for(Map.Entry<Network,String> entry:names.entrySet()) if(target.equals(entry.getValue()) && Boolean.TRUE.equals(addresses.get(entry.getKey()))) return true;
        return false;
    }
    private final class Listener extends ConnectivityManager.NetworkCallback {
        Listener() {super();}
        Listener(int flags) {super(flags);}
        @Override public void onCapabilitiesChanged(Network network,NetworkCapabilities caps) {
            if(callback!=this) return;
            if(caps.getTransportInfo() instanceof WifiInfo) {
                WifiInfo info=(WifiInfo)caps.getTransportInfo();
                if(info.getSupplicantState()!=SupplicantState.COMPLETED) {names.remove(network);return;}
                String name=info.getSSID();
                if(name==null || WifiManager.UNKNOWN_SSID.equals(name)) {names.remove(network);return;}
                if(name.length()>=2 && name.charAt(0)==34 && name.charAt(name.length()-1)==34) name=name.substring(1,name.length()-1);
                names.put(network,name);
            } else names.remove(network);
        }
        @Override public void onLinkPropertiesChanged(Network network,LinkProperties link) {
            if(callback!=this) return;
            addresses.put(network,link.getLinkAddresses().stream().anyMatch(address->!address.getAddress().isAnyLocalAddress() && !address.getAddress().isLoopbackAddress() && !address.getAddress().isLinkLocalAddress()));
        }
        @Override public void onLost(Network network) {if(callback==this) {names.remove(network);addresses.remove(network);}}
    }
}
