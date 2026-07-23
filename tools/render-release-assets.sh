#!/bin/sh
set -eu

repository_root=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
asset_root="$repository_root/docs/release/assets"
chrome_binary="/Applications/Google Chrome.app/Contents/MacOS/Google Chrome"

if [ ! -x "$chrome_binary" ] || ! command -v sips >/dev/null 2>&1; then
    echo "Google Chrome and macOS sips are required." >&2
    exit 1
fi

mkdir -p "$asset_root/rendered"
temporary_directory=$(mktemp -d)
trap 'rm -rf "$temporary_directory"' EXIT

"$chrome_binary" --headless --disable-gpu --hide-scrollbars \
    --force-device-scale-factor=1 --window-size=512,512 \
    --screenshot="$asset_root/rendered/play-icon-512.png" \
    "file://$asset_root/source/play-icon-512.svg"

"$chrome_binary" --headless --disable-gpu --hide-scrollbars \
    --force-device-scale-factor=1 --window-size=1024,500 \
    --screenshot="$temporary_directory/feature.png" \
    "file://$asset_root/source/feature-graphic-1024x500.svg"

sips -s format jpeg -s formatOptions 95 "$temporary_directory/feature.png" \
    --out "$asset_root/rendered/feature-graphic-1024x500.jpg" >/dev/null

sips -g pixelWidth -g pixelHeight "$asset_root/rendered/play-icon-512.png"
sips -g pixelWidth -g pixelHeight "$asset_root/rendered/feature-graphic-1024x500.jpg"
