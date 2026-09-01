package androidx.fragment.app;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;

public class DialogFragment extends Fragment {
    public static final int STYLE_NORMAL = 0;
    public static final int STYLE_NO_TITLE = 1;
    public static final int STYLE_NO_FRAME = 2;
    private Dialog dialog;
    private boolean cancelable = true;

    public Dialog onCreateDialog(Bundle savedInstanceState) { return new Dialog(getContext()); }

    public void show(FragmentManager manager, String tag) {
        try {
            onCreate(null);
            View view = onCreateView(getLayoutInflater(), null, null);
            setView(view);
            dialog = onCreateDialog(null);
            if (dialog != null) {
                if (view != null) dialog.setContentView(view);
                dialog.setOnDismissListener(this::onDismiss);
            }
            onViewCreated(view, null);
            // The window opens before onStart/onResume so that anything those hooks add
            // to the tree streams into the already-visible dialog.
            if (dialog != null) dialog.show();
            onStart();
            onResume();
        } catch (Throwable t) {
            dev.csdesktop.extloader.DialogHost.errorDialog("Extension settings", t);
        }
    }

    public void show(FragmentTransaction transaction, String tag) {
        show(new FragmentManager(), tag);
    }

    public void showNow(FragmentManager manager, String tag) {
        show(manager, tag);
    }

    public void dismiss() {
        if (dialog != null) dialog.dismiss();
        dialog = null;
    }

    public void dismissAllowingStateLoss() { dismiss(); }

    public void onDismiss(android.content.DialogInterface dialog) {}
    public void setCancelable(boolean cancelable) { this.cancelable = cancelable; }
    public boolean isCancelable() { return cancelable; }
    public void setStyle(int style, int theme) {}
    public Dialog getDialog() { return dialog; }
    public Context requireContext() { return getContext(); }
}
