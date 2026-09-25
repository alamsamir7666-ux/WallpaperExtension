#!/usr/bin/env bash
# Fetches an android.jar for LOCAL d8 dexing (the --lib the packaging task
# passes to d8). Verification only: never packaged, never committed —
# .gitignore excludes tools/android.jar.
#
# Idempotent: exits immediately when a real Android SDK is present, and
# when tools/android.jar already exists. CI runners that ship an SDK never
# download anything.
#
# Order of sources:
#   1. Sable/android-platforms single-file mirror (fast)
#   2. Google's official platform zip (authoritative fallback)
# Any reasonably modern platform works — d8 uses it only to resolve library
# references while dexing, mirroring the host app's own packaging.

set -euo pipefail

for d in "${ANDROID_HOME:-}" "${ANDROID_SDK_ROOT:-}" "$HOME/Android/Sdk" /usr/local/lib/android/sdk /opt/android-sdk; do
  if [ -n "$d" ] && compgen -G "$d/platforms/android-*/android.jar" > /dev/null; then
    echo "android.jar found in SDK: $d — nothing to do."
    exit 0
  fi
done

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
DEST="$ROOT/tools/android.jar"
if [ -f "$DEST" ]; then
  echo "already present: $DEST — nothing to do."
  exit 0
fi
mkdir -p "$ROOT/tools"

SABLE_URL="https://github.com/Sable/android-platforms/raw/master/android-35/android.jar"
GOOGLE_URL="https://dl.google.com/android/repository/platform-35_r01.zip"

if curl -fsSL "$SABLE_URL" -o "$DEST"; then
  echo "fetched android.jar from the Sable mirror."
else
  echo "Sable mirror unavailable — falling back to the official Google platform zip."
  TMP="$(mktemp -d)"
  trap 'rm -rf "$TMP"' EXIT
  curl -fsSL "$GOOGLE_URL" -o "$TMP/platform.zip"
  python3 - "$TMP/platform.zip" "$DEST" <<'EOF'
import sys, zipfile

src, dest = sys.argv[1], sys.argv[2]
with zipfile.ZipFile(src) as archive:
    entry = next(n for n in archive.namelist() if n.endswith("android.jar"))
    with open(dest, "wb") as out:
        out.write(archive.read(entry))
EOF
  echo "extracted android.jar from the Google platform zip."
fi

echo "sha256: $(sha256sum "$DEST" | cut -d' ' -f1)"
