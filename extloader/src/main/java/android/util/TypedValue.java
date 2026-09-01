package android.util;

public class TypedValue {
    public static final int TYPE_NULL = 0;
    public static final int TYPE_STRING = 3;
    public static final int TYPE_DIMENSION = 5;
    public static final int TYPE_INT_COLOR_ARGB8 = 0x1c;
    public int type;
    public int data;
    public int resourceId;
    public CharSequence string;
    public float getFloat() { return Float.intBitsToFloat(data); }
    public int getComplexUnit() { return 0; }
    public static float complexToDimension(int data, DisplayMetrics metrics) { return 0; }
    public static int complexToDimensionPixelSize(int data, DisplayMetrics metrics) { return 0; }
}
