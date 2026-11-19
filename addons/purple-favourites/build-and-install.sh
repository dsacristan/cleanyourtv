#!/usr/bin/env bash
# Build "Favoritos", install it on the TV and switch its accessibility service on, which is the half that
# takes the remote's Netflix button and presses the player's own Favoritos tile.
#
# Usage: TV_HOST=<tv ip or host:port> addons/purple-favourites/build-and-install.sh
#        (add ADB=/path/to/adb when the TV's debugging is the encrypted wireless one and needs the paired adb)
set -euo pipefail
HERE="$(cd "$(dirname "$0")" && pwd)"
: "${TV_HOST:?set TV_HOST to the TV address, e.g. TV_HOST=192.168.1.50 $0}"
ADB="${ADB:-adb}"
PKG=com.pikita.purplefavs
SERVICE="$PKG/.FavKeyService"

(cd "$HERE" && ./gradlew assembleRelease -q) || {
    echo "build failed (JDK 17 and Android SDK 34 needed; see launcher/README)" >&2; exit 1; }

APK="$HERE/app/build/outputs/apk/release/app-release.apk"
"$ADB" connect "$TV_HOST" >/dev/null 2>&1 || true
"$ADB" -s "$TV_HOST" install -r "$APK"

# Append, never replace: the launcher's own key helper is in that list and taking it out kills Home's buttons.
"$ADB" -s "$TV_HOST" shell "cur=\$(settings get secure enabled_accessibility_services); \
  case \":\$cur:\" in *:$SERVICE:*) ;; *) settings put secure enabled_accessibility_services \
  \"\${cur:+$cur:}$SERVICE\" ;; esac; settings put secure accessibility_enabled 1; \
  settings get secure enabled_accessibility_services"

echo "Installed. The Netflix button now opens Purple IPTV on Favoritos."
