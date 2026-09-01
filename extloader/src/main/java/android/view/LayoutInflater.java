package android.view;

import android.content.Context;
import android.content.res.Resources;
import android.content.res.XmlResourceParser;
import android.util.AttributeSet;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;
import java.lang.reflect.Constructor;
import org.xmlpull.v1.XmlPullParser;

public class LayoutInflater {
    private static final String ANDROID_NS = "http://schemas.android.com/apk/res/android";

    private final Context context;
    private Factory factory;
    private Factory2 factory2;
    private Filter filter;
    /** Resources that produced the layout currently being inflated, if known. */
    private Resources sourceResources;

    public LayoutInflater(Context context) { this.context = context; }
    public static LayoutInflater from(Context context) { return new LayoutInflater(context); }

    public Context getContext() { return context; }
    public LayoutInflater cloneInContext(Context newContext) { return new LayoutInflater(newContext); }

    public void setFactory(Factory factory) { this.factory = factory; }
    public void setFactory2(Factory2 factory) { this.factory2 = factory; this.factory = factory; }
    public Factory getFactory() { return factory; }
    public Factory2 getFactory2() { return factory2; }
    public void setFilter(Filter filter) { this.filter = filter; }
    public Filter getFilter() { return filter; }

    public View inflate(int resource, ViewGroup root) {
        return inflate(resource, root, root != null);
    }

    public View inflate(int resource, ViewGroup root, boolean attachToRoot) {
        Resources res = context != null ? context.getResources() : null;
        Resources fromLoader = Resources.forLoader(Thread.currentThread().getContextClassLoader());
        if (fromLoader != null && fromLoader.fileForId(resource) != null) res = fromLoader;
        if (res != null) {
            try {
                XmlResourceParser parser = res.getXml(resource);
                try {
                    return inflate(parser, root, attachToRoot);
                } finally {
                    parser.close();
                }
            } catch (Throwable ignored) {}
        }
        return fallback(root, attachToRoot);
    }

    public View inflate(XmlPullParser parser, ViewGroup root) {
        return inflate(parser, root, root != null);
    }

    public View inflate(XmlPullParser parser, ViewGroup root, boolean attachToRoot) {
        Resources previous = sourceResources;
        if (parser instanceof XmlResourceParser) {
            Resources owner = ((XmlResourceParser) parser).getOwner();
            if (owner != null) sourceResources = owner;
        }
        try {
            return inflateInternal(parser, root, attachToRoot);
        } finally {
            sourceResources = previous;
        }
    }

    private View inflateInternal(XmlPullParser parser, ViewGroup root, boolean attachToRoot) {
        try {
            int type = parser.getEventType();
            while (type != XmlPullParser.START_TAG && type != XmlPullParser.END_DOCUMENT) {
                type = parser.next();
            }
            if (type != XmlPullParser.START_TAG) return fallback(root, attachToRoot);
            AttributeSet attrs = snapshot(parser);
            View view = createViewFromTag(root, parser.getName(), attrs);
            if (view instanceof ViewGroup) {
                rInflate(parser, (ViewGroup) view);
            } else {
                skip(parser);
            }
            applyAttributes(view, attrs);
            if (root != null && attachToRoot) {
                root.addView(view);
                return root;
            }
            if (root != null) {
                view.setLayoutParams(root.generateLayoutParams(attrs));
            }
            return view;
        } catch (Throwable t) {
            return fallback(root, attachToRoot);
        }
    }

    private void rInflate(XmlPullParser parser, ViewGroup parent) throws Exception {
        int depth = parser.getDepth();
        int type;
        while ((type = parser.next()) != XmlPullParser.END_DOCUMENT
                && (type != XmlPullParser.END_TAG || parser.getDepth() > depth)) {
            if (type != XmlPullParser.START_TAG) continue;
            String name = parser.getName();
            if ("requestFocus".equals(name) || "tag".equals(name) || "include".equals(name) || "merge".equals(name)) {
                if ("include".equals(name) || "merge".equals(name)) {
                    View child = new LinearLayout(context);
                    applyAttributes(child, asAttrs(parser));
                    parent.addView(child);
                    skip(parser);
                } else {
                    skip(parser);
                }
                continue;
            }
            AttributeSet attrs = snapshot(parser);
            View child = createViewFromTag(parent, name, attrs);
            applyAttributes(child, attrs);
            if (child instanceof ViewGroup && !parser.isEmptyElementTag()) {
                rInflate(parser, (ViewGroup) child);
            } else {
                skip(parser);
            }
            parent.addView(child);
        }
    }

