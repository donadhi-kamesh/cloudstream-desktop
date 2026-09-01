package android.text;
public class TextUtils {
    public static boolean isEmpty(CharSequence s) { return s == null || s.length() == 0; }
    public static String join(CharSequence delimiter, Iterable<?> tokens) {
        StringBuilder sb = new StringBuilder();
        boolean first = true;
        for (Object t : tokens) {
            if (!first) sb.append(delimiter);
            first = false;
            sb.append(t);
        }
        return sb.toString();
    }
    public static boolean equals(CharSequence a, CharSequence b) {
        if (a == b) return true;
        if (a == null || b == null) return false;
        return a.toString().equals(b.toString());
    }
    public enum TruncateAt { START, MIDDLE, END, MARQUEE }
}
