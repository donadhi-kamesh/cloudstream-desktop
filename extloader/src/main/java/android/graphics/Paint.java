package android.graphics;
public class Paint {
    public static final int ANTI_ALIAS_FLAG = 1;
    public static final int FILTER_BITMAP_FLAG = 2;
    public static final int DITHER_FLAG = 4;
    public static final int LINEAR_TEXT_FLAG = 64;
    public Paint() {}
    public Paint(int flags) {}
    public void setColor(int color) {}
    public void setAlpha(int a) {}
    public void setAntiAlias(boolean aa) {}
    public void setStyle(Style style) {}
    public void setStrokeWidth(float width) {}
    public void setTextSize(float size) {}
    public void setTypeface(Typeface typeface) {}
    public void setColorFilter(ColorFilter filter) {}
    public float measureText(String text) { return text == null ? 0 : text.length() * 8; }
    public enum Style { FILL, STROKE, FILL_AND_STROKE }
}
