package android.view;

import android.content.Context;
import android.graphics.drawable.Drawable;

public class Window {
    public static final int FEATURE_NO_TITLE = 1;
    public static final int FEATURE_ACTION_BAR = 8;
    private final Context context;
    private View decor = new View(null);
    public Window(Context context) { this.context = context; }
    public View getDecorView() { return decor; }
    public void setContentView(View view) { this.decor = view != null ? view : this.decor; }
    public void setBackgroundDrawable(Drawable drawable) {}
    public void setLayout(int width, int height) {}
    public void setGravity(int gravity) {}
    public void setDimAmount(float amount) {}
    public void addFlags(int flags) {}
    public void clearFlags(int flags) {}
    public void setFlags(int flags, int mask) {}
    public void setSoftInputMode(int mode) {}
    public WindowManager.LayoutParams getAttributes() { return new WindowManager.LayoutParams(); }
    public void setAttributes(WindowManager.LayoutParams params) {}
    public View findViewById(int id) { return decor.findViewById(id); }
    public Context getContext() { return context; }
}
