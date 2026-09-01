package com.google.android.material.bottomsheet;

import android.content.Context;
import android.view.View;

public class BottomSheetDialog extends android.app.Dialog {
    private final BottomSheetBehavior<View> behavior = new BottomSheetBehavior<>();

    public BottomSheetDialog(Context context) { super(context); }
    public BottomSheetDialog(Context context, int theme) { super(context, theme); }

    public BottomSheetBehavior<View> getBehavior() { return behavior; }
    public void setDismissWithAnimation(boolean dismissWithAnimation) {}
}
