package org.xmlpull.v1;

import java.io.InputStream;
import java.io.Reader;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamReader;

public class XmlPullParserFactory {
    public static final String PROPERTY_NAME = "org.xmlpull.v1.XmlPullParserFactory";
    protected boolean processNamespaces = true;

    public static XmlPullParserFactory newInstance() { return new XmlPullParserFactory(); }
    public static XmlPullParserFactory newInstance(String unused, Class unusedClass) { return new XmlPullParserFactory(); }

    public XmlPullParser newPullParser() {
        return new StaxPullParser();
    }

    public void setNamespaceAware(boolean awareness) { processNamespaces = awareness; }
    public boolean isNamespaceAware() { return processNamespaces; }
    public void setValidating(boolean validating) {}
    public boolean isValidating() { return false; }
    public void setFeature(String name, boolean state) {}
    public boolean getFeature(String name) { return false; }

    static final class StaxPullParser implements XmlPullParser {
        private XMLStreamReader reader;
        private int event = START_DOCUMENT;
        private String encoding = "UTF-8";
        private int depth = 0;

        @Override public void setFeature(String name, boolean state) {}
        @Override public boolean getFeature(String name) { return FEATURE_PROCESS_NAMESPACES.equals(name); }
        @Override public void setProperty(String name, Object value) {}
        @Override public Object getProperty(String name) { return null; }

        @Override public void setInput(Reader in) throws XmlPullParserException {
            try {
                XMLInputFactory factory = XMLInputFactory.newInstance();
                factory.setProperty(XMLInputFactory.IS_NAMESPACE_AWARE, true);
                factory.setProperty(XMLInputFactory.SUPPORT_DTD, false);
                factory.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false);
                reader = factory.createXMLStreamReader(in);
                event = START_DOCUMENT;
                depth = 0;
            } catch (Exception e) {
                throw new XmlPullParserException(e.getMessage());
            }
        }

        @Override public void setInput(InputStream inputStream, String inputEncoding) throws XmlPullParserException {
            encoding = inputEncoding != null ? inputEncoding : "UTF-8";
            try {
                XMLInputFactory factory = XMLInputFactory.newInstance();
                factory.setProperty(XMLInputFactory.IS_NAMESPACE_AWARE, true);
                factory.setProperty(XMLInputFactory.SUPPORT_DTD, false);
                factory.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false);
                reader = factory.createXMLStreamReader(inputStream, encoding);
                event = START_DOCUMENT;
                depth = 0;
            } catch (Exception e) {
                throw new XmlPullParserException(e.getMessage());
            }
        }

        @Override public String getInputEncoding() { return encoding; }
        @Override public void defineEntityReplacementText(String entityName, String replacementText) {}
        @Override public int getNamespaceCount(int depth) { return 0; }
        @Override public String getNamespacePrefix(int pos) { return null; }
        @Override public String getNamespaceUri(int pos) { return null; }
        @Override public String getNamespace(String prefix) {
            if (reader == null) return NO_NAMESPACE;
            String uri = reader.getNamespaceURI(prefix);
            return uri != null ? uri : NO_NAMESPACE;
        }
        @Override public int getDepth() { return depth; }
        @Override public String getPositionDescription() {
            return reader == null ? "stax" : ("line " + reader.getLocation().getLineNumber());
        }
        @Override public int getLineNumber() { return reader == null ? 1 : reader.getLocation().getLineNumber(); }
        @Override public int getColumnNumber() { return reader == null ? 1 : reader.getLocation().getColumnNumber(); }
        @Override public boolean isWhitespace() {
            return event == TEXT && reader != null && reader.isWhiteSpace();
        }
        @Override public String getText() { return reader == null ? "" : reader.getText(); }
        @Override public char[] getTextCharacters(int[] holderForStartAndLength) {
            String t = getText();
            if (holderForStartAndLength != null && holderForStartAndLength.length >= 2) {
                holderForStartAndLength[0] = 0;
                holderForStartAndLength[1] = t.length();
            }
            return t.toCharArray();
        }
        @Override public String getNamespace() {
            if (reader == null) return NO_NAMESPACE;
            String ns = reader.getNamespaceURI();
            return ns != null ? ns : NO_NAMESPACE;
        }
        @Override public String getName() { return reader == null ? null : reader.getLocalName(); }
        @Override public String getPrefix() { return reader == null ? null : reader.getPrefix(); }
        @Override public boolean isEmptyElementTag() {
            return false;
        }
        @Override public int getAttributeCount() { return reader == null || event != START_TAG ? -1 : reader.getAttributeCount(); }
        @Override public String getAttributeNamespace(int index) {
            if (reader == null) return NO_NAMESPACE;
            String ns = reader.getAttributeNamespace(index);
            return ns != null ? ns : NO_NAMESPACE;
        }
        @Override public String getAttributeName(int index) { return reader == null ? "" : reader.getAttributeLocalName(index); }
        @Override public String getAttributePrefix(int index) { return reader == null ? null : reader.getAttributePrefix(index); }
        @Override public String getAttributeType(int index) { return "CDATA"; }
        @Override public boolean isAttributeDefault(int index) { return false; }
        @Override public String getAttributeValue(int index) { return reader == null ? "" : reader.getAttributeValue(index); }
        @Override public String getAttributeValue(String namespace, String name) {
            if (reader == null || name == null) return null;
            if (namespace == null || namespace.isEmpty()) {
                int n = reader.getAttributeCount();
                for (int i = 0; i < n; i++) {
                    if (name.equals(reader.getAttributeLocalName(i))) return reader.getAttributeValue(i);
                }
                return null;
            }
            return reader.getAttributeValue(namespace, name);
        }
        @Override public int getEventType() { return event; }

        @Override public int next() throws XmlPullParserException, java.io.IOException {
            if (reader == null) {
                event = END_DOCUMENT;
                return event;
            }
            try {
                while (reader.hasNext()) {
                    int stax = reader.next();
                    int mapped = map(stax);
                    if (mapped >= 0) {
                        if (mapped == START_TAG) depth++;
                        else if (mapped == END_TAG && depth > 0) depth--;
                        event = mapped;
                        return event;
                    }
                }
            } catch (Exception e) {
                throw new XmlPullParserException(e.getMessage());
            }
            event = END_DOCUMENT;
            return event;
        }

        @Override public int nextToken() throws XmlPullParserException, java.io.IOException { return next(); }

        @Override public void require(int type, String namespace, String name) throws XmlPullParserException {
            if (event != type) throw new XmlPullParserException("expected " + type);
        }

        @Override public String nextText() throws XmlPullParserException, java.io.IOException {
            if (event != START_TAG) throw new XmlPullParserException("nextText from start tag");
            int n = next();
            if (n == TEXT) {
                String t = getText();
                next();
                return t;
            }
            return "";
        }

        @Override public int nextTag() throws XmlPullParserException, java.io.IOException {
            int n = next();
            if (n == TEXT && isWhitespace()) n = next();
            return n;
        }

        private static int map(int stax) {
            switch (stax) {
                case XMLStreamConstants.START_ELEMENT: return START_TAG;
                case XMLStreamConstants.END_ELEMENT: return END_TAG;
                case XMLStreamConstants.CHARACTERS:
                case XMLStreamConstants.CDATA: return TEXT;
                case XMLStreamConstants.END_DOCUMENT: return END_DOCUMENT;
                case XMLStreamConstants.START_DOCUMENT: return START_DOCUMENT;
                default: return -1;
            }
        }
    }
}
