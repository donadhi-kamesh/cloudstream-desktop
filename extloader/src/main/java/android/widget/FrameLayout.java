package android.widget;

import android.content.Context;
import android.view.ViewGroup;

public class FrameLayout extends ViewGroup {
    public FrameLayout(Context context) { super(context); }
    public FrameLayout(Context context, android.util.AttributeSet attrs) { super(context); }
    public static class LayoutParams extends ViewGroup.MarginLayoutParams {
        public int gravity = 0;
        public LayoutParams(int w, int h) { super(w, h); }
        public LayoutParams(int w, int h, int gravity) { super(w, h); this.gravity = gravity; }
        public LayoutParams(ViewGroup.LayoutParams source) { super(source.width, source.height); }
    }
}
