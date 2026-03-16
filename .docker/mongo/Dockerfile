FROM mongo:7

# Copy the keyfile from your Windows machine into the image
COPY mongo-keyfile /data/configdb/mongo-keyfile

# Change ownership and permissions inside the Linux image
# 999 is the default ID for the 'mongodb' user
USER root
RUN chown 999:999 /data/configdb/mongo-keyfile && \
    chmod 400 /data/configdb/mongo-keyfile

# Switch back to the standard mongodb user
USER mongodb

# Set the command to use the internal keyfile
CMD ["mongod", "--replSet", "rs0", "--bind_ip_all", "--keyFile", "/data/configdb/mongo-keyfile"]