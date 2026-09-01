package android.widget;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;

public class AdapterView<T extends Adapter> extends ViewGroup {
    public AdapterView(Context context) { super(context); }
    public AdapterView(Context context, android.util.AttributeSet attrs) { super(context); }

    public interface OnItemClickListener {
        void onItemClick(AdapterView<?> parent, View view, int position, long id);
    }
    public interface OnItemSelectedListener {
        void onItemSelected(AdapterView<?> parent, View view, int position, long id);
        void onNothingSelected(AdapterView<?> parent);
    }

    public void setOnItemClickListener(OnItemClickListener listener) {}
    public void setOnItemSelectedListener(OnItemSelectedListener listener) {}
    public void setAdapter(T adapter) {}
    public T getAdapter() { return null; }
}
