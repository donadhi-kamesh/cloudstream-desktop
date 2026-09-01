package dalvik.system;
import java.net.URL;
import java.net.URLClassLoader;
public class PathClassLoader extends URLClassLoader {
    public PathClassLoader(String dexPath, ClassLoader parent) {
        super(toUrls(dexPath), parent);
    }
    public PathClassLoader(String dexPath, String librarySearchPath, ClassLoader parent) {
        super(toUrls(dexPath), parent);
    }
    private static URL[] toUrls(String dexPath) {
        try {
            java.io.File f = new java.io.File(dexPath);
            return new URL[] { f.toURI().toURL() };
        } catch (Exception e) {
            return new URL[0];
        }
    }
}
