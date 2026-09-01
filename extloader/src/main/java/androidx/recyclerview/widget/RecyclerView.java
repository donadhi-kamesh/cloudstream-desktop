package androidx.recyclerview.widget;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;
import java.util.ArrayList;
import java.util.List;

/**
 * Desktop stand-in that actually materialises adapter rows. The androidx AAR copy is a
 * no-op, so every plugin that builds its settings list with a RecyclerView (the usual
 * pattern once there are more than a handful of providers) came up empty.
 */
public class RecyclerView extends ViewGroup {
    private Adapter<?> adapter;
    private final AdapterDataObserver rebuildObserver = new AdapterDataObserver() {
        @Override public void onChanged() { rebuild(); }
        @Override public void onItemRangeChanged(int positionStart, int itemCount) { rebuild(); }
        @Override public void onItemRangeInserted(int positionStart, int itemCount) { rebuild(); }
        @Override public void onItemRangeRemoved(int positionStart, int itemCount) { rebuild(); }
        @Override public void onItemRangeMoved(int fromPosition, int toPosition, int itemCount) { rebuild(); }
    };

    public RecyclerView(Context context) { super(context); }
    public RecyclerView(Context context, AttributeSet attrs) { super(context); }
    public RecyclerView(Context context, AttributeSet attrs, int defStyleAttr) { super(context); }

    public void setLayoutManager(LayoutManager layout) {}
    public LayoutManager getLayoutManager() { return null; }
    public void setHasFixedSize(boolean hasFixedSize) {}
    public void setItemAnimator(ItemAnimator animator) {}
    public void addItemDecoration(ItemDecoration decor) {}
    public void setAdapter(Adapter<?> adapter) {
        if (this.adapter != null) this.adapter.unregisterAdapterDataObserver(rebuildObserver);
        this.adapter = adapter;
        if (adapter != null) adapter.registerAdapterDataObserver(rebuildObserver);
        rebuild();
    }

    public Adapter<?> getAdapter() { return adapter; }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private void rebuild() {
        removeAllViews();
        Adapter a = adapter;
        if (a == null) return;
        int n = a.getItemCount();
        for (int i = 0; i < n; i++) {
            ViewHolder vh = a.createViewHolder(this, a.getItemViewType(i));
            a.bindViewHolder(vh, i);
            if (vh != null && vh.itemView != null) addView(vh.itemView);
        }
    }

    public abstract static class Adapter<VH extends ViewHolder> {
        private final List<AdapterDataObserver> observers = new ArrayList<>();

        public abstract VH onCreateViewHolder(ViewGroup parent, int viewType);
        public abstract void onBindViewHolder(VH holder, int position);
        public abstract int getItemCount();
        public int getItemViewType(int position) { return 0; }
        public long getItemId(int position) { return position; }
        public void setHasStableIds(boolean hasStableIds) {}

        public final VH createViewHolder(ViewGroup parent, int viewType) {
            return onCreateViewHolder(parent, viewType);
        }

        public final void bindViewHolder(VH holder, int position) {
            onBindViewHolder(holder, position);
        }

        public void registerAdapterDataObserver(AdapterDataObserver observer) {
            if (observer != null) observers.add(observer);
        }

        public void unregisterAdapterDataObserver(AdapterDataObserver observer) {
            observers.remove(observer);
        }

        public final void notifyDataSetChanged() { dispatch(o -> o.onChanged()); }
        public final void notifyItemChanged(int position) { dispatch(o -> o.onItemRangeChanged(position, 1)); }
        public final void notifyItemInserted(int position) { dispatch(o -> o.onItemRangeInserted(position, 1)); }
        public final void notifyItemRemoved(int position) { dispatch(o -> o.onItemRangeRemoved(position, 1)); }
        public final void notifyItemRangeChanged(int positionStart, int itemCount) {
            dispatch(o -> o.onItemRangeChanged(positionStart, itemCount));
        }
        public final void notifyItemRangeInserted(int positionStart, int itemCount) {
            dispatch(o -> o.onItemRangeInserted(positionStart, itemCount));
        }
        public final void notifyItemRangeRemoved(int positionStart, int itemCount) {
            dispatch(o -> o.onItemRangeRemoved(positionStart, itemCount));
        }
        public final void notifyItemMoved(int fromPosition, int toPosition) {
            dispatch(o -> o.onItemRangeMoved(fromPosition, toPosition, 1));
        }

        private void dispatch(java.util.function.Consumer<AdapterDataObserver> fn) {
            for (AdapterDataObserver o : new ArrayList<>(observers)) fn.accept(o);
        }
    }

    public static class ViewHolder {
        public final View itemView;
        public ViewHolder(View itemView) { this.itemView = itemView; }
        public final int getBindingAdapterPosition() { return 0; }
        public final int getAbsoluteAdapterPosition() { return 0; }
        public final int getLayoutPosition() { return 0; }
        public final int getAdapterPosition() { return getBindingAdapterPosition(); }
    }

    public static class LayoutManager {}
    public static class LinearLayoutManager extends LayoutManager {
        public static final int HORIZONTAL = 0;
        public static final int VERTICAL = 1;
        public LinearLayoutManager(Context context) {}
        public LinearLayoutManager(Context context, int orientation, boolean reverseLayout) {}
        public LinearLayoutManager(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {}
        public void setOrientation(int orientation) {}
        public void setReverseLayout(boolean reverseLayout) {}
    }
    public static class GridLayoutManager extends LinearLayoutManager {
        public GridLayoutManager(Context context, int spanCount) { super(context); }
        public GridLayoutManager(Context context, int spanCount, int orientation, boolean reverseLayout) {
            super(context, orientation, reverseLayout);
        }
    }
    public static class AdapterDataObserver {
        public void onChanged() {}
        public void onItemRangeChanged(int positionStart, int itemCount) {}
        public void onItemRangeInserted(int positionStart, int itemCount) {}
        public void onItemRangeRemoved(int positionStart, int itemCount) {}
        public void onItemRangeMoved(int fromPosition, int toPosition, int itemCount) {}
    }
    public static class ItemDecoration {}
    public static class ItemAnimator {}
    public static class RecycledViewPool {}
}
