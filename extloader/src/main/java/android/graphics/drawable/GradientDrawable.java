package android.graphics.drawable;

import android.graphics.Canvas;
import android.content.res.ColorStateList;

public class GradientDrawable extends Drawable {
    public static final int RECTANGLE = 0;
    public static final int OVAL = 1;
    public static final int LINE = 2;
    public static final int RING = 3;
    public static final int LINEAR_GRADIENT = 0;
    public static final int RADIAL_GRADIENT = 1;
    public static final int SWEEP_GRADIENT = 2;
    public static final int Orientation_TOP_BOTTOM = 0;

    public enum Orientation {
        TOP_BOTTOM, TR_BL, RIGHT_LEFT, BR_TL, BOTTOM_TOP, BL_TR, LEFT_RIGHT, TL_BR
    }

    public GradientDrawable() {}
    public GradientDrawable(Orientation orientation, int[] colors) {}
    public void setColor(int argb) {}
    public void setColor(ColorStateList colors) {}
    public void setColors(int[] colors) {}
    public void setCornerRadius(float radius) {}
    public void setCornerRadii(float[] radii) {}
    public void setStroke(int width, int color) {}
    public void setStroke(int width, ColorStateList colors) {}
    public void setShape(int shape) {}
    public void setGradientType(int gradient) {}
    public void setOrientation(Orientation orientation) {}
    public void setSize(int width, int height) {}
    @Override public void draw(Canvas canvas) {}
}
