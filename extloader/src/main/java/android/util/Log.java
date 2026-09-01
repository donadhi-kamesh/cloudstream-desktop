package android.util;

import dev.csdesktop.log.Logcat;

public final class Log {
    public static final int VERBOSE = 2, DEBUG = 3, INFO = 4, WARN = 5, ERROR = 6, ASSERT = 7;
    public static int v(String tag, String msg) { Logcat.v(tag, msg); return 0; }
    public static int v(String tag, String msg, Throwable tr) { Logcat.e(tag, msg, tr); return 0; }
    public static int d(String tag, String msg) { Logcat.d(tag, msg); return 0; }
    public static int d(String tag, String msg, Throwable tr) { Logcat.e(tag, msg, tr); return 0; }
    public static int i(String tag, String msg) { Logcat.i(tag, msg); return 0; }
    public static int i(String tag, String msg, Throwable tr) { Logcat.e(tag, msg, tr); return 0; }
    public static int w(String tag, String msg) { Logcat.w(tag, msg); return 0; }
    public static int w(String tag, String msg, Throwable tr) { Logcat.e(tag, msg, tr); return 0; }
    public static int w(String tag, Throwable tr) { Logcat.e(tag, "", tr); return 0; }
    public static int e(String tag, String msg) { Logcat.e(tag, msg); return 0; }
    public static int e(String tag, String msg, Throwable tr) { Logcat.e(tag, msg, tr); return 0; }
    public static int wtf(String tag, String msg) { return e(tag, "WTF " + msg); }
    public static int wtf(String tag, String msg, Throwable tr) { return e(tag, "WTF " + msg, tr); }
    public static String getStackTraceString(Throwable tr) {
        java.io.StringWriter sw = new java.io.StringWriter();
        tr.printStackTrace(new java.io.PrintWriter(sw));
        return sw.toString();
    }
    public static int println(int priority, String tag, String msg) { return i(tag, msg); }
}
