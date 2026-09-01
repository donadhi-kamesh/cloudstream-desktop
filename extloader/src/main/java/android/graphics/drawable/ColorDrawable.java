package android.graphics.drawable;

import android.graphics.Canvas;

public class ColorDrawable extends Drawable {
    private int color;
    public ColorDrawable() {}
    public ColorDrawable(int color) { this.color = color; }
    public void setColor(int color) { this.color = color; }
    public int getColor() { return color; }
    @Override public void draw(Canvas canvas) {}
}
