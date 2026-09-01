package android.os;
public final class Message {
    public int what;
    public int arg1, arg2;
    public Object obj;
    public static Message obtain() { return new Message(); }
    public void recycle() {}
}
