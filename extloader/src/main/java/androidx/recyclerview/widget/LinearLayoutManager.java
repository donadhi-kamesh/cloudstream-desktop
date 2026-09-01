package androidx.recyclerview.widget;

import android.content.Context;
import android.util.AttributeSet;

public class LinearLayoutManager extends RecyclerView.LayoutManager {
    public static final int HORIZONTAL = 0;
    public static final int VERTICAL = 1;

    public LinearLayoutManager(Context context) {}
    public LinearLayoutManager(Context context, int orientation, boolean reverseLayout) {}
    public LinearLayoutManager(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {}
    public void setOrientation(int orientation) {}
    public void setReverseLayout(boolean reverseLayout) {}
    public void setStackFromEnd(boolean stackFromEnd) {}
}
