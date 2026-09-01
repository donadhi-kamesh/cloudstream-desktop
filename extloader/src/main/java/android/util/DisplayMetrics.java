package android.util;
public class DisplayMetrics {
    public int widthPixels = 1920;
    public int heightPixels = 1080;
    public DisplayMetrics() {
        try {
            java.awt.Dimension s = java.awt.Toolkit.getDefaultToolkit().getScreenSize();
            widthPixels = s.width;
            heightPixels = s.height;
        } catch (Throwable ignored) {}
    }
    public float density = 1f;
    public int densityDpi = 160;
    public float scaledDensity = 1f;
    public float xdpi = 160f;
    public float ydpi = 160f;
}
