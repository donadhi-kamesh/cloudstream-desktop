package android.content;
import java.util.HashMap;
import java.util.Map;
public final class ContentValues {
    private final Map<String, Object> m = new HashMap<>();
    public void put(String key, String value) { m.put(key, value); }
    public void put(String key, Integer value) { m.put(key, value); }
    public void put(String key, Long value) { m.put(key, value); }
    public Object get(String key) { return m.get(key); }
    public String getAsString(String key) { Object v = m.get(key); return v == null ? null : String.valueOf(v); }
}
