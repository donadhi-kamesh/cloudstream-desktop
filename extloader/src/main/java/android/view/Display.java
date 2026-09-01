package android.view;

public class Display {
    public static final int DEFAULT_DISPLAY = 0;
    public int getDisplayId() { return DEFAULT_DISPLAY; }
    public void getSize(android.graphics.Point outSize) {
        if (outSize != null) { outSize.x = 1920; outSize.y = 1080; }
    }
    public void getRealSize(android.graphics.Point outSize) { getSize(outSize); }
    public int getWidth() { return 1920; }
    public int getHeight() { return 1080; }
    public int getRotation() { return 0; }
    public float getRefreshRate() { return 60f; }
    public android.util.DisplayMetrics getMetrics() { return new android.util.DisplayMetrics(); }
}
