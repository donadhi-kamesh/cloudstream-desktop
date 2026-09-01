package dev.csdesktop.extloader;

import android.content.Context;
import android.view.View;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GraphicsEnvironment;
import java.awt.Window;
import java.util.ArrayList;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;

/**
 * Desktop window host for extension UI: dark styled dialogs for popups and
 * settings, activity windows for startActivity, and toast notifications.
 */
public final class DialogHost {
    public static final Color BG = new Color(0x14, 0x14, 0x18);
    public static final Color SURFACE = new Color(0x1C, 0x1C, 0x22);
    public static final Color TEXT = new Color(0xFA, 0xFA, 0xFA);
    public static final Color TEXT_MUTED = new Color(0xB3, 0xB3, 0xB8);
    public static final Color BORDER = new Color(0x2E, 0x2E, 0x33);
    public static final Color ACCENT = new Color(0x9F, 0x6C, 0xF6);

    private static final Font TITLE_FONT = new Font("Segoe UI", Font.BOLD, 16);
    private static final Font BODY_FONT = new Font("Segoe UI", Font.PLAIN, 14);
    private static final Font BUTTON_FONT = new Font("Segoe UI", Font.BOLD, 13);

    private DialogHost() {}

    // ---- dialogs ----

    /** Handle returned to the android shim so {@code Dialog.dismiss()} closes the window. */
    public static final class Handle {
        private volatile JDialog dialog;
        private volatile Long sessionId;

        void attach(JDialog dialog) { this.dialog = dialog; }
        public void attachSession(long id) { this.sessionId = id; }

        public void dispose() {
            Long id = sessionId;
            sessionId = null;
            if (id != null) {
                try {
                    ExtensionUi.INSTANCE.dismiss(id);
                } catch (Throwable ignored) {}
            }
            runEdt(() -> {
                JDialog d = dialog;
                dialog = null;
                if (d != null) {
                    d.setVisible(false);
                    d.dispose();
                }
            });
        }

        public boolean isShowing() {
            JDialog d = dialog;
            return d != null && d.isVisible();
        }
    }

    /**
     * Shows an extension dialog rendering [content] as the body. The dialog is
     * modeless: extension code continues to run, may keep adding views, and calls
     * dismiss() later.
     */
    public static Handle showDialog(String title, View content) {
        return showButtonDialog(title, null, content, null, null);
    }

    /**
     * Shows an extension dialog with a message, a live-rendered android view body and a
     * button row. Any of the three may be absent; a notice popup that only sets a
     * message still shows its text, and a settings sheet that only sets a view still
     * shows its widgets.
     */
    public static Handle showButtonDialog(
            String title,
            String message,
            View content,
            List<DialogButton> buttons,
            Runnable onDismiss
    ) {
        return showButtonDialog(title, message, content, null, buttons, onDismiss);
    }

