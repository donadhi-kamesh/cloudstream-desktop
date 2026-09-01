package android.content.res;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.Deque;

/** Converts Android binary XML (AXML) to UTF-8 text XML plugins can inflate. */
public final class AxmlDecoder {
    private static final int RES_XML = 0x0003;
    private static final int RES_STRING_POOL = 0x0001;
    private static final int RES_XML_RESOURCE_MAP = 0x0180;
    private static final int START_NAMESPACE = 0x0100;
    private static final int END_NAMESPACE = 0x0101;
    private static final int START_ELEMENT = 0x0102;
    private static final int END_ELEMENT = 0x0103;
    private static final int TYPE_NULL = 0x00;
    private static final int TYPE_REFERENCE = 0x01;
    private static final int TYPE_ATTRIBUTE = 0x02;
    private static final int TYPE_STRING = 0x03;
    private static final int TYPE_FLOAT = 0x04;
    private static final int TYPE_DIMENSION = 0x05;
    private static final int TYPE_FRACTION = 0x06;
    private static final int TYPE_INT_DEC = 0x10;
    private static final int TYPE_INT_HEX = 0x11;
    private static final int TYPE_INT_BOOLEAN = 0x12;

    public static boolean isBinary(byte[] data) {
        return data != null && data.length >= 8
            && (data[0] & 0xff) == 0x03 && (data[1] & 0xff) == 0x00
            && (data[2] & 0xff) == 0x08 && (data[3] & 0xff) == 0x00;
    }

