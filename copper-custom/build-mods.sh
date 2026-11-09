#!/usr/bin/env bash
# Prepares, for several Minecraft versions, a ready Fabric setup that the launcher turns into instances on first start:
#   assets/custom/versions/<fabric-loader-X-GAME>.json      newest stable Fabric Loader for that game version
#   assets/custom/mods/<GAME>/*.jar                          Fabric API + Meteor Client (installed on first start)
#   assets/custom/perf/<GAME>/*.jar                          performance mods (installed only if the user turns the option on in the settings)
#   assets/custom/skin/<GAME>/*.jar                          CustomSkinLoader (installed only when a local account gets a chosen skin)
# Meteor for 1.20.1-1.21.1 comes from the archive releases (SHA-256 pinned, both mirrors identical); newer versions are
# tried from Meteor's own snapshot repository and skipped when not available. A version that cannot be completed is skipped.
#   usage: build-mods.sh <assets-dir>
set -uo pipefail
A="$(mkdir -p "$1" && cd "$1" && pwd)"
UA="blockcomet-build (github.com/justaguyLV/effectcraft)"
mkdir -p "$A/custom/mods" "$A/custom/versions"
: > "$A/custom/bundled.txt"
WORK="$(mktemp -d)"

# game  meteor-version  sha256
STATIC=(
  "1.20.1 0.5.4 90d558f4d32bf96cc5464b869bc69c0f677a1be62e94cb897acf117c0bf82b97"
  "1.20.2 0.5.5 f957dd8364dfc66794b08f2bb3ebdceec822950dae67fb588e8daf7f4a9b7890"
  "1.20.4 0.5.6 0058cfe2b24392bb6d84a7cbae10c66ecadfcef47b31e7fa5ad0e8f0b4f71c99"
  "1.20.6 0.5.7 5de1f1d09664c1c8c60186a9385ae837b3d7c11e3ab919aa667eb7cf3f2ae3dc"
  "1.21.1 0.5.8 090ad8f02273a67eca753ef37ccee2256e4dd3d715501ed75433ec8f57d7c5e9"
)
MIRRORS=(
  "https://raw.githubusercontent.com/ManInMyVan/meteor-archive/main/files/meteor-client"
  "https://raw.githubusercontent.com/Galax71/meteor-archive-and-Baritone-archive/master/files/meteor-client"
)

warn() { echo "::warning title=BlockComet $1::$2"; }

