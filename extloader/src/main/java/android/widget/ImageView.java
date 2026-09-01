package android.widget;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.view.View;

public class ImageView extends View {
    public enum ScaleType { MATRIX, FIT_XY, FIT_START, FIT_CENTER, FIT_END, CENTER, CENTER_CROP, CENTER_INSIDE }
    public ImageView(Context context) { super(context); }
    public ImageView(Context context, android.util.AttributeSet attrs) { super(context); }
    public void setImageDrawable(Drawable drawable) { setBackground(drawable); }
    public void setImageBitmap(Bitmap bm) {}
    public void setImageResource(int resId) {}
    public void setScaleType(ScaleType scaleType) {}
    public void setAdjustViewBounds(boolean adjustViewBounds) {}
    public void setColorFilter(int color) {}
    public void setImageTintList(android.content.res.ColorStateList tint) {}
}
