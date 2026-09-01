package android.net;
import java.net.URI;
import java.util.List;
public class Uri {
    private final String value;
    private Uri(String value) { this.value = value == null ? "" : value; }
    public static Uri parse(String uriString) { return new Uri(uriString); }
    public static Uri fromFile(java.io.File file) { return new Uri(file.toURI().toString()); }
    public String toString() { return value; }
    public String getScheme() { try { return URI.create(value).getScheme(); } catch (Exception e) { return null; } }
    public String getHost() { try { return URI.create(value).getHost(); } catch (Exception e) { return null; } }
    public String getPath() { try { return URI.create(value).getPath(); } catch (Exception e) { return value; } }
    public String getLastPathSegment() {
        String p = getPath();
        if (p == null || p.isEmpty()) return null;
        int i = p.lastIndexOf('/');
        return i >= 0 ? p.substring(i + 1) : p;
    }
    public String getQueryParameter(String key) {
        try {
            String q = URI.create(value).getQuery();
            if (q == null) return null;
            for (String part : q.split("&")) {
                int eq = part.indexOf('=');
                String k = eq >= 0 ? part.substring(0, eq) : part;
                String v = eq >= 0 ? part.substring(eq + 1) : "";
                if (k.equals(key)) return java.net.URLDecoder.decode(v, "UTF-8");
            }
        } catch (Exception ignored) {}
        return null;
    }
    public Builder buildUpon() { return new Builder(value); }
    public static final class Builder {
        private String current;
        public Builder() { this.current = ""; }
        Builder(String current) { this.current = current; }
        public Builder scheme(String scheme) {
            int idx = current.indexOf("://");
            if (idx >= 0) current = scheme + current.substring(idx);
            else current = scheme + "://" + current;
            return this;
        }
        public Builder appendQueryParameter(String key, String value) {
            current += (current.contains("?") ? "&" : "?") + key + "=" + value;
            return this;
        }
        public Builder path(String path) { return this; }
        public Uri build() { return Uri.parse(current); }
        public String toString() { return current; }
    }
}