    private static void skip(XmlPullParser parser) throws Exception {
        if (parser.getEventType() != XmlPullParser.START_TAG) return;
        int depth = 1;
        while (depth > 0) {
            int type = parser.next();
            if (type == XmlPullParser.END_TAG) depth--;
            else if (type == XmlPullParser.START_TAG) depth++;
        }
    }

    private View createViewFromTag(View parent, String name, AttributeSet attrs) {
        String fqn = name;
        if (name != null && name.indexOf('.') == -1) {
            String cls = attrs != null ? attrs.getClassAttribute() : null;
            if (cls != null && cls.length() > 0) fqn = cls;
        }
        View fromFactory = null;
        if (factory2 != null) {
            fromFactory = factory2.onCreateView(parent, fqn, context, attrs);
        } else if (factory != null) {
            fromFactory = factory.onCreateView(fqn, context, attrs);
        }
        if (fromFactory != null) return fromFactory;
        View builtIn = builtin(name, attrs);
        if (builtIn != null) return builtIn;
        View created = construct(fqn, attrs);
        if (created != null) return created;
        if (name != null && name.indexOf('.') == -1) {
            created = construct("android.widget." + name, attrs);
            if (created != null) return created;
            created = construct("android.view." + name, attrs);
            if (created != null) return created;
            created = construct("androidx.appcompat.widget.AppCompat" + name, attrs);
            if (created != null) return created;
        }
        return new LinearLayout(context);
    }

    private View builtin(String name, AttributeSet attrs) {
        if (name == null) return null;
        String simple = name;
        int dot = name.lastIndexOf('.');
        if (dot >= 0) simple = name.substring(dot + 1);
        switch (simple) {
            case "LinearLayout": return new LinearLayout(context, attrs);
            case "FrameLayout": return new FrameLayout(context, attrs);
            case "RelativeLayout": return new RelativeLayout(context, attrs);
            case "ConstraintLayout": return new androidx.constraintlayout.widget.ConstraintLayout(context, attrs);
            case "CoordinatorLayout": return new androidx.coordinatorlayout.widget.CoordinatorLayout(context, attrs);
            case "ScrollView": return new android.widget.ScrollView(context, attrs);
            case "NestedScrollView": return new androidx.core.widget.NestedScrollView(context, attrs);
            case "HorizontalScrollView": return new android.widget.HorizontalScrollView(context, attrs);
            case "RecyclerView": return new androidx.recyclerview.widget.RecyclerView(context, attrs);
            case "TextView": return new TextView(context, attrs);
            case "EditText":
            case "TextInputEditText": return new EditText(context, attrs);
            case "AutoCompleteTextView": return new android.widget.AutoCompleteTextView(context, attrs);
            case "Button":
            case "MaterialButton": return new Button(context, attrs);
            case "ImageView": return new ImageView(context, attrs);
            case "ImageButton": return new ImageButton(context, attrs);
            case "CheckBox":
            case "MaterialCheckBox": return new CheckBox(context, attrs);
            case "RadioButton": return new android.widget.RadioButton(context, attrs);
            case "RadioGroup": return new android.widget.RadioGroup(context, attrs);
            case "Switch":
            case "SwitchCompat":
            case "SwitchMaterial": return new android.widget.Switch(context, attrs);
            case "ProgressBar": return new android.widget.ProgressBar(context, attrs);
            case "Space": return new android.widget.Space(context, attrs);
            case "View": return new View(context);
            default: return null;
        }
    }

