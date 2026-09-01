package android.text.style;

public class ForegroundColorSpan {
    private final int color;
    public ForegroundColorSpan(int color) { this.color = color; }
    public int getForegroundColor() { return color; }
    public void updateDrawState(Object ds) {}
}
