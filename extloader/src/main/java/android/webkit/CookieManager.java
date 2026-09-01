package android.webkit;

import com.lagradost.cloudstream3.network.DesktopCookieJar;

public class CookieManager {
    private static final CookieManager INSTANCE = new CookieManager();
    public static CookieManager getInstance() { return INSTANCE; }

    public String getCookie(String url) {
        return DesktopCookieJar.INSTANCE.getCookieHeader(url);
    }

    public void setCookie(String url, String value) {
        if (url != null && value != null) DesktopCookieJar.INSTANCE.put(url, value);
    }

    public void removeAllCookies(ValueCallback<Boolean> callback) {
        DesktopCookieJar.INSTANCE.clear();
        if (callback != null) callback.onReceiveValue(true);
    }

    public void flush() { DesktopCookieJar.INSTANCE.flush(); }
    public void setAcceptCookie(boolean accept) {}
    public void setAcceptThirdPartyCookies(WebView webview, boolean accept) {}
}
