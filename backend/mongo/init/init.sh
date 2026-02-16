#!/bin/bash

# Assign arguments to variables
USER=$1
PASS=$2
AUTH_DB="admin" # Usually 'admin' for root users

# 1. Wait for MongoDB using credentials
# We use --authenticationDatabase to tell Mongo where the user is defined
until mongosh --host mongodb:27017 \
    -u "$USER" -p "$PASS" \
    --authenticationDatabase "$AUTH_DB" \
    --eval "db.adminCommand('ping')" &>/dev/null; do
  echo "Waiting for authenticated MongoDB to be ready..."
  sleep 2
done

echo "MongoDB is up! Initiating Replica Set..."

# 2. Initiate the Replica Set
# Note: In the rs.initiate, the 'host' should usually be the container name
# or the address the Java app uses. 'localhost:27017' works for single-node.
mongosh --host mongodb:27017 \
    -u "$USER" -p "$PASS" \
    --authenticationDatabase "$AUTH_DB" \
    --eval 'rs.initiate({_id:"rs0", members:[{_id:0, host:"localhost:27017"}]})'

echo "Replica set 'rs0' initialized successfully."