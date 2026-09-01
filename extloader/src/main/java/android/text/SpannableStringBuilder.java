package android.text;

public class SpannableStringBuilder implements CharSequence, Editable, Spannable {
    private final StringBuilder buf;

    public SpannableStringBuilder() { this.buf = new StringBuilder(); }
    public SpannableStringBuilder(CharSequence text) {
        this.buf = new StringBuilder(text == null ? "" : text.toString());
    }

    @Override public int length() { return buf.length(); }
    @Override public char charAt(int index) { return buf.charAt(index); }
    @Override public CharSequence subSequence(int start, int end) { return buf.subSequence(start, end); }
    @Override public String toString() { return buf.toString(); }

    @Override public SpannableStringBuilder append(CharSequence text) {
        buf.append(text);
        return this;
    }
    @Override public SpannableStringBuilder append(CharSequence text, int start, int end) {
        buf.append(text, start, end);
        return this;
    }
    @Override public SpannableStringBuilder append(char text) {
        buf.append(text);
        return this;
    }
    public SpannableStringBuilder append(CharSequence text, Object what, int flags) {
        return append(text);
    }
    @Override public SpannableStringBuilder replace(int st, int en, CharSequence source, int start, int end) {
        buf.replace(st, en, source == null ? "" : source.subSequence(start, end).toString());
        return this;
    }
    @Override public SpannableStringBuilder replace(int st, int en, CharSequence text) {
        buf.replace(st, en, text == null ? "" : text.toString());
        return this;
    }
    @Override public SpannableStringBuilder insert(int where, CharSequence text, int start, int end) {
        buf.insert(where, text, start, end);
        return this;
    }
    @Override public SpannableStringBuilder insert(int where, CharSequence text) {
        buf.insert(where, text);
        return this;
    }
    @Override public SpannableStringBuilder delete(int st, int en) {
        buf.delete(st, en);
        return this;
    }
    @Override public void clear() { buf.setLength(0); }

    @Override public void setSpan(Object what, int start, int end, int flags) {}
    @Override public void removeSpan(Object what) {}
    @Override public <T> T[] getSpans(int start, int end, Class<T> type) {
        @SuppressWarnings("unchecked") T[] empty = (T[]) java.lang.reflect.Array.newInstance(type, 0);
        return empty;
    }
    @Override public int getSpanStart(Object tag) { return -1; }
    @Override public int getSpanEnd(Object tag) { return -1; }
    @Override public int getSpanFlags(Object tag) { return 0; }
    @Override public int nextSpanTransition(int start, int limit, Class<?> type) { return limit; }
}
