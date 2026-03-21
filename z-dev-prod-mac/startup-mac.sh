#!/usr/bin/env bash
set -euo pipefail

# /bin/sh ./z-dev-prod-mac/startup-mac.sh --env mac
############################################
# Parsing arguments
############################################

ENV_SUFFIX=""
SKIP_BUILD=false

# Parse arguments
while [[ $# -gt 0 ]]; do
  case "$1" in
    --env)
      ENV_SUFFIX="$2"
      shift 2
      ;;
    --skip-build)
      SKIP_BUILD=true
      shift
      ;;
    *)
      echo "❌ Unknown argument: $1"
      exit 1
      ;;
  esac
done


############################################
# Resolve directories
############################################

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"

############################################
# Resolve env file
############################################

if [[ -z "$ENV_SUFFIX" ]]; then
  ENV_FILE="$SCRIPT_DIR/.env"
else
  ENV_FILE="$SCRIPT_DIR/.env.$ENV_SUFFIX"
fi

if [[ ! -f "$ENV_FILE" ]]; then
  echo "❌ Env file not found: $ENV_FILE"
  exit 1
fi

echo "Loading env: $ENV_FILE"

# be careful as env variable is loaded - any same name used in bash script afterward will overwrite the env - use unique name
set -a
source "$ENV_FILE"
set +a

RAM_DISK_NAME="${RAM_VOLUME_NAME}"

if [ ! -d "/Volumes/$RAM_DISK_NAME" ]; then
    : "${RAM_SIZE_BYTES:=536870912}"

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


############################################
# Build project
############################################

if [ "$SKIP_BUILD" = true ]; then
    echo "Skipping Maven build..."
else
    echo "Building Maven project..."

    cd "$ROOT_DIR"

    # -DskipTests makes it fast
    # -T 1C uses one thread per CPU core
    ./mvnw clean install -DskipTests -T 1C

    if [ $? -ne 0 ]; then
        echo "❌ Maven build failed! Fix the code and try again."
        exit 1
    fi
fi

############################################
# Start docker
############################################

docker compose \
  -f "$SCRIPT_DIR/compose.mac.yml" \
  --env-file "$ENV_FILE" \
  up --build -d \
&& echo "System is live!" \
|| echo "❌ Failed to launch fleet."


# docker compose -f compose.mac.yml --env-file "$ENV_FILE" up --build -d && echo "System is live!" || echo "❌ Failed to launch fleet."