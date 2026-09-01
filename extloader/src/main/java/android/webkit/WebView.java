package android.webkit;

import android.content.Context;
import android.view.MotionEvent;
import android.widget.FrameLayout;
import com.lagradost.api.Log;
import com.lagradost.cloudstream3.network.DesktopChromium;
import com.lagradost.cloudstream3.network.DesktopCookieJar;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Plugin-facing android.webkit.WebView. CNC / Cricify construct this to solve
 * Cloudflare and intercept embeds. Desktop drives Edge/Chrome over CDP and
 * writes cookies into {@link CookieManager}.
 */
public class WebView extends FrameLayout {
    private final WebSettings settings = new WebSettings();
    private WebViewClient client;
    private WebChromeClient chrome;
    private volatile DesktopChromium.PageSession session;
    private volatile String currentUrl = "";
    private final AtomicBoolean destroyed = new AtomicBoolean(false);
    private final Map<String, Object> jsInterfaces = new ConcurrentHashMap<>();
    private Thread worker;

    public WebView(Context context) {
        super(context);
        measuredWidth = 1100;
        measuredHeight = 800;
        Log.INSTANCE.i("WebView", "Using in-app Edge/Chrome WebView.");
    }

    public WebView(Context context, android.util.AttributeSet attrs) {
        this(context);
    }

    public WebSettings getSettings() { return settings; }

    public void setWebViewClient(WebViewClient client) { this.client = client; }
    public void setWebChromeClient(WebChromeClient client) { this.chrome = client; }

    public void addJavascriptInterface(Object object, String name) {
        if (name != null && object != null) jsInterfaces.put(name, object);
    }
    public void removeJavascriptInterface(String name) {
        if (name != null) jsInterfaces.remove(name);
    }

    private static final java.util.concurrent.Executor WEB = java.util.concurrent.Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "cs-webview");
        t.setDaemon(true);
        return t;
    });

    public synchronized void loadUrl(String url) {
        if (url == null) return;
        DesktopChromium.PageSession existing = session;
        if (existing != null && !destroyed.get()) {
            String now = existing.currentUrl();
            if (url.equals(now) || DesktopChromium.INSTANCE.looksLikeChallenge(now)) {
                Log.INSTANCE.i("WebView", "already solving challenge, not reloading");
                currentUrl = now;
                return;
            }
        }
        currentUrl = url;
        destroyed.set(false);
        closeSession();
        final WebView self = this;
        WEB.execute(() -> runSession(self, url));
    }

    private void runSession(WebView self, String url) {
        try {
            if (client != null) client.onPageStarted(self, url, null);
            DesktopChromium.PageSession opened = DesktopChromium.INSTANCE.openPage(url, settings.getUserAgentString());
            session = opened;
            opened.injectJsInterfaces(jsInterfaces);
            boolean finished = false;
            long start = System.currentTimeMillis();
            long deadline = start + 180_000L;
            while (!destroyed.get() && opened.isOpen() && System.currentTimeMillis() < deadline) {
                opened.harvestCookies(url);
                String now = opened.currentUrl();
                if (now != null && !now.isEmpty()) currentUrl = now;
                String title = opened.title();
                boolean challenge = looksLikeChallengePage(now, title);
                boolean cleared = com.lagradost.cloudstream3.network.DesktopCookieJar.INSTANCE.hasClearance(now);
                if (!finished && (cleared || (!challenge && System.currentTimeMillis() - start > 2_000L))) {
                    finished = true;
                    if (client != null) {
                        try { client.onPageFinished(self, currentUrl); } catch (Throwable t) {
                            Log.INSTANCE.e("WebView", "onPageFinished: " + t.getMessage());
                        }
                    }
                    if (chrome != null) {
                        try { chrome.onReceivedTitle(self, title); } catch (Throwable ignored) {}
                    }
                }
                if (client != null) {
                    for (okhttp3.Request req : opened.drainRequests()) {
                        try {
                            client.shouldInterceptRequest(self, new SimpleRequest(req));
                        } catch (Throwable ignored) {}
                    }
                }
                Thread.sleep(400);
            }
        } catch (Throwable t) {
            Log.INSTANCE.e("WebView", "loadUrl failed: " + t.getMessage());
        }
    }

    private static boolean looksLikeChallengePage(String url, String title) {
        String u = url == null ? "" : url.toLowerCase();
        String t = title == null ? "" : title.toLowerCase();
        return t.contains("just a moment") || t.contains("attention required")
            || t.contains("verify you are human") || t.contains("cloudflare")
            || DesktopChromium.INSTANCE.looksLikeChallenge(u);
    }

    public void loadData(String data, String mime, String encoding) {}
    public void loadDataWithBaseURL(String baseUrl, String data, String mimeType, String encoding, String historyUrl) {
        if (baseUrl != null) loadUrl(baseUrl);
    }

    public void destroy() {
        destroyed.set(true);
        closeSession();
    }

    private void closeSession() {
        DesktopChromium.PageSession s = session;
        session = null;
        if (s != null) {
            try { s.close(); } catch (Throwable ignored) {}
        }
    }

    public void stopLoading() { closeSession(); }
    public void reload() { if (currentUrl != null && !currentUrl.isEmpty()) loadUrl(currentUrl); }
    public void goBack() {}
    public void goForward() {}
    public boolean canGoBack() { return false; }
    public String getUrl() { return currentUrl; }
    public String getTitle() {
        DesktopChromium.PageSession s = session;
        return s != null ? s.title() : "";
    }
    public void setBackgroundColor(int color) {}
    public void setLayerType(int layerType, android.graphics.Paint paint) {}
    public void onPause() {}
    public void onResume() {}
    public android.os.IBinder getWindowToken() { return new android.os.Binder(); }

    public void evaluateJavascript(String script, ValueCallback<String> resultCallback) {
        DesktopChromium.PageSession s = session;
        String result = "null";
        if (s != null && script != null) {
            String got = s.evaluate(script);
            if (got != null) result = got;
        }
        if (resultCallback != null) {
            final String value = result;
            post(() -> resultCallback.onReceiveValue(value));
        }
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent event) {
        DesktopChromium.PageSession s = session;
        if (s != null && event != null && event.getAction() == MotionEvent.ACTION_UP) {
            s.click(event.getX(), event.getY());
        }
        return true;
    }

    public static void setWebContentsDebuggingEnabled(boolean enabled) {}

    private static final class SimpleRequest implements WebResourceRequest {
        private final okhttp3.Request req;
        SimpleRequest(okhttp3.Request req) { this.req = req; }
        @Override public android.net.Uri getUrl() { return android.net.Uri.parse(req.url().toString()); }
        @Override public boolean isForMainFrame() { return true; }
        @Override public boolean isRedirect() { return false; }
        @Override public boolean hasGesture() { return false; }
        @Override public String getMethod() { return req.method(); }
        @Override public java.util.Map<String, String> getRequestHeaders() {
            return req.headers().toMultimap().entrySet().stream()
                .collect(java.util.stream.Collectors.toMap(java.util.Map.Entry::getKey, e -> String.join(",", e.getValue())));
        }
    }
}
