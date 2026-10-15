package cn.scanwifi.app;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/** WIFI QR parser. Escapes are decoded once; secrets never enter logs/storage. */
public final class WifiQr {
    public final String ssid, password, security;
    public final boolean hidden;
    private WifiQr(String s, String p, String t, boolean h) { ssid=s; password=p; security=t; hidden=h; }
    public static WifiQr parse(String raw) {
        if (raw == null || !raw.startsWith("WIFI:")) throw new IllegalArgumentException("这不是 Wi-Fi 二维码，请扫描路由器或手机分享的 Wi-Fi 码。");
        Map<String,String> fields = new HashMap<>();
        StringBuilder token = new StringBuilder();
        boolean escaped=false;
        for (int i=5; i<raw.length(); i++) {
            char c=raw.charAt(i);
            if (escaped) { token.append(c); escaped=false; }
            else if (c=='\\') escaped=true;
            else if (c==';') { add(fields, token.toString()); token.setLength(0); }
            else token.append(c);
        }
        if (escaped) throw new IllegalArgumentException("二维码转义格式不完整。");
        if (token.length()>0) add(fields, token.toString());
        String ssid=fields.getOrDefault("S", "");
        if (ssid.isEmpty() || ssid.getBytes(StandardCharsets.UTF_8).length>32) throw new IllegalArgumentException("网络名称为空或超过 32 字节。");
        String type=fields.getOrDefault("T", "nopass").toUpperCase(Locale.ROOT);
        if (type.equals("WPA2") || type.equals("WPA/WPA2")) type="WPA";
        if (type.equals("SAE")) type="WPA3";
        if (!type.equals("WPA") && !type.equals("WPA3") && !type.equals("NOPASS")) throw new IllegalArgumentException("暂不支持此加密类型（WEP / 企业认证）。请使用系统 Wi-Fi 设置。");
        String pass=fields.getOrDefault("P", "");
        if (!type.equals("NOPASS") && pass.isEmpty()) throw new IllegalArgumentException("二维码缺少 Wi-Fi 密码。");
        if (type.equals("WPA") && (pass.length()<8 || pass.length()>63 || !StandardCharsets.US_ASCII.newEncoder().canEncode(pass))) throw new IllegalArgumentException("WPA/WPA2 密码应为 8–63 位 ASCII 字符；暂不支持原始十六进制密钥。");
        return new WifiQr(ssid, pass, type, "true".equalsIgnoreCase(fields.get("H")));
    }
    private static void add(Map<String,String> fields, String token) {
        int colon=token.indexOf(':');
        if (colon>0) {
            String key=token.substring(0,colon);
            if (fields.putIfAbsent(key,token.substring(colon+1))!=null) throw new IllegalArgumentException("二维码包含重复字段。");
        }
    }
}