    private View construct(String className, AttributeSet attrs) {
        if (className == null || className.indexOf('.') < 0) return null;
        Class<?> clazz = loadClass(className);
        if (clazz == null || !View.class.isAssignableFrom(clazz)) return null;
        Object[][] tries = new Object[][] {
            new Object[] { new Class[] { Context.class, AttributeSet.class, int.class }, new Object[] { context, attrs, 0 } },
            new Object[] { new Class[] { Context.class, AttributeSet.class }, new Object[] { context, attrs } },
            new Object[] { new Class[] { Context.class }, new Object[] { context } },
        };
        for (Object[] attempt : tries) {
            try {
                Constructor<?> ctor = clazz.getConstructor((Class<?>[]) attempt[0]);
                ctor.setAccessible(true);
                return (View) ctor.newInstance((Object[]) attempt[1]);
            } catch (Throwable ignored) {}
        }
        return null;
    }

    private Class<?> loadClass(String name) {
        ClassLoader[] loaders = new ClassLoader[] {
            Thread.currentThread().getContextClassLoader(),
            context != null ? context.getClassLoader() : null,
            LayoutInflater.class.getClassLoader(),
        };
        for (ClassLoader cl : loaders) {
            if (cl == null) continue;
            try {
                return Class.forName(name, false, cl);
            } catch (Throwable ignored) {}
        }
        return null;
    }

    private void applyAttributes(View view, AttributeSet attrs) {
        if (view == null || attrs == null) return;
        view.setDesktopAttrs(attrs);
        String id = firstAttr(attrs, "id");
        if (id != null) {
            String name = idName(id);
            if (name != null) view.setDesktopIdName(name);
            int parsed = parseId(id);
            if (parsed != 0) view.setId(parsed);
        }
        String vis = firstAttr(attrs, "visibility");
        if ("gone".equals(vis)) view.setVisibility(View.GONE);
        else if ("invisible".equals(vis)) view.setVisibility(View.INVISIBLE);
        int[] pad = parsePaddings(attrs);
        if (pad != null) view.setPadding(pad[0], pad[1], pad[2], pad[3]);
        if (view instanceof TextView) {
            String text = firstAttr(attrs, "text");
            if (text != null) ((TextView) view).setText(resolveValue(text));
            String hint = firstAttr(attrs, "hint");
            if (hint != null) ((TextView) view).setHint(resolveValue(hint));
        }
        if (view instanceof android.widget.CompoundButton) {
            String checked = firstAttr(attrs, "checked");
            if (checked != null) ((android.widget.CompoundButton) view).setChecked("true".equalsIgnoreCase(checked));
        }
        if (view instanceof LinearLayout) {
            String orientation = firstAttr(attrs, "orientation");
            if ("horizontal".equals(orientation)) ((LinearLayout) view).setOrientation(LinearLayout.HORIZONTAL);
            else if ("vertical".equals(orientation)) ((LinearLayout) view).setOrientation(LinearLayout.VERTICAL);
        }
    }

    private int[] parsePaddings(AttributeSet attrs) {
        String all = firstAttr(attrs, "padding");
        Integer l = dim(firstAttr(attrs, "paddingStart"), all), t = dim(firstAttr(attrs, "paddingTop"), all);
        Integer r = dim(firstAttr(attrs, "paddingEnd"), all), b = dim(firstAttr(attrs, "paddingBottom"), all);
        if (l == null && t == null && r == null && b == null) return null;
        return new int[] { or(l, 0), or(t, 0), or(r, 0), or(b, 0) };
    }

    private static int or(Integer v, int fallback) { return v != null ? v : fallback; }

    /** "10dp" / "8px" → pixels (desktop ≈ mdpi so 1dp ≈ 1px); resource refs resolve to 0. */
    private Integer dim(String raw, String all) {
        String v = raw != null ? raw : all;
        if (v == null || v.startsWith("@")) return null;
        return parseDimension(v);
    }

