#!/bin/sh
set -e

echo "Waiting for MinIO..."
sleep 5

echo "Configuring MinIO with mc..."

mc alias set local http://minio:9000 \
  "$MINIO_ROOT_USER" \
  "$MINIO_ROOT_PASSWORD"

#echo "Creating buckets (if missing) create too apply policies..."
mc mb -p local/video || true
mc mb -p local/image || true
mc mb -p local/audio || true
mc mb -p local/other || true
mc mb -p local/preview || true
mc mb -p local/thumbnail || true

echo "Applying anonymous read policies..."
mc anonymous set download local/video
mc anonymous set download local/image
mc anonymous set download local/audio
mc anonymous set download local/other
mc anonymous set download local/preview
mc anonymous set download local/thumbnail

echo "MinIO bootstrap completed."
