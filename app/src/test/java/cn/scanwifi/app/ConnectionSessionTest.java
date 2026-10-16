package cn.scanwifi.app;
import org.junit.Test;
import static org.junit.Assert.*;
public class ConnectionSessionTest {
    @Test public void suggestionApprovalTimeoutAllowsRetry() {ConnectionSession s=new ConnectionSession();s.approval("Home");s.waitForApproval(1000);assertEquals(ConnectionSession.Action.NONE,s.tick(60999,"",false));assertEquals(ConnectionSession.Action.FAILURE,s.tick(61000,"",false));assertEquals(ConnectionSession.Action.SCAN,s.tick(66000,"",false));}
    private ConnectionSession connecting() {ConnectionSession s=new ConnectionSession();s.approval("测试网络");s.connect(1000);return s;}
    @Test public void savedNetworkDoesNotMeanConnected() {ConnectionSession s=connecting();assertEquals(ConnectionSession.Action.NONE,s.tick(2000,"",false));assertEquals(ConnectionSession.Phase.CONNECTING,s.phase);}
    @Test public void wrongNetworkNeverSucceeds() {ConnectionSession s=connecting();assertEquals(ConnectionSession.Action.NONE,s.tick(2000,"Other",true));}
    @Test public void associationWithoutAddressDoesNotSucceed() {ConnectionSession s=connecting();assertEquals(ConnectionSession.Action.NONE,s.tick(2000,"测试网络",false));}
    @Test public void successWaitsThreeSecondsBeforeHide() {ConnectionSession s=connecting();assertEquals(ConnectionSession.Action.SUCCESS,s.tick(2000,"测试网络",true));assertEquals(3,s.seconds(2000));assertEquals(ConnectionSession.Action.NONE,s.tick(4999,"测试网络",true));assertEquals(ConnectionSession.Action.HIDE,s.tick(5000,"测试网络",true));assertEquals(ConnectionSession.Action.NONE,s.tick(6000,"测试网络",true));}
    @Test public void timeoutShowsFailureThenWaitsFiveSeconds() {ConnectionSession s=connecting();assertEquals(ConnectionSession.Action.NONE,s.tick(30999,"",false));assertEquals(ConnectionSession.Action.FAILURE,s.tick(31000,"",false));assertEquals(5,s.seconds(31000));assertEquals(ConnectionSession.Action.NONE,s.tick(35999,"",false));assertEquals(ConnectionSession.Action.SCAN,s.tick(36000,"",false));assertEquals("",s.target);}
    @Test public void approvalDoesNotTimeoutBehindSystemDialog() {ConnectionSession s=new ConnectionSession();s.approval("Home");assertEquals(ConnectionSession.Action.NONE,s.tick(999999,"Home",true));assertEquals(ConnectionSession.Phase.APPROVAL,s.phase);}
    @Test public void failureCancellationCannotBecomeSuccess() {ConnectionSession s=connecting();s.fail(1500);assertEquals(ConnectionSession.Action.NONE,s.tick(2000,"测试网络",true));assertEquals(ConnectionSession.Action.SCAN,s.tick(6500,"测试网络",true));}
    @Test public void droppedConnectionDuringSuccessTurnsRed() {ConnectionSession s=connecting();s.tick(2000,"测试网络",true);assertEquals(ConnectionSession.Action.FAILURE,s.tick(2500,"Other",true));assertEquals(ConnectionSession.Action.SCAN,s.tick(7500,"",false));}
    @Test public void manualRetryClearsOldTimerAndTarget() {ConnectionSession s=connecting();s.tick(2000,"测试网络",true);s.scan();assertEquals(ConnectionSession.Action.NONE,s.tick(5000,"测试网络",true));assertEquals("",s.target);}
}