    /**
     * @param content android view tree kept live-rendered, or null.
     * @param staticBody pre-built Swing body (list-style dialogs), or null.
     */
    public static Handle showButtonDialog(
            String title,
            String message,
            View content,
            JComponent staticBody,
            List<DialogButton> buttons,
            Runnable onDismiss
    ) {
        if (ExtensionUi.INSTANCE.getComposeAttached() || ExtensionUi.INSTANCE.getSuppressPopups()) {
            return ExtensionUi.INSTANCE.present(title, message, content, buttons, onDismiss);
        }
        Handle handle = new Handle();
        runEdt(() -> {
            JDialog dialog = baseDialog(title);
            handle.attach(dialog);
            JPanel container = new JPanel(new BorderLayout());
            container.setBackground(BG);
            container.setOpaque(true);
            if (message != null && !message.isBlank()) {
                JLabel label = new JLabel(ViewRenderer.wrap(message));
                label.setFont(BODY_FONT);
                label.setForeground(TEXT);
                label.setBackground(BG);
                label.setBorder(new EmptyBorder(4, 0, 10, 0));
                container.add(label, BorderLayout.NORTH);
            }

            JComponent body = staticBody;
            if (body == null && content != null) {
                JPanel live = new JPanel(new BorderLayout());
                live.setBackground(BG);
                live.setOpaque(true);
                JScrollPane pane = scrollPane(live);
                container.add(pane, BorderLayout.CENTER);
                ViewRenderer.mount(live, content, () -> resizeBody(dialog, pane, live));
            } else if (body != null) {
                container.add(scrollPane(body), BorderLayout.CENTER);
            } else if (message == null || message.isBlank()) {
                container.add(ViewRenderer.placeholder(), BorderLayout.CENTER);
            }

            JPanel row = new JPanel();
            row.setLayout(new BoxLayout(row, BoxLayout.X_AXIS));
            row.setBackground(BG);
            row.setBorder(new EmptyBorder(14, 0, 0, 0));
            row.add(Box.createHorizontalGlue());
            // A dialog closed by its window chrome must still notify the extension, but
            // exactly once — a button press already reports its own outcome.
            java.util.concurrent.atomic.AtomicBoolean notified = new java.util.concurrent.atomic.AtomicBoolean();
            boolean any = false;
            if (buttons != null) {
                for (DialogButton b : buttons) {
                    if (b == null) continue;
                    any = true;
                    row.add(button(b.label, b.destructive, () -> {
                        notified.set(true);
                        handle.dispose();
                        if (b.onClick != null) b.onClick.run();
                    }));
                    row.add(Box.createHorizontalStrut(10));
                }
            }
            if (!any) {
                row.add(button("Close", false, handle::dispose));
            }
            container.add(row, BorderLayout.SOUTH);
            dialog.getContentPane().add(container, BorderLayout.CENTER);
            dialog.addWindowListener(new java.awt.event.WindowAdapter() {
                @Override public void windowClosed(java.awt.event.WindowEvent e) {
                    if (notified.compareAndSet(false, true) && onDismiss != null) onDismiss.run();
                }
            });
            dialog.pack();
            position(dialog, 520, 0);
            dialog.setVisible(true);
        });
        return handle;
    }

    public static final class DialogButton {
        public final String label;
        public final boolean destructive;
        public final Runnable onClick;

        public DialogButton(String label, boolean destructive, Runnable onClick) {
            this.label = label;
            this.destructive = destructive;
            this.onClick = onClick;
        }
    }

    private static final int BODY_WIDTH = 480;
    private static final int BODY_MIN_HEIGHT = 120;
    private static final int BODY_MAX_HEIGHT = 560;

    /** Wraps the rendered body in a scroll pane with dialog-friendly sizing. */
    private static JScrollPane scrollPane(JComponent body) {
        body.setBackground(BG);
        body.setOpaque(true);
        JScrollPane pane = new JScrollPane(body);
        pane.setBorder(null);
        pane.getViewport().setBackground(BG);
        pane.setBackground(BG);
        pane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        pane.getVerticalScrollBar().setUnitIncrement(16);
        pane.setPreferredSize(bodySize(body));
        return pane;
    }

    private static Dimension bodySize(JComponent body) {
        int height = body.getPreferredSize().height + 12;
        return new Dimension(BODY_WIDTH, Math.min(Math.max(height, BODY_MIN_HEIGHT), BODY_MAX_HEIGHT));
    }

    /**
     * Re-fits the window after the live body grew or shrank. Without this a dialog that
     * was packed while its provider list was still empty keeps the empty size forever.
     */
    private static void resizeBody(JDialog dialog, JScrollPane pane, JComponent body) {
        Dimension wanted = bodySize(body);
        if (wanted.equals(pane.getPreferredSize())) return;
        pane.setPreferredSize(wanted);
        pane.revalidate();
        if (!dialog.isVisible()) return;
        int width = dialog.getWidth();
        dialog.pack();
        // pack() may shrink the width the user resized to; keep the wider of the two, and
        // leave the window where it is so a growing list doesn't make it jump around.
        dialog.setSize(Math.max(width, dialog.getWidth()), dialog.getHeight());
    }

