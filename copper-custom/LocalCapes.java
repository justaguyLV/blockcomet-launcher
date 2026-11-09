package git.artdeell.mojo.skins;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import androidx.annotation.NonNull;

import git.artdeell.mojo.Tools;

import org.apache.commons.io.IOUtils;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * BlockComet: capes for local (offline) accounts. Three legendary capes ship inside the app (assets/custom/capes),
 * and the user can add their own PNGs (64x32 layout, or the same layout scaled by a whole number).
 */
public final class LocalCapes {
    private LocalCapes() {}

    private static final String[] BUILTIN_IDS = {"builtin:phoenix", "builtin:void", "builtin:crown"};
    private static final String[] BUILTIN_NAMES = {"Phoenix", "Void", "Crown"};
    private static final String[] BUILTIN_FILES = {"phoenix.png", "void.png", "crown.png"};
    private static final String CUSTOM_PREFIX = "custom:";

    /** Key of a local account's chosen cape in SkinLibrary's selection map */
    public static String key(@NonNull String username) {
        return "localcape:" + username;
    }

    private static File dir() {
        return new File(Tools.DIR_DATA, "local_capes");
    }

    public static boolean isCustom(@NonNull String id) {
        return id.startsWith(CUSTOM_PREFIX);
    }

    @NonNull
    public static List<MinecraftSkinApi.Cape> list() {
        List<MinecraftSkinApi.Cape> capes = new ArrayList<>();
        for (int i = 0; i < BUILTIN_IDS.length; i++) capes.add(make(BUILTIN_IDS[i], BUILTIN_NAMES[i]));
        File[] files = dir().listFiles((d, name) -> name.endsWith(".png"));
        if (files != null) {
            java.util.Arrays.sort(files, (a, b) -> Long.compare(a.lastModified(), b.lastModified()));
            for (File f : files) {
                String name = f.getName();
                capes.add(make(CUSTOM_PREFIX + name.substring(0, name.length() - 4), "Custom"));
            }
        }
        return capes;
    }

    private static MinecraftSkinApi.Cape make(String id, String alias) {
        MinecraftSkinApi.Cape cape = new MinecraftSkinApi.Cape();
        cape.id = id;
        cape.alias = alias;
        return cape;
    }

    /** The PNG of the cape, exactly as it is written to the game */
    @NonNull
    public static byte[] bytes(@NonNull Context context, @NonNull String id) throws IOException {
        for (int i = 0; i < BUILTIN_IDS.length; i++) {
            if (BUILTIN_IDS[i].equals(id)) {
                try (InputStream in = context.getAssets().open("custom/capes/" + BUILTIN_FILES[i])) {
                    return IOUtils.toByteArray(in);
                }
            }
        }
        if (!isCustom(id)) throw new IOException("Unknown cape " + id);
        File file = new File(dir(), id.substring(CUSTOM_PREFIX.length()) + ".png");
        try (InputStream in = new FileInputStream(file)) {
            return IOUtils.toByteArray(in);
        }
    }

    @NonNull
    public static Bitmap texture(@NonNull Context context, @NonNull String id) throws IOException {
        byte[] data = bytes(context, id);
        Bitmap raw = BitmapFactory.decodeByteArray(data, 0, data.length);
        if (raw == null) throw new IOException("Broken cape " + id);
        return SkinTextures.normalizeCape(raw);
    }

    /** A cape texture is 2:1 and a whole multiple of 64 pixels wide (64x32, 128x64, ...) */
    public static boolean isValidSize(int width, int height) {
        return width >= 64 && width % 64 == 0 && width == height * 2;
    }

    @NonNull
    public static MinecraftSkinApi.Cape add(@NonNull byte[] png) throws IOException {
        File dir = dir();
        if (!dir.isDirectory() && !dir.mkdirs()) throw new IOException("Cannot create " + dir);
        String uuid = UUID.randomUUID().toString();
        try (FileOutputStream out = new FileOutputStream(new File(dir, uuid + ".png"))) {
            out.write(png);
        }
        return make(CUSTOM_PREFIX + uuid, "Custom");
    }

    public static void delete(@NonNull String id) throws IOException {
        if (!isCustom(id)) return;
        File file = new File(dir(), id.substring(CUSTOM_PREFIX.length()) + ".png");
        if (file.exists() && !file.delete()) throw new IOException("Cannot delete " + file);
    }
}
