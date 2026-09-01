package android.content.res;
public class ColorStateList {
    private final int defaultColor;
    public ColorStateList(int[][] states, int[] colors) {
        this.defaultColor = colors != null && colors.length > 0 ? colors[0] : 0;
    }
    public static ColorStateList valueOf(int color) { return new ColorStateList(new int[][]{new int[0]}, new int[]{color}); }
    public int getDefaultColor() { return defaultColor; }
    public int getColorForState(int[] stateSet, int defaultColor) { return this.defaultColor != 0 ? this.defaultColor : defaultColor; }
    public boolean isStateful() { return false; }
}
