#!/bin/bash
# Build the app icons (macOS .icns, Windows .ico, window .png) from one square-ish source image.
# usage: tools/make_icons.sh path/to/logo.png [output-dir]   (default output: src/main/resources/icons, as "fedora.*")
# Needs macOS (sips, iconutil) and python3.
set -euo pipefail
SRC="${1:?usage: make_icons.sh logo.png [output-dir]}"
OUT="${2:-$(cd "$(dirname "$0")/.." && pwd)/src/main/resources/icons}"
NAME="fedora"   # the name build.gradle.kts and the window icon already use
TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT
mkdir -p "$OUT"

# 1. Pad to a square (a wide logo must not be stretched). sips pads with a solid colour, so give it a square PNG
#    with a transparent background if you want transparency around the logo.
W=$(sips -g pixelWidth "$SRC" | awk '/pixelWidth/{print $2}')
H=$(sips -g pixelHeight "$SRC" | awk '/pixelHeight/{print $2}')
S=$(( W > H ? W : H ))
sips -s format png -p "$S" "$S" --padColor 000000 "$SRC" --out "$TMP/square.png" >/dev/null

# 2. Every size the platforms ask for.
for s in 16 32 48 64 128 256 512 1024; do sips -z $s $s "$TMP/square.png" --out "$TMP/$s.png" >/dev/null; done

# 3. macOS .icns
ICONSET="$TMP/$NAME.iconset"; mkdir "$ICONSET"
cp "$TMP/16.png"   "$ICONSET/icon_16x16.png";      cp "$TMP/32.png"   "$ICONSET/icon_16x16@2x.png"
cp "$TMP/32.png"   "$ICONSET/icon_32x32.png";      cp "$TMP/64.png"   "$ICONSET/icon_32x32@2x.png"
cp "$TMP/128.png"  "$ICONSET/icon_128x128.png";    cp "$TMP/256.png"  "$ICONSET/icon_128x128@2x.png"
cp "$TMP/256.png"  "$ICONSET/icon_256x256.png";    cp "$TMP/512.png"  "$ICONSET/icon_256x256@2x.png"
cp "$TMP/512.png"  "$ICONSET/icon_512x512.png";    cp "$TMP/1024.png" "$ICONSET/icon_512x512@2x.png"
iconutil -c icns "$ICONSET" -o "$OUT/$NAME.icns"

# 4. Windows .ico (PNG-compressed entries, 16-256 px) and the window/Dock .png
python3 -I - "$TMP" "$OUT/$NAME.ico" <<'PY'
import struct, sys
tmp, out = sys.argv[1], sys.argv[2]
sizes = [16, 32, 48, 64, 128, 256]
blobs = [open(f"{tmp}/{s}.png", "rb").read() for s in sizes]
header = struct.pack("<HHH", 0, 1, len(sizes))
offset = 6 + 16 * len(sizes)
entries = b""
for s, b in zip(sizes, blobs):
    entries += struct.pack("<BBBBHHII", s % 256, s % 256, 0, 0, 1, 32, len(b), offset)
    offset += len(b)
open(out, "wb").write(header + entries + b"".join(blobs))
PY
cp "$TMP/512.png" "$OUT/$NAME.png"
echo "wrote $OUT/$NAME.icns $NAME.ico $NAME.png"
