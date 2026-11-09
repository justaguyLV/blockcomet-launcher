#!/usr/bin/env python3
"""localskin.py <res-dir> <java-dir> <here>

1. Skin editor for local (offline) accounts: choose a skin from the library with one tap (no Microsoft login, no network).
   The chosen skin is put where the CustomSkinLoader mod reads local skins when the game starts.
2. Settings switch "Download performance mods" (off by default): performance mods are no longer installed by themselves.
3. At game start the bundled mods are (re)checked, so the switch takes effect without reinstalling.
"""
import os
import shutil
import sys

res, java, here = sys.argv[1:4]


def patch(path, pairs):
    s = open(path, encoding="utf-8").read()
    for old, new in pairs:
        assert s.count(old) == 1, "upstream changed, anchor not found in %s:\n%s" % (path, old)
        s = s.replace(old, new, 1)
    open(path, "w", encoding="utf-8").write(s)


# ---- LocalSkins helper -------------------------------------------------------------------------------------------
shutil.copy(os.path.join(here, "LocalSkins.java"), os.path.join(java, "skins", "LocalSkins.java"))

# ---- SkinLibrary: separate selection per local account name ------------------------------------------------------
patch(os.path.join(java, "skins", "SkinLibrary.java"), [(
    "    public static File fileOf(@NonNull Entry entry) {",
    "    /** Key of a local account in the selection map (local accounts all share one profile UUID, so the name is used) */\n"
    "    public static String localKey(@NonNull String username) {\n"
    "        return \"local:\" + username;\n"
    "    }\n\n"
    "    public static File fileOf(@NonNull Entry entry) {")])

# ---- Account: face of a local account (from the chosen skin) -----------------------------------------------------
patch(os.path.join(java, "authenticator", "accounts", "Account.java"), [
    ("        if(authType.skinUrl == null) return;\n        Bitmap skinBitmap = BitmapFactory.decodeByteArray(skinPng, 0, skinPng.length);",
     "        if(authType.skinUrl == null && authType != AuthType.LOCAL) return;\n        Bitmap skinBitmap = BitmapFactory.decodeByteArray(skinPng, 0, skinPng.length);"),
    ("        if(isLocal()) return null;\n        File skinFaceFile = getSkinFaceFile();",
     "        File skinFaceFile = getSkinFaceFile();"),
    ('"skin-face-" + profileId +"-"+authType.name() + ".webp"',
     '"skin-face-" + profileId +"-"+authType.name() + (authType == AuthType.LOCAL ? "-" + username : "") + ".webp"'),
])

# ---- Account list: the skin button for local accounts too --------------------------------------------------------
patch(os.path.join(java, "..", "..", "..", "com", "kdt", "mcgui", "AccountSpinner.java"), [(
    "            if(account.authType == AuthType.MICROSOFT) {\n                editButton.setVisibility(View.VISIBLE);",
    "            if(account.authType == AuthType.MICROSOFT || account.authType == AuthType.LOCAL) {\n                editButton.setVisibility(View.VISIBLE);")])

# ---- Skin editor --------------------------------------------------------------------------------------------------
patch(os.path.join(java, "skins", "SkinEditorFragment.java"), [
    ("""                if (result.account == null || result.account.authType != AuthType.MICROSOFT) {
                    result.notMicrosoft = true;
                } else {
                    loadInBackground(result, seedName);
                }""",
     """                if (result.account == null || (result.account.authType != AuthType.MICROSOFT && result.account.authType != AuthType.LOCAL)) {
                    result.notMicrosoft = true;
                } else if (result.account.authType == AuthType.LOCAL) {
                    // Local account: only the local library, nothing to download
                    result.skins = SkinLibrary.list();
                    result.selectedSkinId = SkinLibrary.getSelected(SkinLibrary.localKey(result.account.username));
                } else {
                    loadInBackground(result, seedName);
                }"""),
    ("        if (result.account != null) mAccount = result.account;\n        mLoaded = true;\n",
     """        if (result.account != null) mAccount = result.account;
        mLoaded = true;
        if (mAccount != null && mAccount.authType == AuthType.LOCAL && getView() != null) {
            // Capes belong to a Minecraft profile, a local account has none
            View capesTab = getView().findViewById(R.id.skin_tab_capes);
            if (capesTab != null) capesTab.setVisibility(View.GONE);
        }
"""),
    ("""                fresh = MinecraftSkinApi.ensureFreshSession(account);
                byte[] skinBytes = SkinLibrary.readBytes(entry);
                MinecraftSkinApi.uploadSkin(fresh.accessToken, skinBytes, slim);
                SkinLibrary.setSelected(fresh.profileId, skinId);""",
     """                byte[] skinBytes = SkinLibrary.readBytes(entry);
                if (account.authType == AuthType.LOCAL) {
                    // No upload: the skin is read from the library when the game starts
                    SkinLibrary.setSelected(SkinLibrary.localKey(account.username), skinId);
                } else {
                    fresh = MinecraftSkinApi.ensureFreshSession(account);
                    MinecraftSkinApi.uploadSkin(fresh.accessToken, skinBytes, slim);
                    SkinLibrary.setSelected(fresh.profileId, skinId);
                }"""),
])

