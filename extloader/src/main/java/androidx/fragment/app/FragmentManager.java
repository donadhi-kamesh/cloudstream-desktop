package androidx.fragment.app;

public class FragmentManager {
    public FragmentTransaction beginTransaction() { return new FragmentTransaction(); }
    public Fragment findFragmentByTag(String tag) { return null; }
    public boolean isDestroyed() { return false; }
    public boolean isStateSaved() { return false; }
    public void executePendingTransactions() {}
    public void popBackStack() {}
    public boolean popBackStackImmediate() { return false; }
    public java.util.List<Fragment> getFragments() { return java.util.Collections.emptyList(); }
}
