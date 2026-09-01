package android.widget;

public class Filter {
    public void filter(CharSequence constraint) {}
    public void filter(CharSequence constraint, FilterListener listener) {}
    public interface FilterListener {
        void onFilterComplete(int count);
    }
}
