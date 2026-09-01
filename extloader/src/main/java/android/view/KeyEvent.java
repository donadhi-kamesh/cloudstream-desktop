package android.view;

public class KeyEvent {
    public static final int ACTION_DOWN = 0;
    public static final int ACTION_UP = 1;
    public static final int ACTION_MULTIPLE = 2;
    public static final int KEYCODE_UNKNOWN = 0;
    public static final int KEYCODE_BACK = 4;
    public static final int KEYCODE_ENTER = 66;
    public static final int KEYCODE_ESCAPE = 111;
    public static final int KEYCODE_DPAD_CENTER = 23;
    public static final int KEYCODE_DPAD_UP = 19;
    public static final int KEYCODE_DPAD_DOWN = 20;
    public static final int KEYCODE_DPAD_LEFT = 21;
    public static final int KEYCODE_DPAD_RIGHT = 22;
    public static final int META_SHIFT_ON = 1;
    public static final int META_ALT_ON = 2;
    public static final int META_CTRL_ON = 4096;

    private final int action;
    private final int keyCode;
    public KeyEvent(int action, int keyCode) { this.action = action; this.keyCode = keyCode; }
    public int getAction() { return action; }
    public int getKeyCode() { return keyCode; }
    public int getMetaState() { return 0; }
    public char getUnicodeChar() { return 0; }
    public boolean isShiftPressed() { return false; }
    public boolean isCtrlPressed() { return false; }
    public static String keyCodeToString(int keyCode) { return "KEYCODE_" + keyCode; }
}
