package android.graphics;
public class Canvas {
    public Canvas() {}
    public Canvas(Bitmap bitmap) {}
    public void drawColor(int color) {}
    public void drawRect(float l, float t, float r, float b, Paint paint) {}
    public void drawCircle(float cx, float cy, float radius, Paint paint) {}
    public void drawText(String text, float x, float y, Paint paint) {}
    public int save() { return 0; }
    public void restore() {}
}
