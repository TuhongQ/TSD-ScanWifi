package cn.scanwifi.app;

/** Monotonic-time connection workflow; it never treats a save request as a connection. */
final class ConnectionSession {
    static final long CONNECT_TIMEOUT_MS=30_000, SUCCESS_MS=3_000, RETRY_MS=5_000;
    enum Phase { SCANNING, APPROVAL, CONNECTING, SUCCESS, FAILURE, HIDDEN }
    enum Action { NONE, SUCCESS, FAILURE, HIDE, SCAN }
    Phase phase=Phase.SCANNING;
    String target="";
    long deadline;
    void scan() { phase=Phase.SCANNING; target=""; deadline=0; }
    void approval(String ssid) { target=ssid; phase=Phase.APPROVAL; deadline=0; }
    void waitForApproval(long now) { phase=Phase.APPROVAL; deadline=now+60_000; }
    void connect(long now) { phase=Phase.CONNECTING; deadline=now+CONNECT_TIMEOUT_MS; }
    void fail(long now) { phase=Phase.FAILURE; deadline=now+RETRY_MS; }
    boolean matches(String ssid, boolean ready) { return ready && !target.isEmpty() && target.equals(ssid); }
    Action tick(long now, String ssid, boolean ready) {
        if(phase==Phase.APPROVAL && deadline>0 && now>=deadline) { fail(now); return Action.FAILURE; }
        if(phase==Phase.CONNECTING) {
            if(matches(ssid,ready)) { phase=Phase.SUCCESS; deadline=now+SUCCESS_MS; return Action.SUCCESS; }
            if(now>=deadline) { fail(now); return Action.FAILURE; }
        } else if(phase==Phase.SUCCESS) {
            if(!matches(ssid,ready)) { fail(now); return Action.FAILURE; }
            if(now>=deadline) { phase=Phase.HIDDEN; return Action.HIDE; }
        } else if(phase==Phase.FAILURE && now>=deadline) { scan(); return Action.SCAN; }
        return Action.NONE;
    }
    int seconds(long now) { return (int)Math.max(0,(deadline-now+999)/1000); }
}
