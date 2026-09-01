package android.content.pm;
public class PackageManager {
    public static final int GET_META_DATA = 128;
    public ApplicationInfo getApplicationInfo(String packageName, int flags) { return new ApplicationInfo(); }
    public String getInstallerPackageName(String packageName) { return null; }
}
