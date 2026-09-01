package androidx.fragment.app;

public class FragmentTransaction {
    public FragmentTransaction add(Fragment fragment, String tag) {
        showIfDialog(fragment, tag);
        return this;
    }
    public FragmentTransaction add(int containerViewId, Fragment fragment) {
        showIfDialog(fragment, null);
        return this;
    }
    public FragmentTransaction add(int containerViewId, Fragment fragment, String tag) {
        showIfDialog(fragment, tag);
        return this;
    }
    public FragmentTransaction replace(int containerViewId, Fragment fragment) {
        showIfDialog(fragment, null);
        return this;
    }
    public FragmentTransaction replace(int containerViewId, Fragment fragment, String tag) {
        showIfDialog(fragment, tag);
        return this;
    }
    public FragmentTransaction remove(Fragment fragment) {
        if (fragment instanceof DialogFragment) {
            ((DialogFragment) fragment).dismiss();
        }
        return this;
    }
    public FragmentTransaction show(Fragment fragment) {
        showIfDialog(fragment, null);
        return this;
    }
    public FragmentTransaction hide(Fragment fragment) { return this; }
    public int commit() { return 0; }
    public int commitAllowingStateLoss() { return 0; }
    public void commitNow() {}
    public void commitNowAllowingStateLoss() {}

    private static void showIfDialog(Fragment fragment, String tag) {
        if (fragment instanceof DialogFragment) {
            DialogFragment dialog = (DialogFragment) fragment;
            dialog.show(new FragmentManager(), tag);
        }
    }
}
