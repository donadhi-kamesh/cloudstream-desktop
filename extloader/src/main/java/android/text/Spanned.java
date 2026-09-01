package android.text;

public interface Spanned extends CharSequence {
    int SPAN_EXCLUSIVE_EXCLUSIVE = 33;
    int SPAN_EXCLUSIVE_INCLUSIVE = 34;
    int SPAN_INCLUSIVE_EXCLUSIVE = 17;
    int SPAN_INCLUSIVE_INCLUSIVE = 18;
    int SPAN_COMPOSING = 256;
    int SPAN_INTERMEDIATE = 512;
    int SPAN_MARK_MARK = 17;
    int SPAN_MARK_POINT = 18;
    int SPAN_POINT_MARK = 33;
    int SPAN_POINT_POINT = 34;
    int SPAN_PARAGRAPH = 51;
    int SPAN_PRIORITY = 16711680;
    int SPAN_USER = -16777216;

    <T> T[] getSpans(int start, int end, Class<T> type);
    int getSpanStart(Object tag);
    int getSpanEnd(Object tag);
    int getSpanFlags(Object tag);
    int nextSpanTransition(int start, int limit, Class<?> type);
}
