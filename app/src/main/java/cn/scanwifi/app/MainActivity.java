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
import android.net.wifi.WifiManager;
import android.os.*;
import android.provider.Settings;
import android.view.*;
import android.widget.*;
import com.google.zxing.BarcodeFormat;
import com.journeyapps.barcodescanner.*;
import java.util.*;

public final class MainActivity extends Activity {
    private static final int PERMISSIONS=10, WIFI_PANEL=30;
    private static final int NORMAL=Color.rgb(244,248,247), GREEN=Color.rgb(19,111,65), RED=Color.rgb(169,39,48);
    private final Handler handler=new Handler(Looper.getMainLooper());
    private final ConnectionSession session=new ConnectionSession();
    private final java.util.concurrent.ExecutorService wifiWorker=java.util.concurrent.Executors.newSingleThreadExecutor();
    private java.util.concurrent.Future<?> attemptWork;
    private WifiConnectionStatus wifiStatus;
    private WifiManager wifi;
    private final SuggestionApproval approval=new SuggestionApproval();
    private WifiManager.SuggestionConnectionStatusListener suggestionFailure;
    private boolean usingSuggestions, suggestionSubmitted, wifiStarting, wifiPanelShown, wifiPanelActive;
    private BroadcastReceiver wifiReceiver;
    private BarcodeView camera;
    private LinearLayout root;
    private ScrollView scroll;
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
        if(session.phase==ConnectionSession.Phase.APPROVAL && usingSuggestions && connected) session.connect(now);
        ConnectionSession.Action action=session.tick(now,connected?session.target:"",connected);
        if(action==ConnectionSession.Action.FAILURE) failureText="未连接到目标 Wi-Fi，请检查密码、信号和权限";
        if(action==ConnectionSession.Action.HIDE) {finishAndRemoveTask();return;}
        if(action==ConnectionSession.Action.SCAN) {startScan();return;}
        render();
        if(session.phase!=ConnectionSession.Phase.SCANNING) handler.postDelayed(this,250);
    }};

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
        wifiStatus=new WifiConnectionStatus(this,handler);
        wifi=getApplicationContext().getSystemService(WifiManager.class);
        android.graphics.Rect bounds=getWindowManager().getCurrentWindowMetrics().getBounds();
        TabletLayout layout=new TabletLayout(Math.round(bounds.width()/getResources().getDisplayMetrics().density),Math.round(bounds.height()/getResources().getDisplayMetrics().density));
        root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setPadding(dp(22),dp(14),dp(22),dp(14));
        root.setOnApplyWindowInsetsListener((v,insets)-> {
            android.graphics.Insets bars=insets.getInsets(WindowInsets.Type.systemBars()|WindowInsets.Type.displayCutout());
            int margin=Math.max(dp(16),(bounds.width()-bars.left-bars.right-dp(layout.contentWidth))/2);
            v.setPadding(margin+bars.left,dp(14)+bars.top,margin+bars.right,dp(14)+bars.bottom);return insets;
        });
        scroll=new ScrollView(this);scroll.setFillViewport(true);scroll.addView(root);setContentView(scroll);
        root.addView(label("测试工程部开发",11));
        TextView title=label("扫码连接WIFI",layout.titleSize);title.setTypeface(null,Typeface.BOLD);root.addView(title);
        root.addView(label("对准 Wi-Fi 二维码，即可开始连接",12));
        LinearLayout body=new LinearLayout(this);body.setOrientation(layout.sideBySide?LinearLayout.HORIZONTAL:LinearLayout.VERTICAL);body.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams bodyParams=new LinearLayout.LayoutParams(-1,-2);bodyParams.setMargins(0,dp(12),0,dp(8));root.addView(body,bodyParams);
        preview=new FrameLayout(this);
        LinearLayout.LayoutParams frameLayout=layout.sideBySide?new LinearLayout.LayoutParams(0,dp(layout.previewHeight),1.35f):new LinearLayout.LayoutParams(-1,dp(layout.previewHeight));body.addView(preview,frameLayout);
        LinearLayout details=new LinearLayout(this);details.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams detailParams=layout.sideBySide?new LinearLayout.LayoutParams(0,-2,1):new LinearLayout.LayoutParams(-1,-2);detailParams.setMargins(layout.sideBySide?dp(24):0,layout.sideBySide?0:dp(12),0,0);body.addView(details,detailParams);
        camera=new BarcodeView(this);camera.setDecoderFactory(new DefaultDecoderFactory(Collections.singletonList(BarcodeFormat.QR_CODE)));preview.addView(camera,new FrameLayout.LayoutParams(-1,-1));
        camera.decodeContinuous(new BarcodeCallback() {public void barcodeResult(BarcodeResult result) {if(resumed && !wifiStarting && session.phase==ConnectionSession.Phase.SCANNING) receive(result.getText());}});
        camera.addStateListener(new CameraPreview.StateListener() {
            public void previewSized() {} public void previewStarted() {} public void previewStopped() {} public void cameraClosed() {}
            public void cameraError(Exception error) {if(session.phase==ConnectionSession.Phase.SCANNING) fail("相机不可用，请关闭其他使用相机的应用");}
        });
        TextView hint=label("将二维码放入镜头中央",11);labels.remove(hint);hint.setTextColor(Color.WHITE);hint.setGravity(Gravity.CENTER);hint.setBackgroundColor(0xAA102C28);
        preview.addView(hint,new FrameLayout.LayoutParams(-1,dp(36),Gravity.BOTTOM));
        network=label("等待识别网络",layout.networkSize);network.setTypeface(null,Typeface.BOLD);details.addView(network);
        status=label("",12);status.setMinHeight(dp(56));status.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);details.addView(status);
        LinearLayout first=row();first.addView(button("重新扫码",v->startScan()));
        light=button("手电筒",v->{if(session.phase==ConnectionSession.Phase.SCANNING && has(Manifest.permission.CAMERA)) {torch=!torch;camera.setTorch(torch);light.setText(torch?"关闭灯光":"手电筒");}});first.addView(light);details.addView(first);
        LinearLayout second=row();second.addView(button("Wi-Fi 设置",v->openSettings(Settings.ACTION_WIFI_SETTINGS)));second.addView(button("权限 / 定位",v->{
            if(has(Manifest.permission.ACCESS_FINE_LOCATION) && !locationEnabled()) openSettings(Settings.ACTION_LOCATION_SOURCE_SETTINGS);
            else startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,android.net.Uri.parse("package:"+getPackageName())));
        }));details.addView(second);
        root.addView(label("仅本机识别 · 不上传密码 · 不保存扫码记录",10));
        if(state!=null) {
            permissionsAsked=state.getBoolean("permissionsAsked");session.target=state.getString("target","");session.deadline=state.getLong("deadline");failureText=state.getString("failure",failureText);
            usingSuggestions=state.getBoolean("usingSuggestions");suggestionSubmitted=state.getBoolean("suggestionSubmitted");wifiPanelShown=state.getBoolean("wifiPanelShown");wifiPanelActive=state.getBoolean("wifiPanelActive");
            try {session.phase=ConnectionSession.Phase.valueOf(state.getString("phase","SCANNING"));} catch(IllegalArgumentException ignored) {session.scan();}
            // A recreated Activity must re-confirm with fresh network callbacks before showing green.
            if(session.phase==ConnectionSession.Phase.SUCCESS) session.connect(SystemClock.elapsedRealtime());
        }
        wifiReceiver=new BroadcastReceiver() {public void onReceive(Context context,Intent intent) {
            if(intent.getIntExtra(WifiManager.EXTRA_WIFI_STATE,-1)==WifiManager.WIFI_STATE_ENABLED && session.phase==ConnectionSession.Phase.SCANNING) {
                if(wifiPanelActive) {wifiPanelActive=false;finishActivity(WIFI_PANEL);}
                if(resumed) startScan();
            }
        }};
        IntentFilter wifiFilter=new IntentFilter(WifiManager.WIFI_STATE_CHANGED_ACTION);
        if(Build.VERSION.SDK_INT>=33) registerReceiver(wifiReceiver,wifiFilter,Context.RECEIVER_EXPORTED);else registerReceiver(wifiReceiver,wifiFilter);
        render();
    }
    private boolean has(String permission) {return checkSelfPermission(permission)==PackageManager.PERMISSION_GRANTED;}
    private boolean locationEnabled() {LocationManager manager=getSystemService(LocationManager.class);return manager!=null && manager.isLocationEnabled();}
    private boolean ensureWifiEnabled() {
        if(wifi!=null && wifi.isWifiEnabled()) return true;
        camera.pause();
        if(wifi==null) {status.setText("设备未提供 Wi-Fi 功能");return false;}
        if(PrivilegedWifi.available(this)) {
            if(wifiStarting) return false;
            wifiStarting=true;status.setText("正在自动开启 Wi-Fi…");
            wifiWorker.submit(()-> {
                boolean enabled;
                try {PrivilegedWifi.enable(this);enabled=true;} catch(Exception failure) {enabled=false;}
                final boolean ready=enabled;
                handler.post(()-> {if(!isDestroyed()) {wifiStarting=false;if(ready) startScan();else fail("系统未允许开启 Wi-Fi，请使用 Wi-Fi 设置");}});
            });return false;
        }
        status.setText("请开启 Wi-Fi；开启后自动返回扫码");
        if(!wifiPanelShown && resumed) {
            wifiPanelShown=true;wifiPanelActive=true;
            try {startActivityForResult(new Intent(Settings.Panel.ACTION_WIFI),WIFI_PANEL);}
            catch(ActivityNotFoundException error) {wifiPanelActive=false;openSettings(Settings.ACTION_WIFI_SETTINGS);}
        }
        return false;
    }
    private void registerSuggestionListeners() {
        approval.start(wifi,getMainExecutor(),this::handleSuggestionApproval);
        if(wifi!=null && suggestionFailure==null && has(Manifest.permission.ACCESS_FINE_LOCATION)) {
            suggestionFailure=(suggestion,reason)-> {
                if(usingSuggestions && suggestion.getSsid().equals(session.target) && (session.phase==ConnectionSession.Phase.CONNECTING || session.phase==ConnectionSession.Phase.APPROVAL)) fail(reason==WifiManager.STATUS_SUGGESTION_CONNECTION_FAILURE_AUTHENTICATION?"Wi-Fi 认证失败，请检查密码":"Wi-Fi 连接失败，请检查信号与网络配置");
            };
            try {wifi.addSuggestionConnectionStatusListener(getMainExecutor(),suggestionFailure);} catch(SecurityException error) {suggestionFailure=null;}
        }
    }
    private void handleSuggestionApproval() {
        if(isDestroyed() || !usingSuggestions || !suggestionSubmitted || session.phase!=ConnectionSession.Phase.APPROVAL) return;
        if(!approval.supported() || approval.approved()) beginConnecting();
        else if(approval.denied()) fail("未允许自动连接，请在系统特殊权限中允许本应用控制 Wi-Fi");
        else {render();schedule();}
    }
    private void startScan() {
        if(wifiStarting) return;
        usingSuggestions=false;suggestionSubmitted=false;
        generation++;if(attemptWork!=null) {attemptWork.cancel(true);attemptWork=null;}handler.removeCallbacks(ticker);session.scan();torch=false;camera.setTorch(false);light.setText("手电筒");render();
        ArrayList<String> missing=new ArrayList<>();
        if(!has(Manifest.permission.CAMERA)) missing.add(Manifest.permission.CAMERA);
        if(!has(Manifest.permission.ACCESS_FINE_LOCATION)) {missing.add(Manifest.permission.ACCESS_COARSE_LOCATION);missing.add(Manifest.permission.ACCESS_FINE_LOCATION);}
        if(!missing.isEmpty() && !permissionsAsked) {permissionsAsked=true;requestPermissions(missing.toArray(new String[0]),PERMISSIONS);return;}
        if(!ensureWifiEnabled()) return;
        if(resumed && has(Manifest.permission.CAMERA)) camera.resume();
        else if(!has(Manifest.permission.CAMERA)) status.setText("请在“权限 / 定位”中允许相机权限后重新扫码。");
    }
    private void receive(String raw) {
        camera.setTorch(false);camera.pause();torch=false;light.setText("手电筒");
        try {
            WifiQr qr=WifiQr.parse(raw);
            session.approval(qr.ssid);render();
            if(!has(Manifest.permission.ACCESS_FINE_LOCATION) || !locationEnabled()) {fail("请允许精确位置权限并开启定位，以确认连接的 Wi-Fi 名称");return;}
            if(wifi==null || !wifi.isWifiEnabled()) {fail("Wi-Fi 尚未开启，请使用 Wi-Fi 设置");return;}
            if(wifiStatus.connected(qr.ssid)) {beginConnecting();return;}
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
            usingSuggestions=true;suggestionSubmitted=false;session.waitForApproval(SystemClock.elapsedRealtime());render();schedule();int attempt=generation;
            attemptWork=wifiWorker.submit(()-> {
                int result;
                try {result=wifi.addNetworkSuggestions(list);} catch(SecurityException|IllegalArgumentException error) {result=WifiManager.STATUS_NETWORK_SUGGESTIONS_ERROR_APP_DISALLOWED;}
                final int addResult=result;
                handler.post(()-> {
                    if(isDestroyed() || generation!=attempt || session.phase!=ConnectionSession.Phase.APPROVAL) return;
                    if(addResult==WifiManager.STATUS_NETWORK_SUGGESTIONS_SUCCESS || addResult==WifiManager.STATUS_NETWORK_SUGGESTIONS_ERROR_ADD_DUPLICATE) {suggestionSubmitted=true;handleSuggestionApproval();}
                    else if(addResult==WifiManager.STATUS_NETWORK_SUGGESTIONS_ERROR_APP_DISALLOWED || addResult==WifiManager.STATUS_NETWORK_SUGGESTIONS_ERROR_ADD_NOT_ALLOWED) fail("系统未授权自动连接，请在特殊权限中允许本应用控制 Wi-Fi");
                    else fail("自动连接请求未成功，请检查系统 Wi-Fi 设置");
                });
            });
        } catch(IllegalArgumentException error) {fail(error.getMessage());}
        catch(ActivityNotFoundException|SecurityException error) {fail("系统未允许添加网络，请使用 Wi-Fi 设置");}
    }
    private void beginConnecting() {session.connect(SystemClock.elapsedRealtime());render();schedule();}
    private void fail(String text) {session.fail(SystemClock.elapsedRealtime());failureText=text;camera.pause();render();schedule();}
    private void schedule() {handler.removeCallbacks(ticker);if(resumed) handler.post(ticker);}
    private void render() {
        boolean success=session.phase==ConnectionSession.Phase.SUCCESS,failed=session.phase==ConnectionSession.Phase.FAILURE;
        root.setBackgroundColor(success?GREEN:failed?RED:NORMAL);
        scroll.setBackgroundColor(success?GREEN:failed?RED:NORMAL);
        for(TextView text:labels) text.setTextColor(success||failed?Color.WHITE:Color.rgb(25,49,45));
        preview.setVisibility(success||failed?View.GONE:View.VISIBLE);
        light.setEnabled(session.phase==ConnectionSession.Phase.SCANNING && getPackageManager().hasSystemFeature(PackageManager.FEATURE_CAMERA_FLASH));
        String name=session.target.isEmpty()?"等待识别网络":(success?"已连接：":"目标 Wi-Fi：")+session.target;
        if(!network.getText().toString().equals(name)) network.setText(name);
        int seconds=session.seconds(SystemClock.elapsedRealtime());
        String message="";
        switch(session.phase) {
            case SCANNING:message=PrivilegedWifi.available(this)?"扫码后直接连接":"扫码后自动尝试连接 · 首次需系统授权";break;
            case APPROVAL:message="首次请允许本应用连接 Wi-Fi，无需逐个点击保存";break;
            case CONNECTING:message="正在连接并检查 Wi-Fi 状态… "+seconds+" 秒";break;
            case SUCCESS:message="连接成功，"+seconds+" 秒后自动隐藏";break;
            case FAILURE:message=failureText+"\n"+seconds+" 秒后重新扫码";break;
            default:break;
        }
        if(!status.getText().toString().equals(message)) status.setText(message);
    }
    @Override protected void onActivityResult(int request,int result,Intent data) {
        super.onActivityResult(request,result,data);if(request==WIFI_PANEL) {wifiPanelActive=false;startScan();}
    }
    @Override public void onRequestPermissionsResult(int request,String[] permissions,int[] grants) {super.onRequestPermissionsResult(request,permissions,grants);if(request==PERMISSIONS) {wifiStatus.stop();wifiStatus.start();registerSuggestionListeners();startScan();}}
    @Override protected void onResume() {
        super.onResume();resumed=true;wifiStatus.stop();wifiStatus.start();
        if(session.phase==ConnectionSession.Phase.SUCCESS) session.connect(SystemClock.elapsedRealtime());
        else if(session.phase==ConnectionSession.Phase.CONNECTING) session.deadline=Math.max(session.deadline,SystemClock.elapsedRealtime()+1000);
        registerSuggestionListeners();
        if(session.phase==ConnectionSession.Phase.SCANNING) startScan();
        else {handleSuggestionApproval();schedule();}
    }
    @Override protected void onPause() {resumed=false;handler.removeCallbacks(ticker);wifiStatus.stop();camera.setTorch(false);camera.pause();torch=false;light.setText("手电筒");super.onPause();}
    @Override protected void onDestroy() {generation++;handler.removeCallbacksAndMessages(null);wifiStatus.stop();approval.stop();if(wifiReceiver!=null) unregisterReceiver(wifiReceiver);if(wifi!=null && suggestionFailure!=null) {try {wifi.removeSuggestionConnectionStatusListener(suggestionFailure);} catch(SecurityException|IllegalArgumentException ignored) {}}wifiWorker.shutdownNow();super.onDestroy();}
    @Override protected void onSaveInstanceState(Bundle state) {super.onSaveInstanceState(state);state.putBoolean("permissionsAsked",permissionsAsked);state.putString("phase",session.phase.name());state.putString("target",session.target);state.putLong("deadline",session.deadline);state.putString("failure",failureText);state.putBoolean("usingSuggestions",usingSuggestions);state.putBoolean("suggestionSubmitted",suggestionSubmitted);state.putBoolean("wifiPanelShown",wifiPanelShown);state.putBoolean("wifiPanelActive",wifiPanelActive);}
    private void openSettings(String action) {try {startActivity(new Intent(action));} catch(ActivityNotFoundException error) {fail("无法打开系统设置，请手动进入设置");}}
    private TextView label(String value,int size) {TextView text=new TextView(this);text.setText(value);text.setTextSize(size);text.setPadding(0,dp(4),0,dp(4));labels.add(text);return text;}
    private LinearLayout row() {LinearLayout row=new LinearLayout(this);row.setOrientation(LinearLayout.HORIZONTAL);return row;}
    private Button button(String value,View.OnClickListener listener) {Button button=new Button(this);button.setText(value);button.setTextColor(Color.WHITE);button.setTextSize(11);button.setAllCaps(false);button.setMinWidth(0);button.setPadding(dp(6),0,dp(6),0);GradientDrawable background=new GradientDrawable();background.setColor(Color.rgb(18,95,86));background.setCornerRadius(dp(8));button.setBackground(background);LinearLayout.LayoutParams params=new LinearLayout.LayoutParams(0,dp(48),1);params.setMargins(dp(3),dp(4),dp(3),0);button.setLayoutParams(params);button.setOnClickListener(listener);return button;}
    private int dp(int value) {return Math.round(value*getResources().getDisplayMetrics().density);}
}
