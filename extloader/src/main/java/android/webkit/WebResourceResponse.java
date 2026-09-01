package android.webkit;
import java.io.InputStream;
import java.util.Map;
public class WebResourceResponse {
    public WebResourceResponse(String mimeType, String encoding, InputStream data) {}
    public WebResourceResponse(String mimeType, String encoding, int statusCode, String reasonPhrase, Map<String, String> responseHeaders, InputStream data) {}
}
