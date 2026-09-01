package android.util;

import java.nio.charset.StandardCharsets;

public final class Base64 {
    public static final int DEFAULT = 0;
    public static final int NO_PADDING = 1;
    public static final int NO_WRAP = 2;
    public static final int CRLF = 4;
    public static final int URL_SAFE = 8;
    public static final int NO_CLOSE = 16;

    private Base64() {}

    public static byte[] decode(String str, int flags) {
        if (str == null || str.isEmpty()) return new byte[0];
        try {
            if ((flags & URL_SAFE) != 0) {
                return java.util.Base64.getUrlDecoder().decode(str);
            }
            return java.util.Base64.getDecoder().decode(str);
        } catch (IllegalArgumentException e) {
            try {
                return java.util.Base64.getMimeDecoder().decode(str);
            } catch (IllegalArgumentException e2) {
                return new byte[0];
            }
        }
    }

    public static byte[] decode(byte[] input, int flags) {
        if (input == null) return new byte[0];
        return decode(new String(input, StandardCharsets.ISO_8859_1), flags);
    }

    public static String encodeToString(byte[] input, int flags) {
        if (input == null) return "";
        java.util.Base64.Encoder enc = (flags & URL_SAFE) != 0
            ? java.util.Base64.getUrlEncoder()
            : java.util.Base64.getEncoder();
        if ((flags & NO_PADDING) != 0) enc = enc.withoutPadding();
        String out = enc.encodeToString(input);
        if ((flags & NO_WRAP) != 0) out = out.replace("\n", "").replace("\r", "");
        return out;
    }

    public static byte[] encode(byte[] input, int flags) {
        return encodeToString(input, flags).getBytes(StandardCharsets.US_ASCII);
    }
}
