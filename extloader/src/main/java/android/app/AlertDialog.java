package android.app;

import android.content.Context;
import android.content.DialogInterface;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;

import javax.swing.JComponent;
import javax.swing.JPanel;
import java.util.ArrayList;
import java.util.List;

/**
 * Extension alert dialogs rendered as dark desktop dialogs. Views containing a
 * WebView (captcha flows) go to the embedded browser window instead of a modal.
 */
public class AlertDialog extends Dialog {
    public static final int BUTTON_POSITIVE = DialogInterface.BUTTON_POSITIVE;
    public static final int BUTTON_NEGATIVE = DialogInterface.BUTTON_NEGATIVE;
    public static final int BUTTON_NEUTRAL = DialogInterface.BUTTON_NEUTRAL;

    private dev.csdesktop.extloader.DialogHost.Handle handle;

    public static class Builder {
        private final Context context;
        private CharSequence title;
        private CharSequence message;
        private View customView;
        private CharSequence positive;
        private CharSequence negative;
        private CharSequence neutral;
        private OnClickListener positiveL;
        private OnClickListener negativeL;
        private OnClickListener neutralL;
        private CharSequence[] items;
        private OnClickListener itemsL;
        private OnDismissListener dismissL;
        private OnCancelListener cancelL;

        public Builder(Context context) { this.context = context; }
        public Builder setTitle(CharSequence title) { this.title = title; return this; }
        public Builder setTitle(int resId) {
            if (context != null) {
                try {
                    CharSequence t = context.getText(resId);
                    if (t != null && t.length() > 0) title = t;
                } catch (Throwable ignored) {
                }
            }
            return this;
        }
        public Builder setMessage(CharSequence message) { this.message = message; return this; }
        public Builder setMessage(int resId) {
            if (context != null) {
                try {
                    CharSequence m = context.getText(resId);
                    if (m != null && m.length() > 0) message = m;
                } catch (Throwable ignored) {
                }
            }
            return this;
        }
        public Builder setView(View view) { this.customView = view; return this; }
        public Builder setView(int layoutResId) {
            try {
                View inflated = android.view.LayoutInflater.from(context).inflate(layoutResId, null, false);
                if (inflated != null) customView = inflated;
            } catch (Throwable ignored) {
            }
            return this;
        }
        public Builder setCustomTitle(View view) { return this; }
        public Builder setIcon(int resId) { return this; }
        public Builder setCancelable(boolean cancelable) { return this; }
        public Context getContext() { return context; }
        public Builder setPositiveButton(CharSequence text, OnClickListener listener) {
            positive = text; positiveL = listener; return this;
        }
        public Builder setPositiveButton(int resId, OnClickListener listener) {
            return setPositiveButton("OK", listener);
        }
        public Builder setNegativeButton(CharSequence text, OnClickListener listener) {
            negative = text; negativeL = listener; return this;
        }
        public Builder setNegativeButton(int resId, OnClickListener listener) {
            return setNegativeButton("Cancel", listener);
        }
        public Builder setNeutralButton(CharSequence text, OnClickListener listener) {
            neutral = text; neutralL = listener; return this;
        }
        public Builder setNeutralButton(int resId, OnClickListener listener) {
            return setNeutralButton("Neutral", listener);
        }
        public Builder setItems(CharSequence[] items, OnClickListener listener) {
            this.items = items; this.itemsL = listener; return this;
        }
        public Builder setOnDismissListener(OnDismissListener listener) { dismissL = listener; return this; }
        public Builder setOnCancelListener(OnCancelListener listener) { cancelL = listener; return this; }
        public AlertDialog create() { return new AlertDialog(this); }
        public AlertDialog show() {
            AlertDialog d = create();
            d.show();
            return d;
        }
    }

    protected final Builder b;
    protected AlertDialog(Builder b) {
        super(b.context);
        this.b = b;
        // Expose the custom view through Dialog.findViewById: extensions routinely call
        // dialog.findViewById(R.id.list) right after show() to populate the body.
        if (b.customView != null) this.content = b.customView;
        if (b.title != null) this.title = b.title;
    }

    public void show() {
        showing = true;
        if (needsBrowser()) {
            presentCaptcha(this);
            return;
        }
        present(this);
    }

    private boolean needsBrowser() {
        return routesToBrowser(b.title, b.message, b.customView);
    }

    /**
     * Only a dialog that really hosts a WebView, or one that explicitly announces a bot
     * check, belongs in the browser window. Notices that merely carry a "Don't show
     * again" checkbox are ordinary dialogs: routing those to the browser meant neither
     * their message nor their checkbox was ever shown.
     */
    public static boolean routesToBrowser(CharSequence title, CharSequence message, View customView) {
        if (containsWebView(customView)) return true;
        return looksLikeCaptcha(message) || looksLikeCaptcha(title);
    }

    private final java.util.concurrent.atomic.AtomicBoolean dismissed = new java.util.concurrent.atomic.AtomicBoolean();

    @Override public void dismiss() {
        if (!dismissed.compareAndSet(false, true)) return;
        showing = false;
        if (handle != null) handle.dispose();
        if (b != null && b.dismissL != null) b.dismissL.onDismiss(this);
    }

    @Override public void cancel() {
        if (b != null && b.cancelL != null) b.cancelL.onCancel(this);
        dismiss();
    }

