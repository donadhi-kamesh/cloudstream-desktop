package android.webkit;

public class WebChromeClient {
    public void onProgressChanged(WebView view, int newProgress) {}
    public void onReceivedTitle(WebView view, String title) {}
    public boolean onConsoleMessage(ConsoleMessage consoleMessage) { return false; }
    public boolean onJsAlert(WebView view, String url, String message, JsResult result) { return false; }
    public static class FileChooserParams {
        public static final int MODE_OPEN = 0;
        public android.content.Intent createIntent() { return new android.content.Intent(); }
    }
    public static class ConsoleMessage {
        public String message() { return ""; }
        public int lineNumber() { return 0; }
        public String sourceId() { return ""; }
    }
    public static class JsResult {
        public void confirm() {}
        public void cancel() {}
    }
}
