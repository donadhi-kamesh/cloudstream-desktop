package android.content.res;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import java.io.InputStream;
public class Resources {
    private final AssetManager assets;
    private final Configuration config = new Configuration();
    private final DisplayMetrics metrics = new DisplayMetrics();
    private final Theme theme = new Theme();
    public Resources(AssetManager assets, DisplayMetrics metrics, Configuration config) {
        this.assets = assets != null ? assets : new AssetManager();
    }
    public Resources(AssetManager assets) { this.assets = assets != null ? assets : new AssetManager(); }
    private static final java.util.concurrent.ConcurrentHashMap<ClassLoader, Resources> BY_LOADER =
        new java.util.concurrent.ConcurrentHashMap<>();
    private static final java.util.concurrent.CopyOnWriteArrayList<Resources> ALL =
        new java.util.concurrent.CopyOnWriteArrayList<>();
    private static final Resources SYSTEM = new Resources(new AssetManager());
    public static Resources getSystem() { return SYSTEM; }
    public AssetManager getAssets() { return assets; }
    public Configuration getConfiguration() { return config; }
    public DisplayMetrics getDisplayMetrics() { return metrics; }
    public Theme newTheme() { return theme; }
    public Theme getTheme() { return theme; }
    public String getString(int id) {
        if (arsc != null) {
            String s = arsc.stringOf(id);
            if (s != null) return s;
        }
        return "";
    }
    public CharSequence getText(int id) { return getString(id); }
    public int getColor(int id) { return 0xFFFFFFFF; }
    public ColorStateList getColorStateList(int id) { return ColorStateList.valueOf(0xFFFFFFFF); }
    public Drawable getDrawable(int id) { return new ColorDrawable(0); }
    public Drawable getDrawable(int id, Theme theme) { return getDrawable(id); }
    public int getDimensionPixelSize(int id) { return 0; }
    public float getDimension(int id) { return 0; }
    public boolean getBoolean(int id) { return false; }
    public int getInteger(int id) { return 0; }
    public String[] getStringArray(int id) { return new String[0]; }
    public int[] getIntArray(int id) { return new int[0]; }
    public XmlResourceParser getXml(int id) {
        java.io.File f = fileForId(id);
        ArscTable table = arsc;
        if (f == null || !f.isFile()) {
            for (Resources other : ALL) {
                if (other == this) continue;
                java.io.File found = other.fileForId(id);
                if (found != null && found.isFile()) {
                    f = found;
                    table = other.arsc;
                    break;
                }
            }
        }
        if (f != null && f.isFile()) {
            try {
                XmlResourceParser parser = XmlResourceParser.open(f, table);
                parser.setOwner(this);
                return parser;
            } catch (java.io.IOException ignored) {}
        }
        XmlResourceParser empty = new XmlResourceParser();
        empty.setOwner(this);
        return empty;
    }
    public XmlResourceParser getLayout(int id) { return getXml(id); }

    private java.io.File resRoot;
    private ArscTable arsc = new ArscTable();
    private final java.util.Map<Integer, java.io.File> idFiles = new java.util.HashMap<>();

    public void loadArsc(java.io.File arscFile) {
        java.io.File unpack = arscFile != null ? arscFile.getParentFile() : null;
        java.io.File res = unpack != null ? new java.io.File(unpack, "res") : resRoot;
        this.arsc = ArscTable.load(arscFile, res != null ? res : unpack);
        if (this.arsc != null) {
            idFiles.putAll(this.arsc.files());
        }
    }

    public ArscTable arsc() { return arsc; }

    public void setResRoot(java.io.File dir) {
        this.resRoot = dir;
        decodeBinaryXmlTree(dir);
        indexTree(new java.io.File(dir, "xml"), "xml");
        indexTree(new java.io.File(dir, "layout"), "layout");
        indexTree(new java.io.File(dir, "drawable"), "drawable");
        indexTree(new java.io.File(dir, "values"), "values");
    }

    public void bindClassLoader(ClassLoader loader) {
        bindR(loader, "xml");
        bindR(loader, "layout");
        bindR(loader, "id");
        bindR(loader, "string");
        bindR(loader, "drawable");
        if (loader != null) BY_LOADER.put(loader, this);
        if (!ALL.contains(this)) ALL.add(this);
    }

    public static Resources forLoader(ClassLoader loader) {
        return loader == null ? null : BY_LOADER.get(loader);
    }

    public java.io.File fileForId(int id) {
        java.io.File f = idFiles.get(id);
        if (f != null) return f;
        return arsc != null ? arsc.fileOf(id) : null;
    }

    public java.io.File firstXmlFile() {
        if (resRoot == null) return null;
        java.io.File xml = new java.io.File(resRoot, "xml");
        java.io.File[] files = xml.listFiles((d, n) -> n.endsWith(".xml"));
        return files != null && files.length > 0 ? files[0] : null;
    }

