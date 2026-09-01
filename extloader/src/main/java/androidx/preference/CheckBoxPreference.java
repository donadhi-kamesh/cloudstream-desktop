package androidx.preference;

import android.content.Context;

public class CheckBoxPreference extends Preference {
    public CheckBoxPreference(Context context) { super(context); }
    public void setChecked(boolean checked) {
        String key = getKey();
        if (key != null) getSharedPreferences().edit().putBoolean(key, checked).apply();
    }
    public boolean isChecked() {
        String key = getKey();
        return key != null && getSharedPreferences().getBoolean(key, false);
    }
}
