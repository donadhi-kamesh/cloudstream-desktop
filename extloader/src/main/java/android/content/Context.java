package android.content;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.res.AssetManager;
import android.content.res.Resources;
import android.os.Looper;
import java.io.File;
public class Context {
    public static final int MODE_PRIVATE = 0;
    public static final int MODE_MULTI_PROCESS = 4;
    public static final String MODE_WORLD_READABLE_DEPRECATED = "MODE_WORLD_READABLE";
    public static final String WIFI_SERVICE = "wifi";
    public static final String CONNECTIVITY_SERVICE = "connectivity";
    public static final String ACTIVITY_SERVICE = "activity";
    public static final String NOTIFICATION_SERVICE = "notification";
    public static final String CLIPBOARD_SERVICE = "clipboard";
    public static final String WINDOW_SERVICE = "window";
    public static final String LAYOUT_INFLATER_SERVICE = "layout_inflater";

    protected File filesDir;
    protected File cacheDir;
    protected String packageName = "dev.csdesktop";
    protected AssetManager assetManager = new AssetManager();
    protected Resources resources;
    protected SharedPreferencesStore prefsStore;

    public Context() {
        this.filesDir = new File(System.getProperty("java.io.tmpdir"), "cs-desktop-files");
        this.cacheDir = new File(System.getProperty("java.io.tmpdir"), "cs-desktop-cache");
        this.filesDir.mkdirs();
        this.cacheDir.mkdirs();
        this.resources = new Resources(assetManager);
        this.prefsStore = SharedPreferencesStore.get();
    }

    public SharedPreferences getSharedPreferences(String name, int mode) {
        return prefsStore.get(name);
    }
    public File getFilesDir() { return filesDir; }
    public File getCacheDir() { return cacheDir; }
    public File getExternalFilesDir(String type) { return filesDir; }
    public File[] getExternalFilesDirs(String type) { return new File[] { filesDir }; }
    public File getExternalCacheDir() { return cacheDir; }
    public File getDir(String name, int mode) {
        File d = new File(filesDir, name);
        d.mkdirs();
        return d;
    }
    public File getDatabasePath(String name) { return new File(filesDir, name); }
    public String getPackageName() { return packageName; }
    public AssetManager getAssets() { return assetManager; }
    public Resources getResources() { return resources; }
    public Context getApplicationContext() { return this; }
    public ApplicationInfo getApplicationInfo() { return new ApplicationInfo(); }
    public PackageManager getPackageManager() { return new PackageManager(); }
    public ContentResolver getContentResolver() { return new ContentResolver(); }
    public Looper getMainLooper() { return Looper.getMainLooper(); }
    public ClassLoader getClassLoader() { return getClass().getClassLoader(); }
    public Object getSystemService(String name) {
        if (WINDOW_SERVICE.equals(name)) return new android.view.WindowManager.Stub();
        if (LAYOUT_INFLATER_SERVICE.equals(name)) return android.view.LayoutInflater.from(this);
        return null;
    }
    @SuppressWarnings("unchecked")
    public <T> T getSystemService(Class<T> serviceClass) {
        if (serviceClass == null) return null;
        if (android.view.LayoutInflater.class.isAssignableFrom(serviceClass)) {
            return (T) android.view.LayoutInflater.from(this);
        }
        if (android.view.WindowManager.class.isAssignableFrom(serviceClass)) {
            return (T) new android.view.WindowManager.Stub();
        }
        return null;
    }
    public android.view.WindowManager getWindowManager() { return new android.view.WindowManager.Stub(); }
    public android.content.res.Resources.Theme getTheme() { return getResources().newTheme(); }
    public void setTheme(int resid) {}
    public int getColor(int id) { return 0xFFFFFFFF; }
    public android.graphics.drawable.Drawable getDrawable(int id) {
        return new android.graphics.drawable.ColorDrawable(0);
    }
    public android.view.LayoutInflater getLayoutInflater() { return android.view.LayoutInflater.from(this); }
    public void startActivity(Intent intent) {
        dev.csdesktop.extloader.DialogHost.startActivity(this, intent);
    }
    public boolean bindService(Intent service, ServiceConnection conn, int flags) { return false; }
    public void unbindService(ServiceConnection conn) {}
    public String getString(int resId) { return ""; }
    public CharSequence getText(int resId) { return ""; }
    public int checkSelfPermission(String permission) { return 0; }
}
