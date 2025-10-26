# Extend the official OpenResty image
FROM openresty/openresty:latest

# Install git + luarocks so we can install lua-resty-http
RUN apt-get update && \
    apt-get install -y git luarocks && \
    luarocks install lua-resty-http && \
    apt-get clean && rm -rf /var/lib/apt/lists/*

# Expose port 80 (default)
EXPOSE 80