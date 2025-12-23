FROM openresty/openresty:1.27.1.2-0-bookworm-fat

# Copy Lua scripts
# manual copy: docker cp nginx/lua/auth.lua nginx:/usr/local/openresty/nginx/lua
COPY lua /usr/local/openresty/nginx/lua/

RUN apt-get update && apt-get install -y \
        luarocks \
        gcc \
        make \
        libpcre3-dev \
        libssl-dev \
        git \
    && luarocks install lua-resty-http \
    && luarocks install lua-resty-jwt \
    && luarocks install lua-resty-lrucache \
    && luarocks install lua-resty-openssl \
    && apt-get clean \
    && rm -rf /var/lib/apt/lists/*

EXPOSE 80