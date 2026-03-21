#!/bin/sh

RAM_DISK_NAME="RAMDISK"

if [ ! -d "/Volumes/$RAM_DISK_NAME" ]; then
    : "${RAM_SIZE_BYTES:=2500000000}"

    SECTORS=$((RAM_SIZE_BYTES / 512))
    echo "Step 1: Attaching RAM device..."

    # Use 'awk' to grab ONLY the first column (the /dev/diskX part)
    # This removes all the trailing spaces and extra info
    RAW_DEVICE=$(hdiutil attach -nomount "ram://$SECTORS")
    DEVICE=$(echo "$RAW_DEVICE" | awk '{print $1}')

    if [ -n "$DEVICE" ]; then
        echo "Step 2: Erasing volume on $DEVICE and naming it $RAM_DISK_NAME..."
        # We use 'quiet' to avoid unnecessary output and ensure it works
        diskutil erasevolume HFS+ "$RAM_DISK_NAME" "$DEVICE"
    else
        echo "❌ Failed to capture device path from hdiutil."
        exit 1
    fi
fi

docker compose -f compose.mac.dev.yml --env-file .env.dev -p ss-dev up -d && echo "System is live!" || echo "❌ Failed to launch fleet."