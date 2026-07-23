#!/bin/sh
set -eu

if [ "$#" -ne 2 ]; then
    echo "Usage: $0 <phone|tablet7|tablet10> <shot-name>" >&2
    exit 2
fi

profile=$1
shot_name=$2
case "$profile" in
    phone|tablet7|tablet10) ;;
    *) echo "Unsupported profile: $profile" >&2; exit 2 ;;
esac

case "$shot_name" in
    *[!a-z0-9-]*|"") echo "Shot name must use lowercase letters, digits, and hyphens." >&2; exit 2 ;;
esac

adb get-state >/dev/null
repository_root=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
output_directory="$repository_root/docs/release/screenshots/$profile"
mkdir -p "$output_directory"

power_state=$(adb shell dumpsys power | tr -d '\r' | sed -n 's/.*mWakefulness=//p' | head -1)
if [ "$power_state" != "Awake" ]; then
    echo "Device must be awake and unlocked before capture (state: $power_state)." >&2
    exit 1
fi

adb shell wm size >"$output_directory/device-size.txt"
adb shell wm density >"$output_directory/device-density.txt"
adb exec-out screencap -p >"$output_directory/$shot_name.png"
echo "Captured $output_directory/$shot_name.png"
