#!/usr/bin/env python3
"""friends.py <app-dir> <res-dir> <java-dir> <here>

BlockComet friends, step 1: a "Friends" screen in the main menu with an identity made on the device, a profile
(username + picture), a friend code, and a friend list. It talks to public Nostr relays through OkHttp.
"""
import os
import shutil
import sys

app, res, java, here = sys.argv[1:5]


def patch(path, pairs):
    s = open(path, encoding="utf-8").read()
    for old, new in pairs:
        assert s.count(old) == 1, "upstream changed, anchor not found in %s:\n%s" % (path, old)
        s = s.replace(old, new, 1)
    open(path, "w", encoding="utf-8").write(s)


# ---- the code ---------------------------------------------------------------------------------------------------------
dest = os.path.join(java, "friends")
os.makedirs(dest, exist_ok=True)
for name in os.listdir(os.path.join(here, "friends")):
    if name.endswith(".java"):
        shutil.copy(os.path.join(here, "friends", name), os.path.join(dest, name))

# ---- WebSocket client library -------------------------------------------------------------------------------------------
patch(os.path.join(app, "build.gradle"), [(
    "    implementation 'commons-codec:commons-codec:1.19.0'",
    "    implementation 'commons-codec:commons-codec:1.19.0'\n    implementation 'com.squareup.okhttp3:okhttp:4.12.0'")])

# ---- menu button (portrait and landscape) -------------------------------------------------------------------------------
button = ('\n\n\t\t\t<com.kdt.mcgui.LauncherMenuButton\n'
          '\t\t\t\tandroid:id="@+id/friends_button"\n'
          '\t\t\t\tstyle="@style/LauncherMenuButton.Universal"\n'
          '\t\t\t\tandroid:layout_width="match_parent"\n'
          '\t\t\t\tandroid:drawableStart="@drawable/ic_px_friends"\n'
          '\t\t\t\tandroid:text="@string/friends_title" />')
anchor = 'android:text="@string/mcl_option_customcontrol" />'
for layout in ("layout", "layout-land"):
    path = os.path.join(res, layout, "fragment_launcher.xml")
    if os.path.isfile(path):
        patch(path, [(anchor, anchor + button)])

patch(os.path.join(java, "fragments", "MainMenuFragment.java"), [(
    "        mCustomControlButton.setOnClickListener(v -> startActivity(new Intent(requireContext(), CustomControlsActivity.class)));",
    "        mCustomControlButton.setOnClickListener(v -> startActivity(new Intent(requireContext(), CustomControlsActivity.class)));\n"
    "        View friendsButton = view.findViewById(R.id.friends_button);\n"
    "        if (friendsButton != null) {\n"
    "            friendsButton.setOnClickListener(v -> Tools.swapFragment(requireActivity(),\n"
    "                    git.artdeell.mojo.friends.FriendsFragment.class, git.artdeell.mojo.friends.FriendsFragment.TAG, null));\n"
    "        }")])

# ---- icon: two pixel heads ----------------------------------------------------------------------------------------------
open(os.path.join(res, "drawable", "ic_px_friends.xml"), "w", encoding="utf-8").write(
    '<vector xmlns:android="http://schemas.android.com/apk/res/android" android:height="32dp" android:viewportHeight="24" '
    'android:viewportWidth="24" android:width="32dp">\n'
    '    <path android:fillColor="?attr/colorOnSurface" android:pathData="M4,4h7v7H4z M2,13h11v8H2z M14,7h6v6h-6z M13,15h9v6h-9z"/>\n'
    '</vector>\n')