    public static int parseDimension(String v) {
        try {
            String s = v.trim().toLowerCase();
            if (s.endsWith("dp") || s.endsWith("sp") || s.endsWith("px") || s.endsWith("dip")) {
                return (int) Float.parseFloat(s.substring(0, s.length() - 2));
            }
            return (int) Float.parseFloat(s);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /**
     * Resources to resolve references against: the table the layout came from wins over
     * the host Context, whose resources point at whichever plugin was attached last.
     */
    private Resources resources() {
        if (sourceResources != null) return sourceResources;
        return context != null ? context.getResources() : null;
    }

    /** Resolves "@string/name" / "@color/name" style references against plugin resources. */
    private String resolveValue(String raw) {
        if (raw == null || !raw.startsWith("@")) return raw;
        Resources res = resources();
        if (res == null) return raw;
        try {
            String v = raw.substring(1);
            int slash = v.indexOf('/');
            if (slash <= 0) return raw;
            String type = v.substring(0, slash);
            String name = v.substring(slash + 1);
            String pkg = context != null ? context.getPackageName() : null;
            int id = res.getIdentifier(name, type, pkg);
            if (id == 0) return raw;
            if ("string".equals(type) || "text".equals(type)) {
                String s = res.getString(id);
                return s != null && !s.isEmpty() ? s : raw;
            }
            if ("color".equals(type)) return String.format("#%08X", res.getColor(id));
            return raw;
        } catch (Throwable t) {
            return raw;
        }
    }

    /** "@+id/list" -> "list"; null for anything that is not an id reference. */
    private static String idName(String raw) {
        if (raw == null) return null;
        String s = raw.trim();
        if (!s.startsWith("@+id/") && !s.startsWith("@id/")) return null;
        String name = s.substring(s.indexOf('/') + 1);
        return name.isEmpty() ? null : name;
    }

    private int parseId(String raw) {
        if (raw == null) return 0;
        String s = raw.trim();
        String name = idName(s);
        if (name != null) {
            Resources res = resources();
            if (res != null) {
                String pkg = context != null ? context.getPackageName() : null;
                int id = res.getIdentifier(name, "id", pkg);
                if (id != 0) return id;
            }
            return View.stableId(name);
        }
        try {
            if (s.startsWith("0x") || s.startsWith("0X")) return Integer.parseInt(s.substring(2), 16);
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            return Math.abs(s.hashCode());
        }
    }

    private static String firstAttr(AttributeSet attrs, String name) {
        String v = attrs.getAttributeValue(ANDROID_NS, name);
        if (v != null) return v;
        v = attrs.getAttributeValue(null, name);
        if (v != null) return v;
        v = attrs.getAttributeValue(null, "android:" + name);
        return v;
    }

    private static AttributeSet snapshot(XmlPullParser parser) {
        AttributeSet live = asAttrs(parser);
        int n = 0;
        try { n = live.getAttributeCount(); } catch (Throwable ignored) {}
        String[] names = new String[Math.max(n, 0)];
        String[] values = new String[names.length];
        String[] nss = new String[names.length];
        for (int i = 0; i < names.length; i++) {
            names[i] = live.getAttributeName(i);
            values[i] = live.getAttributeValue(i);
            nss[i] = parser.getAttributeNamespace(i);
        }
        String classAttr = live.getClassAttribute();
        String idAttr = live.getIdAttribute();
        return new SnapshotAttrs(names, values, nss, classAttr, idAttr);
    }

    private static AttributeSet asAttrs(XmlPullParser parser) {
        if (parser instanceof AttributeSet) return (AttributeSet) parser;
        return new PullAttrs(parser);
    }

    private static final class SnapshotAttrs implements AttributeSet {
        private final String[] names;
        private final String[] values;
        private final String[] nss;
        private final String classAttr;
        private final String idAttr;
        SnapshotAttrs(String[] names, String[] values, String[] nss, String classAttr, String idAttr) {
            this.names = names;
            this.values = values;
            this.nss = nss;
            this.classAttr = classAttr;
            this.idAttr = idAttr;
        }
        @Override public int getAttributeCount() { return names.length; }
        @Override public String getAttributeName(int index) { return names[index]; }
        @Override public String getAttributeValue(int index) { return values[index]; }
        @Override public String getAttributeValue(String namespace, String name) {
            for (int i = 0; i < names.length; i++) {
                if (!name.equals(names[i])) continue;
                if (namespace == null || namespace.isEmpty() || namespace.equals(nss[i])
                    || (ANDROID_NS.equals(namespace) && nss[i] != null && nss[i].contains("apk/res/android"))) {
                    return values[i];
                }
            }
            if (name != null && name.startsWith("android:")) {
                return getAttributeValue(null, name.substring(8));
            }
            return null;
        }
        @Override public int getAttributeResourceValue(String namespace, String attribute, int defaultValue) { return defaultValue; }
        @Override public int getAttributeIntValue(String namespace, String attribute, int defaultValue) {
            String v = getAttributeValue(namespace, attribute);
            if (v == null) return defaultValue;
            if ("match_parent".equals(v) || "fill_parent".equals(v)) return -1;
            if ("wrap_content".equals(v)) return -2;
            try { return Integer.parseInt(v); } catch (NumberFormatException e) { return defaultValue; }
        }
        @Override public boolean getAttributeBooleanValue(String namespace, String attribute, boolean defaultValue) {
            String v = getAttributeValue(namespace, attribute);
            if (v == null) return defaultValue;
            return "true".equalsIgnoreCase(v) || "1".equals(v);
        }
        @Override public String getClassAttribute() { return classAttr; }
        @Override public String getIdAttribute() { return idAttr != null ? idAttr : getAttributeValue(ANDROID_NS, "id"); }
        @Override public int getIdAttributeResourceValue(int defaultValue) { return defaultValue; }
        @Override public int getStyleAttribute() { return 0; }
    }

    private View fallback(ViewGroup root, boolean attachToRoot) {
        LinearLayout layout = new LinearLayout(context);
        if (root != null && attachToRoot) {
            root.addView(layout);
            return root;
        }
        return layout;
    }

    public View createView(String name, String prefix, AttributeSet attrs) throws ClassNotFoundException {
        String fqn = prefix != null ? prefix + name : name;
        View v = construct(fqn, attrs);
        if (v == null) throw new ClassNotFoundException(fqn);
        return v;
    }

    protected View onCreateView(String name, AttributeSet attrs) {
        return createViewFromTag(null, name, attrs);
    }

    protected View onCreateView(View parent, String name, AttributeSet attrs) {
        return createViewFromTag(parent, name, attrs);
    }

    public interface Factory {
        View onCreateView(String name, Context context, AttributeSet attrs);
    }

    public interface Factory2 extends Factory {
        View onCreateView(View parent, String name, Context context, AttributeSet attrs);
    }

    public interface Filter {
        boolean onLoadClass(Class<?> clazz);
    }

    private static final class PullAttrs implements AttributeSet {
        private final XmlPullParser p;
        PullAttrs(XmlPullParser p) { this.p = p; }
        @Override public int getAttributeCount() { return p.getAttributeCount(); }
        @Override public String getAttributeName(int index) { return p.getAttributeName(index); }
        @Override public String getAttributeValue(int index) { return p.getAttributeValue(index); }
        @Override public String getAttributeValue(String namespace, String name) { return p.getAttributeValue(namespace, name); }
        @Override public int getAttributeResourceValue(String namespace, String attribute, int defaultValue) { return defaultValue; }
        @Override public int getAttributeIntValue(String namespace, String attribute, int defaultValue) {
            String v = getAttributeValue(namespace, attribute);
            if (v == null) return defaultValue;
            try { return Integer.parseInt(v); } catch (NumberFormatException e) { return defaultValue; }
        }
        @Override public boolean getAttributeBooleanValue(String namespace, String attribute, boolean defaultValue) {
            String v = getAttributeValue(namespace, attribute);
            if (v == null) return defaultValue;
            return "true".equalsIgnoreCase(v) || "1".equals(v);
        }
        @Override public String getClassAttribute() { return getAttributeValue(null, "class"); }
        @Override public String getIdAttribute() { return getAttributeValue(null, "id"); }
        @Override public int getIdAttributeResourceValue(int defaultValue) { return defaultValue; }
        @Override public int getStyleAttribute() { return 0; }
    }
}
