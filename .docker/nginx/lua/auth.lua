local pkey   = require "resty.openssl.pkey"
local digest = require "resty.openssl.digest"
local cjson  = require "cjson.safe"
local http   = require "resty.http"

local CACHE_DICT_NAME = "jwks_cache"
local CACHE_TTL       = 86400 -- 24 hours

-----------------------------------------------------
-- Base64URL decoder
-----------------------------------------------------
local function b64url_decode(input)
    if not input then return nil end
    input = input:gsub("-", "+"):gsub("_", "/")
    local pad = #input % 4
    if pad > 0 then
        input = input .. string.rep("=", 4 - pad)
    end
    return ngx.decode_base64(input)
end

-----------------------------------------------------
-- Fetch and Cache JWKS Keys by `kid`
-----------------------------------------------------
local function fetch_and_cache_jwks(dict)
    local httpc = http.new()
    httpc:set_timeout(3000)

    local res, err = httpc:request_uri("http://host.docker.internal:8084/.well-known/jwks.json", {
        method = "GET",
        ssl_verify = false,
        headers = { ["Accept"] = "application/json" }
    })

    if not res or res.status ~= 200 then
        ngx.log(ngx.ERR, "Failed to fetch JWKS: ", err or ("HTTP " .. (res and res.status or "nil")))
        return nil
    end

    local data = cjson.decode(res.body)
    if not data or not data.keys then
        ngx.log(ngx.ERR, "Invalid JWKS format")
        return nil
    end

    -- Store each key by "jwk_kid:<kid>"
    for _, key in ipairs(data.keys) do
        if key.kid then
            dict:safe_set("jwk_kid:" .. key.kid, cjson.encode(key), CACHE_TTL)
        end
    end

    return data.keys
end

local function get_jwk_by_kid(kid)
    local dict = ngx.shared[CACHE_DICT_NAME]
    if not dict then
        ngx.log(ngx.ERR, "Shared dict '" .. CACHE_DICT_NAME .. "' not defined in nginx.conf")
        return nil
    end

    -- 1. Try Cache
    local cached_json = dict:get("jwk_kid:" .. kid)
    if cached_json then
        return cjson.decode(cached_json)
    end

    -- 2. Cache miss / Key rotation: fetch latest JWKS
    local keys = fetch_and_cache_jwks(dict)
    if not keys then return nil end

    for _, key in ipairs(keys) do
        if key.kid == kid then
            return key
        end
    end

    return nil
end

-----------------------------------------------------
-- Map JWT alg claim to OpenSSL Digest
-----------------------------------------------------
local function get_digest_name(alg)
    local map = {
        RS256 = "SHA256",
        RS384 = "SHA384",
        RS512 = "SHA512"
    }
    return map[alg]
end

-----------------------------------------------------
-- Verify JWT
-----------------------------------------------------
local function verify_jwt(jwt_str)
    local header_b64, payload_b64, sig_b64 = jwt_str:match("^([^.]+)%.([^.]+)%.([^.]+)$")
    if not header_b64 or not payload_b64 or not sig_b64 then
        return false, "Malformed JWT structure"
    end

    -- 1. Decode Header (to get 'kid' and 'alg')
    local header_json = b64url_decode(header_b64)
    local header = header_json and cjson.decode(header_json)
    if not header or not header.kid or not header.alg then
        return false, "Missing 'kid' or 'alg' in token header"
    end

    local hash_algo = get_digest_name(header.alg)
    if not hash_algo then
        return false, "Unsupported algorithm: " .. tostring(header.alg)
    end

    -- 2. Retrieve Matching JWK
    local jwk = get_jwk_by_kid(header.kid)
    if not jwk then
        return false, "No matching public key found for kid: " .. header.kid
    end

    -- 3. Construct Public Key directly using lua-resty-openssl
    local pub, err = pkey.new(jwk)
    if not pub then
        return false, "Failed to parse JWK into pkey: " .. tostring(err)
    end

    -- 4. Verify Signature
    local signature = b64url_decode(sig_b64)
    if not signature then
        return false, "Failed to decode signature"
    end

    local d = digest.new(hash_algo)
    d:update(header_b64 .. "." .. payload_b64)

    local ok, verify_err = pub:verify(signature, d)
    if not ok then
        return false, "Signature verification failed: " .. tostring(verify_err)
    end

    -- 5. Decode & Validate Payload Claims
    local payload_json = b64url_decode(payload_b64)
    local payload = payload_json and cjson.decode(payload_json)
    if not payload then
        return false, "Failed to decode payload JSON"
    end

    -- Check Expiration ('exp')
    local now = ngx.time()
    if payload.exp and payload.exp < now then
        return false, "Token has expired"
    end

    -- Check Not Before ('nbf')
    if payload.nbf and payload.nbf > now then
        return false, "Token is not active yet"
    end

    return true, payload
end

-----------------------------------------------------
-- Entry Point
-----------------------------------------------------
local raw_jwt = ngx.var.cookie_Auth
if not raw_jwt then
    ngx.status = 401
    ngx.say('{"error": "unauthorized", "message": "Missing Auth cookie"}')
    return ngx.exit(401)
end

local jwt_str = raw_jwt:gsub("%s+", "")
local valid, result = verify_jwt(jwt_str)

if not valid then
    ngx.log(ngx.WARN, "JWT validation failed: ", result)
    ngx.status = 401
    ngx.say('{"error": "unauthorized", "message": "' .. result .. '"}')
    return ngx.exit(401)
end

-- Validate user ID
local user_id = result.sub
if not user_id then
    ngx.status = 403
    ngx.say('{"error": "forbidden", "message": "Missing sub claim"}')
    return ngx.exit(403)
end

-- Export variable for downstream proxy_pass
ngx.var.jwt_user_id = user_id