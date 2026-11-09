package git.artdeell.mojo.skins;

import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;

import git.artdeell.mojo.authenticator.AuthType;
import git.artdeell.mojo.authenticator.accounts.Account;
import git.artdeell.mojo.instances.Instance;
import git.artdeell.mojo.tasks.AsyncAssetManager;

/**
 * BlockComet: skins for local (offline) accounts. The skin the user chose in the skin editor is written where the
 * CustomSkinLoader mod looks for local skins ({gameDir}/CustomSkinLoader/LocalSkin/skins/{name}.png), and the mod itself
 * is added to the instance (once) the first time a skin is chosen. Nothing happens for accounts without a chosen skin.
 */
public final class LocalSkins {
    private LocalSkins() {}

    public static void install(Account account, Instance instance) {
        try {
            if (account == null || instance == null || instance.versionId == null) return;
            if (account.authType != AuthType.LOCAL || account.username == null) return;
            SkinLibrary.Entry entry = SkinLibrary.find(SkinLibrary.getSelected(SkinLibrary.localKey(account.username)));
            if (entry == null) return;
            if (instance.versionId.startsWith("fabric-loader")) AsyncAssetManager.installSkinMod(instance);
            File dir = new File(instance.getGameDirectory(), "CustomSkinLoader/LocalSkin/skins");
            //noinspection ResultOfMethodCallIgnored
            dir.mkdirs();
            byte[] png = SkinLibrary.readBytes(entry);
            try (FileOutputStream out = new FileOutputStream(new File(dir, account.username + ".png"))) {
                out.write(png);
            }
        } catch (Exception e) {
            Log.w("LocalSkins", "Could not prepare the local skin", e);
        }
    }
}
