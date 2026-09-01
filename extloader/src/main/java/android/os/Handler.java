package android.os;
import java.util.concurrent.Executor;
public class Handler {
    public Handler() {}
    public Handler(Looper looper) {}
    public Handler(Looper looper, Callback callback) {}
    public boolean post(Runnable r) {
        if (r != null) {
            java.util.concurrent.Executors.newSingleThreadExecutor().execute(r);
        }
        return true;
    }
    public boolean postDelayed(Runnable r, long delayMillis) {
        new Thread(() -> {
            try { Thread.sleep(delayMillis); } catch (InterruptedException ignored) {}
            r.run();
        }).start();
        return true;
    }
    public void removeCallbacks(Runnable r) {}
    public void removeCallbacksAndMessages(Object token) {}
    public boolean sendEmptyMessage(int what) { return true; }
    public interface Callback { boolean handleMessage(Message msg); }
}
