package androidx.preference;

import android.content.Context;
import android.content.SharedPreferences;

public class Preference {
    private final Context context;
    private String key;
    private CharSequence title;
    private CharSequence summary;
    private boolean enabled = true;

    public Preference(Context context) { this.context = context; }
    public Context getContext() { return context; }
    public void setKey(String key) { this.key = key; }
    public String getKey() { return key; }
    public void setTitle(CharSequence title) { this.title = title; }
    public CharSequence getTitle() { return title; }
    public void setSummary(CharSequence summary) { this.summary = summary; }
    public CharSequence getSummary() { return summary; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public boolean isEnabled() { return enabled; }
    public void setDefaultValue(Object defaultValue) {}
    public SharedPreferences getSharedPreferences() {
        return PreferenceManager.getDefaultSharedPreferences(context);
    }
    public void setOnPreferenceChangeListener(OnPreferenceChangeListener listener) {}
    public void setOnPreferenceClickListener(OnPreferenceClickListener listener) {}

    public interface OnPreferenceChangeListener {
        boolean onPreferenceChange(Preference preference, Object newValue);
    }
    public interface OnPreferenceClickListener {
        boolean onPreferenceClick(Preference preference);
    }
}
