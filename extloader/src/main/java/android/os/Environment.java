package android.os;
import java.io.File;
public class Environment {
    public static File getExternalStorageDirectory() { return new File(System.getProperty("user.home")); }
    public static String getExternalStorageState() { return "mounted"; }
    public static File getDataDirectory() { return new File(System.getProperty("user.home")); }
}
