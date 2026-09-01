package com.google.android.material.dialog;

import android.content.Context;
import android.content.DialogInterface;
import android.view.View;

public class MaterialAlertDialogBuilder extends androidx.appcompat.app.AlertDialog.Builder {
    public MaterialAlertDialogBuilder(Context context) { super(context); }
    public MaterialAlertDialogBuilder(Context context, int overrideThemeResId) { super(context); }

    @Override public MaterialAlertDialogBuilder setTitle(CharSequence title) { super.setTitle(title); return this; }
    @Override public MaterialAlertDialogBuilder setTitle(int resId) { super.setTitle(resId); return this; }
    @Override public MaterialAlertDialogBuilder setMessage(CharSequence message) { super.setMessage(message); return this; }
    @Override public MaterialAlertDialogBuilder setMessage(int resId) { super.setMessage(resId); return this; }
    @Override public MaterialAlertDialogBuilder setView(View view) { super.setView(view); return this; }
    @Override public MaterialAlertDialogBuilder setCancelable(boolean cancelable) { super.setCancelable(cancelable); return this; }
    @Override public MaterialAlertDialogBuilder setPositiveButton(CharSequence text, DialogInterface.OnClickListener listener) { super.setPositiveButton(text, listener); return this; }
    @Override public MaterialAlertDialogBuilder setPositiveButton(int resId, DialogInterface.OnClickListener listener) { super.setPositiveButton(resId, listener); return this; }
    @Override public MaterialAlertDialogBuilder setNegativeButton(CharSequence text, DialogInterface.OnClickListener listener) { super.setNegativeButton(text, listener); return this; }
    @Override public MaterialAlertDialogBuilder setNegativeButton(int resId, DialogInterface.OnClickListener listener) { super.setNegativeButton(resId, listener); return this; }
    @Override public MaterialAlertDialogBuilder setNeutralButton(CharSequence text, DialogInterface.OnClickListener listener) { super.setNeutralButton(text, listener); return this; }
    @Override public MaterialAlertDialogBuilder setNeutralButton(int resId, DialogInterface.OnClickListener listener) { super.setNeutralButton(resId, listener); return this; }
    @Override public MaterialAlertDialogBuilder setItems(CharSequence[] items, DialogInterface.OnClickListener listener) { super.setItems(items, listener); return this; }
    @Override public MaterialAlertDialogBuilder setOnDismissListener(DialogInterface.OnDismissListener listener) { super.setOnDismissListener(listener); return this; }
    @Override public MaterialAlertDialogBuilder setOnCancelListener(DialogInterface.OnCancelListener listener) { super.setOnCancelListener(listener); return this; }
}
