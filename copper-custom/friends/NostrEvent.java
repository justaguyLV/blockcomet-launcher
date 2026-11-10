package git.artdeell.mojo.friends;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/** BlockComet friends: a Nostr event (NIP-01): create, sign, check and (de)serialize. */
public final class NostrEvent {
    public String id;
    public String pubkey;
    public long createdAt;
    public int kind;
    public List<List<String>> tags = new ArrayList<>();
    public String content = "";
    public String sig;

    /** NIP-01 string escaping: only the characters it names are escaped, everything else is written as is */
    static void escape(StringBuilder sb, String s) {
        sb.append('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"': sb.append("\\\""); break;
                case '\\': sb.append("\\\\"); break;
                case '\n': sb.append("\\n"); break;
                case '\r': sb.append("\\r"); break;
                case '\t': sb.append("\\t"); break;
                case '\b': sb.append("\\b"); break;
                case '\f': sb.append("\\f"); break;
                default:
                    if (c < 0x20) sb.append(String.format("\\u%04x", (int) c));
                    else sb.append(c);
            }
        }
        sb.append('"');
    }

    private static void appendTags(StringBuilder sb, List<List<String>> tags) {
        sb.append('[');
        for (int i = 0; i < tags.size(); i++) {
            if (i > 0) sb.append(',');
            sb.append('[');
            List<String> tag = tags.get(i);
            for (int j = 0; j < tag.size(); j++) {
                if (j > 0) sb.append(',');
                escape(sb, tag.get(j));
            }
            sb.append(']');
        }
        sb.append(']');
    }

    private static String serializeForId(String pubkey, long createdAt, int kind, List<List<String>> tags, String content) {
        StringBuilder sb = new StringBuilder("[0,");
        escape(sb, pubkey);
        sb.append(',').append(createdAt).append(',').append(kind).append(',');
        appendTags(sb, tags);
        sb.append(',');
        escape(sb, content);
        sb.append(']');
        return sb.toString();
    }

    public static NostrEvent create(byte[] secretKey, int kind, List<List<String>> tags, String content) {
        NostrEvent e = new NostrEvent();
        e.pubkey = Hex.encode(Bip340.publicKey(secretKey));
        e.createdAt = System.currentTimeMillis() / 1000L;
        e.kind = kind;
        e.tags = tags;
        e.content = content;
        byte[] hash = Bip340.sha256(serializeForId(e.pubkey, e.createdAt, kind, tags, content).getBytes(StandardCharsets.UTF_8));
        e.id = Hex.encode(hash);
        e.sig = Hex.encode(Bip340.sign(hash, secretKey));
        return e;
    }

    /** True when the id matches the content and the signature belongs to the author */
    public boolean isValid() {
        try {
            byte[] hash = Bip340.sha256(serializeForId(pubkey, createdAt, kind, tags, content).getBytes(StandardCharsets.UTF_8));
            if (!Hex.encode(hash).equals(id)) return false;
            byte[] pk = Hex.decode(pubkey), sg = Hex.decode(sig);
            return pk != null && sg != null && Bip340.verify(hash, pk, sg);
        } catch (Exception e) {
            return false;
        }
    }

    public String toJson() {
        StringBuilder sb = new StringBuilder("{\"id\":");
        escape(sb, id);
        sb.append(",\"pubkey\":");
        escape(sb, pubkey);
        sb.append(",\"created_at\":").append(createdAt).append(",\"kind\":").append(kind).append(",\"tags\":");
        appendTags(sb, tags);
        sb.append(",\"content\":");
        escape(sb, content);
        sb.append(",\"sig\":");
        escape(sb, sig);
        return sb.append('}').toString();
    }

    /** Null when the object is not a well formed event */
    public static NostrEvent fromJson(JsonObject o) {
        try {
            NostrEvent e = new NostrEvent();
            e.id = o.get("id").getAsString();
            e.pubkey = o.get("pubkey").getAsString();
            e.createdAt = o.get("created_at").getAsLong();
            e.kind = o.get("kind").getAsInt();
            e.content = o.get("content").getAsString();
            e.sig = o.get("sig").getAsString();
            for (JsonElement tagElement : o.getAsJsonArray("tags")) {
                List<String> tag = new ArrayList<>();
                for (JsonElement part : tagElement.getAsJsonArray()) tag.add(part.getAsString());
                e.tags.add(tag);
            }
            return e;
        } catch (Exception ex) {
            return null;
        }
    }

    public static JsonArray reqMessage(String subId, String filterJson) {
        JsonArray array = new JsonArray();
        array.add("REQ");
        array.add(subId);
        array.add(new com.google.gson.JsonParser().parse(filterJson));
        return array;
    }
}
