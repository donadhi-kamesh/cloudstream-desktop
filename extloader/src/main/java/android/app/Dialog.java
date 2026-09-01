package android.app;

import android.content.Context;
import android.content.DialogInterface;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;

public class Dialog implements DialogInterface {
    protected final Context context;
    protected Window window;
    protected boolean showing;
    protected OnDismissListener dismissListener;
    protected OnCancelListener cancelListener;
    protected View content;
    protected CharSequence title;
    private dev.csdesktop.extloader.DialogHost.Handle handle;

    public Dialog(Context context) { this(context, 0); }
    public Dialog(Context context, int themeResId) {
        this.context = context;
        this.window = new Window(context);
    }
    public void show() {
        showing = true;
        View toShow = content != null ? content : window.getDecorView();
        String dialogTitle = title != null && title.length() > 0 ? String.valueOf(title) : null;
        handle = dev.csdesktop.extloader.DialogHost.showDialog(dialogTitle, toShow);
    }
    public void hide() {
        showing = false;
        if (handle != null) handle.dispose();
    }
    @Override public void dismiss() {
        showing = false;
        if (handle != null) {
            handle.dispose();
            handle = null;
        }
        if (dismissListener != null) dismissListener.onDismiss(this);
    }
    @Override public void cancel() {
        if (cancelListener != null) cancelListener.onCancel(this);
        dismiss();
    }
    public boolean isShowing() { return showing; }
    public Window getWindow() { return window; }
    public Context getContext() { return context; }
    public void setTitle(CharSequence t) { title = t; }
    public void setTitle(int titleId) {
        try {
            CharSequence t = context.getString(titleId);
            if (t != null && t.length() > 0) title = t;
        } catch (Throwable ignored) {
        }
    }
    public void setCancelable(boolean flag) {}
    public void setCanceledOnTouchOutside(boolean cancel) {}
    public void setOnDismissListener(OnDismissListener listener) { dismissListener = listener; }
    public void setOnCancelListener(OnCancelListener listener) { cancelListener = listener; }
    public void setOnKeyListener(OnKeyListener listener) {}
    public void setContentView(View view) {
        content = view;
        if (window != null) window.setContentView(view);
    }
    public void setContentView(int layoutResID) {
        try {
            View inflated = android.view.LayoutInflater.from(context).inflate(layoutResID, null, false);
            if (inflated != null) setContentView(inflated);
        } catch (Throwable ignored) {
        }
    }
    public View findViewById(int id) {
        View root = content != null ? content : window.getDecorView();
        return root == null ? null : root.findViewById(id);
    }
    public View findViewByName(String name) {
        View root = content != null ? content : window.getDecorView();
        return root == null ? null : root.findViewByName(name);
    }
    public View getDesktopContentView() { return content; }
    public void requestWindowFeature(int featureId) {}
    public WindowManager getWindowManager() { return new WindowManager.Stub(); }

    public interface OnKeyListener {
        boolean onKey(DialogInterface dialog, int keyCode, android.view.KeyEvent event);
    }
}
