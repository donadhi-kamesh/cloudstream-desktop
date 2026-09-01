package android.app;
import android.content.ContextWrapper;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;

public class Activity extends ContextWrapper {
    public static final int RESULT_CANCELED = 0;
    public static final int RESULT_OK = -1;
    public static final int RESULT_FIRST_USER = 1;
    private static final Application APPLICATION = new Application();
    private View contentView;
    private CharSequence title;
    private javax.swing.JDialog desktopWindow;
    private Runnable desktopCloseListener;

    public Activity() { super(null); }
    public Application getApplication() { return APPLICATION; }
    public void runOnUiThread(Runnable action) {
        if (action == null) return;
        javax.swing.SwingUtilities.invokeLater(action);
    }
    public void finish() {
        if (desktopWindow != null) {
            javax.swing.SwingUtilities.invokeLater(() -> {
                desktopWindow.setVisible(false);
                desktopWindow.dispose();
            });
        }
        if (desktopCloseListener != null) desktopCloseListener.run();
    }
    public boolean isFinishing() { return false; }
    private final Window window = new Window(this);
    public Window getWindow() { return window; }
    public WindowManager getWindowManager() { return new WindowManager.Stub(); }
    public android.view.LayoutInflater getLayoutInflater() { return android.view.LayoutInflater.from(this); }
    public View findViewById(int id) { return contentView != null ? contentView.findViewById(id) : null; }

    public void setContentView(int layoutResID) {
        try {
            View inflated = android.view.LayoutInflater.from(this).inflate(layoutResID, null, false);
            if (inflated != null) contentView = inflated;
        } catch (Throwable ignored) {
        }
        window.setContentView(contentView);
    }
    public void setContentView(View view) {
        contentView = view;
        window.setContentView(view);
    }
    public View getDesktopContentView() { return contentView; }

    public void setTitle(CharSequence t) { title = t; }
    public void setTitle(int titleId) {}
    public CharSequence getDesktopTitle() { return title != null ? String.valueOf(title) : null; }
    public CharSequence getTitle() { return title; }

    public void setResult(int resultCode) {}
    public void setResult(int resultCode, android.content.Intent data) {}

    /** Desktop-only: window shown for this activity and a close callback. */
    public void attachDesktopWindow(javax.swing.JDialog window, Runnable onClose) {
        this.desktopWindow = window;
        this.desktopCloseListener = onClose;
    }

    // Desktop activity lifecycle entry points; plugins override these.
    protected void onCreate(android.os.Bundle savedInstanceState) {}
    protected void onStart() {}
    protected void onResume() {}
    protected void onPause() {}
    protected void onStop() {}
    protected void onDestroy() {}
}
