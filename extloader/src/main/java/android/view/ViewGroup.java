package android.view;

import android.content.Context;
import java.util.ArrayList;
import java.util.List;

public class ViewGroup extends View {
    public static class LayoutParams {
        public static final int MATCH_PARENT = -1;
        public static final int WRAP_CONTENT = -2;
        public int width;
        public int height;
        public LayoutParams() {}
        public LayoutParams(int w, int h) { width = w; height = h; }
    }
    public static class MarginLayoutParams extends LayoutParams {
        public int leftMargin, topMargin, rightMargin, bottomMargin;
        public MarginLayoutParams(int w, int h) { super(w, h); }
        public MarginLayoutParams(LayoutParams source) { super(source.width, source.height); }
        public void setMargins(int l, int t, int r, int b) {
            leftMargin = l; topMargin = t; rightMargin = r; bottomMargin = b;
        }
        public void setMarginStart(int start) { leftMargin = start; }
        public void setMarginEnd(int end) { rightMargin = end; }
        public int getMarginStart() { return leftMargin; }
        public int getMarginEnd() { return rightMargin; }
        public void setMarginRelative(int start, int top, int end, int bottom) {
            setMargins(start, top, end, bottom);
        }
    }
    private final List<View> children = new ArrayList<>();
    public ViewGroup(Context context) { super(context); }
    public ViewGroup(Context context, android.util.AttributeSet attrs) { super(context); }

    public void addView(View child) { addView(child, children.size()); }

    public void addView(View child, int index) {
        if (child == null) return;
        if (index < 0 || index > children.size()) children.add(child);
        else children.add(index, child);
        child.setDesktopParent(this);
        notifyDesktopTreeChanged();
    }
    public void addView(View child, LayoutParams params) {
        if (child != null) child.setLayoutParams(params);
        addView(child, children.size());
    }
    public void addView(View child, int width, int height) {
        if (child != null) child.setLayoutParams(new LayoutParams(width, height));
        addView(child, children.size());
    }
    public void removeView(View child) {
        if (children.remove(child)) {
            if (child != null) child.setDesktopParent(null);
            notifyDesktopTreeChanged();
        }
    }
    public void removeViewAt(int index) {
        if (index < 0 || index >= children.size()) return;
        View removed = children.remove(index);
        if (removed != null) removed.setDesktopParent(null);
        notifyDesktopTreeChanged();
    }
    public void removeAllViews() {
        if (children.isEmpty()) return;
        for (View child : children) {
            if (child != null) child.setDesktopParent(null);
        }
        children.clear();
        notifyDesktopTreeChanged();
    }
    public int getChildCount() { return children.size(); }
    public View getChildAt(int index) {
        return index >= 0 && index < children.size() ? children.get(index) : null;
    }
    public int indexOfChild(View child) { return children.indexOf(child); }
    public List<View> getChildren() { return children; }
    public LayoutParams generateLayoutParams(android.util.AttributeSet attrs) {
        return new MarginLayoutParams(-2, -2);
    }
    public void addView(View child, int index, LayoutParams params) {
        if (child != null) child.setLayoutParams(params);
        addView(child, index);
    }
}
