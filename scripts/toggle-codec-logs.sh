#!/bin/bash

# --- Functions ---

print_usage() {
    echo "Usage: $0 [enable|disable]"
    exit 1
}

# Check for adb
if ! command -v adb &> /dev/null; then
    echo "❌ adb not found. Please install Android Platform Tools and ensure adb is in your PATH."
    exit 1
fi

# Check for connected device
DEVICE_COUNT=$(adb devices | grep -w "device" | wc -l)
if [ "$DEVICE_COUNT" -eq 0 ]; then
    echo "❌ No device connected. Please connect a device and enable USB debugging."
    exit 1
fi

# --- Handle Argument ---

ACTION=$(echo "$1" | tr '[:upper:]' '[:lower:]')

case "$ACTION" in
    enable)
        echo "✅ Enabling native logs (DEBUG)..."
        adb shell setprop log.tag.MPEG4Writer D
        adb shell setprop log.tag.CCodec D
        adb shell setprop log.tag.VQApply D
        adb shell setprop log.tag.ColorUtils D
        adb shell setprop log.tag.MediaCodec D
        adb shell setprop log.tag.MediaCodecList D
        adb shell setprop log.tag.MediaWriter D
        adb shell setprop log.tag.CCodecConfig D
        adb shell setprop log.tag.Codec2Client D
        adb shell setprop log.tag.CCodecBufferChannel D
        adb shell setprop log.tag.CodecProperties D
        adb shell setprop log.tag.CodecSeeding D
        adb shell setprop log.tag.C2Store D
        adb shell setprop log.tag.C2NodeImpl D
        adb shell setprop log.tag.GraphicBufferSource D
        adb shell setprop log.tag.BufferQueueProducer D
        adb shell setprop log.tag.ReflectedParamUpdater D
        adb shell setprop log.tag.hw-BpHwBinder D
        echo "✅ Logs ENABLED"
        ;;
    disable)
        echo "🚫 Disabling native logs (SILENT)..."
        adb shell setprop log.tag.MPEG4Writer SILENT
        adb shell setprop log.tag.CCodec SILENT
        adb shell setprop log.tag.VQApply SILENT
        adb shell setprop log.tag.ColorUtils SILENT
        adb shell setprop log.tag.MediaCodec SILENT
        adb shell setprop log.tag.MediaCodecList SILENT
        adb shell setprop log.tag.MediaWriter SILENT
        adb shell setprop log.tag.CCodecConfig SILENT
        adb shell setprop log.tag.Codec2Client SILENT
        adb shell setprop log.tag.CCodecBufferChannel SILENT
        adb shell setprop log.tag.CodecProperties SILENT
        adb shell setprop log.tag.CodecSeeding SILENT
        adb shell setprop log.tag.C2Store SILENT
        adb shell setprop log.tag.C2NodeImpl SILENT
        adb shell setprop log.tag.GraphicBufferSource SILENT
        adb shell setprop log.tag.BufferQueueProducer SILENT
        adb shell setprop log.tag.ReflectedParamUpdater SILENT
        adb shell setprop log.tag.hw-BpHwBinder SILENT
        echo "🚫 Logs DISABLED"
        ;;
    *)
        echo "❓ Unknown or missing argument: '$1'"
        print_usage
        ;;
esac
