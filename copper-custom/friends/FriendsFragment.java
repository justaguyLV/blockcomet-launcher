package git.artdeell.mojo.friends;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.util.Base64;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.graphics.drawable.RoundedBitmapDrawable;
import androidx.core.graphics.drawable.RoundedBitmapDrawableFactory;
import androidx.fragment.app.Fragment;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.Arrays;
import java.util.List;

import git.artdeell.mojo.MojoApplication;
import git.artdeell.mojo.R;

/**
 * BlockComet friends, step 1: your identity (made on this device, no account), your profile (username + picture),
 * a friend code to share, and the friend list. Profiles travel over public Nostr relays.
 */
public class FriendsFragment extends Fragment {
    public static final String TAG = "FriendsFragment";

    private static final List<String> RELAYS = Arrays.asList(
            "wss://relay.damus.io", "wss://nos.lol", "wss://relay.primal.net", "wss://relay.nostr.band");
    private static final int AVATAR_PX = 128;
    private static final int MAX_PICTURE_CHARS = 60000;

    private final Handler mMain = new Handler(Looper.getMainLooper());
    private final ActivityResultLauncher<String> mPickPhoto =
            registerForActivityResult(new ActivityResultContracts.GetContent(), this::onPhotoPicked);

    private NostrIdentity mIdentity;
    private FriendsStore mStore;
    private RelayPool mPool;
    private String mProfilesSub;

    private ImageView mAvatar;
    private EditText mNameEdit;
    private TextView mCodeText;
    private TextView mStatus;
    private LinearLayout mFriendsList;
    private String mPicture = "";

    private final Runnable mStatusTicker = new Runnable() {
        @Override
        public void run() {
            if (mPool != null && mStatus != null) {
                mStatus.setText(getString(R.string.friends_status, String.valueOf(mPool.connectedCount()), String.valueOf(mPool.relayCount())));
            }
            mMain.postDelayed(this, 2000);
        }
    };

    // ------------------------------------------------------------------ view

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        Context context = requireContext();
        mIdentity = NostrIdentity.get(context);
        mStore = new FriendsStore(context);
        mPicture = mIdentity.picture();

