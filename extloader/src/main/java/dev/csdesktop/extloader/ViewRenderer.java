package dev.csdesktop.extloader;

import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Insets;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.border.EmptyBorder;

/**
 * Renders an inflated Android view tree as a Swing component tree so extension
 * dialogs / settings pages appear as real UI instead of blank windows. Styling is
 * derived from the attributes captured at inflation time (see
 * {@link android.view.LayoutInflater}).
 */
public final class ViewRenderer {
    // CloudStream Desktop dark palette.
    public static final Color BG = new Color(0x14, 0x14, 0x18);
    public static final Color SURFACE = new Color(0x1C, 0x1C, 0x22);
    public static final Color TEXT = new Color(0xFA, 0xFA, 0xFA);
    public static final Color TEXT_MUTED = new Color(0xB3, 0xB3, 0xB8);
    public static final Color BORDER = new Color(0x2E, 0x2E, 0x33);
    public static final Color ACCENT = new Color(0x9F, 0x6C, 0xF6);

    private static final String ANDROID_NS = "http://schemas.android.com/apk/res/android";
    private static final Font BODY = new Font("Segoe UI", Font.PLAIN, 14);
    private static final Font BODY_BOLD = new Font("Segoe UI", Font.BOLD, 14);

    private ViewRenderer() {}

    /** Renders a view tree; returns null for views that have no desktop form (WebView). */
    public static JComponent render(View view) {
        if (view == null || view.getVisibility() == View.GONE) return null;
        try {
            if (view instanceof android.webkit.WebView) return null;
            if (view instanceof EditText) return renderEdit((EditText) view);
            if (view instanceof CompoundButton) return renderCompound((CompoundButton) view);
            if (view instanceof Button || view instanceof ImageButton) return renderButton(view);
            if (view instanceof ScrollView) return renderScroll((ScrollView) view);
            if (view instanceof TextView) return renderText((TextView) view);
            if (view instanceof ViewGroup) return renderGroup((ViewGroup) view);
            if (view instanceof ImageView) return null;
        } catch (Throwable t) {
            // A broken extension layout must never crash the app, but swallowing this
            // silently once cost a whole settings list, so leave a trail.
            com.lagradost.api.Log.INSTANCE.w(
                "ViewRenderer", "Failed to render " + view.getClass().getName() + ": " + t);
        }
        return null;
    }

    /**
     * Renders [root] into [container] and keeps it in sync. Extensions routinely build
     * their settings UI <em>after</em> calling {@code dialog.show()} — inflating an empty
     * {@code LinearLayout} with id {@code list} and then adding a row per provider — so a
     * one-shot render leaves the user staring at an empty dialog. Every structural or
     * text change in the tree re-renders here, coalesced so a loop that adds twenty rows
     * costs one rebuild.
     *
     * @param onUpdate run on the EDT after each rebuild, so the window can resize itself.
     */
    public static Mounted mount(JComponent container, View root, Runnable onUpdate) {
        if (container == null || root == null) return new Mounted(() -> {});
        container.setLayout(new BorderLayout());
        Runnable rebuild = () -> {
            container.removeAll();
            JComponent body = render(root);
            if (body == null) {
                body = placeholder();
            }
            container.add(body, BorderLayout.CENTER);
            container.revalidate();
            container.repaint();
            if (onUpdate != null) onUpdate.run();
        };
        // Coalesce bursts of addView calls into a single rebuild on the EDT: a loop that
        // adds one row per provider should cost one rebuild, not one per row.
        javax.swing.Timer debounce = new javax.swing.Timer(DEBOUNCE_MS, e -> rebuild.run());
        debounce.setRepeats(false);
        root.setDesktopTreeListener(() -> javax.swing.SwingUtilities.invokeLater(debounce::restart));
        rebuild.run();
        return new Mounted(rebuild);
    }

    static final int DEBOUNCE_MS = 60;

    /** Lets the owner force an immediate rebuild instead of waiting for the debounce. */
    public static final class Mounted {
        private final Runnable rebuild;

        Mounted(Runnable rebuild) { this.rebuild = rebuild; }

        public void refresh() {
            if (javax.swing.SwingUtilities.isEventDispatchThread()) {
                rebuild.run();
                return;
            }
            try {
                javax.swing.SwingUtilities.invokeAndWait(rebuild);
            } catch (Exception e) {
                // Never let a rendering hiccup propagate into extension code.
            }
        }
    }

    /** Shown instead of an empty window when a layout has nothing renderable yet. */
    public static JComponent placeholder() {
        JLabel label = new JLabel("No configurable options available.");
        label.setFont(BODY);
        label.setForeground(TEXT_MUTED);
        label.setBorder(new EmptyBorder(8, 0, 8, 0));
        JPanel panel = new JPanel(new BorderLayout());
        panel.setOpaque(false);
        panel.add(label, BorderLayout.NORTH);
        return panel;
    }

