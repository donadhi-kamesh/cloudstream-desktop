package android.os;
public class StatFs {
    public StatFs(String path) {}
    public long getAvailableBytes() { return 1L << 34; }
    public long getTotalBytes() { return 1L << 36; }
    public int getBlockSize() { return 4096; }
    public int getAvailableBlocks() { return Integer.MAX_VALUE / 4; }
}
