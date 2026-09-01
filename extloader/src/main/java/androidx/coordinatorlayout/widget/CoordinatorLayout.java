package androidx.coordinatorlayout.widget;

import android.content.Context;
import android.util.AttributeSet;
import android.widget.FrameLayout;

public class CoordinatorLayout extends FrameLayout {
    public CoordinatorLayout(Context context) { super(context); }
    public CoordinatorLayout(Context context, AttributeSet attrs) { super(context); }

    public static class LayoutParams extends FrameLayout.LayoutParams {
        public LayoutParams(int w, int h) { super(w, h); }
        public void setBehavior(Object behavior) {}
        public Object getBehavior() { return null; }
    }
}
