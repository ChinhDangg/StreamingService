FROM openresty/openresty:1.27.1.2-0-bookworm-fat

# Install git + luarocks to install lua-resty-http
RUN apt-get update && \
    apt-get install -y git luarocks && \
    luarocks install lua-resty-http && \
    apt-get clean && rm -rf /var/lib/apt/lists/*

# Expose port 80 (default)
EXPOSE 80