# install_game <game> <meteor-jar>: loader JSON + Fabric API + performance mods + Meteor. Returns 1 (and cleans up) on failure.
install_game() {
  local game="$1" meteor="$2" modsdir="$A/custom/mods/$1" perfdir="$A/custom/perf/$1" skindir="$A/custom/skin/$1" id loader fapi json
  mkdir -p "$modsdir" "$perfdir" "$skindir"
  loader=$(curl -fsSL --retry 3 "https://meta.fabricmc.net/v2/versions/loader/$game" | python3 -c "
import sys, json
print(next(v['loader']['version'] for v in json.load(sys.stdin) if v['loader'].get('stable')))") || { warn "$game" "no stable Fabric Loader"; rm -rf "$modsdir" "$perfdir" "$skindir"; return 1; }
  curl -fsSL --retry 3 -o "$WORK/$game.json" "https://meta.fabricmc.net/v2/versions/loader/$game/$loader/profile/json" || { warn "$game" "no loader profile"; rm -rf "$modsdir" "$perfdir" "$skindir"; return 1; }
  id=$(python3 -c "import json;d=json.load(open('$WORK/$game.json'));assert d['inheritsFrom']=='$game';print(d['id'])") || { warn "$game" "bad loader profile"; rm -rf "$modsdir" "$perfdir" "$skindir"; return 1; }

  local M=https://maven.fabricmc.net/net/fabricmc/fabric-api/fabric-api
  fapi=$(curl -fsSL --retry 3 "$M/maven-metadata.xml" | grep -o "<version>[^<]*+$game</version>" | sed 's/<[^>]*>//g' | sort -V | tail -1)
  [ -n "$fapi" ] || { warn "$game" "no Fabric API"; rm -rf "$modsdir" "$perfdir" "$skindir"; return 1; }
  curl -fsSL --retry 3 -o "$modsdir/fabric-api-$fapi.jar" "$M/$fapi/fabric-api-$fapi.jar" \
    && echo "$(curl -fsSL --retry 3 "$M/$fapi/fabric-api-$fapi.jar.sha1")  $modsdir/fabric-api-$fapi.jar" | sha1sum -c - >/dev/null \
    || { warn "$game" "Fabric API download/verify failed"; rm -rf "$modsdir" "$perfdir" "$skindir"; return 1; }

  cp "$meteor" "$modsdir/$(basename "$meteor")"

  # performance mods (not Sodium: its OpenGL needs are risky on phones; chosen not to break Meteor's ESP/X-ray either): optional, newest release for this game version, sha512 checked against Modrinth's API
  local slug
  for slug in ferrite-core lithium modernfix krypton noisium memoryleakfix scalablelux customskinloader; do
    (
      set -e
      json=$(curl -fsSL --retry 3 -H "User-Agent: $UA" "https://api.modrinth.com/v2/project/$slug/version?game_versions=%5B%22$game%22%5D&loaders=%5B%22fabric%22%5D")
      read -r url name sha512 < <(printf '%s' "$json" | python3 -c "
import sys, json
versions = json.load(sys.stdin)
releases = [v for v in versions if v['version_type'] == 'release'] or versions
f = next(x for x in releases[0]['files'] if x['primary'])
print(f['url'], f['filename'], f['hashes']['sha512'])")
      dest="$perfdir"; [ "$slug" = customskinloader ] && dest="$skindir"
      curl -fsSL --retry 3 -o "$dest/$name" "$url"
      echo "$sha512  $dest/$name" | sha512sum -c - >/dev/null
    ) || echo "   (no $slug for $game)"
  done

  cp "$WORK/$game.json" "$A/custom/versions/$id.json"
  echo "$id" >> "$A/custom/bundled.txt"
  echo "== $game: $id, mods: $(ls "$modsdir" | tr '\n' ' ') perf: $(ls "$perfdir" | tr '\n' ' ') skin: $(ls "$skindir" | tr '\n' ' ')"
}

# 1) pinned releases
declare -A HAVE
for row in "${STATIC[@]}"; do
  read -r game ver sha <<<"$row"
  jar="$WORK/meteor-client-$ver.jar"; ok=0
  for m in "${MIRRORS[@]}"; do
    curl -fsSL --retry 3 -o "$jar" "$m/meteor-client-$ver.jar" && echo "$sha  $jar" | sha256sum -c - >/dev/null && { ok=1; break; }
  done
  [ "$ok" = 1 ] || { warn "$game" "Meteor $ver does not match the pinned SHA-256; skipped"; continue; }
  install_game "$game" "$jar" && HAVE[$game]=1
done

# 2) newer Minecraft versions: Meteor's own snapshot repository, newest stable first, at most 2 successes
got=0
for game in $(curl -fsSL --retry 3 https://meta.fabricmc.net/v2/versions/game | python3 -c "
import sys, json
print(' '.join(v['version'] for v in json.load(sys.stdin) if v.get('stable'))[:2000])" | tr ' ' '\n' | head -n 12); do
  [ "$game" = "1.21.1" ] && break
  [ -n "${HAVE[$game]:-}" ] && continue
  base="https://maven.meteordev.org/snapshots/meteordevelopment/meteor-client/${game}-SNAPSHOT"
  value=$(curl -fsSL --retry 2 "$base/maven-metadata.xml" 2>/dev/null | python3 -c "
import sys, xml.etree.ElementTree as ET
try:
    root = ET.fromstring(sys.stdin.read())
    for sv in root.iter('snapshotVersion'):
        if sv.findtext('extension') == 'jar' and not sv.findtext('classifier'):
            print(sv.findtext('value')); break
except Exception:
    pass") || value=""
  [ -n "$value" ] || { echo "-- $game: no Meteor snapshot"; continue; }
  jar="$WORK/meteor-$game.jar"
  curl -fsSL --retry 3 -o "$jar" "$base/meteor-client-$value.jar" || { echo "-- $game: snapshot download failed"; continue; }
  python3 -c "
import zipfile, json
m = json.loads(zipfile.ZipFile('$jar').read('fabric.mod.json'))
assert m['id'] == 'meteor-client'" 2>/dev/null || { warn "$game" "snapshot jar is not Meteor Client; skipped"; continue; }
  echo "   Meteor snapshot $value sha256 $(sha256sum "$jar" | cut -d' ' -f1)"
  install_game "$game" "$jar" && { HAVE[$game]=1; got=$((got + 1)); }
  [ "$got" -ge 2 ] && break
done

rm -rf "$WORK"
echo "---- bundled versions:"; cat "$A/custom/bundled.txt"
[ -s "$A/custom/bundled.txt" ] || { echo "no version could be prepared"; exit 1; }
du -sh "$A/custom"
