#!/usr/bin/env python3
"""chat.py <res-dir> <java-dir> <manifest> <custom-dir>

Wires BlockComet Chat into the launcher: a friends/DM chat backed by a PocketBase server (self-hosted, free tier
friendly), shown in a WebView activity, with a settings entry right after Color Studio. A chat message can carry a
"join server" invite; tapping it is handled by ChatActivity.java (currently shows the server address and copies it
to the clipboard -- wiring it into the instance launch pipeline is tracked as a follow-up, see docs/CHAT.md).
Must run after studio.py (reuses its settings-entry anchor).
"""
import os
import shutil
import sys

res, java, manifest, custom = sys.argv[1:5]

# ---- Java -------------------------------------------------------------------------------------------------
for name in ("BlockCometChat.java", "ChatActivity.java"):
    shutil.copy(os.path.join(custom, "java", name), os.path.join(java, name))

# ---- WebView asset (the actual chat UI; talks to PocketBase with its JS SDK) ------------------------------
assets_dir = os.path.join(os.path.dirname(java.rstrip("/")), "assets", "blockcomet_chat")
os.makedirs(assets_dir, exist_ok=True)
shutil.copy(os.path.join(custom, "chat_web", "chat.html"), os.path.join(assets_dir, "chat.html"))

# ---- strings -----------------------------------------------------------------------------------------------
EN = """<?xml version="1.0" encoding="utf-8"?>
<resources>
    <string name="chat_open_title">Chat</string>
    <string name="chat_open_summary">Talk to friends and send them a one-tap invite into your server</string>
    <string name="chat_server_not_set">Set your chat server address first (Settings &#8594; Chat &#8594; Server URL)</string>
</resources>
"""
AR = """<?xml version="1.0" encoding="utf-8"?>
<resources>
    <string name="chat_open_title">الدردشة</string>
    <string name="chat_open_summary">تحدث مع أصدقائك وأرسل لهم دعوة بضغطة واحدة للانضمام إلى سيرفرك</string>
    <string name="chat_server_not_set">حدد عنوان سيرفر الدردشة أولاً (الإعدادات &#8592; الدردشة &#8592; رابط السيرفر)</string>
</resources>
"""
open(os.path.join(res, "values", "blockcomet_chat.xml"), "w", encoding="utf-8").write(EN)
os.makedirs(os.path.join(res, "values-ar"), exist_ok=True)
open(os.path.join(res, "values-ar", "blockcomet_chat.xml"), "w", encoding="utf-8").write(AR)

# ---- manifest: the activity ---------------------------------------------------------------------------------
m = open(manifest, encoding="utf-8").read()
if "ChatActivity" not in m:
    assert "</application>" in m
    activity = '        <activity\n            android:name="git.artdeell.mojo.ChatActivity"\n            android:exported="false" />\n'
    m = m.replace("</application>", activity + "    </application>", 1)
    open(manifest, "w", encoding="utf-8").write(m)

# ---- settings entry, right after Color Studio ----------------------------------------------------------------
xp = os.path.join(res, "xml", "pref_appearance.xml")
x = open(xp, encoding="utf-8").read()
i = x.index("blockcomet_color_studio")
j = x.index("/>", i) + 2
entry = (
    '\n        <androidx.preference.Preference\n'
    '            android:key="blockcomet_chat"\n'
    '            android:title="@string/chat_open_title"\n'
    '            android:summary="@string/chat_open_summary"\n'
    '            app:iconSpaceReserved="false" />'
)
open(xp, "w", encoding="utf-8").write(x[:j] + entry + x[j:])

fp = os.path.join(java, "prefs", "screens", "LauncherPreferenceAppearanceFragment.java")
f = open(fp, encoding="utf-8").read()
anchor = 'androidx.preference.Preference blockCometLanguage = findPreference("app_language");'
assert anchor in f, "language hook not found (run look.py / apply.sh section 4 first)"
hook = (
    'androidx.preference.Preference blockCometChat = findPreference("blockcomet_chat");\n'
    "        if (blockCometChat != null) {\n"
    "            blockCometChat.setOnPreferenceClickListener(preference -> {\n"
    "                startActivity(new android.content.Intent(requireContext(), git.artdeell.mojo.ChatActivity.class));\n"
    "                return true;\n"
    "            });\n"
    "        }\n        "
)
open(fp, "w", encoding="utf-8").write(f.replace(anchor, hook + anchor, 1))

print("chat wired (WIP: server invite tap only copies the address for now, see docs/CHAT.md)")
