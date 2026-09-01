package androidx.constraintlayout.widget;

import android.content.Context;
import android.util.AttributeSet;
import android.view.ViewGroup;
import android.widget.RelativeLayout;

/** Constraint layouts inflate as a relative/vertical stack on desktop. */
public class ConstraintLayout extends RelativeLayout {
    public ConstraintLayout(Context context) { super(context); }
    public ConstraintLayout(Context context, AttributeSet attrs) { super(context, attrs); }
    public ConstraintLayout(Context context, AttributeSet attrs, int defStyleAttr) { super(context, attrs); }

    public static class LayoutParams extends ViewGroup.MarginLayoutParams {
        public int startToStart, startToEnd, endToStart, endToEnd;
        public int topToTop, topToBottom, bottomToTop, bottomToBottom;
        public float horizontalBias = 0.5f;
        public float verticalBias = 0.5f;
        public LayoutParams(int w, int h) { super(w, h); }
        public LayoutParams(ViewGroup.LayoutParams source) { super(source.width, source.height); }
    }
}
