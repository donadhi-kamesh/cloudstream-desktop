package android.content;
import android.net.Uri;
public class Intent {
    public static final String ACTION_VIEW = "android.intent.action.VIEW";
    public static final String ACTION_MAIN = "android.intent.action.MAIN";
    private String action;
    private Uri data;
    private Class<?> componentClass;
    private String componentClassName;
    public Intent() {}
    public Intent(String action) { this.action = action; }
    public Intent(String action, Uri uri) { this.action = action; this.data = uri; }
    public Intent(Context packageContext, Class<?> cls) { this.componentClass = cls; }
    public Intent setData(Uri data) { this.data = data; return this; }
    public Uri getData() { return data; }
    public String getAction() { return action; }
    public Intent setAction(String action) { this.action = action; return this; }
    public Intent setClass(Context packageContext, Class<?> cls) { this.componentClass = cls; return this; }
    public Intent setClassName(Context packageContext, String className) { this.componentClassName = className; return this; }
    public Intent setClassName(String packageName, String className) { this.componentClassName = className; return this; }
    public Intent addFlags(int flags) { return this; }
    public Intent putExtra(String name, String value) { return this; }
    public Intent putExtra(String name, boolean value) { return this; }
    public Intent putExtra(String name, int value) { return this; }
    /** Desktop-only: component target resolved for startActivity emulation. */
    public Class<?> componentClass() { return componentClass; }
    public String componentClassName() { return componentClassName; }
}
