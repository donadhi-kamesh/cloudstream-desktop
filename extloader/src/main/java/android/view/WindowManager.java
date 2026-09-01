package android.view;

public interface WindowManager {
    void addView(View view, ViewGroup.LayoutParams params);
    void updateViewLayout(View view, ViewGroup.LayoutParams params);
    void removeView(View view);
    Display getDefaultDisplay();

    class LayoutParams extends ViewGroup.LayoutParams {
        public static final int MATCH_PARENT = -1;
        public static final int WRAP_CONTENT = -2;
        public static final int TYPE_APPLICATION = 2;
        public static final int TYPE_APPLICATION_PANEL = 1000;
        public static final int TYPE_APPLICATION_ATTACHED_DIALOG = 1003;
        public static final int FLAG_NOT_FOCUSABLE = 8;
        public static final int FLAG_NOT_TOUCHABLE = 16;
        public static final int FLAG_NOT_TOUCH_MODAL = 32;
        public static final int FLAG_LAYOUT_IN_SCREEN = 256;
        public static final int FLAG_FULLSCREEN = 1024;
        public static final int FLAG_DIM_BEHIND = 2;
        public static final int FLAG_KEEP_SCREEN_ON = 128;
        public static final int SOFT_INPUT_STATE_HIDDEN = 2;
        public static final int SOFT_INPUT_ADJUST_RESIZE = 16;
        public int x, y;
        public int gravity;
        public int flags;
        public int type = TYPE_APPLICATION;
        public int format;
        public float dimAmount = 0.5f;
        public int softInputMode;
        public String token;
        public LayoutParams() { super(WRAP_CONTENT, WRAP_CONTENT); }
        public LayoutParams(int w, int h) { super(w, h); }
        public LayoutParams(int type) { super(WRAP_CONTENT, WRAP_CONTENT); this.type = type; }
        public LayoutParams(int w, int h, int type, int flags, int format) {
            super(w, h); this.type = type; this.flags = flags; this.format = format;
        }
    }

    class Stub implements WindowManager {
        private final Display display = new Display();
        @Override public void addView(View view, ViewGroup.LayoutParams params) {}
        @Override public void updateViewLayout(View view, ViewGroup.LayoutParams params) {}
        @Override public void removeView(View view) {}
        @Override public Display getDefaultDisplay() { return display; }
    }
}
