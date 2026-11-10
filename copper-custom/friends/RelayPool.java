package git.artdeell.mojo.friends;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;

/**
 * BlockComet friends: talks to several public Nostr relays at once. Every message is sent to all of them and
 * every subscription is opened on all of them, so one relay going down changes nothing for the user.
 */
public final class RelayPool {
    public interface Listener {
        /** Called on a background thread, once per relay that has the event; the event is not checked yet */
        void onEvent(NostrEvent event);
    }

    private static final String TAG = "RelayPool";

    private static final class Subscription {
        final String filterJson;
        final Listener listener;

        Subscription(String filterJson, Listener listener) {
            this.filterJson = filterJson;
            this.listener = listener;
        }
    }

    private final OkHttpClient mClient = new OkHttpClient.Builder()
            .pingInterval(25, TimeUnit.SECONDS)
            .connectTimeout(10, TimeUnit.SECONDS)
            .build();
    private final Handler mMain = new Handler(Looper.getMainLooper());
    private final List<Relay> mRelays = new ArrayList<>();
    private final Map<String, Subscription> mSubs = new HashMap<>();
    private volatile boolean mStarted;

    private final class Relay extends WebSocketListener {
        final String url;
        WebSocket socket;
        boolean open;
        boolean connecting;
        final List<String> queue = new ArrayList<>();

        Relay(String url) {
            this.url = url;
        }

        synchronized void connect() {
            if (open || connecting || !mStarted) return;
            connecting = true;
            try {
                socket = mClient.newWebSocket(new Request.Builder().url(url).build(), this);
            } catch (Exception e) {
                connecting = false;
                scheduleReconnect();
            }
        }

        synchronized void send(String message) {
            if (open && socket != null && socket.send(message)) return;
            queue.add(message);
        }

        synchronized void close() {
            open = false;
            connecting = false;
            queue.clear();
            if (socket != null) socket.close(1000, null);
            socket = null;
        }

        private void scheduleReconnect() {
            mMain.postDelayed(this::connect, 6000);
        }

        @Override
        public void onOpen(WebSocket webSocket, Response response) {
            synchronized (this) {
                open = true;
                connecting = false;
                for (String message : queue) webSocket.send(message);
                queue.clear();
            }
            synchronized (RelayPool.this) {
                for (Map.Entry<String, Subscription> entry : mSubs.entrySet()) {
                    webSocket.send(NostrEvent.reqMessage(entry.getKey(), entry.getValue().filterJson).toString());
                }
            }
        }

        @Override
        public void onMessage(WebSocket webSocket, String text) {
            try {
                JsonArray message = new JsonParser().parse(text).getAsJsonArray();
                if (message.size() < 3 || !"EVENT".equals(message.get(0).getAsString())) return;
                Subscription sub;
                synchronized (RelayPool.this) {
                    sub = mSubs.get(message.get(1).getAsString());
                }
                if (sub == null) return;
                JsonElement element = message.get(2);
                NostrEvent event = element.isJsonObject() ? NostrEvent.fromJson(element.getAsJsonObject()) : null;
                if (event != null) sub.listener.onEvent(event);
            } catch (Exception e) {
                Log.w(TAG, "Bad message from " + url, e);
            }
        }

        @Override
        public void onClosed(WebSocket webSocket, int code, String reason) {
            synchronized (this) {
                open = false;
                connecting = false;
            }
            if (mStarted) scheduleReconnect();
        }

        @Override
        public void onFailure(WebSocket webSocket, Throwable t, Response response) {
            synchronized (this) {
                open = false;
                connecting = false;
            }
            if (mStarted) scheduleReconnect();
        }
    }

    public RelayPool(List<String> urls) {
        for (String url : urls) mRelays.add(new Relay(url));
    }

    public void start() {
        mStarted = true;
        for (Relay relay : mRelays) relay.connect();
    }

    public void stop() {
        mStarted = false;
        for (Relay relay : mRelays) relay.close();
        synchronized (this) {
            mSubs.clear();
        }
    }

    public int connectedCount() {
        int count = 0;
        for (Relay relay : mRelays) {
            synchronized (relay) {
                if (relay.open) count++;
            }
        }
        return count;
    }

    public int relayCount() {
        return mRelays.size();
    }

    /** Sends the event to every relay (relays that are still connecting get it when they are up) */
    public void publish(NostrEvent event) {
        String message = "[\"EVENT\"," + event.toJson() + "]";
        for (Relay relay : mRelays) relay.send(message);
    }

    public void subscribe(String subId, String filterJson, Listener listener) {
        synchronized (this) {
            mSubs.put(subId, new Subscription(filterJson, listener));
        }
        String message = NostrEvent.reqMessage(subId, filterJson).toString();
        for (Relay relay : mRelays) relay.send(message);
    }

    public void unsubscribe(String subId) {
        synchronized (this) {
            if (mSubs.remove(subId) == null) return;
        }
        String message = "[\"CLOSE\",\"" + subId + "\"]";
        for (Relay relay : mRelays) {
            synchronized (relay) {
                if (relay.open && relay.socket != null) relay.socket.send(message);
            }
        }
    }
}
