package cn.scanwifi.app;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.ActivityNotFoundException;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.wifi.WifiNetworkSuggestion;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.view.WindowManager;
import android.widget.*;
import com.google.zxing.BarcodeFormat;
import com.journeyapps.barcodescanner.*;
import java.util.ArrayList;
import java.util.Collections;

public final class MainActivity extends Activity {
    private BarcodeView camera;
    private TextView status, network;
    private Button retry, light;
    private boolean scanning=true, torch=false, awaitingSystem=false;
    private final java.util.concurrent.ExecutorService wifiWorker=java.util.concurrent.Executors.newSingleThreadExecutor();
    private static final int CAMERA=10, SAVE=20;
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
        LinearLayout root=new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(24),dp(16),dp(24),dp(16)); root.setBackgroundColor(Color.rgb(244,248,247));
        root.setOnApplyWindowInsetsListener((v,insets)-> {
            android.graphics.Insets bars=insets.getInsets(android.view.WindowInsets.Type.systemBars() | android.view.WindowInsets.Type.displayCutout());
            v.setPadding(dp(24)+bars.left,dp(16)+bars.top,dp(24)+bars.right,dp(16)+bars.bottom); return insets;
        });
        ScrollView scroll=new ScrollView(this); scroll.setFillViewport(true); scroll.addView(root); setContentView(scroll);
        TextView badge=label("测试工程部开发",14); badge.setTextColor(Color.rgb(18,110,99)); root.addView(badge);
        TextView title=label("扫码连接WIFI",30); title.setTypeface(null,Typeface.BOLD); root.addView(title);
        root.addView(label("对准 Wi-Fi 二维码，无需手动输入密码",15));
        FrameLayout frame=new FrameLayout(this); frame.setMinimumHeight(dp(180));
        LinearLayout.LayoutParams preview=new LinearLayout.LayoutParams(-1,0,1); preview.setMargins(0,dp(24),0,dp(16)); root.addView(frame,preview);
        camera=new BarcodeView(this); camera.setDecoderFactory(new DefaultDecoderFactory(Collections.singletonList(BarcodeFormat.QR_CODE))); frame.addView(camera,new FrameLayout.LayoutParams(-1,-1));
        camera.addStateListener(new CameraPreview.StateListener() {
            public void previewSized() {} public void previewStarted() {} public void previewStopped() {} public void cameraClosed() {}
            public void cameraError(Exception error) { scanning=false; status.setText("相机暂时不可用，请关闭其他使用相机的应用后重新扫码。"); }
        });
        TextView hint=label("将二维码放入镜头中央",14); hint.setTextColor(Color.WHITE); hint.setBackgroundColor(0xAA102C28); hint.setGravity(17);
        FrameLayout.LayoutParams hintLayout=new FrameLayout.LayoutParams(-1,dp(44),android.view.Gravity.BOTTOM); frame.addView(hint,hintLayout);
        network=label("等待识别网络",21); network.setTypeface(null,Typeface.BOLD); root.addView(network);
        status=label("识别后将打开系统确认窗口。确认后，系统保存网络并尝试连接。",14); root.addView(status);
        retry=button("重新扫码",v->startScan()); root.addView(retry);
        light=button("打开手电筒",v->{ if(checkSelfPermission(Manifest.permission.CAMERA)==PackageManager.PERMISSION_GRANTED && scanning) {torch=!torch; camera.setTorch(torch); light.setText(torch?"关闭手电筒":"打开手电筒");} }); root.addView(light);
        light.setEnabled(getPackageManager().hasSystemFeature(PackageManager.FEATURE_CAMERA_FLASH));
        root.addView(button("打开 Wi-Fi 设置",v->openWifi()));
        root.addView(button("相机权限设置",v->startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,android.net.Uri.parse("package:"+getPackageName())))));
        root.addView(label("仅在本机识别 · 不上传密码 · 不保存扫码记录",12));
        if(state!=null) { scanning=state.getBoolean("scanning",true); awaitingSystem=state.getBoolean("awaiting",false); network.setText(state.getString("network","等待识别网络")); status.setText(state.getString("status","点击重新扫码")); }
        if(scanning) startScan();
    }
    private void startScan() {
        if(awaitingSystem) return;
        scanning=true; network.setText("等待识别网络"); status.setText(PrivilegedWifi.available(this)?"系统预装模式 · 识别后直接发起连接":"普通安装模式 · 识别后请在系统窗口确认连接");
        if(checkSelfPermission(Manifest.permission.CAMERA)!=PackageManager.PERMISSION_GRANTED) {requestPermissions(new String[]{Manifest.permission.CAMERA},CAMERA);return;}
        camera.decodeContinuous(new BarcodeCallback() {
            @Override public void barcodeResult(BarcodeResult result) {if(scanning && !awaitingSystem) receive(result.getText());}
        });
        camera.resume();
    }
    private void receive(String raw) {
        scanning=false; camera.pause(); torch=false; light.setText("打开手电筒");
        try {
            WifiQr qr=WifiQr.parse(raw);
            network.setText(qr.ssid);
            if(PrivilegedWifi.available(this)) {
                awaitingSystem=true; status.setText("正在保存网络并发起连接…");
                wifiWorker.execute(()-> {
                    String message;
                    try {PrivilegedWifi.connect(this,qr); message="已发起连接，无需系统确认。实际连接状态请查看 Wi-Fi 设置。";}
                    catch(Exception failure) {message="直接连接未完成，请检查系统预装权限、Wi-Fi 状态和二维码。可使用 Wi-Fi 设置。";}
                    String resultMessage=message;
                    runOnUiThread(()-> {if(!isDestroyed()) {awaitingSystem=false;status.setText(resultMessage);}});
                });
                return;
            }
            WifiNetworkSuggestion.Builder builder=new WifiNetworkSuggestion.Builder().setSsid(qr.ssid).setIsHiddenSsid(qr.hidden);
            if(qr.security.equals("WPA")) builder.setWpa2Passphrase(qr.password);
            else if(qr.security.equals("WPA3")) builder.setWpa3Passphrase(qr.password);
            ArrayList<WifiNetworkSuggestion> list=new ArrayList<>(); list.add(builder.build());
            Intent intent=new Intent(Settings.ACTION_WIFI_ADD_NETWORKS); intent.putParcelableArrayListExtra(Settings.EXTRA_WIFI_NETWORK_LIST,list);
            status.setText("已识别 "+qr.security+" 网络。请在系统窗口中确认连接。");
            awaitingSystem=true; startActivityForResult(intent,SAVE);
        } catch(IllegalArgumentException e) {awaitingSystem=false; status.setText(e.getMessage());}
        catch(ActivityNotFoundException | SecurityException e) {awaitingSystem=false; status.setText("此设备未提供网络添加窗口，请打开 Wi-Fi 设置连接。");}
    }
    @Override protected void onActivityResult(int request,int result,Intent data) {
        super.onActivityResult(request,result,data);
        if(request!=SAVE) return;
        awaitingSystem=false;
        if(result!=RESULT_OK) {status.setText("已取消系统确认。可重新扫码连接。");return;}
        ArrayList<Integer> results=data==null?null:data.getIntegerArrayListExtra(Settings.EXTRA_WIFI_NETWORK_RESULT_LIST);
        if(results==null || results.isEmpty()) status.setText("系统未返回保存结果，请在 Wi-Fi 设置中确认连接情况。");
        else if(results.get(0)==Settings.ADD_WIFI_RESULT_SUCCESS) status.setText("网络已保存，系统正在尝试连接。实际连接状态请查看 Wi-Fi 设置。");
        else if(results.get(0)==Settings.ADD_WIFI_RESULT_ALREADY_EXISTS) status.setText("此网络已保存。请打开 Wi-Fi 设置确认或选择连接。");
        else status.setText("网络保存失败，请检查二维码、加密方式或使用 Wi-Fi 设置。");
    }
    @Override public void onRequestPermissionsResult(int request,String[] permissions,int[] grants) {
        super.onRequestPermissionsResult(request,permissions,grants);
        if(request==CAMERA && grants.length>0 && grants[0]==PackageManager.PERMISSION_GRANTED) startScan();
        else {scanning=false; status.setText("需要相机权限才能扫码。请在应用设置中允许相机权限，然后重新扫码。");}
    }
    @Override protected void onResume() {super.onResume(); if(camera!=null && scanning && checkSelfPermission(Manifest.permission.CAMERA)==PackageManager.PERMISSION_GRANTED) camera.resume();}
    @Override protected void onPause() {if(camera!=null) {camera.setTorch(false); camera.pause();} torch=false; if(light!=null) light.setText("打开手电筒"); super.onPause();}
    @Override protected void onDestroy() {wifiWorker.shutdownNow();super.onDestroy();}
    @Override protected void onSaveInstanceState(Bundle state) {super.onSaveInstanceState(state); state.putBoolean("scanning",scanning); state.putBoolean("awaiting",awaitingSystem && !PrivilegedWifi.available(this)); state.putString("network",network.getText().toString()); state.putString("status",status.getText().toString());}
    private void openWifi() {try {startActivity(new Intent(Settings.ACTION_WIFI_SETTINGS));} catch(ActivityNotFoundException e) {status.setText("无法打开 Wi-Fi 设置，请手动进入系统设置。");}}
    private TextView label(String s,int size) {TextView t=new TextView(this);t.setText(s);t.setTextSize(size);t.setTextColor(Color.rgb(25,49,45)); t.setPadding(0,dp(5),0,dp(5));return t;}
    private Button button(String s,View.OnClickListener listener) {Button b=new Button(this); b.setText(s);b.setTextColor(Color.WHITE);b.setTextSize(16);b.setAllCaps(false); GradientDrawable bg=new GradientDrawable();bg.setColor(Color.rgb(18,110,99));bg.setCornerRadius(dp(14));b.setBackground(bg);LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,dp(52));p.setMargins(0,dp(10),0,0);b.setLayoutParams(p);b.setOnClickListener(listener);return b;}
    private int dp(int x) {return Math.round(x*getResources().getDisplayMetrics().density);}
}