    public static java.io.File xmlFileForId(android.content.Context ctx, int id) {
        if (ctx == null || ctx.getResources() == null) return null;
        java.io.File f = ctx.getResources().fileForId(id);
        if (f != null) return f;
        return ctx.getResources().firstXmlFile();
    }

    public static java.io.File firstXmlFile(android.content.Context ctx) {
        return ctx == null || ctx.getResources() == null ? null : ctx.getResources().firstXmlFile();
    }

    private void indexTree(java.io.File dir, String type) {
        if (dir == null || !dir.isDirectory()) return;
        java.io.File[] files = dir.listFiles();
        if (files == null) return;
        for (java.io.File f : files) {
            String name = f.getName();
            int dot = name.lastIndexOf('.');
            String base = dot > 0 ? name.substring(0, dot) : name;
            int id = arsc != null ? arsc.getIdentifier(base, type, null) : 0;
            if (id == 0) id = Math.abs((type + "/" + base).hashCode());
            if (id == 0) id = 1;
            idFiles.put(id, f);
        }
    }

    private void decodeBinaryXmlTree(java.io.File dir) {
        if (dir == null || !dir.isDirectory()) return;
        java.io.File[] files = dir.listFiles();
        if (files == null) return;
        for (java.io.File f : files) {
            if (f.isDirectory()) decodeBinaryXmlTree(f);
            else if (f.getName().endsWith(".xml")) decodeBinaryXmlFile(f);
        }
    }

    private void decodeBinaryXmlFile(java.io.File f) {
        try {
            byte[] data = java.nio.file.Files.readAllBytes(f.toPath());
            if (!AxmlDecoder.isBinary(data)) return;
            String xml = AxmlDecoder.decode(data, arsc);
            java.nio.file.Files.write(f.toPath(), xml.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        } catch (Throwable ignored) {}
    }

    private void bindR(ClassLoader loader, String type) {
        if (loader == null || resRoot == null) return;
        if (!(loader instanceof java.net.URLClassLoader)) return;
        for (java.net.URL url : ((java.net.URLClassLoader) loader).getURLs()) {
            try {
                java.io.File jar = new java.io.File(url.toURI());
                if (!jar.isFile()) continue;
                try (java.util.zip.ZipFile zip = new java.util.zip.ZipFile(jar)) {
                    java.util.Enumeration<? extends java.util.zip.ZipEntry> en = zip.entries();
                    while (en.hasMoreElements()) {
                        String name = en.nextElement().getName();
                        if (!name.endsWith("R$" + type + ".class")) continue;
                        String cn = name.substring(0, name.length() - 6).replace('/', '.');
                        Class<?> clazz = Class.forName(cn, true, loader);
                        for (java.lang.reflect.Field field : clazz.getFields()) {
                            if (field.getType() != int.class) continue;
                            int id = field.getInt(null);
                            java.io.File typed = new java.io.File(resRoot, type);
                            java.io.File f = new java.io.File(typed, field.getName() + ".xml");
                            if (f.isFile()) idFiles.put(id, f);
                        }
                    }
                }
            } catch (Throwable ignored) {}
        }
    }
    public InputStream openRawResource(int id) { return new java.io.ByteArrayInputStream(new byte[0]); }
    public int getIdentifier(String name, String defType, String defPackage) {
        if (name == null || name.isEmpty()) return 0;
        String type = defType != null ? defType : "";
        String base = name;
        int slash = name.indexOf('/');
        if (slash >= 0) {
            type = name.substring(0, slash);
            base = name.substring(slash + 1);
        }
        if (arsc != null) {
            int id = arsc.getIdentifier(base, type, defPackage);
            if (id != 0) return id;
        }
        for (java.util.Map.Entry<Integer, java.io.File> e : idFiles.entrySet()) {
            java.io.File f = e.getValue();
            String fn = f.getName();
            int dot = fn.lastIndexOf('.');
            String b = dot > 0 ? fn.substring(0, dot) : fn;
            if (!b.equals(base)) continue;
            if (type.isEmpty() || (f.getParentFile() != null && type.equals(f.getParentFile().getName()))) {
                return e.getKey();
            }
        }
        int fallback = Math.abs((type + "/" + base).hashCode());
        return fallback != 0 ? fallback : 1;
    }
    public void getValue(int id, TypedValue outValue, boolean resolveRefs) {
        if (outValue != null) outValue.type = TypedValue.TYPE_NULL;
    }
    public static class Theme {
        public boolean resolveAttribute(int resid, TypedValue outValue, boolean resolveRefs) { return false; }
        public void applyStyle(int resid, boolean force) {}
        public Drawable getDrawable(int id) { return new ColorDrawable(0); }
        public int getChangingConfigurations() { return 0; }
        public Resources getResources() { return new Resources(new AssetManager()); }
    }
}
