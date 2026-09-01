package android.graphics;
public class Point {
    public int x;
    public int y;
    public Point() {}
    public Point(int x, int y) { this.x = x; this.y = y; }
    public Point(Point src) { this.x = src.x; this.y = src.y; }
    public void set(int x, int y) { this.x = x; this.y = y; }
}
