package android.os;
import java.util.HashMap;
import java.util.Map;
public class Bundle implements android.os.Parcelable {
    private final Map<String, Object> map = new HashMap<>();
    public Bundle() {}
    public Bundle(Bundle other) { if (other != null) map.putAll(other.map); }
    public void putString(String k, String v) { map.put(k, v); }
    public void putInt(String k, int v) { map.put(k, v); }
    public void putLong(String k, long v) { map.put(k, v); }
    public void putBoolean(String k, boolean v) { map.put(k, v); }
    public String getString(String k) { Object v = map.get(k); return v instanceof String ? (String) v : null; }
    public String getString(String k, String def) { String v = getString(k); return v != null ? v : def; }
    public int getInt(String k) { Object v = map.get(k); return v instanceof Integer ? (Integer) v : 0; }
    public int getInt(String k, int def) { Object v = map.get(k); return v instanceof Integer ? (Integer) v : def; }
    public long getLong(String k) { Object v = map.get(k); return v instanceof Long ? (Long) v : 0L; }
    public boolean getBoolean(String k) { Object v = map.get(k); return v instanceof Boolean ? (Boolean) v : false; }
    public boolean containsKey(String k) { return map.containsKey(k); }
    public boolean isEmpty() { return map.isEmpty(); }
    public void clear() { map.clear(); }
    public int describeContents() { return 0; }
    public void writeToParcel(Parcel dest, int flags) {}
}
