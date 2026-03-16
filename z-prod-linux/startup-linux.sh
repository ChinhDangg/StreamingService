#!/usr/bin/env bash
set -euo pipefail


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

RAM_VOLUME_NAME="${RAM_VOLUME:-/mnt/ramdisk}"
: "${RAM_SIZE_BYTES:=536870912}"

echo "Preparing RAM disk at $RAM_VOLUME_NAME"
# Ensure directory exists
sudo mkdir -p "$RAM_VOLUME_NAME"

# If something is already mounted there, unmount it
if mountpoint -q "$RAM_VOLUME_NAME"; then
    echo "Unmounting existing mount at $RAM_VOLUME_NAME..."
    sudo umount "$RAM_VOLUME_NAME"
fi

echo "Mounting tmpfs (${RAM_SIZE_BYTES}MB)..."
sudo mount -t tmpfs -o size="${RAM_SIZE_BYTES}" tmpfs "$RAM_VOLUME_NAME"

# Verify mount
if mountpoint -q "$RAM_VOLUME_NAME"; then
    echo "✅ RAM disk mounted at $RAM_VOLUME_NAME"
else
    echo "❌ Failed to mount RAM disk"
    exit 1
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
  -f "$SCRIPT_DIR/compose.linux.yml" \
  --env-file "$ENV_FILE" \
  up --build -d \
&& echo "System is live!" \
|| echo "❌ Failed to launch fleet."