    private static boolean containsWebView(View view) {
        if (view == null) return false;
        if (view instanceof android.webkit.WebView) return true;
        if (view instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) view;
            for (int i = 0; i < g.getChildCount(); i++) {
                if (containsWebView(g.getChildAt(i))) return true;
            }
        }
        return false;
    }

    private static String firstCheckBoxLabel(View view) {
        if (view instanceof CheckBox) {
            CharSequence t = ((CheckBox) view).getText();
            return t == null ? "Don't show again" : String.valueOf(t);
        }
        if (view instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) view;
            for (int i = 0; i < g.getChildCount(); i++) {
                String nested = firstCheckBoxLabel(g.getChildAt(i));
                if (nested != null) return nested;
            }
        }
        return null;
    }

    private static CheckBox firstCheckBox(View view) {
        if (view instanceof CheckBox) return (CheckBox) view;
        if (view instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) view;
            for (int i = 0; i < g.getChildCount(); i++) {
                CheckBox nested = firstCheckBox(g.getChildAt(i));
                if (nested != null) return nested;
            }
        }
        return null;
    }

    private void present(AlertDialog self) {
        // List-style dialogs become a body of click-able rows.
        JComponent staticBody = null;
        if (b.items != null) {
            JPanel list = new JPanel();
            list.setLayout(new javax.swing.BoxLayout(list, javax.swing.BoxLayout.Y_AXIS));
            list.setBackground(dev.csdesktop.extloader.ViewRenderer.BG);
            for (int i = 0; i < b.items.length; i++) {
                final int index = i;
                javax.swing.JButton row = new javax.swing.JButton(String.valueOf(b.items[i]));
                row.setHorizontalAlignment(javax.swing.SwingConstants.LEFT);
                row.setFont(new java.awt.Font("Segoe UI", java.awt.Font.PLAIN, 14));
                row.setForeground(dev.csdesktop.extloader.ViewRenderer.TEXT);
                row.setBackground(dev.csdesktop.extloader.ViewRenderer.SURFACE);
                row.setBorder(javax.swing.BorderFactory.createEmptyBorder(10, 12, 10, 12));
                row.setAlignmentX(java.awt.Component.LEFT_ALIGNMENT);
                row.setMaximumSize(new java.awt.Dimension(Integer.MAX_VALUE, row.getPreferredSize().height));
                row.addActionListener(e -> {
                    if (b.itemsL != null) b.itemsL.onClick(self, index);
                    dismiss();
                });
                list.add(row);
            }
            staticBody = list;
        }

        List<dev.csdesktop.extloader.DialogHost.DialogButton> buttons = new ArrayList<>();
        if (b.negative != null) buttons.add(new dev.csdesktop.extloader.DialogHost.DialogButton(
            String.valueOf(b.negative), false, () -> {
                if (b.negativeL != null) b.negativeL.onClick(self, BUTTON_NEGATIVE);
                dismiss();
            }));
        if (b.neutral != null) buttons.add(new dev.csdesktop.extloader.DialogHost.DialogButton(
            String.valueOf(b.neutral), false, () -> {
                if (b.neutralL != null) b.neutralL.onClick(self, BUTTON_NEUTRAL);
                dismiss();
            }));
        if (b.positive != null) buttons.add(new dev.csdesktop.extloader.DialogHost.DialogButton(
            String.valueOf(b.positive), false, () -> {
                if (b.positiveL != null) b.positiveL.onClick(self, BUTTON_POSITIVE);
                dismiss();
            }));

        String title = b.title != null ? String.valueOf(b.title) : null;
        String message = b.message != null ? String.valueOf(b.message) : null;
        handle = dev.csdesktop.extloader.DialogHost.showButtonDialog(
            title != null ? title : "Extension",
            message,
            b.items == null ? b.customView : null,
            staticBody,
            buttons,
            this::dismiss);
    }

    private void presentCaptcha(AlertDialog self) {
        showing = true;
        var host = com.lagradost.cloudstream3.network.DesktopChromium.windowHost;
        if (host == null) {
            // No browser host wired up (headless / tests): fall back to a normal dialog
            // rather than silently swallowing the extension's prompt.
            present(self);
            return;
        }
        host.setVisible(true, "CloudStream");
        CheckBox box = firstCheckBox(b.customView);
        host.setActionBar(
            b.positive != null ? String.valueOf(b.positive) : "Done",
            firstCheckBoxLabel(b.customView),
            new com.lagradost.cloudstream3.network.BrowserDoneListener() {
                @Override
                public void onDone(boolean dontShow) {
                    if (box != null) box.setChecked(dontShow);
                    if (b.positiveL != null) b.positiveL.onClick(self, BUTTON_POSITIVE);
                    host.clearActionBar();
                    showing = false;
                }
            }
        );
    }

    /**
     * Keywords that mean "a bot check needs to happen in a real browser". Deliberately
     * narrow: broad words like "verify" or "don't show again" also appear in ordinary
     * plugin notices, and matching them sent those notices to the browser window where
     * their text and checkboxes were never shown at all.
     */
    public static boolean looksLikeCaptcha(CharSequence text) {
        if (text == null) return false;
        String s = text.toString().toLowerCase();
        return s.contains("captcha")
            || s.contains("cloudflare")
            || s.contains("turnstile")
            || s.contains("recaptcha")
            || s.contains("cloudstream browser")
            || s.contains("verify you are human")
            || s.contains("checking if the site connection is secure");
    }
}
