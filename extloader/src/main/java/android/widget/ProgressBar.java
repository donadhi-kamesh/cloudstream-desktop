package android.widget;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;

public class ProgressBar extends View {
    public ProgressBar(Context context) { super(context); }
    public ProgressBar(Context context, AttributeSet attrs) { super(context); }
    public ProgressBar(Context context, AttributeSet attrs, int defStyleAttr) { super(context); }
    public void setIndeterminate(boolean indeterminate) {}
    public void setMax(int max) {}
    public void setProgress(int progress) {}
    public void setVisibility(int visibility) { super.setVisibility(visibility); }
}
