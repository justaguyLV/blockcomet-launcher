package git.artdeell.mojo.friends;

/** BlockComet friends: tiny hex helper */
public final class Hex {
    private Hex() {}

    private static final char[] DIGITS = "0123456789abcdef".toCharArray();

    public static String encode(byte[] data) {
        char[] out = new char[data.length * 2];
        for (int i = 0; i < data.length; i++) {
            out[i * 2] = DIGITS[(data[i] >> 4) & 15];
            out[i * 2 + 1] = DIGITS[data[i] & 15];
        }
        return new String(out);
    }

    /** Returns null when the text is not valid hex */
    public static byte[] decode(String text) {
        if (text == null || text.length() % 2 != 0) return null;
        byte[] out = new byte[text.length() / 2];
        for (int i = 0; i < out.length; i++) {
            int hi = Character.digit(text.charAt(i * 2), 16), lo = Character.digit(text.charAt(i * 2 + 1), 16);
            if (hi < 0 || lo < 0) return null;
            out[i] = (byte) ((hi << 4) | lo);
        }
        return out;
    }
}
