#!/bin/sh
set -e

echo "Waiting for MinIO..."
sleep 5

echo "Configuring MinIO with mc..."

mc alias set local http://minio:9000 \
  "$MINIO_ROOT_USER" \
  "$MINIO_ROOT_PASSWORD"

echo "Creating buckets (if missing)..."
mc mb -p local/media || true
mc mb -p local/thumbnails || true

echo "Applying anonymous read policies..."
mc anonymous set download local/media
mc anonymous set download local/thumbnails

echo "MinIO bootstrap completed."
