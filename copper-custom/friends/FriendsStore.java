package git.artdeell.mojo.friends;

import android.content.Context;
import android.util.Log;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.util.ArrayList;
import java.util.List;

/** BlockComet friends: the friend list, kept in a JSON file in the app's private storage. */
public final class FriendsStore {
    @androidx.annotation.Keep
    public static final class Friend {
        public String pub;          // hex public key
        public String name = "";    // from the friend's own profile, empty until it arrives
        public String picture = ""; // "data:image/jpeg;base64,..." or empty
        public long profileTime;    // created_at of the profile event that filled name/picture
    }

    private static final String TAG = "FriendsStore";
    private final File mFile;
    private final List<Friend> mFriends = new ArrayList<>();

    public FriendsStore(Context context) {
        mFile = new File(context.getFilesDir(), "friends.json");
        load();
    }

    private void load() {
        if (!mFile.isFile()) return;
        try (FileReader reader = new FileReader(mFile)) {
            List<Friend> list = new Gson().fromJson(reader, new TypeToken<List<Friend>>() {}.getType());
            if (list != null) for (Friend f : list) if (f != null && f.pub != null) mFriends.add(f);
        } catch (Exception e) {
            Log.w(TAG, "Could not read the friend list", e);
        }
    }

    public synchronized List<Friend> list() {
        return new ArrayList<>(mFriends);
    }

    public synchronized Friend find(String pub) {
        for (Friend f : mFriends) if (f.pub.equals(pub)) return f;
        return null;
    }

    public synchronized Friend add(String pub) {
        Friend existing = find(pub);
        if (existing != null) return existing;
        Friend friend = new Friend();
        friend.pub = pub;
        mFriends.add(friend);
        save();
        return friend;
    }

    public synchronized void remove(String pub) {
        Friend friend = find(pub);
        if (friend != null && mFriends.remove(friend)) save();
    }

    /** Applies a newer profile; returns true when something changed */
    public synchronized boolean updateProfile(String pub, String name, String picture, long time) {
        Friend friend = find(pub);
        if (friend == null || time <= friend.profileTime) return false;
        friend.name = name;
        friend.picture = picture;
        friend.profileTime = time;
        save();
        return true;
    }

    private void save() {
        try (FileWriter writer = new FileWriter(mFile)) {
            new Gson().toJson(mFriends, writer);
        } catch (Exception e) {
            Log.w(TAG, "Could not save the friend list", e);
        }
    }
}
