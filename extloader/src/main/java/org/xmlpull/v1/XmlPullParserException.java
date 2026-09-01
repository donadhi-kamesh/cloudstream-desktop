package org.xmlpull.v1;

public class XmlPullParserException extends Exception {
    protected int row = -1;
    protected int column = -1;

    public XmlPullParserException(String s) { super(s); }
    public XmlPullParserException(String msg, XmlPullParser parser, Throwable chain) {
        super(msg);
        if (chain != null) initCause(chain);
    }
    public int getLineNumber() { return row; }
    public int getColumnNumber() { return column; }
}
