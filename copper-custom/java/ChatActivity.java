package git.artdeell.mojo;

import android.annotation.SuppressLint;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.view.ViewGroup;
import android.webkit.JavascriptInterface;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

/**
 * BlockComet Chat: a thin WebView wrapper. The actual UI (login, friends, messages) lives in
 * assets/blockcomet_chat/chat.html and talks directly to a self-hosted PocketBase server using its JS SDK --
 * no native networking code here.
 *
 * Two extra bridge jobs beyond opening the chat server: saving/opening a local JSON backup file of your
 * friends + messages (so you don't lose them if the server ever gets reset), and the "join server" invite
 * button, which for now just copies the address -- see docs/CHAT.md.
 */
public class ChatActivity extends AppCompatActivity {

    private ActivityResultLauncher<String> filePicker;
    private ChatBridge bridge;

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        filePicker = registerForActivityResult(new ActivityResultContracts.GetContent(), uri -> {
            if (uri != null && bridge != null) bridge.deliverPickedFile(uri);
        });

        WebView webView = new WebView(this);
        webView.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        bridge = new ChatBridge(this);
        bridge.attachWebView(webView);
        webView.addJavascriptInterface(bridge, "AndroidChat");
        webView.setWebChromeClient(new WebChromeClient() {
            // Lets the page's own <input type=file> (used as a fallback outside the app) still work;
            // the in-app restore button goes through AndroidChat.pickBackupFile() below instead.
            @Override
            public boolean onShowFileChooser(WebView view, ValueCallback<Uri[]> callback, FileChooserParams params) {
                return false;
            }
        });
        setContentView(webView);

        if (BlockCometChat.getServerUrl(this).isEmpty()) {
            Toast.makeText(this, R.string.chat_server_not_set, Toast.LENGTH_LONG).show();
        }
        webView.loadUrl("file:///android_asset/blockcomet_chat/chat.html");
    }

    /** JS -> Android bridge exposed to chat.html as window.AndroidChat. */
    public final class ChatBridge {
        private final Context context;
        private WebView webViewRef;

        ChatBridge(Context context) {
            this.context = context;
        }

        void attachWebView(WebView webView) {
            this.webViewRef = webView;
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
            runOnUiThread(() ->
                    Toast.makeText(context, address + " copied -- paste it in Multiplayer, direct join is coming", Toast.LENGTH_LONG).show());
        }

        /** Saves a backup JSON file to the device's public Downloads folder so it survives even an app uninstall. */
        @JavascriptInterface
        public void saveBackupFile(String filename, String jsonContent) {
            try {
                ContentValues values = new ContentValues();
                values.put(MediaStore.Downloads.DISPLAY_NAME, filename);
                values.put(MediaStore.Downloads.MIME_TYPE, "application/json");
                values.put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS);
                Uri uri = context.getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
                if (uri == null) throw new IOException("could not create download entry");
                try (OutputStream out = context.getContentResolver().openOutputStream(uri)) {
                    out.write(jsonContent.getBytes(StandardCharsets.UTF_8));
                }
                runOnUiThread(() -> Toast.makeText(context, "Saved to Downloads/" + filename, Toast.LENGTH_LONG).show());
            } catch (IOException e) {
                runOnUiThread(() -> Toast.makeText(context, "Backup failed: " + e.getMessage(), Toast.LENGTH_LONG).show());
            }
        }

        /** Opens the system file picker so the user can choose a backup JSON file to restore from. */
        @JavascriptInterface
        public void pickBackupFile() {
            runOnUiThread(() -> filePicker.launch("application/json"));
        }

        void deliverPickedFile(Uri uri) {
            try (InputStream in = context.getContentResolver().openInputStream(uri)) {
                if (in == null) throw new IOException("could not open file");
                byte[] bytes = in.readAllBytes();
                String json = new String(bytes, StandardCharsets.UTF_8).replace("\\", "\\\\").replace("`", "\\`");
                runOnUiThread(() -> {
                    if (webViewRef != null) {
                        webViewRef.evaluateJavascript("window.onBackupFilePicked(`" + json + "`)", null);
                    }
                });
            } catch (IOException e) {
                runOnUiThread(() -> Toast.makeText(context, "Could not read file: " + e.getMessage(), Toast.LENGTH_LONG).show());
            }
        }
    }
}
