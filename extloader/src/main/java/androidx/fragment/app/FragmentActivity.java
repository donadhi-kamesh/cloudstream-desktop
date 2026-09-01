package androidx.fragment.app;

import androidx.activity.ComponentActivity;

public class FragmentActivity extends ComponentActivity {
    private final FragmentManager fragmentManager = new FragmentManager();

    public FragmentManager getSupportFragmentManager() { return fragmentManager; }

    public FragmentManager getFragmentManagerCompat() { return fragmentManager; }
}
