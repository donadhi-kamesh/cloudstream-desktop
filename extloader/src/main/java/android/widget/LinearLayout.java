package android.widget;

import android.content.Context;
import android.view.ViewGroup;

public class LinearLayout extends ViewGroup {
    public static final int HORIZONTAL = 0;
    public static final int VERTICAL = 1;
    private int orientation = VERTICAL;
    private int gravity = 0;
    public LinearLayout(Context context) { super(context); }
    public LinearLayout(Context context, android.util.AttributeSet attrs) {
        super(context);
        if (attrs != null) {
            String o = firstAttribute(attrs, "orientation");
            if ("horizontal".equals(o)) orientation = HORIZONTAL;
            else if ("vertical".equals(o)) orientation = VERTICAL;
        }
    }
    public LinearLayout(Context context, android.util.AttributeSet attrs, int defStyleAttr) { this(context, attrs); }
    public void setOrientation(int orientation) { this.orientation = orientation; }
    public int getOrientation() { return orientation; }
    public void setGravity(int gravity) { this.gravity = gravity; }
    public int getGravity() { return gravity; }
    public void setHorizontalGravity(int gravity) { this.gravity = gravity; }
    public void setVerticalGravity(int gravity) { this.gravity = gravity; }
    public void setWeightSum(float weightSum) {}
    public float getWeightSum() { return 0f; }
    private static String firstAttribute(android.util.AttributeSet attrs, String name) {
        String v = attrs.getAttributeValue("http://schemas.android.com/apk/res/android", name);
        if (v != null) return v;
        v = attrs.getAttributeValue(null, name);
        if (v != null) return v;
        return attrs.getAttributeValue(null, "android:" + name);
    }
    public static class LayoutParams extends ViewGroup.MarginLayoutParams {
        public float weight;
        public int gravity;
        public LayoutParams(int w, int h) { super(w, h); }
        public LayoutParams(int w, int h, float weight) { super(w, h); this.weight = weight; }
        public LayoutParams(ViewGroup.LayoutParams source) { super(source.width, source.height); }
    }
}