    private static JDialog baseDialog(String title) {
        JDialog dialog = new JDialog();
        dialog.setTitle(title == null || title.isBlank() ? "CloudStream" : title);
        dialog.setModal(false);
        dialog.setDefaultCloseOperation(javax.swing.WindowConstants.DISPOSE_ON_CLOSE);
        dialog.getContentPane().setBackground(BG);
        JPanel chrome = new JPanel(new BorderLayout());
        chrome.setBackground(BG);
        chrome.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(BORDER, 1),
            new EmptyBorder(16, 18, 16, 18)));
        if (title != null && !title.isBlank()) {
            JLabel t = new JLabel(title);
            t.setFont(TITLE_FONT);
            t.setForeground(TEXT);
            t.setBorder(new EmptyBorder(0, 0, 12, 0));
            chrome.add(t, BorderLayout.NORTH);
        }
        // The window content is the chrome panel; callers add their body into it.
        dialog.setContentPane(chrome);
        return dialog;
    }

    private static void position(Window window, int preferredWidth, int preferredHeight) {
        try {
            var screen = GraphicsEnvironment.getLocalGraphicsEnvironment()
                .getDefaultScreenDevice().getDefaultConfiguration().getBounds();
            int w = Math.max(window.getWidth(), preferredWidth);
            int h = Math.max(window.getHeight(), preferredHeight);
            w = Math.min(w, screen.width - 40);
            h = Math.min(h, screen.height - 40);
            window.setSize(w, h);
            window.setLocation(screen.x + (screen.width - w) / 2, screen.y + (screen.height - h) / 2);
        } catch (Throwable ignored) {
        }
    }

    private static Component button(String label, boolean destructive, Runnable onClick) {
        javax.swing.JButton b = new javax.swing.JButton(label);
        b.setFont(BUTTON_FONT);
        b.setForeground(Color.WHITE);
        b.setBackground(destructive ? new Color(0xE1, 0x1D, 0x48) : ACCENT);
        b.setBorder(BorderFactory.createEmptyBorder(8, 18, 8, 18));
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        b.addActionListener(e -> onClick.run());
        return b;
    }

    // ---- activity windows ----

    private static final List<android.app.Activity> openActivities = new ArrayList<>();

    /** Emulates startActivity for extension settings activities. */
    public static void startActivity(Context context, android.content.Intent intent) {
        if (intent == null) return;
        runEdt(() -> {
            Class<?> clazz = intent.componentClass();
            if (clazz == null && intent.componentClassName() != null && context != null) {
                try {
                    clazz = Class.forName(intent.componentClassName(), true, context.getClassLoader());
                } catch (Throwable ignored) {
                }
            }
            if (clazz == null || !android.app.Activity.class.isAssignableFrom(clazz)) {
                // Plain ACTION_VIEW intents fall back to the browser.
                String data = intent.getData() != null ? intent.getData().toString() : null;
                if (data != null && intent.getAction() != null
                        && intent.getAction().equals(android.content.Intent.ACTION_VIEW)) {
                    openExternally(data);
                }
                return;
            }
            try {
                android.app.Activity activity = (android.app.Activity) clazz.getDeclaredConstructor().newInstance();
                if (activity instanceof android.content.ContextWrapper wrapper) {
                    wrapper.attachBaseContext(context != null ? context : wrapper);
                }
                // Lifecycle methods stay protected (plugins override them); invoke reflectively.
                callLifecycle(activity, "onCreate", new Object[]{null});
                callLifecycle(activity, "onStart", new Object[]{});
                callLifecycle(activity, "onResume", new Object[]{});
                View content = activity.getDesktopContentView();
                if (content == null) {
                    // Activity did not setContentView — nothing meaningful to show.
                    return;
                }
                CharSequence t = activity.getDesktopTitle();
                String title = t != null && t.length() > 0 ? String.valueOf(t) : "Extension";
                JDialog window = baseDialog(title);
                JPanel live = new JPanel(new BorderLayout());
                live.setBackground(BG);
                live.setOpaque(true);
                JScrollPane pane = scrollPane(live);
                window.getContentPane().add(pane, BorderLayout.CENTER);
                ViewRenderer.mount(live, content, () -> resizeBody(window, pane, live));
                window.pack();
                position(window, 480, 0);
                window.setVisible(true);
                openActivities.add(activity);
                activity.attachDesktopWindow(window, () -> {
                    openActivities.remove(activity);
                });
            } catch (Throwable t) {
                errorDialog("Could not open extension screen", t);
            }
        });
    }

    static void openExternally(String url) {
        try {
            java.awt.Desktop.getDesktop().browse(new java.net.URI(url));
        } catch (Throwable ignored) {
        }
    }

    private static void callLifecycle(android.app.Activity activity, String name, Object[] args) throws Exception {
        Class<?> type = activity.getClass();
        while (type != null && !type.getName().startsWith("android.") && !type.getName().startsWith("androidx.")) {
            for (java.lang.reflect.Method m : type.getDeclaredMethods()) {
                if (!m.getName().equals(name) || m.getParameterCount() != args.length) continue;
                Class<?>[] params = m.getParameterTypes();
                boolean matches = true;
                for (int i = 0; i < params.length; i++) {
                    Object a = args[i];
                    if (a != null && !params[i].isAssignableFrom(a.getClass())) matches = false;
                }
                if (matches) {
                    m.setAccessible(true);
                    m.invoke(activity, args);
                    return;
                }
            }
            type = type.getSuperclass();
        }
        // Fall back to the stub Activity implementations.
        java.lang.reflect.Method stub = android.app.Activity.class.getDeclaredMethod(
            name, name.equals("onCreate") ? new Class<?>[]{android.os.Bundle.class} : new Class<?>[]{});
        stub.setAccessible(true);
        stub.invoke(activity, args);
    }

    public static void errorDialog(String title, Throwable t) {
        runEdt(() -> {
            javax.swing.JOptionPane.showMessageDialog(
                null,
                String.valueOf(t.getClass().getSimpleName()) + ": " + t.getMessage(),
                title,
                javax.swing.JOptionPane.ERROR_MESSAGE);
        });
    }

    // ---- toasts ----

    public static void toast(CharSequence message) {
        if (message == null || message.length() == 0) return;
        runEdt(() -> {
            javax.swing.JWindow toast = new javax.swing.JWindow();
            JPanel panel = new JPanel();
            panel.setBackground(SURFACE);
            panel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(BORDER, 1),
                new EmptyBorder(12, 18, 12, 18)));
            JLabel label = new JLabel(String.valueOf(message));
            label.setForeground(TEXT);
            label.setFont(BODY_FONT);
            panel.add(label);
            toast.getContentPane().setBackground(new Color(0, 0, 0, 0));
            toast.setContentPane(panel);
            toast.pack();
            try {
                var screen = GraphicsEnvironment.getLocalGraphicsEnvironment()
                    .getDefaultScreenDevice().getDefaultConfiguration().getBounds();
                toast.setLocation(screen.x + (screen.width - toast.getWidth()) / 2, screen.y + screen.height - 140);
            } catch (Throwable ignored) {
            }
            toast.setVisible(true);
            javax.swing.Timer hide = new javax.swing.Timer(2400, e -> {
                toast.setVisible(false);
                toast.dispose();
            });
            hide.setRepeats(false);
            hide.start();
        });
    }

    private static void runEdt(Runnable runnable) {
        if (SwingUtilities.isEventDispatchThread()) {
            runnable.run();
        } else {
            SwingUtilities.invokeLater(runnable);
        }
    }
}
