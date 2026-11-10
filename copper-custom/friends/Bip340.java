package git.artdeell.mojo.friends;

import java.math.BigInteger;
import java.security.MessageDigest;
import java.security.SecureRandom;

/**
 * BlockComet friends: BIP-340 Schnorr signatures on secp256k1 (what Nostr uses), in plain Java.
 * Slow compared to native code (tens of milliseconds) but it needs no library, which is fine for chat-sized work.
 */
public final class Bip340 {
    private Bip340() {}

    private static final BigInteger P = new BigInteger("FFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFEFFFFFC2F", 16);
    private static final BigInteger N = new BigInteger("FFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFEBAAEDCE6AF48A03BBFD25E8CD0364141", 16);
    private static final BigInteger[] G = {
            new BigInteger("79BE667EF9DCBBAC55A06295CE870B07029BFCDB2DCE28D959F2815B16F81798", 16),
            new BigInteger("483ADA7726A3C4655DA4FBFC0E1108A8FD17B448A68554199C47D08FFB10D4B8", 16)};
    private static final BigInteger TWO = BigInteger.valueOf(2), THREE = BigInteger.valueOf(3);
    private static final SecureRandom RANDOM = new SecureRandom();

    // ---- points: affine {x, y}, null is the point at infinity
    private static BigInteger[] add(BigInteger[] a, BigInteger[] b) {
        if (a == null) return b;
        if (b == null) return a;
        BigInteger lam;
        if (a[0].equals(b[0])) {
            if (!a[1].equals(b[1]) || a[1].signum() == 0) return null;
            lam = a[0].pow(2).multiply(THREE).multiply(a[1].multiply(TWO).modInverse(P)).mod(P);
        } else {
            lam = b[1].subtract(a[1]).multiply(b[0].subtract(a[0]).mod(P).modInverse(P)).mod(P);
        }
        BigInteger x = lam.pow(2).subtract(a[0]).subtract(b[0]).mod(P);
        BigInteger y = lam.multiply(a[0].subtract(x)).subtract(a[1]).mod(P);
        return new BigInteger[]{x, y};
    }

    private static BigInteger[] mul(BigInteger[] point, BigInteger k) {
        BigInteger[] result = null;
        BigInteger[] addend = point;
        for (int i = 0; i < k.bitLength(); i++) {
            if (k.testBit(i)) result = add(result, addend);
            addend = add(addend, addend);
        }
        return result;
    }

    private static boolean hasEvenY(BigInteger[] point) {
        return !point[1].testBit(0);
    }

    private static BigInteger[] liftX(BigInteger x) {
        if (x.compareTo(P) >= 0) return null;
        BigInteger ySq = x.pow(3).add(BigInteger.valueOf(7)).mod(P);
        BigInteger y = ySq.modPow(P.add(BigInteger.ONE).shiftRight(2), P);
        if (!y.pow(2).mod(P).equals(ySq)) return null;
        return new BigInteger[]{x, y.testBit(0) ? P.subtract(y) : y};
    }

    // ---- helpers
    private static byte[] sha256(byte[]... parts) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            for (byte[] part : parts) md.update(part);
            return md.digest();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    public static byte[] sha256(byte[] data) {
        return sha256(new byte[][]{data});
    }

    private static byte[] taggedHash(String tag, byte[]... parts) {
        byte[] tagHash = sha256(tag.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        byte[][] all = new byte[parts.length + 2][];
        all[0] = tagHash;
        all[1] = tagHash;
        System.arraycopy(parts, 0, all, 2, parts.length);
        return sha256(all);
    }

    private static byte[] bytes32(BigInteger value) {
        byte[] raw = value.toByteArray();
        byte[] out = new byte[32];
        if (raw.length > 32) System.arraycopy(raw, raw.length - 32, out, 0, 32);
        else System.arraycopy(raw, 0, out, 32 - raw.length, raw.length);
        return out;
    }

    private static BigInteger toInt(byte[] data) {
        return new BigInteger(1, data);
    }

    // ---- public API
    public static byte[] newSecretKey() {
        while (true) {
            byte[] key = new byte[32];
            RANDOM.nextBytes(key);
            BigInteger d = toInt(key);
            if (d.signum() > 0 && d.compareTo(N) < 0) return key;
        }
    }

    /** The 32-byte x-only public key */
    public static byte[] publicKey(byte[] secretKey) {
        BigInteger d = toInt(secretKey);
        if (d.signum() <= 0 || d.compareTo(N) >= 0) throw new IllegalArgumentException("Invalid secret key");
        return bytes32(mul(G, d)[0]);
    }

    public static byte[] sign(byte[] message32, byte[] secretKey, byte[] aux32) {
        BigInteger d0 = toInt(secretKey);
        if (d0.signum() <= 0 || d0.compareTo(N) >= 0) throw new IllegalArgumentException("Invalid secret key");
        BigInteger[] pub = mul(G, d0);
        BigInteger d = hasEvenY(pub) ? d0 : N.subtract(d0);
        byte[] t = bytes32(d.xor(toInt(taggedHash("BIP0340/aux", aux32))));
        BigInteger k0 = toInt(taggedHash("BIP0340/nonce", t, bytes32(pub[0]), message32)).mod(N);
        if (k0.signum() == 0) throw new IllegalStateException("Bad nonce");
        BigInteger[] r = mul(G, k0);
        BigInteger k = hasEvenY(r) ? k0 : N.subtract(k0);
        BigInteger e = toInt(taggedHash("BIP0340/challenge", bytes32(r[0]), bytes32(pub[0]), message32)).mod(N);
        byte[] sig = new byte[64];
        System.arraycopy(bytes32(r[0]), 0, sig, 0, 32);
        System.arraycopy(bytes32(k.add(e.multiply(d)).mod(N)), 0, sig, 32, 32);
        return sig;
    }

    public static byte[] sign(byte[] message32, byte[] secretKey) {
        byte[] aux = new byte[32];
        RANDOM.nextBytes(aux);
        return sign(message32, secretKey, aux);
    }

    public static boolean verify(byte[] message32, byte[] publicKey32, byte[] signature64) {
        try {
            if (message32.length != 32 || publicKey32.length != 32 || signature64.length != 64) return false;
            BigInteger[] pub = liftX(toInt(publicKey32));
            if (pub == null) return false;
            BigInteger r = toInt(java.util.Arrays.copyOfRange(signature64, 0, 32));
            BigInteger s = toInt(java.util.Arrays.copyOfRange(signature64, 32, 64));
            if (r.compareTo(P) >= 0 || s.compareTo(N) >= 0) return false;
            BigInteger e = toInt(taggedHash("BIP0340/challenge", bytes32(r), publicKey32, message32)).mod(N);
            BigInteger[] negEP = mul(pub, N.subtract(e));
            BigInteger[] point = add(mul(G, s), negEP);
            return point != null && hasEvenY(point) && point[0].equals(r);
        } catch (Exception ex) {
            return false;
        }
    }
}
