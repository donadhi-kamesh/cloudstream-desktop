package android.view;

import android.content.Context;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.os.Looper;

import java.util.HashMap;
import java.util.Map;

public class View {
    public static final int VISIBLE = 0;
    public static final int INVISIBLE = 4;
    public static final int GONE = 8;
    public static final int NO_ID = -1;
    public static final int SOUND_EFFECTS_ENABLED = 1;
    public static final int FOCUSABLE = 1;
    public static final int NOT_FOCUSABLE = 0;

    protected Context context;
    protected int visibility = VISIBLE;
    protected int id = NO_ID;
    protected Object tag;
    protected Drawable background;
    protected ViewGroup.LayoutParams layoutParams;
    protected OnClickListener clickListener;
    protected OnKeyListener keyListener;
    protected OnFocusChangeListener focusListener;
    protected final Handler handler = new Handler(Looper.getMainLooper());
    protected final Map<Integer, View> idMap = new HashMap<>();
    protected final ViewTreeObserver treeObserver = new ViewTreeObserver();

    protected int measuredWidth = 0;
    protected int measuredHeight = 0;
    protected int paddingLeft, paddingTop, paddingRight, paddingBottom;
    private static final java.util.concurrent.atomic.AtomicInteger sNextId = new java.util.concurrent.atomic.AtomicInteger(1);
    // Inflation attributes kept for the desktop renderer; unused by plugin code.
    private Object desktopAttrs;
    /** The `@+id/name` this view was inflated with, so name lookups work across Resources instances. */
    private String idName;
    private ViewGroup desktopParent;
    private Runnable desktopTreeListener;

    public void setDesktopAttrs(Object attrs) { this.desktopAttrs = attrs; }
    public Object getDesktopAttrs() { return desktopAttrs; }

    /**
     * Stable id derived from a resource name. Used whenever no resources.arsc entry
     * exists, so inflation and {@code Resources.getIdentifier} always agree.
     */
    public static int stableId(String name) {
        if (name == null || name.isEmpty()) return 0;
        int hash = Math.abs(("id/" + name).hashCode());
        return hash != 0 ? hash : 1;
    }

    public void setDesktopIdName(String name) { this.idName = name; }
    public String getDesktopIdName() { return idName; }

    void setDesktopParent(ViewGroup parent) { this.desktopParent = parent; }
    public ViewGroup getDesktopParent() { return desktopParent; }

    /** Root of the tree this view belongs to. */
    public View getDesktopRoot() {
        View v = this;
        while (v.desktopParent != null) v = v.desktopParent;
        return v;
    }

    /**
     * Registered by the desktop renderer on the root of a mounted tree. Any structural
     * or content change anywhere in the tree re-renders it, so extensions that add
     * views after {@code dialog.show()} are not stuck with a blank window.
     */
    public void setDesktopTreeListener(Runnable listener) { this.desktopTreeListener = listener; }

    public void notifyDesktopTreeChanged() {
        if (syncDepth.get()[0] > 0) return;
        Runnable listener = getDesktopRoot().desktopTreeListener;
        if (listener != null) listener.run();
    }

    private static final ThreadLocal<int[]> syncDepth = ThreadLocal.withInitial(() -> new int[1]);

    /**
     * Guards writes the desktop renderer makes back into the view tree (a typed
     * character, a toggled checkbox) so they don't trigger a re-render that would
     * throw away the widget the user is interacting with.
     */
    public static void beginDesktopSync() { syncDepth.get()[0]++; }

    public static void endDesktopSync() {
        int[] depth = syncDepth.get();
        if (depth[0] > 0) depth[0]--;
    }

