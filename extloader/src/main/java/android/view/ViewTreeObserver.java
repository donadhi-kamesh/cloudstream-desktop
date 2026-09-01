package android.view;

import java.util.ArrayList;
import java.util.List;

public class ViewTreeObserver {
    private final List<OnGlobalLayoutListener> layout = new ArrayList<>();
    private final List<OnScrollChangedListener> scroll = new ArrayList<>();

    public interface OnGlobalLayoutListener { void onGlobalLayout(); }
    public interface OnScrollChangedListener { void onScrollChanged(); }
    public interface OnPreDrawListener { boolean onPreDraw(); }

    public void addOnGlobalLayoutListener(OnGlobalLayoutListener listener) {
        if (listener != null) {
            layout.add(listener);
            listener.onGlobalLayout();
        }
    }
    public void dispatchOnGlobalLayout() {
        for (OnGlobalLayoutListener listener : layout.toArray(new OnGlobalLayoutListener[0])) {
            listener.onGlobalLayout();
        }
    }
    public void removeOnGlobalLayoutListener(OnGlobalLayoutListener listener) { layout.remove(listener); }
    public void removeGlobalOnLayoutListener(OnGlobalLayoutListener listener) { removeOnGlobalLayoutListener(listener); }
    public void addOnScrollChangedListener(OnScrollChangedListener listener) {
        if (listener != null) scroll.add(listener);
    }
    public void removeOnScrollChangedListener(OnScrollChangedListener listener) { scroll.remove(listener); }
    public void addOnPreDrawListener(OnPreDrawListener listener) {}
    public void removeOnPreDrawListener(OnPreDrawListener listener) {}
    public boolean isAlive() { return true; }
}
