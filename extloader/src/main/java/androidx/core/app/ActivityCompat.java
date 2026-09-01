package androidx.core.app;
import android.app.Activity;
public class ActivityCompat {
    public static void requestPermissions(Activity activity, String[] permissions, int requestCode) {}
    public static boolean shouldShowRequestPermissionRationale(Activity activity, String permission) { return false; }
}
