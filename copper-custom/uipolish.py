#!/usr/bin/env python3
"""uipolish.py <res-dir> <java-dir>  - small, safe look-and-feel improvements."""
import os
import sys

res, java = sys.argv[1:3]


def patch(path, pairs):
    s = open(path, encoding="utf-8").read()
    for old, new in pairs:
        assert s.count(old) == 1, "upstream changed, anchor not found in %s:\n%s" % (path, old)
        s = s.replace(old, new, 1)
    open(path, "w", encoding="utf-8").write(s)


# Mods list: a turned-off mod is dimmed, so on/off is clear at a glance (and follows the switch immediately)
patch(os.path.join(java, "modding", "optifine", "InstalledModAdapter.java"), [(
    "            toggle.setChecked(entry.enabled);\n"
    "            toggle.setOnCheckedChangeListener((btn, checked) -> entry.setEnabled(checked));",
    "            toggle.setChecked(entry.enabled);\n"
    "            itemView.setAlpha(entry.enabled ? 1f : 0.5f);\n"
    "            toggle.setOnCheckedChangeListener((btn, checked) -> {\n"
    "                entry.setEnabled(checked);\n"
    "                itemView.animate().alpha(checked ? 1f : 0.5f).setDuration(150).start();\n"
    "            });")])
print("uipolish: done")