    public View(Context context) { this.context = context; }
    public View(Context context, android.util.AttributeSet attrs) { this(context); }
    public View(Context context, android.util.AttributeSet attrs, int defStyleAttr) { this(context); }
    public Context getContext() { return context; }
    public void setLayoutParams(ViewGroup.LayoutParams params) { this.layoutParams = params; }
    public ViewGroup.LayoutParams getLayoutParams() {
        if (layoutParams == null) layoutParams = new ViewGroup.LayoutParams(-2, -2);
        return layoutParams;
    }
    public void setVisibility(int visibility) {
        if (this.visibility == visibility) return;
        this.visibility = visibility;
        notifyDesktopTreeChanged();
    }
    public int getVisibility() { return visibility; }
    public void setEnabled(boolean enabled) {}
    public boolean isEnabled() { return true; }
    public void setPadding(int l, int t, int r, int b) {
        paddingLeft = l; paddingTop = t; paddingRight = r; paddingBottom = b;
    }
    public void setPaddingRelative(int start, int top, int end, int bottom) {
        setPadding(start, top, end, bottom);
    }
    public int getPaddingLeft() { return paddingLeft; }
    public int getPaddingTop() { return paddingTop; }
    public int getPaddingRight() { return paddingRight; }
    public int getPaddingBottom() { return paddingBottom; }
    public int getPaddingStart() { return paddingLeft; }
    public int getPaddingEnd() { return paddingRight; }
    public void setOnClickListener(OnClickListener listener) { this.clickListener = listener; }
    public void setOnLongClickListener(OnLongClickListener listener) {}
    public void setOnKeyListener(OnKeyListener listener) { this.keyListener = listener; }
    public void setOnTouchListener(OnTouchListener listener) {}
    public void setClickable(boolean clickable) {}
    public void setFocusable(boolean focusable) {}
    public void setFocusableInTouchMode(boolean focusable) {}
    public void requestFocus() {}
    public void setAlpha(float alpha) {}
    public void setElevation(float elevation) {}
    public void setTranslationX(float x) {}
    public void setTranslationY(float y) {}
    public void setScaleX(float x) {}
    public void setScaleY(float y) {}
    public void setRotation(float r) {}
    public void setBackground(Drawable background) { this.background = background; }
    public void setBackgroundDrawable(Drawable background) { setBackground(background); }
    public void setBackgroundColor(int color) { setBackground(new ColorDrawable(color)); }
    public void setBackgroundResource(int resid) {}
    public Drawable getBackground() { return background; }
    public void setId(int id) { this.id = id; }
    public int getId() { return id; }
    public static int generateViewId() {
        while (true) {
            int result = sNextId.get();
            int next = result + 1;
            if (next > 0x00FFFFFF) next = 1;
            if (sNextId.compareAndSet(result, next)) return result;
        }
    }
    public void setTag(Object tag) { this.tag = tag; }
    public Object getTag() { return tag; }
    public void setMinimumHeight(int minHeight) { if (minHeight > measuredHeight) measuredHeight = minHeight; }
    public void setMinimumWidth(int minWidth) { if (minWidth > measuredWidth) measuredWidth = minWidth; }
    public int getWidth() { return measuredWidth > 0 ? measuredWidth : 1100; }
    public int getHeight() { return measuredHeight > 0 ? measuredHeight : 800; }
    public int getMeasuredWidth() { return getWidth(); }
    public int getMeasuredHeight() { return getHeight(); }
    public void measure(int widthMeasureSpec, int heightMeasureSpec) {}
    public void layout(int l, int t, int r, int b) {}
    public void requestLayout() { notifyDesktopTreeChanged(); }
    public void invalidate() { notifyDesktopTreeChanged(); }
    public void postInvalidate() { notifyDesktopTreeChanged(); }
    public boolean post(Runnable action) { return handler.post(action); }
    public boolean postDelayed(Runnable action, long delayMillis) { return handler.postDelayed(action, delayMillis); }
    public boolean removeCallbacks(Runnable action) { handler.removeCallbacks(action); return true; }
    public ViewTreeObserver getViewTreeObserver() { return treeObserver; }
    public boolean dispatchTouchEvent(MotionEvent event) { return false; }
    public boolean onTouchEvent(MotionEvent event) { return false; }
    public void performClick() { if (clickListener != null) clickListener.onClick(this); }
    public WindowManager getWindowManager() { return new WindowManager.Stub(); }
    public android.os.IBinder getWindowToken() { return new android.os.Binder(); }
    @SuppressWarnings("unchecked")
    public <T extends View> T findViewById(int id) {
        if (id == NO_ID || id == 0) return null;
        // Plugins resolve ids through their own Resources instance, which may differ from
        // the one the inflater used. Matching the inflated name hash as well keeps
        // findViewById working across both.
        if (this.id == id || (idName != null && stableId(idName) == id)) return (T) this;
        View cached = idMap.get(id);
        if (cached != null) return (T) cached;
        if (this instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) this;
            for (int i = 0; i < g.getChildCount(); i++) {
                View found = g.getChildAt(i).findViewById(id);
                if (found != null) return (T) found;
            }
        }
        return null;
    }

    /** Name-based lookup, matching the `findViewByName` helper plugins define themselves. */
    @SuppressWarnings("unchecked")
    public <T extends View> T findViewByName(String name) {
        if (name == null || name.isEmpty()) return null;
        if (name.equals(idName)) return (T) this;
        if (this instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) this;
            for (int i = 0; i < g.getChildCount(); i++) {
                View found = g.getChildAt(i).findViewByName(name);
                if (found != null) return (T) found;
            }
        }
        return findViewById(stableId(name));
    }
    public void setContentDescription(CharSequence contentDescription) {}
    public void setImportantForAccessibility(int mode) {}
    public void setOnFocusChangeListener(OnFocusChangeListener listener) { this.focusListener = listener; }
    public void clearFocus() {
        superClearFocus();
        if (focusListener != null) focusListener.onFocusChange(this, false);
    }
    private void superClearFocus() {}

    public interface OnClickListener { void onClick(View v); }
    public interface OnLongClickListener { boolean onLongClick(View v); }
    public interface OnKeyListener { boolean onKey(View v, int keyCode, KeyEvent event); }
    public interface OnTouchListener { boolean onTouch(View v, MotionEvent event); }
    public interface OnFocusChangeListener { void onFocusChange(View v, boolean hasFocus); }
}