        ScrollView scroll = new ScrollView(context);
        scroll.setFillViewport(true);
        LinearLayout root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16), dp(12), dp(16), dp(24));
        scroll.addView(root, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        // title + connection status
        TextView title = new TextView(context);
        title.setText(R.string.friends_title);
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 22);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        root.addView(title);
        mStatus = new TextView(context);
        mStatus.setAlpha(0.7f);
        mStatus.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        root.addView(mStatus);

        // profile: avatar + username + save
        LinearLayout profile = row(context);
        profile.setPadding(0, dp(16), 0, 0);
        mAvatar = new ImageView(context);
        mAvatar.setScaleType(ImageView.ScaleType.CENTER_CROP);
        mAvatar.setContentDescription(getString(R.string.friends_pick_photo));
        mAvatar.setOnClickListener(v -> mPickPhoto.launch("image/*"));
        profile.addView(mAvatar, new LinearLayout.LayoutParams(dp(88), dp(88)));
        LinearLayout profileText = new LinearLayout(context);
        profileText.setOrientation(LinearLayout.VERTICAL);
        profileText.setPadding(dp(12), 0, 0, 0);
        mNameEdit = new EditText(context);
        mNameEdit.setHint(R.string.friends_name_hint);
        mNameEdit.setSingleLine(true);
        mNameEdit.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        mNameEdit.setText(mIdentity.name());
        profileText.addView(mNameEdit, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        profileText.addView(button(context, getString(R.string.friends_save), false, v -> saveProfile()));
        profile.addView(profileText, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        root.addView(profile);
        refreshAvatar();

        // your code
        root.addView(section(context, R.string.friends_my_code));
        mCodeText = new TextView(context);
        mCodeText.setTextIsSelectable(true);
        mCodeText.setTypeface(Typeface.MONOSPACE);
        mCodeText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        mCodeText.setText(mIdentity.npub());
        root.addView(mCodeText);
        LinearLayout codeButtons = row(context);
        codeButtons.addView(button(context, getString(R.string.friends_copy), true, v -> copy(mIdentity.npub(), R.string.friends_code_copied)));
        codeButtons.addView(button(context, getString(R.string.friends_share), true, v -> shareCode()));
        root.addView(codeButtons);
        LinearLayout keyButtons = row(context);
        keyButtons.addView(button(context, getString(R.string.friends_backup), true, v -> showBackup()));
        keyButtons.addView(button(context, getString(R.string.friends_restore), true, v -> showRestore()));
        root.addView(keyButtons);

        // add a friend
        root.addView(section(context, R.string.friends_add_title));
        LinearLayout add = row(context);
        EditText codeEdit = new EditText(context);
        codeEdit.setHint(R.string.friends_add_hint);
        codeEdit.setSingleLine(true);
        add.addView(codeEdit, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        add.addView(button(context, getString(R.string.friends_add), false, v -> {
            if (addFriend(codeEdit.getText().toString())) codeEdit.setText("");
        }));
        root.addView(add);

        // friend list
        root.addView(section(context, R.string.friends_list_title));
        mFriendsList = new LinearLayout(context);
        mFriendsList.setOrientation(LinearLayout.VERTICAL);
        root.addView(mFriendsList);
        refreshFriends();
        return scroll;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        mPool = new RelayPool(RELAYS);
        mPool.start();
        if (!mIdentity.name().isEmpty()) mPool.publish(mIdentity.profileEvent());
        subscribeToFriends();
        mMain.post(mStatusTicker);
    }

    @Override
    public void onDestroyView() {
        mMain.removeCallbacksAndMessages(null);
        if (mPool != null) mPool.stop();
        mPool = null;
        mAvatar = null;
        mNameEdit = null;
        mCodeText = null;
        mStatus = null;
        mFriendsList = null;
        super.onDestroyView();
    }

    // ------------------------------------------------------------------ profile

    private void saveProfile() {
        String name = mNameEdit.getText().toString().trim();
        if (name.isEmpty() || name.length() > 32) {
            Toast.makeText(requireContext(), R.string.friends_name_invalid, Toast.LENGTH_SHORT).show();
            return;
        }
        mIdentity.saveProfile(name, mPicture);
        if (mPool != null) mPool.publish(mIdentity.profileEvent());
        Toast.makeText(requireContext(), R.string.friends_saved, Toast.LENGTH_SHORT).show();
    }

    private void onPhotoPicked(@Nullable Uri uri) {
        if (uri == null) return;
        final Context appContext = requireContext().getApplicationContext();
        MojoApplication.sExecutorService.execute(() -> {
            String picture = null;
            try {
                picture = makeAvatar(appContext, uri);
            } catch (Exception ignored) {
                // reported below
            }
            final String result = picture;
            mMain.post(() -> {
                if (!isAdded() || mAvatar == null) return;
                if (result == null) {
                    Toast.makeText(requireContext(), R.string.friends_photo_failed, Toast.LENGTH_SHORT).show();
                    return;
                }
                mPicture = result;
                refreshAvatar();
                // The picture is part of the profile: it is published with the next "Save"
                if (!mIdentity.name().isEmpty() || !mNameEdit.getText().toString().trim().isEmpty()) saveProfile();
            });
        });
    }

    /** A small centered square JPEG as a data URI, so the picture travels inside the profile */
    @Nullable
    private static String makeAvatar(Context context, Uri uri) throws Exception {
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        try (InputStream in = context.getContentResolver().openInputStream(uri)) {
            BitmapFactory.decodeStream(in, null, bounds);
        }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null;
        int sample = 1;
        while (bounds.outWidth / (sample * 2) >= AVATAR_PX * 2 && bounds.outHeight / (sample * 2) >= AVATAR_PX * 2) sample *= 2;
        BitmapFactory.Options opts = new BitmapFactory.Options();
        opts.inSampleSize = sample;
        Bitmap source;
        try (InputStream in = context.getContentResolver().openInputStream(uri)) {
            source = BitmapFactory.decodeStream(in, null, opts);
        }
        if (source == null) return null;
        int side = Math.min(source.getWidth(), source.getHeight());
        Bitmap square = Bitmap.createBitmap(source, (source.getWidth() - side) / 2, (source.getHeight() - side) / 2, side, side);
        Bitmap small = Bitmap.createScaledBitmap(square, AVATAR_PX, AVATAR_PX, true);
        for (int quality = 80; quality >= 40; quality -= 20) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            small.compress(Bitmap.CompressFormat.JPEG, quality, out);
            String uriText = "data:image/jpeg;base64," + Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP);
            if (uriText.length() <= MAX_PICTURE_CHARS / 2) return uriText;
        }
        return null;
    }

    /** Pictures of other people come from the network: only small data URIs are accepted, and never huge images */
    @Nullable
    private static Bitmap decodePicture(@Nullable String dataUri) {
        try {
            if (dataUri == null || !dataUri.startsWith("data:image/") || dataUri.length() > MAX_PICTURE_CHARS) return null;
            int comma = dataUri.indexOf(',');
            if (comma < 0) return null;
            byte[] data = Base64.decode(dataUri.substring(comma + 1), Base64.DEFAULT);
            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            BitmapFactory.decodeByteArray(data, 0, data.length, bounds);
            if (bounds.outWidth <= 0 || bounds.outWidth > 512 || bounds.outHeight <= 0 || bounds.outHeight > 512) return null;
            return BitmapFactory.decodeByteArray(data, 0, data.length);
        } catch (Exception e) {
            return null;
        }
    }

    private void setRoundPicture(ImageView view, @Nullable String picture) {
        Bitmap bitmap = decodePicture(picture);
        if (bitmap == null) {
            view.setImageResource(R.drawable.ic_px_skin);
            return;
        }
        RoundedBitmapDrawable round = RoundedBitmapDrawableFactory.create(getResources(), bitmap);
        round.setCircular(true);
        view.setImageDrawable(round);
    }

    private void refreshAvatar() {
        if (mAvatar != null) setRoundPicture(mAvatar, mPicture);
    }

    // ------------------------------------------------------------------ friends

    private boolean addFriend(String text) {
        byte[] key = NostrIdentity.parseKey(text, "npub");
        if (key == null) {
            Toast.makeText(requireContext(), R.string.friends_invalid_code, Toast.LENGTH_SHORT).show();
            return false;
        }
        String hex = Hex.encode(key);
        if (hex.equals(mIdentity.pubHex())) {
            Toast.makeText(requireContext(), R.string.friends_own_code, Toast.LENGTH_SHORT).show();
            return false;
        }
        if (mStore.find(hex) != null) {
            Toast.makeText(requireContext(), R.string.friends_already, Toast.LENGTH_SHORT).show();
            return false;
        }
        mStore.add(hex);
        refreshFriends();
        subscribeToFriends();
        return true;
    }

    /** One subscription for the profiles of all friends; it is replaced when the list changes */
    private void subscribeToFriends() {
        if (mPool == null) return;
        if (mProfilesSub != null) mPool.unsubscribe(mProfilesSub);
        mProfilesSub = null;
        List<FriendsStore.Friend> friends = mStore.list();
        if (friends.isEmpty()) return;
        StringBuilder authors = new StringBuilder();
        for (FriendsStore.Friend f : friends) {
            if (authors.length() > 0) authors.append(',');
            authors.append('"').append(f.pub).append('"');
        }
        mProfilesSub = "profiles" + System.nanoTime();
        mPool.subscribe(mProfilesSub, "{\"kinds\":[0],\"authors\":[" + authors + "],\"limit\":" + (friends.size() * 2) + "}", this::onProfileEvent);
    }

    /** Runs on a network thread: the signature is checked here, only a valid and newer profile is applied */
    private void onProfileEvent(NostrEvent event) {
        if (event.kind != 0 || mStore.find(event.pubkey) == null) return;
        FriendsStore.Friend known = mStore.find(event.pubkey);
        if (event.createdAt <= known.profileTime || !event.isValid()) return;
        try {
            JsonObject content = new JsonParser().parse(event.content).getAsJsonObject();
            String name = content.has("name") ? content.get("name").getAsString()
                    : content.has("display_name") ? content.get("display_name").getAsString() : "";
            if (name.length() > 40) name = name.substring(0, 40);
            String picture = content.has("picture") ? content.get("picture").getAsString() : "";
            if (!picture.startsWith("data:image/") || picture.length() > MAX_PICTURE_CHARS) picture = "";
            if (mStore.updateProfile(event.pubkey, name, picture, event.createdAt)) mMain.post(this::refreshFriends);
        } catch (Exception ignored) {
            // somebody else's malformed profile
        }
    }

    private void refreshFriends() {
        if (mFriendsList == null || !isAdded()) return;
        Context context = requireContext();
        mFriendsList.removeAllViews();
        List<FriendsStore.Friend> friends = mStore.list();
        if (friends.isEmpty()) {
            TextView empty = new TextView(context);
            empty.setText(R.string.friends_empty);
            empty.setAlpha(0.7f);
            empty.setPadding(0, dp(8), 0, dp(8));
            mFriendsList.addView(empty);
            return;
        }
        TypedValue ripple = new TypedValue();
        context.getTheme().resolveAttribute(android.R.attr.selectableItemBackground, ripple, true);
        for (FriendsStore.Friend friend : friends) {
            LinearLayout item = row(context);
            item.setPadding(0, dp(8), 0, dp(8));
            item.setBackgroundResource(ripple.resourceId);
            ImageView picture = new ImageView(context);
            picture.setScaleType(ImageView.ScaleType.CENTER_CROP);
            setRoundPicture(picture, friend.picture);
            item.addView(picture, new LinearLayout.LayoutParams(dp(48), dp(48)));
            LinearLayout text = new LinearLayout(context);
            text.setOrientation(LinearLayout.VERTICAL);
            text.setPadding(dp(12), 0, 0, 0);
            TextView name = new TextView(context);
            name.setText(NostrIdentity.label(friend.name, friend.pub));
            name.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
            text.addView(name);
            TextView code = new TextView(context);
            code.setText(shorten(Bech32.encode("npub", Hex.decode(friend.pub))));
            code.setTypeface(Typeface.MONOSPACE);
            code.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
            code.setAlpha(0.6f);
            text.addView(code);
            item.addView(text, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            item.setOnLongClickListener(v -> {
                confirmRemove(friend);
                return true;
            });
            mFriendsList.addView(item);
        }
    }

    private void confirmRemove(FriendsStore.Friend friend) {
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.friends_remove_title)
                .setMessage(NostrIdentity.label(friend.name, friend.pub))
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.global_delete, (d, w) -> {
                    mStore.remove(friend.pub);
                    refreshFriends();
                    subscribeToFriends();
                })
                .show();
    }

    // ------------------------------------------------------------------ code, backup

    private void shareCode() {
        Intent send = new Intent(Intent.ACTION_SEND);
        send.setType("text/plain");
        send.putExtra(Intent.EXTRA_TEXT, getString(R.string.friends_share_text, mIdentity.npub()));
        startActivity(Intent.createChooser(send, getString(R.string.friends_share)));
    }

    private void copy(String text, int toast) {
        ClipboardManager clipboard = (ClipboardManager) requireContext().getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboard != null) clipboard.setPrimaryClip(ClipData.newPlainText("BlockComet", text));
        Toast.makeText(requireContext(), toast, Toast.LENGTH_SHORT).show();
    }

    private void showBackup() {
        Context context = requireContext();
        LinearLayout box = new LinearLayout(context);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(24), dp(8), dp(24), 0);
        TextView warning = new TextView(context);
        warning.setText(R.string.friends_backup_message);
        box.addView(warning);
        TextView key = new TextView(context);
        key.setTextIsSelectable(true);
        key.setTypeface(Typeface.MONOSPACE);
        key.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        key.setPadding(0, dp(12), 0, 0);
        key.setText(mIdentity.nsec());
        box.addView(key);
        new MaterialAlertDialogBuilder(context)
                .setTitle(R.string.friends_backup)
                .setView(box)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.friends_copy, (d, w) -> copy(mIdentity.nsec(), R.string.friends_key_copied))
                .show();
    }

    private void showRestore() {
        Context context = requireContext();
        EditText input = new EditText(context);
        input.setSingleLine(true);
        input.setHint("nsec1...");
        LinearLayout box = new LinearLayout(context);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(24), dp(8), dp(24), 0);
        TextView message = new TextView(context);
        message.setText(R.string.friends_restore_message);
        box.addView(message);
        box.addView(input);
        new MaterialAlertDialogBuilder(context)
                .setTitle(R.string.friends_restore)
                .setView(box)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.friends_restore, (d, w) -> {
                    if (!mIdentity.restore(input.getText().toString())) {
                        Toast.makeText(requireContext(), R.string.friends_restore_bad, Toast.LENGTH_LONG).show();
                        return;
                    }
                    if (mCodeText != null) mCodeText.setText(mIdentity.npub());
                    Toast.makeText(requireContext(), R.string.friends_restored, Toast.LENGTH_SHORT).show();
                })
                .show();
    }

    // ------------------------------------------------------------------ small view helpers

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    private static String shorten(String code) {
        return code.length() > 24 ? code.substring(0, 14) + "..." + code.substring(code.length() - 8) : code;
    }

    private LinearLayout row(Context context) {
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        return row;
    }

    private TextView section(Context context, int text) {
        TextView view = new TextView(context);
        view.setText(text);
        view.setTypeface(Typeface.DEFAULT_BOLD);
        view.setPadding(0, dp(20), 0, dp(4));
        return view;
    }

    private MaterialButton button(Context context, String text, boolean outlined, View.OnClickListener listener) {
        MaterialButton button = outlined
                ? new MaterialButton(context, null, com.google.android.material.R.attr.materialButtonOutlinedStyle)
                : new MaterialButton(context);
        button.setText(text);
        button.setOnClickListener(listener);
        return button;
    }
}
