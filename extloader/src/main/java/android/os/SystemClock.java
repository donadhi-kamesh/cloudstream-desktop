package android.os;
public final class SystemClock {
    public static long elapsedRealtime() { return System.currentTimeMillis(); }
    public static long uptimeMillis() { return System.currentTimeMillis(); }
    public static long elapsedRealtimeNanos() { return System.nanoTime(); }
    public static void sleep(long ms) { try { Thread.sleep(ms); } catch (InterruptedException ignored) {} }
}
