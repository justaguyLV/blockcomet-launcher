package git.artdeell.mojo;

import android.content.Context;
import android.content.SharedPreferences;

/** BlockComet Chat: stores the user's chosen PocketBase server URL (nothing else -- the chat UI itself is a web page). */
public final class BlockCometChat {
    private static final String PREFS = "blockcomet_chat";
    private static final String KEY_SERVER_URL = "server_url";

    private BlockCometChat() {}

    public static String getServerUrl(Context context) {
        return prefs(context).getString(KEY_SERVER_URL, "");
    }

    public static void setServerUrl(Context context, String url) {
        prefs(context).edit().putString(KEY_SERVER_URL, url).apply();
    }

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
}
