package android.widget;

import android.content.Context;

public abstract class CompoundButton extends TextView {
    private boolean checked;
    private OnCheckedChangeListener listener;
    /**
     * The desktop renderer's mirror of this button, kept apart from {@link #listener} so
     * registering it never displaces the callback the plugin installed — the plugin's
     * listener is usually the only place the new value gets saved.
     */
    private OnCheckedChangeListener desktopMirror;

    public CompoundButton(Context context) { super(context); }
    public void setChecked(boolean value) {
        if (checked == value) return;
        checked = value;
        if (desktopMirror != null) desktopMirror.onCheckedChanged(this, checked);
        if (listener != null) listener.onCheckedChanged(this, checked);
    }
    public boolean isChecked() { return checked; }
    public void toggle() { setChecked(!checked); }
    public void setOnCheckedChangeListener(OnCheckedChangeListener l) { listener = l; }
    public void setDesktopCheckedMirror(OnCheckedChangeListener l) { desktopMirror = l; }
    public interface OnCheckedChangeListener {
        void onCheckedChanged(CompoundButton buttonView, boolean isChecked);
    }
}
