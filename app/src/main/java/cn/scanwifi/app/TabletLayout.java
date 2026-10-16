package cn.scanwifi.app;

final class TabletLayout {
    final boolean sideBySide;
    final int contentWidth, previewHeight, titleSize, networkSize;
    TabletLayout(int width,int height) {
        sideBySide=width>=720 && width>height;
        boolean tablet=width>=600;
        contentWidth=Math.min(Math.max(1,width-32),sideBySide?1120:tablet?720:480);
        previewHeight=Math.min(420,Math.max(180,sideBySide?height-180:height*42/100));
        titleSize=tablet?20:22;networkSize=tablet?16:18;
    }
}
