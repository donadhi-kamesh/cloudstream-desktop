package android.widget;
import android.content.Context;
public class Toast {
    public static final int LENGTH_SHORT = 0;
    public static final int LENGTH_LONG = 1;
    public static Toast makeText(Context context, CharSequence text, int duration) {
        Toast t = new Toast();
        t.message = text == null ? "" : text.toString();
        return t;
    }
    private String message = "";
    public void show() {
        String msg = message;
        if (msg.isBlank()) return;
        com.lagradost.api.Log.INSTANCE.i("Toast", msg);
        dev.csdesktop.extloader.DialogHost.toast(msg);
    }
    public void cancel() {}
}
