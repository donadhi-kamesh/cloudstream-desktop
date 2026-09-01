package androidx.core.content;
import android.content.Context;
public class ContextCompat {
    public static int checkSelfPermission(Context context, String permission) { return 0; }
    public static java.io.File[] getExternalFilesDirs(Context context, String type) {
        return new java.io.File[] { context.getFilesDir() };
    }
}
