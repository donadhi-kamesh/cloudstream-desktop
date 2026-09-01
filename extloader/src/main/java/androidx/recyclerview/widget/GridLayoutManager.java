package androidx.recyclerview.widget;

import android.content.Context;
import android.util.AttributeSet;

public class GridLayoutManager extends LinearLayoutManager {
    public GridLayoutManager(Context context, int spanCount) { super(context); }
    public GridLayoutManager(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
    }
    public GridLayoutManager(Context context, int spanCount, int orientation, boolean reverseLayout) {
        super(context, orientation, reverseLayout);
    }
    public void setSpanCount(int spanCount) {}
}
