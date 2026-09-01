package android.content;
public interface ServiceConnection {
    void onServiceConnected(android.content.ComponentName name, android.os.IBinder service);
    void onServiceDisconnected(android.content.ComponentName name);
}
