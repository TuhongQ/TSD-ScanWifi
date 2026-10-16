package cn.scanwifi.app;

import android.Manifest;
import android.app.Activity;
import android.content.*;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.location.LocationManager;
import android.net.wifi.WifiNetworkSuggestion;
import android.os.*;
import android.provider.Settings;
import android.view.*;
import android.widget.*;
import com.google.zxing.BarcodeFormat;
import com.journeyapps.barcodescanner.*;
import java.util.*;

public final class MainActivity extends Activity {
    private static final int PERMISSIONS=10, SAVE=20;
    private static final int NORMAL=Color.rgb(244,248,247), GREEN=Color.rgb(19,111,65), RED=Color.rgb(169,39,48);
    private final Handler handler=new Handler(Looper.getMainLooper());
    private final ConnectionSession session=new ConnectionSession();
    private final java.util.concurrent.ExecutorService wifiWorker=java.util.concurrent.Executors.newSingleThreadExecutor();
    private java.util.concurrent.Future<?> attemptWork;
    private WifiConnectionStatus wifiStatus;
    private BarcodeView camera;
    private LinearLayout root;
    private FrameLayout preview;
    private TextView status, network;
    private final List<TextView> labels=new ArrayList<>();
    private Button light;
    private boolean torch, resumed, permissionsAsked;
    private int generation;
    private String failureText="连接失败";
    private final Runnable ticker=new Runnable() {public void run() {
        if(!resumed || isDestroyed()) return;
        long now=SystemClock.elapsedRealtime();
        boolean connected=wifiStatus.connected(session.target);
        ConnectionSession.Action action=session.tick(now,connected?session.target:"",connected);
        if(action==ConnectionSession.Action.FAILURE) failureText="未连接到目标 Wi-Fi，请检查密码、信号和权限";
        if(action==ConnectionSession.Action.HIDE) {finishAndRemoveTask();return;}
        if(action==ConnectionSession.Action.SCAN) {startScan();return;}
        render();
        if(session.phase!=ConnectionSession.Phase.SCANNING && session.phase!=ConnectionSession.Phase.APPROVAL) handler.postDelayed(this,250);
    }};

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
        wifiStatus=new WifiConnectionStatus(this,handler);
        root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setPadding(dp(22),dp(14),dp(22),dp(14));
        root.setOnApplyWindowInsetsListener((v,insets)-> {
            android.graphics.Insets bars=insets.getInsets(WindowInsets.Type.systemBars()|WindowInsets.Type.displayCutout());
            v.setPadding(dp(22)+bars.left,dp(14)+bars.top,dp(22)+bars.right,dp(14)+bars.bottom);return insets;
        });
        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);scroll.addView(root);setContentView(scroll);
        root.addView(label("测试工程部开发",14));
        TextView title=label("扫码连接WIFI",28);title.setTypeface(null,Typeface.BOLD);root.addView(title);
        root.addView(label("对准 Wi-Fi 二维码，即可开始连接",14));
        preview=new FrameLayout(this);preview.setMinimumHeight(dp(240));
        LinearLayout.LayoutParams frameLayout=new LinearLayout.LayoutParams(-1,0,1);frameLayout.setMargins(0,dp(14),0,dp(12));root.addView(preview,frameLayout);
        camera=new BarcodeView(this);camera.setDecoderFactory(new DefaultDecoderFactory(Collections.singletonList(BarcodeFormat.QR_CODE)));preview.addView(camera,new FrameLayout.LayoutParams(-1,-1));
        camera.decodeContinuous(new BarcodeCallback() {public void barcodeResult(BarcodeResult result) {if(session.phase==ConnectionSession.Phase.SCANNING) receive(result.getText());}});
        camera.addStateListener(new CameraPreview.StateListener() {
            public void previewSized() {} public void previewStarted() {} public void previewStopped() {} public void cameraClosed() {}
            public void cameraError(Exception error) {if(session.phase==ConnectionSession.Phase.SCANNING) fail("相机不可用，请关闭其他使用相机的应用");}
        });
        TextView hint=label("将二维码放入镜头中央",13);labels.remove(hint);hint.setTextColor(Color.WHITE);hint.setGravity(Gravity.CENTER);hint.setBackgroundColor(0xAA102C28);
        preview.addView(hint,new FrameLayout.LayoutParams(-1,dp(36),Gravity.BOTTOM));
        network=label("等待识别网络",23);network.setTypeface(null,Typeface.BOLD);root.addView(network);
        status=label("",15);status.setMinHeight(dp(64));status.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);root.addView(status);
        LinearLayout first=row();first.addView(button("重新扫码",v->{if(session.phase!=ConnectionSession.Phase.APPROVAL) startScan();}));
        light=button("手电筒",v->{if(session.phase==ConnectionSession.Phase.SCANNING && has(Manifest.permission.CAMERA)) {torch=!torch;camera.setTorch(torch);light.setText(torch?"关闭灯光":"手电筒");}});first.addView(light);root.addView(first);
        LinearLayout second=row();second.addView(button("Wi-Fi 设置",v->openSettings(Settings.ACTION_WIFI_SETTINGS)));second.addView(button("权限 / 定位",v->{
            if(has(Manifest.permission.ACCESS_FINE_LOCATION) && !locationEnabled()) openSettings(Settings.ACTION_LOCATION_SOURCE_SETTINGS);
            else startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,android.net.Uri.parse("package:"+getPackageName())));
        }));root.addView(second);
        root.addView(label("仅本机识别 · 不上传密码 · 不保存扫码记录",11));
        if(state!=null) {
            permissionsAsked=state.getBoolean("permissionsAsked");session.target=state.getString("target","");session.deadline=state.getLong("deadline");failureText=state.getString("failure",failureText);
            try {session.phase=ConnectionSession.Phase.valueOf(state.getString("phase","SCANNING"));} catch(IllegalArgumentException ignored) {session.scan();}
            // A recreated Activity must re-confirm with fresh network callbacks before showing green.
            if(session.phase==ConnectionSession.Phase.SUCCESS) session.connect(SystemClock.elapsedRealtime());
        }
        render();
    }
    private boolean has(String permission) {return checkSelfPermission(permission)==PackageManager.PERMISSION_GRANTED;}
    private boolean locationEnabled() {LocationManager manager=getSystemService(LocationManager.class);return manager!=null && manager.isLocationEnabled();}
    private void startScan() {
        generation++;if(attemptWork!=null) {attemptWork.cancel(true);attemptWork=null;}handler.removeCallbacks(ticker);session.scan();torch=false;camera.setTorch(false);light.setText("手电筒");render();
        ArrayList<String> missing=new ArrayList<>();
        if(!has(Manifest.permission.CAMERA)) missing.add(Manifest.permission.CAMERA);
        if(!has(Manifest.permission.ACCESS_FINE_LOCATION)) {missing.add(Manifest.permission.ACCESS_COARSE_LOCATION);missing.add(Manifest.permission.ACCESS_FINE_LOCATION);}
        if(!missing.isEmpty() && !permissionsAsked) {permissionsAsked=true;requestPermissions(missing.toArray(new String[0]),PERMISSIONS);return;}
        if(resumed && has(Manifest.permission.CAMERA)) camera.resume();
        else if(!has(Manifest.permission.CAMERA)) status.setText("请在“权限 / 定位”中允许相机权限后重新扫码。");
    }
    private void receive(String raw) {
        camera.setTorch(false);camera.pause();torch=false;light.setText("手电筒");
        try {
            WifiQr qr=WifiQr.parse(raw);
            session.approval(qr.ssid);render();
            if(!has(Manifest.permission.ACCESS_FINE_LOCATION) || !locationEnabled()) {fail("请允许精确位置权限并开启定位，以确认连接的 Wi-Fi 名称");return;}
            if(PrivilegedWifi.available(this)) {
                beginConnecting();int attempt=generation;
                attemptWork=wifiWorker.submit(()-> {
                    try {PrivilegedWifi.connect(this,qr);}
                    catch(Exception failure) {handler.post(()-> {if(!isDestroyed() && generation==attempt && session.phase==ConnectionSession.Phase.CONNECTING) fail("直接连接失败，请检查网络密码和系统权限");});}
                });return;
            }
            WifiNetworkSuggestion.Builder builder=new WifiNetworkSuggestion.Builder().setSsid(qr.ssid).setIsHiddenSsid(qr.hidden);
            if(qr.security.equals("WPA")) builder.setWpa2Passphrase(qr.password);
            else if(qr.security.equals("WPA3")) builder.setWpa3Passphrase(qr.password);
            ArrayList<WifiNetworkSuggestion> list=new ArrayList<>();list.add(builder.build());
            startActivityForResult(new Intent(Settings.ACTION_WIFI_ADD_NETWORKS).putParcelableArrayListExtra(Settings.EXTRA_WIFI_NETWORK_LIST,list),SAVE);
        } catch(IllegalArgumentException error) {fail(error.getMessage());}
        catch(ActivityNotFoundException|SecurityException error) {fail("系统未允许添加网络，请使用 Wi-Fi 设置");}
    }
    private void beginConnecting() {session.connect(SystemClock.elapsedRealtime());render();schedule();}
    private void fail(String text) {session.fail(SystemClock.elapsedRealtime());failureText=text;camera.pause();render();schedule();}
    private void schedule() {handler.removeCallbacks(ticker);if(resumed) handler.post(ticker);}
    private void render() {
        boolean success=session.phase==ConnectionSession.Phase.SUCCESS,failed=session.phase==ConnectionSession.Phase.FAILURE;
        root.setBackgroundColor(success?GREEN:failed?RED:NORMAL);
        for(TextView text:labels) text.setTextColor(success||failed?Color.WHITE:Color.rgb(25,49,45));
        preview.setVisibility(success||failed?View.GONE:View.VISIBLE);
        light.setEnabled(session.phase==ConnectionSession.Phase.SCANNING && getPackageManager().hasSystemFeature(PackageManager.FEATURE_CAMERA_FLASH));
        String name=session.target.isEmpty()?"等待识别网络":(success?"已连接：":"目标 Wi-Fi：")+session.target;
        if(!network.getText().toString().equals(name)) network.setText(name);
        int seconds=session.seconds(SystemClock.elapsedRealtime());
        String message="";
        switch(session.phase) {
            case SCANNING:message=PrivilegedWifi.available(this)?"预装模式 · 扫码后直接连接":"扫码后请在系统窗口确认连接";break;
            case APPROVAL:message="请在系统窗口确认连接此 Wi-Fi";break;
            case CONNECTING:message="正在连接并检查 Wi-Fi 状态… "+seconds+" 秒";break;
            case SUCCESS:message="连接成功，"+seconds+" 秒后自动隐藏";break;
            case FAILURE:message=failureText+"\n"+seconds+" 秒后重新扫码";break;
            default:break;
        }
        if(!status.getText().toString().equals(message)) status.setText(message);
    }
    @Override protected void onActivityResult(int request,int result,Intent data) {
        super.onActivityResult(request,result,data);if(request!=SAVE) return;
        if(result!=RESULT_OK) {fail("已取消连接");return;}
        ArrayList<Integer> results=data==null?null:data.getIntegerArrayListExtra(Settings.EXTRA_WIFI_NETWORK_RESULT_LIST);
        if(results!=null && !results.isEmpty() && (results.get(0)==Settings.ADD_WIFI_RESULT_SUCCESS || results.get(0)==Settings.ADD_WIFI_RESULT_ALREADY_EXISTS)) beginConnecting();
        else fail("系统未能保存网络，请检查二维码或密码");
    }
    @Override public void onRequestPermissionsResult(int request,String[] permissions,int[] grants) {super.onRequestPermissionsResult(request,permissions,grants);if(request==PERMISSIONS) {wifiStatus.stop();wifiStatus.start();startScan();}}
    @Override protected void onResume() {
        super.onResume();resumed=true;wifiStatus.start();
        if(session.phase==ConnectionSession.Phase.SCANNING) startScan();
        else if(session.phase!=ConnectionSession.Phase.APPROVAL) schedule();
    }
    @Override protected void onPause() {resumed=false;handler.removeCallbacks(ticker);camera.setTorch(false);camera.pause();torch=false;light.setText("手电筒");super.onPause();}
    @Override protected void onDestroy() {generation++;handler.removeCallbacksAndMessages(null);wifiStatus.stop();wifiWorker.shutdownNow();super.onDestroy();}
    @Override protected void onSaveInstanceState(Bundle state) {super.onSaveInstanceState(state);state.putBoolean("permissionsAsked",permissionsAsked);state.putString("phase",session.phase.name());state.putString("target",session.target);state.putLong("deadline",session.deadline);state.putString("failure",failureText);}
    private void openSettings(String action) {try {startActivity(new Intent(action));} catch(ActivityNotFoundException error) {fail("无法打开系统设置，请手动进入设置");}}
    private TextView label(String value,int size) {TextView text=new TextView(this);text.setText(value);text.setTextSize(size);text.setPadding(0,dp(4),0,dp(4));labels.add(text);return text;}
    private LinearLayout row() {LinearLayout row=new LinearLayout(this);row.setOrientation(LinearLayout.HORIZONTAL);return row;}
    private Button button(String value,View.OnClickListener listener) {Button button=new Button(this);button.setText(value);button.setTextColor(Color.WHITE);button.setTextSize(13);button.setAllCaps(false);button.setMinWidth(0);button.setPadding(dp(8),0,dp(8),0);GradientDrawable background=new GradientDrawable();background.setColor(Color.rgb(18,95,86));background.setCornerRadius(dp(10));button.setBackground(background);LinearLayout.LayoutParams params=new LinearLayout.LayoutParams(0,dp(48),1);params.setMargins(dp(3),dp(5),dp(3),0);button.setLayoutParams(params);button.setOnClickListener(listener);return button;}
    private int dp(int value) {return Math.round(value*getResources().getDisplayMetrics().density);}
}
