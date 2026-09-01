package android.os;
public final class Parcel {
    public static Parcel obtain() { return new Parcel(); }
    public void recycle() {}
}
