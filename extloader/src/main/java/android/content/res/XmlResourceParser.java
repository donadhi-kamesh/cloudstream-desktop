package android.content.res;

import android.util.AttributeSet;
import java.io.IOException;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;
import org.xmlpull.v1.XmlPullParserFactory;

public class XmlResourceParser implements AttributeSet, XmlPullParser {
    private final XmlPullParser parser;
    /**
     * The Resources that handed out this parser. Plugins inflate with
     * {@code inflater.inflate(plugin.resources.getLayout(id), ...)}, so the ids inside
     * the layout must be resolved against the plugin's own resource table rather than
     * whichever Resources happens to be attached to the host Context.
     */
    private Resources owner;

    public void setOwner(Resources owner) { this.owner = owner; }
    public Resources getOwner() { return owner; }

    public XmlResourceParser() {
        this.parser = XmlPullParserFactory.newInstance().newPullParser();
    }

    public XmlResourceParser(java.io.InputStream in) {
        this(readAll(in), null);
    }

    public XmlResourceParser(java.io.InputStream in, ArscTable arsc) {
        this(readAll(in), arsc);
    }

    public XmlResourceParser(byte[] data, ArscTable arsc) {
        XmlPullParser p = XmlPullParserFactory.newInstance().newPullParser();
        try {
            if (AxmlDecoder.isBinary(data)) {
                String xml = AxmlDecoder.decode(data, arsc);
                p.setInput(new java.io.StringReader(xml));
            } else {
                p.setInput(new java.io.ByteArrayInputStream(data), "UTF-8");
            }
        } catch (XmlPullParserException ignored) {}
        this.parser = p;
    }

    public static XmlResourceParser open(java.io.File file, ArscTable arsc) throws java.io.IOException {
        byte[] data = java.nio.file.Files.readAllBytes(file.toPath());
        return new XmlResourceParser(data, arsc);
    }

    private static byte[] readAll(java.io.InputStream in) {
        try {
            return in.readAllBytes();
        } catch (IOException e) {
            return new byte[0];
        } finally {
            try { in.close(); } catch (IOException ignored) {}
        }
    }

    public void close() {}

    @Override public void setFeature(String name, boolean state) throws XmlPullParserException { parser.setFeature(name, state); }
    @Override public boolean getFeature(String name) { return parser.getFeature(name); }
    @Override public void setProperty(String name, Object value) throws XmlPullParserException { parser.setProperty(name, value); }
    @Override public Object getProperty(String name) { return parser.getProperty(name); }
    @Override public void setInput(java.io.Reader in) throws XmlPullParserException { parser.setInput(in); }
    @Override public void setInput(java.io.InputStream inputStream, String inputEncoding) throws XmlPullParserException {
        parser.setInput(inputStream, inputEncoding);
    }
    @Override public String getInputEncoding() { return parser.getInputEncoding(); }
    @Override public void defineEntityReplacementText(String entityName, String replacementText) throws XmlPullParserException {
        parser.defineEntityReplacementText(entityName, replacementText);
    }
    @Override public int getNamespaceCount(int depth) throws XmlPullParserException { return parser.getNamespaceCount(depth); }
    @Override public String getNamespacePrefix(int pos) throws XmlPullParserException { return parser.getNamespacePrefix(pos); }
    @Override public String getNamespaceUri(int pos) throws XmlPullParserException { return parser.getNamespaceUri(pos); }
    @Override public String getNamespace(String prefix) { return parser.getNamespace(prefix); }
    @Override public int getDepth() { return parser.getDepth(); }
    @Override public String getPositionDescription() { return parser.getPositionDescription(); }
    @Override public int getLineNumber() { return parser.getLineNumber(); }
    @Override public int getColumnNumber() { return parser.getColumnNumber(); }
    @Override public boolean isWhitespace() throws XmlPullParserException { return parser.isWhitespace(); }
    @Override public String getText() { return parser.getText(); }
    @Override public char[] getTextCharacters(int[] holderForStartAndLength) { return parser.getTextCharacters(holderForStartAndLength); }
    @Override public String getNamespace() { return parser.getNamespace(); }
    @Override public String getName() { return parser.getName(); }
    @Override public String getPrefix() { return parser.getPrefix(); }
    @Override public boolean isEmptyElementTag() throws XmlPullParserException { return parser.isEmptyElementTag(); }
    @Override public int getAttributeCount() { return parser.getAttributeCount(); }
    @Override public String getAttributeNamespace(int index) { return parser.getAttributeNamespace(index); }
    @Override public String getAttributeName(int index) { return parser.getAttributeName(index); }
    @Override public String getAttributePrefix(int index) { return parser.getAttributePrefix(index); }
    @Override public String getAttributeType(int index) { return parser.getAttributeType(index); }
    @Override public boolean isAttributeDefault(int index) { return parser.isAttributeDefault(index); }
    @Override public String getAttributeValue(int index) { return parser.getAttributeValue(index); }
    @Override public String getAttributeValue(String namespace, String name) { return parser.getAttributeValue(namespace, name); }
    @Override public int getEventType() throws XmlPullParserException { return parser.getEventType(); }
    @Override public int next() throws XmlPullParserException, IOException { return parser.next(); }
    @Override public int nextToken() throws XmlPullParserException, IOException { return parser.nextToken(); }
    @Override public void require(int type, String namespace, String name) throws XmlPullParserException, IOException {
        parser.require(type, namespace, name);
    }
    @Override public String nextText() throws XmlPullParserException, IOException { return parser.nextText(); }
    @Override public int nextTag() throws XmlPullParserException, IOException { return parser.nextTag(); }

    @Override public int getAttributeResourceValue(String namespace, String attribute, int defaultValue) {
        String v = getAttributeValue(namespace, attribute);
        if (v == null) return defaultValue;
        if (v.startsWith("@")) {
            int slash = v.lastIndexOf('/');
            String name = slash >= 0 ? v.substring(slash + 1) : v.substring(1);
            return Math.abs(name.hashCode());
        }
        try {
            if (v.startsWith("0x") || v.startsWith("0X")) return Integer.parseInt(v.substring(2), 16);
            return Integer.parseInt(v);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }
    @Override public int getAttributeIntValue(String namespace, String attribute, int defaultValue) {
        String v = getAttributeValue(namespace, attribute);
        if (v == null) return defaultValue;
        try { return Integer.parseInt(v); } catch (NumberFormatException e) { return defaultValue; }
    }
    @Override public boolean getAttributeBooleanValue(String namespace, String attribute, boolean defaultValue) {
        String v = getAttributeValue(namespace, attribute);
        if (v == null) return defaultValue;
        return Boolean.parseBoolean(v) || "true".equalsIgnoreCase(v) || "1".equals(v);
    }
    @Override public String getClassAttribute() { return getAttributeValue(null, "class"); }
    @Override public String getIdAttribute() { return getAttributeValue(null, "id"); }
    @Override public int getIdAttributeResourceValue(int defaultValue) { return defaultValue; }
    @Override public int getStyleAttribute() { return 0; }
}