# ---- Game start: bundled mods follow the settings switch, local skin is put in place -----------------------------
patch(os.path.join(java, "utils", "jre", "GameRunner.java"), [(
    "        File gamedir = instance.getGameDirectory();\n        JVersionList.Version versionInfo = Tools.getVersionInfo(versionId);",
    "        File gamedir = instance.getGameDirectory();\n"
    "        if (git.artdeell.mojo.tasks.AsyncAssetManager.getBundledVersionIds().contains(instance.versionId)) {\n"
    "            git.artdeell.mojo.tasks.AsyncAssetManager.installBundledMods(instance); // honours \"Download performance mods\"\n"
    "        }\n"
    "        git.artdeell.mojo.skins.LocalSkins.install(account, instance);\n"
    "        JVersionList.Version versionInfo = Tools.getVersionInfo(versionId);")])

# ---- Settings switch + texts ---------------------------------------------------------------------------------------
patch(os.path.join(res, "xml", "pref_misc.xml"), [(
    "<git.artdeell.mojo.prefs.BackButtonPreference />",
    "<git.artdeell.mojo.prefs.BackButtonPreference />\n"
    "    <SwitchPreferenceCompat\n"
    "        android:widgetLayout=\"@layout/preference_widget_material_switch\"\n"
    "        android:defaultValue=\"false\"\n"
    "        android:key=\"blockcomet_perf_mods\"\n"
    "        android:title=\"@string/blockcomet_perf_mods_title\"\n"
    "        android:summary=\"@string/blockcomet_perf_mods_desc\" />")])

en = '''<?xml version="1.0" encoding="utf-8"?>
<resources>
    <string name="blockcomet_perf_mods_title">Download performance mods</string>
    <string name="blockcomet_perf_mods_desc">Add FerriteCore, Lithium, ModernFix, Krypton, Noisium, MemoryLeakFix and ScalableLux to the Meteor instances the next time a game starts. Off by default: nothing is added without your permission, and turning it off never removes mods you already have.</string>
</resources>
'''
ar = '''<?xml version="1.0" encoding="utf-8"?>
<resources>
    <string name="blockcomet_perf_mods_title">تنزيل مودات الأداء</string>
    <string name="blockcomet_perf_mods_desc">يضيف FerriteCore وLithium وModernFix وKrypton وNoisium وMemoryLeakFix وScalableLux إلى نسخ Meteor عند تشغيل اللعبة المرة القادمة. معطّل افتراضيًا: لا يُضاف أي مود بدون إذنك، وإيقافه لا يحذف مودات موجودة عندك.</string>
</resources>
'''
os.makedirs(os.path.join(res, "values-ar"), exist_ok=True)
open(os.path.join(res, "values", "blockcomet_skin_perf.xml"), "w", encoding="utf-8").write(en)
open(os.path.join(res, "values-ar", "blockcomet_skin_perf.xml"), "w", encoding="utf-8").write(ar)

# ---- Skin button: a pixel head instead of the pencil (the pencil is still used for other things) -----------------------
open(os.path.join(res, "drawable", "ic_px_skin.xml"), "w", encoding="utf-8").write("<vector xmlns:android=\"http://schemas.android.com/apk/res/android\" android:height=\"32dp\" android:viewportHeight=\"24\" android:viewportWidth=\"24\" android:width=\"32dp\">\n    <path android:fillColor=\"#4A2F1B\" android:pathData=\"M4,4h16v2h-16z M4,6h16v2h-16z M4,8h2v2h-2z M18,8h2v2h-2z\"/>\n    <path android:fillColor=\"#D9A47A\" android:pathData=\"M6,8h12v2h-12z M4,10h16v2h-16z M4,12h2v2h-2z M10,12h4v2h-4z M18,12h2v2h-2z M4,14h6v2h-6z M14,14h6v2h-6z M4,16h4v2h-4z M16,16h4v2h-4z M4,18h16v2h-16z\"/>\n    <path android:fillColor=\"#B97F58\" android:pathData=\"M10,14h4v2h-4z\"/>\n    <path android:fillColor=\"#FFFFFF\" android:pathData=\"M6,12h2v2h-2z M16,12h2v2h-2z\"/>\n    <path android:fillColor=\"#5B3FA0\" android:pathData=\"M8,12h2v2h-2z M14,12h2v2h-2z\"/>\n    <path android:fillColor=\"#7A4B35\" android:pathData=\"M8,16h8v2h-8z\"/>\n</vector>\n")
patch(os.path.join(res, "layout", "item_account.xml"), [(
    'android:contentDescription="@string/skin_editor_edit_button"\n        android:src="@drawable/ic_px_edit"',
    'android:contentDescription="@string/skin_editor_edit_button"\n        android:src="@drawable/ic_px_skin"')])

print("localskin: done")