# ---- texts ----------------------------------------------------------------------------------------------------------------
EN = {
    "friends_title": "Friends",
    "friends_status": "Connected to %1$s of %2$s servers",
    "friends_name_hint": "Your username",
    "friends_save": "Save",
    "friends_saved": "Profile saved",
    "friends_name_invalid": "Pick a username of 1 to 32 characters",
    "friends_pick_photo": "Change profile picture",
    "friends_photo_failed": "Could not use this picture",
    "friends_my_code": "Your friend code",
    "friends_copy": "Copy",
    "friends_share": "Share",
    "friends_code_copied": "Code copied",
    "friends_share_text": "Add me on BlockComet: %1$s",
    "friends_backup": "Back up key",
    "friends_backup_message": "This secret key is your account. Anyone who has it can pretend to be you, so keep it private. Save it somewhere safe: without it you cannot get your account back on a new phone.",
    "friends_key_copied": "Key copied",
    "friends_restore": "Restore key",
    "friends_restore_message": "Paste a secret key (nsec...). It replaces the account on this phone.",
    "friends_restore_bad": "This is not a valid secret key",
    "friends_restored": "Account restored",
    "friends_add_title": "Add a friend",
    "friends_add_hint": "Paste your friend's code",
    "friends_add": "Add",
    "friends_invalid_code": "This is not a valid friend code",
    "friends_own_code": "This is your own code",
    "friends_already": "Already in your friends",
    "friends_list_title": "Your friends",
    "friends_empty": "No friends yet. Share your code, or paste theirs above.",
    "friends_remove_title": "Remove this friend?",
}
AR = {
    "friends_title": "الأصدقاء",
    "friends_status": "متصل بـ %1$s من %2$s خوادم",
    "friends_name_hint": "اسم المستخدم",
    "friends_save": "حفظ",
    "friends_saved": "تم حفظ الملف الشخصي",
    "friends_name_invalid": "اختر اسم مستخدم من 1 إلى 32 حرفًا",
    "friends_pick_photo": "تغيير صورة البروفايل",
    "friends_photo_failed": "تعذّر استخدام هذه الصورة",
    "friends_my_code": "كود الصداقة الخاص بك",
    "friends_copy": "نسخ",
    "friends_share": "مشاركة",
    "friends_code_copied": "تم نسخ الكود",
    "friends_share_text": "أضفني في BlockComet: %1$s",
    "friends_backup": "نسخ احتياطي للمفتاح",
    "friends_backup_message": "هذا المفتاح السري هو حسابك. أي شخص يملكه يقدر ينتحل شخصيتك، فلا تشاركه مع أحد. احفظه في مكان آمن: بدونه ما تقدر ترجع حسابك على جوال جديد.",
    "friends_key_copied": "تم نسخ المفتاح",
    "friends_restore": "استعادة مفتاح",
    "friends_restore_message": "الصق مفتاحًا سريًا (nsec...). يستبدل الحساب الموجود على هذا الجوال.",
    "friends_restore_bad": "هذا ليس مفتاحًا سريًا صالحًا",
    "friends_restored": "تمت استعادة الحساب",
    "friends_add_title": "إضافة صديق",
    "friends_add_hint": "الصق كود صديقك",
    "friends_add": "إضافة",
    "friends_invalid_code": "هذا ليس كود صداقة صالحًا",
    "friends_own_code": "هذا كودك أنت",
    "friends_already": "موجود في أصدقائك",
    "friends_list_title": "أصدقاؤك",
    "friends_empty": "ما عندك أصدقاء بعد. شارك كودك، أو الصق كودهم في الأعلى.",
    "friends_remove_title": "حذف هذا الصديق؟",
}


def xml(strings):
    esc = lambda v: v.replace("&", "&amp;").replace("<", "&lt;").replace("'", "\\'")
    body = "".join('    <string name="%s">%s</string>\n' % (k, esc(v)) for k, v in strings.items())
    return '<?xml version="1.0" encoding="utf-8"?>\n<resources>\n' + body + "</resources>\n"


os.makedirs(os.path.join(res, "values-ar"), exist_ok=True)
open(os.path.join(res, "values", "blockcomet_friends.xml"), "w", encoding="utf-8").write(xml(EN))
open(os.path.join(res, "values-ar", "blockcomet_friends.xml"), "w", encoding="utf-8").write(xml(AR))
print("friends: done")
