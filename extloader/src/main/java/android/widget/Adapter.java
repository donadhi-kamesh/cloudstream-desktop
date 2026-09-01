package android.widget;

import android.view.View;
import android.view.ViewGroup;

public interface Adapter {
    int getCount();
    Object getItem(int position);
    long getItemId(int position);
    View getView(int position, View convertView, ViewGroup parent);
    boolean isEmpty();
    boolean hasStableIds();
}
