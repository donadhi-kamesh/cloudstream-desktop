package androidx.preference;

import android.content.Context;

public class EditTextPreference extends Preference {
    public EditTextPreference(Context context) { super(context); }
    public void setText(String text) {
        String key = getKey();
        if (key != null) getSharedPreferences().edit().putString(key, text).apply();
    }
    public String getText() {
        String key = getKey();
        return key == null ? null : getSharedPreferences().getString(key, null);
    }
}
