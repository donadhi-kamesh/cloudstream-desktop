package androidx.appcompat.app;

import androidx.fragment.app.FragmentActivity;
import androidx.fragment.app.FragmentManager;

public class AppCompatActivity extends FragmentActivity {
    @Override
    public FragmentManager getSupportFragmentManager() {
        return super.getSupportFragmentManager();
    }

    public Object getDelegate() { return null; }
    public Object getSupportActionBar() { return null; }
}
