package android.webkit;
import android.graphics.Bitmap;
import android.net.Uri;
public class WebViewClient {
    public void onPageStarted(WebView view, String url, Bitmap favicon) {}
    public void onPageFinished(WebView view, String url) {}
    public boolean shouldOverrideUrlLoading(WebView view, String url) { return false; }
    public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) { return false; }
    public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) { return null; }
    public WebResourceResponse shouldInterceptRequest(WebView view, String url) { return null; }
}
