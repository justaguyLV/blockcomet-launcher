package git.artdeell.mojo.friends;

import android.content.Context;
import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.gson.JsonObject;

import java.security.KeyStore;
import java.util.ArrayList;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/**
 * BlockComet friends: who the user is. The identity is a key pair made on this device (no account, no server).
 * The secret key is kept encrypted with a key of the Android Keystore; the profile (name + picture) is plain preferences.
 */
public final class NostrIdentity {
    private static final String TAG = "NostrIdentity";
    private static final String PREFS = "blockcomet_friends";
    private static final String KEYSTORE_ALIAS = "blockcomet_friends_key";
    private static NostrIdentity sInstance;

    private final SharedPreferences mPrefs;
    private byte[] mSecret;
    private byte[] mPublic;

    public static synchronized NostrIdentity get(Context context) {
        if (sInstance == null) sInstance = new NostrIdentity(context.getApplicationContext());
        return sInstance;
    }

    private NostrIdentity(Context context) {
        mPrefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        byte[] secret = readSecret();
        if (secret == null) {
            secret = Bip340.newSecretKey();
            writeSecret(secret);
        }
        mSecret = secret;
        mPublic = Bip340.publicKey(secret);
    }

    public byte[] secretKey() {
        return mSecret;
    }

    public String pubHex() {
        return Hex.encode(mPublic);
    }

    public String npub() {
        return Bech32.encode("npub", mPublic);
    }

    public String nsec() {
        return Bech32.encode("nsec", mSecret);
    }

    /** Replaces the identity with the one of a nsec / hex secret key. Returns false when the text is not a valid key */
    public boolean restore(String text) {
        byte[] key = parseKey(text, "nsec");
        if (key == null) return false;
        try {
            byte[] pub = Bip340.publicKey(key);
            writeSecret(key);
            mSecret = key;
            mPublic = pub;
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    /** A 32-byte value written as bech32 with the given prefix, or as 64 hex digits. Null when it is neither */
    @Nullable
    public static byte[] parseKey(String text, String prefix) {
        if (text == null) return null;
        text = text.trim();
        if (text.startsWith("nostr:")) text = text.substring(6);
        Object[] decoded = Bech32.decode(text);
        if (decoded != null) {
            byte[] data = (byte[]) decoded[1];
            return prefix.equals(decoded[0]) && data.length == 32 ? data : null;
        }
        byte[] raw = Hex.decode(text);
        return raw != null && raw.length == 32 ? raw : null;
    }

    // ---- profile
    public String name() {
        return mPrefs.getString("name", "");
    }

    /** A "data:image/jpeg;base64,..." picture or an empty string */
    public String picture() {
        return mPrefs.getString("picture", "");
    }

    public void saveProfile(@NonNull String name, @NonNull String picture) {
        mPrefs.edit().putString("name", name).putString("picture", picture).apply();
    }

    public NostrEvent profileEvent() {
        JsonObject content = new JsonObject();
        content.addProperty("name", name());
        content.addProperty("display_name", name());
        if (!picture().isEmpty()) content.addProperty("picture", picture());
        return NostrEvent.create(mSecret, 0, new ArrayList<>(), content.toString());
    }

    /** "name#a1b2": the name is not unique, the four characters of the key tell people apart */
    public static String label(String name, String pubHex) {
        String tag = pubHex.length() >= 4 ? pubHex.substring(0, 4) : pubHex;
        return (name == null || name.isEmpty() ? "?" : name) + "#" + tag;
    }

    // ---- secret key storage
    @Nullable
    private byte[] readSecret() {
        String stored = mPrefs.getString("sk", null);
        if (stored == null) return null;
        try {
            if (stored.startsWith("plain:")) return Hex.decode(stored.substring(6));
            if (!stored.startsWith("ks:")) return null;
            byte[] blob = Base64.decode(stored.substring(3), Base64.NO_WRAP);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, keystoreKey(), new GCMParameterSpec(128, blob, 0, 12));
            return cipher.doFinal(blob, 12, blob.length - 12);
        } catch (Exception e) {
            Log.e(TAG, "Could not read the secret key", e);
            return null;
        }
    }

    private void writeSecret(byte[] secret) {
        String value;
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, keystoreKey());
            byte[] iv = cipher.getIV();
            byte[] encrypted = cipher.doFinal(secret);
            byte[] blob = new byte[iv.length + encrypted.length];
            System.arraycopy(iv, 0, blob, 0, iv.length);
            System.arraycopy(encrypted, 0, blob, iv.length, encrypted.length);
            value = "ks:" + Base64.encodeToString(blob, Base64.NO_WRAP);
        } catch (Exception e) {
            // A few devices have a broken keystore: the key then stays in the private storage of the app only
            Log.w(TAG, "Keystore unavailable, storing the key in the app's private preferences", e);
            value = "plain:" + Hex.encode(secret);
        }
        mPrefs.edit().putString("sk", value).apply();
    }

    private static SecretKey keystoreKey() throws Exception {
        KeyStore store = KeyStore.getInstance("AndroidKeyStore");
        store.load(null);
        if (store.containsAlias(KEYSTORE_ALIAS)) return (SecretKey) store.getKey(KEYSTORE_ALIAS, null);
        KeyGenerator generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
        generator.init(new KeyGenParameterSpec.Builder(KEYSTORE_ALIAS, KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build());
        return generator.generateKey();
    }
}
