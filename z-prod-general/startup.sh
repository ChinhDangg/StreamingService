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
  -f "$SCRIPT_DIR/compose.yml" \
  --env-file "$ENV_FILE" \
  up --build -d \
&& echo "System is live!" \
|| echo "❌ Failed to launch fleet."