    /** True when the rendered tree has at least one visible, non-blank component. */
    public static boolean hasContent(View view) {
        if (view == null || view.getVisibility() == View.GONE) return false;
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                if (hasContent(group.getChildAt(i))) return true;
            }
            return false;
        }
        if (view instanceof TextView) {
            CharSequence t = ((TextView) view).getText();
            return view instanceof CompoundButton || view instanceof Button
                || view instanceof EditText || (t != null && t.length() > 0);
        }
        return view instanceof ImageButton;
    }

    private static JComponent renderEdit(EditText view) {
        JTextField field = new JTextField(String.valueOf(view.getText()));
        field.setFont(BODY);
        field.setForeground(TEXT);
        field.setBackground(SURFACE);
        field.setCaretColor(TEXT);
        field.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(BORDER, 1),
            BorderFactory.createEmptyBorder(6, 8, 6, 8)));
        field.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            private void sync() {
                View.beginDesktopSync();
                try {
                    view.setText(field.getText());
                } finally {
                    View.endDesktopSync();
                }
            }
            public void insertUpdate(javax.swing.event.DocumentEvent e) { sync(); }
            public void removeUpdate(javax.swing.event.DocumentEvent e) { sync(); }
            public void changedUpdate(javax.swing.event.DocumentEvent e) { sync(); }
        });
        // A single-line field must not swallow the dialog's remaining vertical space.
        field.setMaximumSize(new Dimension(Integer.MAX_VALUE, field.getPreferredSize().height));
        return field;
    }

    private static JComponent renderCompound(CompoundButton view) {
        CharSequence t = view.getText();
        String text = (t != null && t.length() > 0) ? String.valueOf(t) : "";
        JCheckBox box = new JCheckBox(text, view.isChecked());
        box.setFont(BODY);
        box.setForeground(TEXT);
        box.setBackground(BG);
        box.setOpaque(false);
        box.addActionListener(e -> {
            View.beginDesktopSync();
            try {
                view.setChecked(box.isSelected());
                view.performClick();
            } finally {
                View.endDesktopSync();
            }
        });
        view.setDesktopCheckedMirror((buttonView, isChecked) -> {
            if (box.isSelected() != isChecked) box.setSelected(isChecked);
        });
        box.setMaximumSize(new Dimension(Integer.MAX_VALUE, box.getPreferredSize().height));
        return box;
    }

    private static JComponent renderButton(View view) {
        String label = view instanceof ImageButton ? "Save" : "OK";
        if (view instanceof TextView) {
            CharSequence t = ((TextView) view).getText();
            if (t != null && t.length() > 0) label = String.valueOf(t);
        }
        if (view instanceof ImageButton) {
            AttributeSet attrs = asAttrs(view);
            String described = attrs != null ? attr(attrs, "contentDescription") : null;
            if (described != null && !described.isBlank()) label = described;
        }
        JButton button = new JButton(label);
        button.setFont(BODY_BOLD);
        button.setForeground(Color.WHITE);
        button.setBackground(ACCENT);
        button.setBorder(BorderFactory.createEmptyBorder(8, 18, 8, 18));
        button.setCursor(java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.HAND_CURSOR));
        button.addActionListener(e -> view.performClick());
        return button;
    }

    private static JComponent renderScroll(ScrollView view) {
        // The dialog body already scrolls; nested scroll panes are janky.
        if (view.getChildCount() == 0) return null;
        return render(view.getChildAt(0));
    }

    private static JComponent renderText(TextView view) {
        CharSequence t = view.getText();
        AttributeSet attrs = asAttrs(view);
        float size = 14f;
        Color color = TEXT;
        boolean bold = false;
        if (attrs != null) {
            String ts = attr(attrs, "textSize");
            if (ts != null) size = Math.max(12f, android.view.LayoutInflater.parseDimension(ts));
            String tc = attr(attrs, "textColor");
            if (tc != null) color = parseColor(tc);
            String style = attr(attrs, "textStyle");
            if (style != null && style.contains("bold")) bold = true;
        }
        Font font = bold ? BODY_BOLD.deriveFont(size) : BODY.deriveFont(size);
        JLabel label = new JLabel(wrap(t != null && t.length() > 0 ? t : " "));
        label.setFont(font);
        label.setForeground(color);
        label.setBorder(new EmptyBorder(3, 0, 3, 0));
        applyGravity(label, attrs);
        return label;
    }

    private static JComponent renderGroup(ViewGroup group) {
        boolean isRelative = group instanceof android.widget.RelativeLayout;
        boolean isSimpleRow = isRelative && group.getChildCount() <= 3 && hasCompoundOrButtonChild(group);
        boolean horizontal = group instanceof LinearLayout
            && ((LinearLayout) group).getOrientation() == LinearLayout.HORIZONTAL;
        AttributeSet attrs = asAttrs(group);
        JPanel panel = new JPanel();
        if (isSimpleRow) {
            panel.setLayout(new BorderLayout(12, 0));
        } else {
            panel.setLayout(new BoxLayout(panel, horizontal ? BoxLayout.X_AXIS : BoxLayout.Y_AXIS));
        }
        panel.setOpaque(false);
        panel.setBackground(BG);
        if (attrs != null) {
            int[] pad = paddings(attrs);
            panel.setBorder(new EmptyBorder(pad[1], pad[0], pad[3], pad[2]));
        } else {
            panel.setBorder(new EmptyBorder(isSimpleRow ? 4 : 0, 0, isSimpleRow ? 4 : 0, 0));
        }

        if (isSimpleRow) {
            JComponent primary = null;
            JComponent secondary = null;
            for (int i = 0; i < group.getChildCount(); i++) {
                View child = group.getChildAt(i);
                JComponent rendered = render(child);
                if (rendered == null) continue;
                if (child instanceof android.widget.CompoundButton || child instanceof android.widget.Button || child instanceof android.widget.ImageButton) {
                    secondary = rendered;
                } else {
                    primary = rendered;
                }
            }
            if (primary != null) panel.add(primary, BorderLayout.CENTER);
            if (secondary != null) panel.add(secondary, BorderLayout.EAST);
            panel.setMaximumSize(new Dimension(Integer.MAX_VALUE, panel.getPreferredSize().height));
            return panel;
        }

        java.util.List<View> children = new java.util.ArrayList<>();
        for (int i = 0; i < group.getChildCount(); i++) {
            View child = group.getChildAt(i);
            if (child != null) children.add(child);
        }
        // layout_alignParentBottom is a RelativeLayout param; in a LinearLayout the
        // declared child order is what the plugin means, so leave it alone.
        if (isRelative) {
            children.sort((a, c) -> {
                boolean aBottom = attr(asAttrs(a), "layout_alignParentBottom") != null;
                boolean cBottom = attr(asAttrs(c), "layout_alignParentBottom") != null;
                return Boolean.compare(aBottom, cBottom);
            });
        }
        int rendered = 0;
        for (View child : children) {
            JComponent component = render(child);
            if (component == null) continue;
            applyChildLayout(panel, component, horizontal, child);
            rendered++;
        }
        // BoxLayout hands leftover space to whichever child has an unbounded maximum.
        // Pinning the content to the top edge keeps a list of checkboxes as a list
        // instead of stretching each row over the whole dialog.
        if (!horizontal && rendered > 0) {
            panel.add(Box.createVerticalGlue());
        }
        if (horizontal) {
            panel.setMaximumSize(new Dimension(Integer.MAX_VALUE, panel.getPreferredSize().height));
        }
        return panel;
    }

    private static boolean hasCompoundOrButtonChild(ViewGroup group) {
        for (int i = 0; i < group.getChildCount(); i++) {
            View c = group.getChildAt(i);
            if (c instanceof CompoundButton || c instanceof Button || c instanceof ImageButton) return true;
        }
        return false;
    }

    private static void applyChildLayout(JPanel panel, JComponent child, boolean horizontal, View childView) {
        child.setAlignmentX(Component.LEFT_ALIGNMENT);
        child.setAlignmentY(Component.CENTER_ALIGNMENT);
        if (childView != null) {
            AttributeSet attrs = asAttrs(childView);
            if (attrs != null) {
                Integer mb = dimOrNull(attr(attrs, "layout_marginBottom"));
                Integer mt = dimOrNull(attr(attrs, "layout_marginTop"));
                Integer ml = dimOrNull(attr(attrs, "layout_marginStart"), attr(attrs, "layout_marginLeft"));
                Integer mr = dimOrNull(attr(attrs, "layout_marginEnd"), attr(attrs, "layout_marginRight"));
                child.setBorder(BorderFactory.createCompoundBorder(
                    child.getBorder(),
                    new EmptyBorder(or(mt), or(ml), or(mb), or(mr))));
            }
            ViewGroup.LayoutParams lp = childView.getLayoutParams();
            boolean fillsCrossAxis = lp != null
                && (horizontal ? lp.height == ViewGroup.LayoutParams.MATCH_PARENT
                               : lp.width == ViewGroup.LayoutParams.MATCH_PARENT);
            boolean fillsMainAxis = lp != null
                && (horizontal ? lp.width == ViewGroup.LayoutParams.MATCH_PARENT
                               : lp.height == ViewGroup.LayoutParams.MATCH_PARENT);
            if (horizontal) {
                child.setMaximumSize(new Dimension(
                    fillsMainAxis ? Integer.MAX_VALUE : child.getPreferredSize().width,
                    fillsCrossAxis ? Integer.MAX_VALUE : Math.max(child.getPreferredSize().height, 1)));
            } else if (!fillsMainAxis) {
                // In a vertical stack only an explicit match_parent height may grow; every
                // other row keeps its natural height so rows don't smear vertically.
                child.setMaximumSize(new Dimension(Integer.MAX_VALUE, child.getPreferredSize().height));
            }
        } else {
            child.setMaximumSize(new Dimension(Integer.MAX_VALUE, child.getPreferredSize().height));
        }
        panel.add(child);
    }

    private static void applyGravity(JLabel label, AttributeSet attrs) {
        if (attrs == null) return;
        String g = attr(attrs, "gravity");
        if (g == null) return;
        if (g.contains("center")) label.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        else if (g.contains("right") || g.contains("end")) label.setHorizontalAlignment(javax.swing.SwingConstants.RIGHT);
        else if (g.contains("left") || g.contains("start")) label.setHorizontalAlignment(javax.swing.SwingConstants.LEFT);
    }

    // ---- attribute helpers ----

    public static AttributeSet asAttrs(View view) {
        Object o = view != null ? view.getDesktopAttrs() : null;
        return o instanceof AttributeSet ? (AttributeSet) o : null;
    }

    /**
     * Reads an attribute trying the android ns, res-auto/app ns and plain names. Views a
     * plugin builds in code carry no AttributeSet at all, so null is a normal input here.
     */
    public static String attr(AttributeSet attrs, String name) {
        if (attrs == null) return null;
        String[] namespaces = { null, ANDROID_NS, "http://schemas.android.com/apk/res-auto", "http://schemas.android.com/apk/res/app" };
        for (String ns : namespaces) {
            String v = attrs.getAttributeValue(ns, name);
            if (v != null) return v;
        }
        for (String prefix : new String[] { "android:", "app:" }) {
            String v = attrs.getAttributeValue(null, prefix + name);
            if (v != null) return v;
        }
        return null;
    }

    public static int[] paddings(AttributeSet attrs) {
        String all = attr(attrs, "padding");
        Integer l = dimOrNull(attr(attrs, "paddingStart"), attr(attrs, "paddingLeft"), all);
        Integer t = dimOrNull(attr(attrs, "paddingTop"), all);
        Integer r = dimOrNull(attr(attrs, "paddingEnd"), attr(attrs, "paddingRight"), all);
        Integer b = dimOrNull(attr(attrs, "paddingBottom"), all);
        return new int[] { or(l), or(t), or(r), or(b) };
    }

    private static Integer dimOrNull(String... raws) {
        for (String raw : raws) {
            if (raw == null) continue;
            String v = raw.trim().toLowerCase();
            if (v.startsWith("@")) continue;
            try {
                if (v.endsWith("dp") || v.endsWith("sp") || v.endsWith("px") || v.endsWith("dip")) {
                    return (int) Float.parseFloat(v.substring(0, v.length() - 2));
                }
                return (int) Float.parseFloat(v);
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private static int or(Integer v) { return v != null ? v : 0; }

    public static Color parseColor(String raw) {
        if (raw == null) return null;
        String v = raw.trim();
        if (v.startsWith("@") || v.startsWith("?")) return null;
        try {
            if (v.startsWith("#")) {
                long value = Long.parseLong(v.substring(1), 16);
                if (v.length() == 6) value |= 0xFF000000L;
                return new Color((int) value, true);
            }
            if (v.startsWith("0x") || v.startsWith("0X")) {
                long value = Long.parseLong(v.substring(2), 16);
                if (v.length() == 8) value |= 0xFF000000L;
                return new Color((int) value, true);
            }
        } catch (NumberFormatException ignored) {
        }
        return namedColor(v);
    }

    private static Color namedColor(String name) {
        switch (name) {
            case "white": return Color.WHITE;
            case "black": return Color.BLACK;
            case "transparent": return new Color(0, 0, 0, 0);
            case "red": return Color.RED;
            case "gray":
            case "grey": return Color.GRAY;
            case "lightgray":
            case "lightgrey": return Color.LIGHT_GRAY;
            case "darkgray":
            case "darkgrey": return Color.DARK_GRAY;
            default: return null;
        }
    }

    /** JLabel-friendly text: escape XML and keep newlines via <br>. */
    public static String wrap(CharSequence text) {
        String t = String.valueOf(text)
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\n", "<br>");
        return "<html><body style='width: 420px'>" + t + "</body></html>";
    }

    public static Insets empty(int top, int left, int bottom, int right) {
        return new Insets(top, left, bottom, right);
    }
}
