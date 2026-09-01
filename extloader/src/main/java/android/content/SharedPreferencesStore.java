package android.content;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** File-backed SharedPreferences used by the desktop Context stub. */
public final class SharedPreferencesStore {
    private static SharedPreferencesStore INSTANCE = new SharedPreferencesStore(new File(System.getProperty("java.io.tmpdir"), "cs-desktop-prefs"));
    private final File dir;
    private final Map<String, Prefs> cache = new ConcurrentHashMap<>();

    public SharedPreferencesStore(File dir) {
        this.dir = dir;
        dir.mkdirs();
    }
    public static SharedPreferencesStore get() { return INSTANCE; }
    public static void init(File dir) { INSTANCE = new SharedPreferencesStore(dir); }
    public SharedPreferences get(String name) {
        return cache.computeIfAbsent(name, n -> new Prefs(new File(dir, n + ".properties")));
    }

    static final class Prefs implements SharedPreferences {
        private final File file;
        private final Map<String, Object> data = new ConcurrentHashMap<>();
        Prefs(File file) {
            this.file = file;
            load();
        }
        private void load() {
            if (!file.isFile()) return;
            Properties p = new Properties();
            try (FileInputStream in = new FileInputStream(file)) {
                p.load(in);
            } catch (Exception ignored) {}
            for (String k : p.stringPropertyNames()) {
                data.put(k, p.getProperty(k));
            }
        }
        private void persist() {
            Properties p = new Properties();
            for (Map.Entry<String, Object> e : data.entrySet()) {
                p.setProperty(e.getKey(), String.valueOf(e.getValue()));
            }
            try (FileOutputStream out = new FileOutputStream(file)) {
                p.store(out, "cs-desktop");
            } catch (Exception ignored) {}
        }
        public Map<String, ?> getAll() { return new HashMap<>(data); }
        public String getString(String key, String defValue) {
            Object v = data.get(key); return v != null ? String.valueOf(v) : defValue;
        }
        public Set<String> getStringSet(String key, Set<String> defValues) {
            Object v = data.get(key);
            if (v instanceof Set) return (Set<String>) v;
            if (v instanceof String && !((String) v).isEmpty()) {
                Set<String> s = new HashSet<>();
                Collections.addAll(s, ((String) v).split("\u001f"));
                return s;
            }
            return defValues;
        }
        public int getInt(String key, int defValue) {
            Object v = data.get(key);
            try { return v == null ? defValue : Integer.parseInt(String.valueOf(v)); } catch (Exception e) { return defValue; }
        }
        public long getLong(String key, long defValue) {
            Object v = data.get(key);
            try { return v == null ? defValue : Long.parseLong(String.valueOf(v)); } catch (Exception e) { return defValue; }
        }
        public float getFloat(String key, float defValue) {
            Object v = data.get(key);
            try { return v == null ? defValue : Float.parseFloat(String.valueOf(v)); } catch (Exception e) { return defValue; }
        }
        public boolean getBoolean(String key, boolean defValue) {
            Object v = data.get(key);
            if (v == null) return defValue;
            if (v instanceof Boolean) return (Boolean) v;
            String s = String.valueOf(v).trim();
            if (s.equalsIgnoreCase("true") || s.equals("1") || s.equalsIgnoreCase("yes")) return true;
            if (s.equalsIgnoreCase("false") || s.equals("0") || s.equalsIgnoreCase("no")) return false;
            return defValue;
        }
        public boolean contains(String key) { return data.containsKey(key); }
        public Editor edit() { return new Ed(); }
        public void registerOnSharedPreferenceChangeListener(OnSharedPreferenceChangeListener listener) {}
        public void unregisterOnSharedPreferenceChangeListener(OnSharedPreferenceChangeListener listener) {}
        final class Ed implements Editor {
            private final Map<String, Object> pending = new HashMap<>();
            private boolean clear;
            public Editor putString(String key, String value) { pending.put(key, value); return this; }
            public Editor putStringSet(String key, Set<String> values) {
                pending.put(key, values == null ? "" : String.join("\u001f", values));
                return this;
            }
            public Editor putInt(String key, int value) { pending.put(key, Integer.toString(value)); return this; }
            public Editor putLong(String key, long value) { pending.put(key, Long.toString(value)); return this; }
            public Editor putFloat(String key, float value) { pending.put(key, Float.toString(value)); return this; }
            public Editor putBoolean(String key, boolean value) { pending.put(key, Boolean.toString(value)); return this; }
            public Editor remove(String key) { pending.put(key, null); return this; }
            public Editor clear() { clear = true; return this; }
            public boolean commit() { apply(); return true; }
            public void apply() {
                if (clear) data.clear();
                for (Map.Entry<String, Object> e : pending.entrySet()) {
                    if (e.getValue() == null) data.remove(e.getKey());
                    else data.put(e.getKey(), e.getValue());
                }
                persist();
            }
        }
    }
}
