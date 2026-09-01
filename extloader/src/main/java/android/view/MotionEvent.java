package android.view;

public class MotionEvent {
    public static final int ACTION_DOWN = 0;
    public static final int ACTION_UP = 1;
    public static final int ACTION_MOVE = 2;
    public static final int ACTION_CANCEL = 3;
    public static final int ACTION_POINTER_DOWN = 5;
    public static final int ACTION_POINTER_UP = 6;
    public static final int ACTION_MASK = 255;
    private final int action;
    private final float x;
    private final float y;
    public MotionEvent(int action, float x, float y) { this.action = action; this.x = x; this.y = y; }
    public int getAction() { return action; }
    public int getActionMasked() { return action & ACTION_MASK; }
    public float getX() { return x; }
    public float getY() { return y; }
    public float getRawX() { return x; }
    public float getRawY() { return y; }
    public int getPointerCount() { return 1; }
    public long getEventTime() { return System.currentTimeMillis(); }
    public long getDownTime() { return 0; }
    public void recycle() {}

    public static MotionEvent obtain(long downTime, long eventTime, int action, float x, float y, int metaState) {
        return new MotionEvent(action, x, y);
    }
    public static MotionEvent obtain(long downTime, long eventTime, int action, int pointerCount, Object properties, Object pointerCoords, int metaState, int buttonState, float xPrecision, float yPrecision, int deviceId, int edgeFlags, int source, int flags) {
        return new MotionEvent(action, 0, 0);
    }
}
