package androidx.fragment.app;

import android.app.Activity;
import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import com.lagradost.cloudstream3.CommonActivity;

public class Fragment {
    private Bundle arguments = new Bundle();
    private View view;

    public Context getContext() {
        Activity act = CommonActivity.INSTANCE.getActivity();
        return act != null ? act : new Context();
    }
    public FragmentActivity getActivity() {
        Activity act = CommonActivity.INSTANCE.getActivity();
        return act instanceof FragmentActivity ? (FragmentActivity) act : null;
    }
    public FragmentActivity requireActivity() {
        FragmentActivity a = getActivity();
        if (a == null) throw new IllegalStateException("Fragment not attached");
        return a;
    }
    public Context requireContext() { return getContext(); }
    public FragmentManager getParentFragmentManager() {
        FragmentActivity a = getActivity();
        return a != null ? a.getSupportFragmentManager() : new FragmentManager();
    }
    public FragmentManager getChildFragmentManager() { return new FragmentManager(); }
    public LayoutInflater getLayoutInflater() { return LayoutInflater.from(getContext()); }
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) { return null; }
    public void onViewCreated(View view, Bundle savedInstanceState) {}
    public void onCreate(Bundle savedInstanceState) {}
    public void onStart() {}
    public void onResume() {}
    public void onPause() {}
    public void onStop() {}
    public void onDestroyView() {}
    public void onDestroy() {}
    public View getView() { return view; }
    public View requireView() {
        if (view == null) throw new IllegalStateException("Fragment has no view");
        return view;
    }
    protected void setView(View view) { this.view = view; }
    public void setArguments(Bundle args) { if (args != null) arguments = args; }
    public Bundle getArguments() { return arguments; }
}
