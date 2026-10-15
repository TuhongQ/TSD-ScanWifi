package cn.scanwifi.app;

import android.content.Context;
import android.content.pm.PackageManager;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiManager;
import java.nio.charset.StandardCharsets;

/** Only for an explicitly provisioned privileged system application. */
@SuppressWarnings("deprecation")
final class PrivilegedWifi {
    static boolean available(Context context) {
        return context.checkSelfPermission("android.permission.NETWORK_SETTINGS")==PackageManager.PERMISSION_GRANTED;
    }
    static void connect(Context context, WifiQr qr) throws Exception {
        WifiManager wifi=context.getApplicationContext().getSystemService(WifiManager.class);
        if(wifi==null) throw new IllegalStateException("设备没有 Wi-Fi 服务。");
        if(!wifi.isWifiEnabled()) {
            if(!wifi.setWifiEnabled(true)) throw new IllegalStateException("系统未允许开启 Wi-Fi，请在设置中开启。");
            for(int i=0;i<40 && !wifi.isWifiEnabled();i++) Thread.sleep(250);
            if(!wifi.isWifiEnabled()) throw new IllegalStateException("开启 Wi-Fi 超时，请在设置中检查。");
        }
        WifiConfiguration config=new WifiConfiguration();
        StringBuilder hex=new StringBuilder();
        for(byte b:qr.ssid.getBytes(StandardCharsets.UTF_8)) hex.append(String.format(java.util.Locale.ROOT,"%02x",b & 255));
        config.SSID=hex.toString(); config.hiddenSSID=qr.hidden;
        if(qr.security.equals("NOPASS")) config.setSecurityParams(WifiConfiguration.SECURITY_TYPE_OPEN);
        else {
            config.preSharedKey="\""+qr.password+"\"";
            config.setSecurityParams(qr.security.equals("WPA3")?WifiConfiguration.SECURITY_TYPE_SAE:WifiConfiguration.SECURITY_TYPE_PSK);
        }
        int id=wifi.addNetwork(config);
        if(id<0) throw new IllegalStateException("系统拒绝保存网络，请检查预装权限和加密支持。");
        if(!wifi.enableNetwork(id,true)) throw new IllegalStateException("网络已保存，但系统拒绝发起连接。");
    }
}
