package android.webkit;
public class WebSettings {
    public static final int LOAD_DEFAULT = -1;
    public static final int LOAD_NO_CACHE = 2;
    public static final int MIXED_CONTENT_ALWAYS_ALLOW = 0;
    private String userAgent = "Mozilla/5.0 (Linux; Android 13; Pixel 5) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36";
    public void setJavaScriptEnabled(boolean flag) {}
    public void setDomStorageEnabled(boolean flag) {}
    public void setDatabaseEnabled(boolean flag) {}
    public void setUserAgentString(String ua) { if (ua != null && !ua.isEmpty()) userAgent = ua; }
    public String getUserAgentString() { return userAgent; }
    public void setLoadWithOverviewMode(boolean overview) {}
    public void setUseWideViewPort(boolean use) {}
    public void setSupportZoom(boolean support) {}
    public void setBuiltInZoomControls(boolean enabled) {}
    public void setDisplayZoomControls(boolean enabled) {}
    public void setCacheMode(int mode) {}
    public void setMediaPlaybackRequiresUserGesture(boolean require) {}
    public void setMixedContentMode(int mode) {}
    public void setAllowFileAccess(boolean allow) {}
    public void setAllowContentAccess(boolean allow) {}
    public void setJavaScriptCanOpenWindowsAutomatically(boolean flag) {}
}
