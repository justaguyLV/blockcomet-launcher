package git.artdeell.mojo.friends;

import java.util.Locale;

/** BlockComet friends: bech32 (BIP-173), used for npub... / nsec... codes. */
public final class Bech32 {
    private Bech32() {}

    private static final String CHARSET = "qpzry9x8gf2tvdw0s3jn54khce6mua7l";
    private static final int[] GEN = {0x3b6a57b2, 0x26508e6d, 0x1ea119fa, 0x3d4233dd, 0x2a1462b3};

    private static int polymod(int[] values) {
        int chk = 1;
        for (int v : values) {
            int top = chk >>> 25;
            chk = ((chk & 0x1ffffff) << 5) ^ v;
            for (int i = 0; i < 5; i++) if (((top >>> i) & 1) != 0) chk ^= GEN[i];
        }
        return chk;
    }

    private static int[] hrpExpand(String hrp) {
        int[] out = new int[hrp.length() * 2 + 1];
        for (int i = 0; i < hrp.length(); i++) {
            out[i] = hrp.charAt(i) >>> 5;
            out[i + hrp.length() + 1] = hrp.charAt(i) & 31;
        }
        return out;
    }

    private static int[] convertBits(int[] data, int from, int to, boolean pad) {
        int acc = 0, bits = 0, maxv = (1 << to) - 1;
        java.util.ArrayList<Integer> out = new java.util.ArrayList<>();
        for (int value : data) {
            if (value < 0 || (value >>> from) != 0) return null;
            acc = (acc << from) | value;
            bits += from;
            while (bits >= to) {
                bits -= to;
                out.add((acc >>> bits) & maxv);
            }
            acc &= (1 << bits) - 1;
        }
        if (pad) {
            if (bits > 0) out.add((acc << (to - bits)) & maxv);
        } else if (bits >= from || ((acc << (to - bits)) & maxv) != 0) {
            return null;
        }
        int[] result = new int[out.size()];
        for (int i = 0; i < result.length; i++) result[i] = out.get(i);
        return result;
    }

    public static String encode(String hrp, byte[] data) {
        int[] ints = new int[data.length];
        for (int i = 0; i < data.length; i++) ints[i] = data[i] & 0xff;
        int[] fiveBit = convertBits(ints, 8, 5, true);
        int[] hrpPart = hrpExpand(hrp);
        int[] values = new int[hrpPart.length + fiveBit.length + 6];
        System.arraycopy(hrpPart, 0, values, 0, hrpPart.length);
        System.arraycopy(fiveBit, 0, values, hrpPart.length, fiveBit.length);
        int mod = polymod(values) ^ 1;
        StringBuilder sb = new StringBuilder(hrp).append('1');
        for (int v : fiveBit) sb.append(CHARSET.charAt(v));
        for (int i = 0; i < 6; i++) sb.append(CHARSET.charAt((mod >>> (5 * (5 - i))) & 31));
        return sb.toString();
    }

    /** Returns {hrp, data} or null when the text is not valid bech32 */
    public static Object[] decode(String text) {
        text = text.trim();
        if (!text.equals(text.toLowerCase(Locale.ROOT)) && !text.equals(text.toUpperCase(Locale.ROOT))) return null;
        text = text.toLowerCase(Locale.ROOT);
        int sep = text.lastIndexOf('1');
        if (sep < 1 || sep + 7 > text.length()) return null;
        String hrp = text.substring(0, sep);
        int[] values = new int[text.length() - sep - 1];
        for (int i = 0; i < values.length; i++) {
            int idx = CHARSET.indexOf(text.charAt(sep + 1 + i));
            if (idx < 0) return null;
            values[i] = idx;
        }
        int[] hrpPart = hrpExpand(hrp);
        int[] all = new int[hrpPart.length + values.length];
        System.arraycopy(hrpPart, 0, all, 0, hrpPart.length);
        System.arraycopy(values, 0, all, hrpPart.length, values.length);
        if (polymod(all) != 1) return null;
        int[] payload = java.util.Arrays.copyOf(values, values.length - 6);
        int[] bytes = convertBits(payload, 5, 8, false);
        if (bytes == null) return null;
        byte[] out = new byte[bytes.length];
        for (int i = 0; i < out.length; i++) out[i] = (byte) bytes[i];
        return new Object[]{hrp, out};
    }
}
