package git.artdeell.mojo.skins;

import android.content.Context;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;

import git.artdeell.mojo.authenticator.AuthType;
import git.artdeell.mojo.authenticator.accounts.Account;
import git.artdeell.mojo.instances.Instance;
import git.artdeell.mojo.tasks.AsyncAssetManager;

/**
 * BlockComet: skins and capes for local (offline) accounts. What the user chose in the skin editor is written where the
 * CustomSkinLoader mod looks for local textures ({gameDir}/CustomSkinLoader/LocalSkin/skins|capes/{name}.png), and the
 * mod itself is added to the instance (once) the first time something is chosen. Nothing happens for accounts without a choice.
 */
public final class LocalSkins {
    private LocalSkins() {}

    public static void install(Context context, Account account, Instance instance) {
        try {
            if (account == null || instance == null || instance.versionId == null) return;
            if (account.authType != AuthType.LOCAL || account.username == null) return;
            SkinLibrary.Entry skin = SkinLibrary.find(SkinLibrary.getSelected(SkinLibrary.localKey(account.username)));
            String capeId = SkinLibrary.getSelected(LocalCapes.key(account.username));
            File root = new File(instance.getGameDirectory(), "CustomSkinLoader/LocalSkin");
            File capeFile = new File(root, "capes/" + account.username + ".png");
            if (skin == null && capeId == null) {
                //noinspection ResultOfMethodCallIgnored
                capeFile.delete();
                return;
            }
            if (instance.versionId.startsWith("fabric-loader")) AsyncAssetManager.installSkinMod(instance);
            if (skin != null) write(new File(root, "skins/" + account.username + ".png"), SkinLibrary.readBytes(skin));
            if (capeId != null) {
                try {
                    write(capeFile, LocalCapes.bytes(context, capeId));
                } catch (Exception e) {
                    Log.w("LocalSkins", "Could not prepare the local cape", e);
                }
            } else {
                //noinspection ResultOfMethodCallIgnored
                capeFile.delete();
            }
        } catch (Exception e) {
            Log.w("LocalSkins", "Could not prepare the local skin", e);
        }
    }

    private static void write(File target, byte[] data) throws java.io.IOException {
        //noinspection ResultOfMethodCallIgnored
        target.getParentFile().mkdirs();
        try (FileOutputStream out = new FileOutputStream(target)) {
            out.write(data);
        }
    }
}
