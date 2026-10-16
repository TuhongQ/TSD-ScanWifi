package cn.scanwifi.app;

import android.net.wifi.WifiManager;
import android.os.Build;
import java.util.concurrent.Executor;

/** API 31 listener lives in its own class so Android 11 can use suggestions too. */
final class SuggestionApproval {
    private int status=-1;
    private Api31 listener;
    boolean supported() {return Build.VERSION.SDK_INT>=31;}
    boolean approved() {return status==WifiManager.STATUS_SUGGESTION_APPROVAL_APPROVED_BY_USER || status==WifiManager.STATUS_SUGGESTION_APPROVAL_APPROVED_BY_CARRIER_PRIVILEGE;}
    boolean denied() {return status==WifiManager.STATUS_SUGGESTION_APPROVAL_REJECTED_BY_USER;}
    void start(WifiManager wifi,Executor executor,Runnable changed) {
        if(supported() && listener==null && wifi!=null) {
            listener=new Api31(wifi,value->{status=value;changed.run();});
            try {wifi.addSuggestionUserApprovalStatusListener(executor,listener.callback);}
            catch(SecurityException error) {listener=null;}
        }
    }
    void stop() {if(listener!=null) {try {listener.wifi.removeSuggestionUserApprovalStatusListener(listener.callback);} catch(SecurityException|IllegalArgumentException ignored) {} listener=null;}}
    private static final class Api31 {
        final WifiManager wifi;
        final WifiManager.SuggestionUserApprovalStatusListener callback;
        Api31(WifiManager manager,java.util.function.IntConsumer changed) {wifi=manager;callback=changed::accept;}
    }
}