    public static String decode(byte[] data, ArscTable arsc) {
        if (data == null || data.length < 8) return "";
        ByteBuffer buf = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN);
        int type = buf.getShort() & 0xffff;
        int headerSize = buf.getShort() & 0xffff;
        int fileSize = buf.getInt();
        if (type != RES_XML) return new String(data, StandardCharsets.UTF_8);
        String[] strings = new String[0];
        StringBuilder xml = new StringBuilder(data.length * 2);
        xml.append("<?xml version=\"1.0\" encoding=\"utf-8\"?>\n");
        Deque<String> nsUri = new ArrayDeque<>();
        Deque<String> nsPrefix = new ArrayDeque<>();
        boolean firstTag = true;
        buf.position(headerSize);
        while (buf.position() + 8 <= Math.min(fileSize, buf.limit())) {
            int pos = buf.position();
            int chunkType = buf.getShort() & 0xffff;
            int hs = buf.getShort() & 0xffff;
            int size = buf.getInt();
            if (size < 8) break;
            if (chunkType == RES_STRING_POOL) {
                strings = ArscTable.readStringPool(buf, pos);
            } else if (chunkType == START_NAMESPACE) {
                buf.position(pos + 16);
                int prefix = buf.getInt();
                int uri = buf.getInt();
                nsPrefix.push(str(strings, prefix));
                nsUri.push(str(strings, uri));
            } else if (chunkType == END_NAMESPACE) {
                if (!nsPrefix.isEmpty()) nsPrefix.pop();
                if (!nsUri.isEmpty()) nsUri.pop();
            } else if (chunkType == START_ELEMENT) {
                buf.position(pos + 16);
                int ns = buf.getInt();
                int name = buf.getInt();
                buf.getShort();
                buf.getShort();
                int attrCount = buf.getShort() & 0xffff;
                buf.getShort();
                buf.getShort();
                buf.getShort();
                String tag = str(strings, name);
                xml.append('<').append(tag);
                if (firstTag) {
                    java.util.Iterator<String> pi = nsPrefix.descendingIterator();
                    java.util.Iterator<String> ui = nsUri.descendingIterator();
                    while (pi.hasNext() && ui.hasNext()) {
                        String p = pi.next();
                        String u = ui.next();
                        if (p == null || p.isEmpty()) xml.append(" xmlns=\"").append(esc(u)).append('"');
                        else xml.append(" xmlns:").append(p).append("=\"").append(esc(u)).append('"');
                    }
                    firstTag = false;
                }
                for (int i = 0; i < attrCount; i++) {
                    int ans = buf.getInt();
                    int aname = buf.getInt();
                    int raw = buf.getInt();
                    buf.getShort();
                    buf.get();
                    int dataType = buf.get() & 0xff;
                    int dataVal = buf.getInt();
                    String attrName = str(strings, aname);
                    String attrNs = str(strings, ans);
                    String value = raw != -1 ? str(strings, raw) : typed(dataType, dataVal, strings, arsc, attrName);
                    String prefix = prefixOf(attrNs, nsPrefix, nsUri);
                    xml.append(' ');
                    if (prefix != null && !prefix.isEmpty()) xml.append(prefix).append(':');
                    xml.append(attrName).append("=\"").append(esc(value)).append('"');
                }
                xml.append(">\n");
            } else if (chunkType == END_ELEMENT) {
                buf.position(pos + 16);
                buf.getInt();
                int name = buf.getInt();
                xml.append("</").append(str(strings, name)).append(">\n");
            }
            buf.position(pos + size);
        }
        return xml.toString();
    }

    private static String typed(int dataType, int data, String[] strings, ArscTable arsc, String attrName) {
        switch (dataType) {
            case TYPE_NULL:
                return "";
            case TYPE_STRING:
                return str(strings, data);
            case TYPE_INT_BOOLEAN:
                return data != 0 ? "true" : "false";
            case TYPE_INT_HEX:
                return "0x" + Integer.toHexString(data);
            case TYPE_FLOAT:
                return Float.toString(Float.intBitsToFloat(data));
            case TYPE_REFERENCE:
            case TYPE_ATTRIBUTE:
                return ref(data, arsc, attrName);
            case TYPE_INT_DEC:
                if ("layout_width".equals(attrName) || "layout_height".equals(attrName)) {
                    if (data == -1) return "match_parent";
                    if (data == -2) return "wrap_content";
                }
                return Integer.toString(data);
            case TYPE_DIMENSION:
                return dimension(data);
            case TYPE_FRACTION:
                return (data >> 8) + "%";
            default:
                return Integer.toString(data);
        }
    }

    private static String ref(int id, ArscTable arsc, String attrName) {
        if (id == 0) return "@null";
        String named = arsc != null ? arsc.nameOf(id) : null;
        if (named != null) {
            int slash = named.indexOf('/');
            String type = slash >= 0 ? named.substring(0, slash) : "id";
            String name = slash >= 0 ? named.substring(slash + 1) : named;
            if ("id".equals(attrName) || "id".equals(type)) return "@+id/" + name;
            return "@" + type + "/" + name;
        }
        return "@0x" + Integer.toHexString(id);
    }

    private static String dimension(int data) {
        int unit = data & 0xf;
        int radix = (data >> 4) & 0x3;
        int mantissa = data >> 8;
        float value;
        switch (radix) {
            case 0: value = mantissa; break;
            case 1: value = mantissa * (1f / 128f); break;
            case 2: value = mantissa * (1f / 32768f); break;
            default: value = mantissa * (1f / (1 << 23)); break;
        }
        String[] units = { "px", "dp", "sp", "pt", "in", "mm" };
        String u = unit < units.length ? units[unit] : "px";
        if (value == (int) value) return ((int) value) + u;
        return value + u;
    }

    private static String prefixOf(String uri, Deque<String> prefixes, Deque<String> uris) {
        if (uri == null || uri.isEmpty()) return null;
        String[] p = prefixes.toArray(new String[0]);
        String[] u = uris.toArray(new String[0]);
        for (int i = 0; i < u.length && i < p.length; i++) {
            if (uri.equals(u[i])) return p[i];
        }
        if (uri.contains("apk/res/android")) return "android";
        return null;
    }

    private static String str(String[] strings, int index) {
        if (index < 0 || strings == null || index >= strings.length) return "";
        return strings[index] == null ? "" : strings[index];
    }

    private static String esc(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("\"", "&quot;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private AxmlDecoder() {}
}
