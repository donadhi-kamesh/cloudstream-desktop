package android.widget;

import android.content.Context;
import android.view.ViewGroup;

public class RelativeLayout extends ViewGroup {
    public static final int TRUE = -1;
    public static final int LEFT_OF = 0;
    public static final int RIGHT_OF = 1;
    public static final int ABOVE = 2;
    public static final int BELOW = 3;
    public static final int ALIGN_BASELINE = 4;
    public static final int ALIGN_LEFT = 5;
    public static final int ALIGN_TOP = 6;
    public static final int ALIGN_RIGHT = 7;
    public static final int ALIGN_BOTTOM = 8;
    public static final int ALIGN_PARENT_LEFT = 9;
    public static final int ALIGN_PARENT_TOP = 10;
    public static final int ALIGN_PARENT_RIGHT = 11;
    public static final int ALIGN_PARENT_BOTTOM = 12;
    public static final int CENTER_IN_PARENT = 13;
    public static final int CENTER_HORIZONTAL = 14;
    public static final int CENTER_VERTICAL = 15;
    public static final int ALIGN_START = 18;
    public static final int ALIGN_END = 19;
    public static final int ALIGN_PARENT_START = 20;
    public static final int ALIGN_PARENT_END = 21;
    public static final int START_OF = 16;
    public static final int END_OF = 17;

    public RelativeLayout(Context context) { super(context); }
    public RelativeLayout(Context context, android.util.AttributeSet attrs) { super(context); }

    public static class LayoutParams extends ViewGroup.MarginLayoutParams {
        public boolean alignWithParent;
        public LayoutParams(int w, int h) { super(w, h); }
        public LayoutParams(ViewGroup.LayoutParams source) { super(source.width, source.height); }
        public void addRule(int verb) {}
        public void addRule(int verb, int subject) {}
        public void removeRule(int verb) {}
        public int getRule(int verb) { return 0; }
        public void setMarginStart(int start) { super.setMarginStart(start); }
        public void setMarginEnd(int end) { super.setMarginEnd(end); }
    }
}
