package cn.scanwifi.app;
import org.junit.Test;
import static org.junit.Assert.*;
public class TabletLayoutTest {
    @Test public void landscapeTabletUsesTwoColumns() {TabletLayout t=new TabletLayout(1280,800);assertTrue(t.sideBySide);assertEquals(1120,t.contentWidth);assertTrue(t.previewHeight<=420);assertEquals(20,t.titleSize);}
    @Test public void portraitTabletIsCenteredAndBounded() {TabletLayout t=new TabletLayout(800,1280);assertFalse(t.sideBySide);assertEquals(720,t.contentWidth);assertEquals(16,t.networkSize);}
    @Test public void narrowSplitScreenFallsBackToSingleColumn() {TabletLayout t=new TabletLayout(520,800);assertFalse(t.sideBySide);assertTrue(t.contentWidth<=488);}
    @Test public void smallPhoneDoesNotUseTabletColumns() {TabletLayout t=new TabletLayout(320,480);assertFalse(t.sideBySide);assertEquals(288,t.contentWidth);assertTrue(t.previewHeight>=180);}
}
