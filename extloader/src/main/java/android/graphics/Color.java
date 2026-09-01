package android.graphics;
public class Color {
    public static final int BLACK = 0xFF000000;
    public static final int WHITE = 0xFFFFFFFF;
    public static final int TRANSPARENT = 0;
    public static int parseColor(String colorString) {
        if (colorString == null || colorString.isEmpty()) return BLACK;
        String s = colorString.trim();
        if (s.charAt(0) == '#') {
            String hex = s.substring(1);
            if (hex.length() == 3) {
                hex = "" + hex.charAt(0) + hex.charAt(0) + hex.charAt(1) + hex.charAt(1) + hex.charAt(2) + hex.charAt(2);
            }
            if (hex.length() == 6) hex = "FF" + hex;
            if (hex.length() == 8) {
                try { return (int) Long.parseLong(hex, 16); } catch (NumberFormatException ignored) {}
            }
        }
        return BLACK;
    }
    public static int argb(int a, int r, int g, int b) { return (a << 24) | (r << 16) | (g << 8) | b; }
    public static int rgb(int r, int g, int b) { return argb(255, r, g, b); }
}
