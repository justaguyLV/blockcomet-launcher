package git.artdeell.mojo;

import android.annotation.SuppressLint;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.os.Bundle;
import android.view.ViewGroup;
import android.webkit.JavascriptInterface;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

/**
 * BlockComet Chat: a thin WebView wrapper. The actual UI (login, friends, messages) lives in
 * assets/blockcomet_chat/chat.html and talks directly to a self-hosted PocketBase server using its JS SDK --
 * no native networking code here.
 *
 * The page calls back into {@link ChatBridge} when the user taps a "join server" invite; for now that just
 * shows the address and copies it to the clipboard. Feeding it straight into an instance launch (e.g. as a
 * --quickPlayMultiplayer argument) is a follow-up -- see docs/CHAT.md for the plan and why it needs its own,
 * separately tested change.
 */
public class ChatActivity extends AppCompatActivity {

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        WebView webView = new WebView(this);
        webView.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        webView.addJavascriptInterface(new ChatBridge(this), "AndroidChat");
        setContentView(webView);

        if (BlockCometChat.getServerUrl(this).isEmpty()) {
            Toast.makeText(this, R.string.chat_server_not_set, Toast.LENGTH_LONG).show();
        }
        webView.loadUrl("file:///android_asset/blockcomet_chat/chat.html");
    }

    /** JS -> Android bridge exposed to chat.html as window.AndroidChat. */
    public static final class ChatBridge {
        private final Context context;

        ChatBridge(Context context) {
            this.context = context;
        }

        /** chat.html reads the saved PocketBase URL on load so it survives app restarts. */
        @JavascriptInterface
        public String getServerUrl() {
            return BlockCometChat.getServerUrl(context);
        }

        /** chat.html calls this once the user types/confirms a PocketBase server URL in its own settings panel. */
        @JavascriptInterface
        public void setServerUrl(String url) {
            BlockCometChat.setServerUrl(context, url);
        }

        /** Called when the user taps a "join server" invite bubble in the chat. */
        @JavascriptInterface
        public void onServerInvite(String host, String port) {
            String address = host + (port == null || port.isEmpty() ? "" : ":" + port);
            ClipboardManager clipboard = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
            if (clipboard != null) {
                clipboard.setPrimaryClip(ClipData.newPlainText("Minecraft server", address));
            }
            ((AppCompatActivity) context).runOnUiThread(() ->
                    Toast.makeText(context, address + " copied -- paste it in Multiplayer, direct join is coming", Toast.LENGTH_LONG).show());
        }
    }
}
