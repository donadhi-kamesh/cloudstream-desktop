package android.graphics.drawable;

import android.content.res.ColorStateList;
import android.graphics.Canvas;
import android.graphics.ColorFilter;

public abstract class Drawable {
    private int alpha = 255;
    private int boundsL, boundsT, boundsR, boundsB;

    public abstract void draw(Canvas canvas);
    public void setBounds(int left, int top, int right, int bottom) {
        boundsL = left; boundsT = top; boundsR = right; boundsB = bottom;
    }
    public void setAlpha(int alpha) { this.alpha = alpha; }
    public int getAlpha() { return alpha; }
    public void setColorFilter(ColorFilter filter) {}
    public void setTint(int tintColor) {}
    public void setTintList(ColorStateList tint) {}
    public int getIntrinsicWidth() { return -1; }
    public int getIntrinsicHeight() { return -1; }
    public int getOpacity() { return 0; }
    public boolean setState(int[] stateSet) { return false; }
    public Drawable mutate() { return this; }
}
