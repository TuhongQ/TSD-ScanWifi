package cn.scanwifi.app;
import org.junit.Test;
import static org.junit.Assert.*;
public class WifiQrTest {
    @Test public void decodesRealQrPixels() throws Exception {
        String payload="WIFI:T:WPA;S:测试网络;P:abc\\;defgh;;";
        java.util.Map<com.google.zxing.EncodeHintType,Object> hints=new java.util.HashMap<>();
        hints.put(com.google.zxing.EncodeHintType.CHARACTER_SET,"UTF-8");
        com.google.zxing.common.BitMatrix matrix=new com.google.zxing.MultiFormatWriter().encode(payload,com.google.zxing.BarcodeFormat.QR_CODE,320,320,hints);
        int[] pixels=new int[320*320];
        for(int y=0;y<320;y++) for(int x=0;x<320;x++) pixels[y*320+x]=matrix.get(x,y)?0xff000000:0xffffffff;
        com.google.zxing.BinaryBitmap bitmap=new com.google.zxing.BinaryBitmap(new com.google.zxing.common.HybridBinarizer(new com.google.zxing.RGBLuminanceSource(320,320,pixels)));
        WifiQr q=WifiQr.parse(new com.google.zxing.MultiFormatReader().decode(bitmap).getText());
        assertEquals("测试网络",q.ssid);assertEquals("abc;defgh",q.password);
    }
    @Test public void parsesWpaAndHidden() {WifiQr q=WifiQr.parse("WIFI:T:WPA;S:家庭网络;P:password123;H:true;;");assertEquals("家庭网络",q.ssid);assertEquals("password123",q.password);assertTrue(q.hidden);}
    @Test public void preservesEscapedDelimiters() {WifiQr q=WifiQr.parse("WIFI:S:office\\;a\\:b\\\\c;T:WPA;P:abc\\;def\\:ghi\\\\;;");assertEquals("office;a:b\\c",q.ssid);assertEquals("abc;def:ghi\\",q.password);}
    @Test public void openNetwork() {assertEquals("NOPASS",WifiQr.parse("WIFI:S:Guest;T:nopass;;").security);}
    @Test public void wpa3() {assertEquals("WPA3",WifiQr.parse("WIFI:S:Home;T:SAE;P:password123;;").security);}
    @Test(expected=IllegalArgumentException.class) public void rejectsOtherQr() {WifiQr.parse("https://example.com");}
    @Test(expected=IllegalArgumentException.class) public void rejectsWep() {WifiQr.parse("WIFI:S:A;T:WEP;P:12345;;");}
    @Test(expected=IllegalArgumentException.class) public void rejectsDuplicate() {WifiQr.parse("WIFI:S:A;S:B;;");}
    @Test(expected=IllegalArgumentException.class) public void rejectsMissingPassword() {WifiQr.parse("WIFI:S:A;T:WPA;;");}
    @Test(expected=IllegalArgumentException.class) public void rejectsShortPassword() {WifiQr.parse("WIFI:S:A;T:WPA;P:123;;");}
    @Test(expected=IllegalArgumentException.class) public void checksUtf8Limit() {WifiQr.parse("WIFI:S:一二三四五六七八九十一二;T:nopass;;");}
    @Test(expected=IllegalArgumentException.class) public void rejectsTrailingEscape() {WifiQr.parse("WIFI:S:A;P:abc\\");}
}
