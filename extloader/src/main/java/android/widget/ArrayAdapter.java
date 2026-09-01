package android.widget;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class ArrayAdapter<T> implements ListAdapter, Filterable, SpinnerAdapter {
    private final Context context;
    private final List<T> items = new ArrayList<>();

    public ArrayAdapter(Context context, int resource) { this.context = context; }
    public ArrayAdapter(Context context, int resource, int textViewResourceId) { this(context, resource); }
    public ArrayAdapter(Context context, int resource, T[] objects) {
        this(context, resource);
        if (objects != null) for (T o : objects) items.add(o);
    }
    public ArrayAdapter(Context context, int resource, int textViewResourceId, T[] objects) {
        this(context, resource, objects);
    }
    public ArrayAdapter(Context context, int resource, List<T> objects) {
        this(context, resource);
        if (objects != null) items.addAll(objects);
    }
    public ArrayAdapter(Context context, int resource, int textViewResourceId, List<T> objects) {
        this(context, resource, objects);
    }

    public void add(T object) { items.add(object); }
    public void addAll(Collection<? extends T> collection) { if (collection != null) items.addAll(collection); }
    @SafeVarargs public final void addAll(T... values) { if (values != null) for (T o : values) items.add(o); }
    public void clear() { items.clear(); }
    public void remove(T object) { items.remove(object); }
    public void insert(T object, int index) { items.add(index, object); }
    public Context getContext() { return context; }
    public void setDropDownViewResource(int resource) {}
    public void notifyDataSetChanged() {}
    public void setNotifyOnChange(boolean notifyOnChange) {}
    public int getPosition(T item) { return items.indexOf(item); }

    @Override public int getCount() { return items.size(); }
    @Override public T getItem(int position) {
        return position >= 0 && position < items.size() ? items.get(position) : null;
    }
    @Override public long getItemId(int position) { return position; }
    @Override public View getView(int position, View convertView, ViewGroup parent) {
        TextView tv = convertView instanceof TextView ? (TextView) convertView : new TextView(context);
        T item = getItem(position);
        tv.setText(item == null ? "" : String.valueOf(item));
        return tv;
    }
    @Override public boolean isEmpty() { return items.isEmpty(); }
    @Override public boolean hasStableIds() { return false; }
    @Override public boolean areAllItemsEnabled() { return true; }
    @Override public boolean isEnabled(int position) { return true; }
    @Override public Filter getFilter() { return new Filter(); }
}
