package android.content;
public class ContextWrapper extends Context {
    private Context base;
    public ContextWrapper(Context base) {
        this.base = base;
        if (base != null) {
            this.filesDir = base.filesDir;
            this.cacheDir = base.cacheDir;
            this.packageName = base.packageName;
            this.assetManager = base.assetManager;
            this.resources = base.resources;
            this.prefsStore = base.prefsStore;
        }
    }
    public Context getBaseContext() { return base != null ? base : this; }
    public Context getApplicationContext() {
        if (base != null) {
            Context app = base.getApplicationContext();
            return app != null ? app : base;
        }
        return this;
    }
    public android.content.res.Resources getResources() {
        if (resources != null) return resources;
        return base != null ? base.getResources() : super.getResources();
    }
    public SharedPreferences getSharedPreferences(String name, int mode) {
        if (prefsStore != null) return prefsStore.get(name);
        return base != null ? base.getSharedPreferences(name, mode) : super.getSharedPreferences(name, mode);
    }
    public Object getSystemService(String name) {
        Object mine = super.getSystemService(name);
        if (mine != null) return mine;
        return base != null ? base.getSystemService(name) : null;
    }
    public void attachBaseContext(Context base) {
        this.base = base;
        if (base != null) {
            this.filesDir = base.filesDir;
            this.cacheDir = base.cacheDir;
            this.packageName = base.packageName;
            this.assetManager = base.assetManager;
            this.resources = base.resources;
            this.prefsStore = base.prefsStore;
        }
    }
}
