package com.google.android.material.bottomsheet;

import android.view.View;

public class BottomSheetBehavior<V extends View> {
    public static final int STATE_DRAGGING = 1;
    public static final int STATE_SETTLING = 2;
    public static final int STATE_EXPANDED = 3;
    public static final int STATE_COLLAPSED = 4;
    public static final int STATE_HIDDEN = 5;
    public static final int STATE_HALF_EXPANDED = 6;

    private int state = STATE_EXPANDED;
    public boolean skipCollapsed;
    public boolean isFitToContents = true;
    public boolean isDraggable = true;
    public boolean isHideable = true;

    public int getState() { return state; }
    public void setState(int state) { this.state = state; }
    public void setSkipCollapsed(boolean skipCollapsed) { this.skipCollapsed = skipCollapsed; }
    public void setFitToContents(boolean fitToContents) { isFitToContents = fitToContents; }
    public void setDraggable(boolean draggable) { isDraggable = draggable; }
    public void setHideable(boolean hideable) { isHideable = hideable; }
    public void setPeekHeight(int peekHeight) {}
    public void addBottomSheetCallback(BottomSheetCallback callback) {}
    public void setBottomSheetCallback(BottomSheetCallback callback) {}

    public static <V extends View> BottomSheetBehavior<V> from(V view) {
        return new BottomSheetBehavior<>();
    }

    public abstract static class BottomSheetCallback {
        public abstract void onStateChanged(View bottomSheet, int newState);
        public abstract void onSlide(View bottomSheet, float slideOffset);
    }
}
