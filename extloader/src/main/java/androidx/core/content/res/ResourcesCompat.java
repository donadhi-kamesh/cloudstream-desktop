package androidx.core.content.res;

import android.content.Context;
import android.content.res.ColorStateList;
import android.content.res.Resources;
import android.graphics.drawable.Drawable;

public final class ResourcesCompat {
    private ResourcesCompat() {}
    public static Drawable getDrawable(Resources res, int id, Resources.Theme theme) {
        return res == null ? null : res.getDrawable(id, theme);
    }
    public static int getColor(Resources res, int id, Resources.Theme theme) {
        return res == null ? 0 : res.getColor(id);
    }
    public static ColorStateList getColorStateList(Resources res, int id, Resources.Theme theme) {
        return res == null ? null : res.getColorStateList(id);
    }
    public static Drawable getDrawable(Context context, int id) {
        return context == null ? null : context.getDrawable(id);
    }
}
