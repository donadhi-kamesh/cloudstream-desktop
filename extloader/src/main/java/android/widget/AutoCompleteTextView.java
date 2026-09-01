package android.widget;

import android.content.Context;
import android.util.AttributeSet;

public class AutoCompleteTextView extends EditText {
    private ListAdapter adapter;

    public AutoCompleteTextView(Context context) { super(context); }
    public AutoCompleteTextView(Context context, AttributeSet attrs) { super(context, attrs); }
    public AutoCompleteTextView(Context context, AttributeSet attrs, int defStyleAttr) { super(context, attrs, defStyleAttr); }

    public <T extends ListAdapter & Filterable> void setAdapter(T adapter) { this.adapter = adapter; }
    public ListAdapter getAdapter() { return adapter; }
    public void setThreshold(int threshold) {}
    public void showDropDown() {}
    public void dismissDropDown() {}
    public void setDropDownHeight(int height) {}
    public void setOnItemClickListener(AdapterView.OnItemClickListener listener) {}
}
