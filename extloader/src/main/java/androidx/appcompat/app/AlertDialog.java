package androidx.appcompat.app;

import android.content.Context;
import android.content.DialogInterface;
import android.view.View;

/** AppCompat alias with fluent Builder methods returning androidx Builder instance. */
public class AlertDialog extends android.app.AlertDialog {
    public AlertDialog(Builder b) {
        super(b);
    }

    public static class Builder extends android.app.AlertDialog.Builder {
        public Builder(Context context) { super(context); }
        public Builder(Context context, int themeResId) { super(context); }

        @Override public Builder setTitle(CharSequence title) { super.setTitle(title); return this; }
        @Override public Builder setTitle(int resId) { super.setTitle(resId); return this; }
        @Override public Builder setMessage(CharSequence message) { super.setMessage(message); return this; }
        @Override public Builder setMessage(int resId) { super.setMessage(resId); return this; }
        @Override public Builder setView(View view) { super.setView(view); return this; }
        @Override public Builder setCancelable(boolean cancelable) { super.setCancelable(cancelable); return this; }
        @Override public Builder setPositiveButton(CharSequence text, DialogInterface.OnClickListener listener) { super.setPositiveButton(text, listener); return this; }
        @Override public Builder setPositiveButton(int resId, DialogInterface.OnClickListener listener) { super.setPositiveButton(resId, listener); return this; }
        @Override public Builder setNegativeButton(CharSequence text, DialogInterface.OnClickListener listener) { super.setNegativeButton(text, listener); return this; }
        @Override public Builder setNegativeButton(int resId, DialogInterface.OnClickListener listener) { super.setNegativeButton(resId, listener); return this; }
        @Override public Builder setNeutralButton(CharSequence text, DialogInterface.OnClickListener listener) { super.setNeutralButton(text, listener); return this; }
        @Override public Builder setNeutralButton(int resId, DialogInterface.OnClickListener listener) { super.setNeutralButton(resId, listener); return this; }
        @Override public Builder setItems(CharSequence[] items, DialogInterface.OnClickListener listener) { super.setItems(items, listener); return this; }
        @Override public Builder setOnDismissListener(DialogInterface.OnDismissListener listener) { super.setOnDismissListener(listener); return this; }
        @Override public Builder setOnCancelListener(DialogInterface.OnCancelListener listener) { super.setOnCancelListener(listener); return this; }

        @Override
        public AlertDialog create() {
            return new AlertDialog(this);
        }

        @Override
        public AlertDialog show() {
            AlertDialog d = create();
            d.show();
            return d;
        }
    }
}
