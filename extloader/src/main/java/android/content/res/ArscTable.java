package android.content.res;

import java.io.File;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * Minimal resources.arsc reader so plugin getIdentifier / getString / layout IDs
 * match the compiled resource IDs referenced from binary AXML.
 */
public final class ArscTable {
    private static final int RES_STRING_POOL = 0x0001;
    private static final int RES_TABLE_PACKAGE = 0x0200;
    private static final int RES_TABLE_TYPE = 0x0201;
    private static final int UTF8_FLAG = 1 << 8;
    private static final int TYPE_STRING = 0x03;

    private final Map<String, Integer> byName = new HashMap<>();
    private final Map<Integer, String> names = new HashMap<>();
    private final Map<Integer, String> strings = new HashMap<>();
    private final Map<Integer, File> files = new HashMap<>();

    public static ArscTable load(File arsc, File resRoot) {
        ArscTable table = new ArscTable();
        if (arsc == null || !arsc.isFile()) return table;
        try {
            byte[] data = java.nio.file.Files.readAllBytes(arsc.toPath());
            table.parse(ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN), resRoot);
        } catch (Throwable ignored) {}
        return table;
    }

    public int getIdentifier(String name, String type, String pkg) {
        if (name == null || name.isEmpty()) return 0;
        String base = name;
        String t = type == null ? "" : type;
        int slash = name.indexOf('/');
        if (slash >= 0) {
            t = name.substring(0, slash);
            base = name.substring(slash + 1);
        }
        Integer id = byName.get(t + "/" + base);
        return id == null ? 0 : id;
    }

    public String nameOf(int id) {
        return names.get(id);
    }

    public String stringOf(int id) {
        return strings.get(id);
    }

    public File fileOf(int id) {
        return files.get(id);
    }

    public Map<Integer, File> files() {
        return files;
    }

    private void parse(ByteBuffer buf, File resRoot) {
        int tableType = buf.getShort() & 0xffff;
        int tableHeader = buf.getShort() & 0xffff;
        int tableSize = buf.getInt();
        int packageCount = buf.getInt();
        if (tableType != 0x0002) return;
        buf.position(tableHeader);
        String[] global = null;
        while (buf.position() + 8 <= tableSize && buf.position() + 8 <= buf.limit()) {
            int pos = buf.position();
            int type = buf.getShort() & 0xffff;
            int headerSize = buf.getShort() & 0xffff;
            int size = buf.getInt();
            if (size < 8) break;
            if (type == RES_STRING_POOL) {
                global = readStringPool(buf, pos);
            } else if (type == RES_TABLE_PACKAGE) {
                parsePackage(buf, pos, headerSize, size, global, resRoot);
            }
            buf.position(pos + size);
        }
        if (packageCount < 0) { /* keep compiler quiet */ }
    }

    private void parsePackage(ByteBuffer buf, int pkgPos, int headerSize, int pkgSize, String[] global, File resRoot) {
        buf.position(pkgPos + 8);
        int pkgId = buf.getInt();
        char[] nameChars = new char[128];
        for (int i = 0; i < 128; i++) nameChars[i] = buf.getChar();
        int typeStrings = buf.getInt();
        buf.getInt(); // lastPublicType
        int keyStrings = buf.getInt();
        String[] types = readStringPool(buf, pkgPos + typeStrings);
        String[] keys = readStringPool(buf, pkgPos + keyStrings);
        int inner = pkgPos + headerSize;
        int end = pkgPos + pkgSize;
        while (inner + 8 <= end) {
            buf.position(inner);
            int type = buf.getShort() & 0xffff;
            int hs = buf.getShort() & 0xffff;
            int size = buf.getInt();
            if (size < 8) break;
            if (type == RES_TABLE_TYPE) {
                parseType(buf, inner, hs, pkgId, types, keys, global, resRoot);
            }
            inner += size;
        }
    }

    private void parseType(ByteBuffer buf, int pos, int headerSize, int pkgId, String[] types, String[] keys, String[] global, File resRoot) {
        buf.position(pos + 8);
        int typeId = buf.get() & 0xff;
        buf.get();
        buf.getShort();
        int entryCount = buf.getInt();
        int entriesStart = buf.getInt();
        String typeName = (typeId > 0 && typeId <= types.length) ? types[typeId - 1] : "";
        int offsetsAt = pos + headerSize;
        for (int i = 0; i < entryCount; i++) {
            buf.position(offsetsAt + i * 4);
            int off = buf.getInt();
            if (off == -1) continue;
            int entry = pos + entriesStart + off;
            if (entry + 8 > buf.limit()) continue;
            buf.position(entry);
            buf.getShort();
            int flags = buf.getShort() & 0xffff;
            int key = buf.getInt();
            String keyName = (key >= 0 && key < keys.length) ? keys[key] : "";
            int resid = (pkgId << 24) | (typeId << 16) | i;
            String full = typeName + "/" + keyName;
            byName.put(full, resid);
            names.put(resid, full);
            if ((flags & 1) != 0) continue;
            if (entry + 16 > buf.limit()) continue;
            buf.position(entry + 8);
            buf.getShort();
            buf.get();
            int dataType = buf.get() & 0xff;
            int data = buf.getInt();
            if (dataType == TYPE_STRING && global != null && data >= 0 && data < global.length) {
                String value = global[data];
                strings.put(resid, value);
                    if (resRoot != null && value.startsWith("res/")) {
                    File unpack = resRoot.getName().equals("res") && resRoot.getParentFile() != null
                        ? resRoot.getParentFile() : resRoot;
                    File f = new File(unpack, value);
                    if (!f.isFile()) f = new File(resRoot, value.substring(4));
                    if (f.isFile()) files.put(resid, f);
                }
            }
        }
    }

    static String[] readStringPool(ByteBuffer buf, int pos) {
        buf.position(pos);
        int type = buf.getShort() & 0xffff;
        int headerSize = buf.getShort() & 0xffff;
        buf.getInt();
        if (type != RES_STRING_POOL) return new String[0];
        int stringCount = buf.getInt();
        buf.getInt();
        int flags = buf.getInt();
        int stringsStart = buf.getInt();
        buf.getInt();
        int[] offsets = new int[stringCount];
        buf.position(pos + headerSize);
        for (int i = 0; i < stringCount; i++) offsets[i] = buf.getInt();
        boolean utf8 = (flags & UTF8_FLAG) != 0;
        String[] out = new String[stringCount];
        int base = pos + stringsStart;
        for (int i = 0; i < stringCount; i++) {
            int p = base + offsets[i];
            out[i] = utf8 ? readUtf8(buf, p) : readUtf16(buf, p);
        }
        return out;
    }

    private static String readUtf8(ByteBuffer buf, int p) {
        int[] o = new int[] { p };
        readUtf8Length(buf, o);
        int byteLen = readUtf8Length(buf, o);
        byte[] bytes = new byte[byteLen];
        buf.position(o[0]);
        buf.get(bytes);
        return new String(bytes, StandardCharsets.UTF_8);
    }

    private static int readUtf8Length(ByteBuffer buf, int[] offset) {
        int p = offset[0];
        int b0 = buf.get(p) & 0xff;
        if ((b0 & 0x80) != 0) {
            int b1 = buf.get(p + 1) & 0xff;
            offset[0] = p + 2;
            return ((b0 & 0x7f) << 8) | b1;
        }
        offset[0] = p + 1;
        return b0;
    }

    private static String readUtf16(ByteBuffer buf, int p) {
        buf.position(p);
        int len = buf.getShort() & 0xffff;
        if ((len & 0x8000) != 0) {
            len = ((len & 0x7fff) << 16) | (buf.getShort() & 0xffff);
        }
        char[] chars = new char[len];
        for (int i = 0; i < len; i++) chars[i] = buf.getChar();
        return new String(chars);
    }
}
