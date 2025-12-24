local pkey   = require "resty.openssl.pkey"
local digest = require "resty.openssl.digest"
local cjson  = require "cjson"
local http   = require "resty.http"


local CACHE_DICT_NAME = "jwks_cache"
local CACHE_KEY       = "JWKS_PEM"
local CACHE_TTL       = 86400 -- 24 hours in seconds

-----------------------------------------------------
-- Base64URL decoder
-----------------------------------------------------
local function b64url_decode(input)
    if not input then return nil end
    input = input:gsub("-", "+"):gsub("_", "/")
    local pad = #input % 4
    if pad > 0 then input = input .. string.rep("=", 4 - pad) end
    return ngx.decode_base64(input)
end

-----------------------------------------------------
-- JWK to PEM Converter (Pure Lua)
-- Converts raw modulus (n) and exponent (e) to PEM
-----------------------------------------------------
local function asn1_encode_len(n)
    if n < 128 then
        return string.char(n)
    end
    local s = ""
    while n > 0 do
        s = string.char(n % 256) .. s
        n = math.floor(n / 256)
    end
    return string.char(128 + #s) .. s
end

local function jwk_to_pem(n_b64, e_b64)
    local n = b64url_decode(n_b64)
    local e = b64url_decode(e_b64)

    if not n or not e then return nil, "Failed to decode n/e" end

    -- ASN.1 Integers must be positive. If the first bit is 1, prepend 0x00
    if string.byte(n, 1) >= 128 then n = "\0" .. n end
    if string.byte(e, 1) >= 128 then e = "\0" .. e end

    local function encode_int(data)
        return "\2" .. asn1_encode_len(#data) .. data
    end

    local function encode_seq(data)
        return "\48" .. asn1_encode_len(#data) .. data
    end

    -- RSAPublicKey = SEQUENCE { n INTEGER, e INTEGER }
    local rsa_key_seq = encode_seq(encode_int(n) .. encode_int(e))

    -- AlgorithmIdentifier = SEQUENCE { algorithm OID, parameters NULL }
    -- OID rsaEncryption: 1.2.840.113549.1.1.1
    local alg_id = "\48\13\6\9\42\134\72\134\247\13\1\1\1\5\0"

    -- PublicKeyInfo = SEQUENCE { algorithm, subjectPublicKey BIT STRING }
    local bit_string = "\3" .. asn1_encode_len(#rsa_key_seq + 1) .. "\0" .. rsa_key_seq
    local full_der = encode_seq(alg_id .. bit_string)

    local body = ngx.encode_base64(full_der)

    -- Wrap lines at 64 chars (standard PEM)
    local pem_body = body:gsub("(.{64})", "%1\n")
    if pem_body:sub(-1) ~= "\n" then pem_body = pem_body .. "\n" end

    return "-----BEGIN PUBLIC KEY-----\n" .. pem_body .. "-----END PUBLIC KEY-----\n"
end

-----------------------------------------------------
-- Fetch JWKS and Generate Public Key (PEM)
-----------------------------------------------------
local function get_jwks_pem()
    local httpc = http.new()
    httpc:set_timeout(2000)

    -- Note: Ensure host.docker.internal is resolvable by your Nginx resolver
    local res, err = httpc:request_uri("http://host.docker.internal:8084/.well-known/jwks.json", {
        method = "GET",
        ssl_verify = false,
        headers = { ["Content-Type"] = "application/json" }
    })

    if not res then
        ngx.log(ngx.ERR, "Failed to fetch JWKS: ", err)
        return nil
    end

    if res.status ~= 200 then
        ngx.log(ngx.ERR, "JWKS endpoint returned status: ", res.status)
        return nil
    end

    local status, data = pcall(cjson.decode, res.body)
    if not status then
        ngx.log(ngx.ERR, "Failed to parse JWKS JSON")
        return nil
    end

    -- Use the first key (simplify logic)
    local key = data.keys and data.keys[1]
    if not key or not key.n or not key.e then
        ngx.log(ngx.ERR, "Invalid JWKS format: missing keys/n/e")
        return nil
    end

    -- Convert JWK to PEM string manually
    return jwk_to_pem(key.n, key.e)
end

-----------------------------------------------------
-- LOAD KEY (Module Level)
-----------------------------------------------------
local function get_pem_from_cache()
    local dict = ngx.shared[CACHE_DICT_NAME]
    if not dict then
        ngx.log(ngx.ERR, "Shared dictionary '" .. CACHE_DICT_NAME .. "' not defined in nginx.conf")
        return nil
    end

    -- Try get from cache first
    local pem, _ = dict:get(CACHE_KEY)
    if pem then
        return pem
    end

    pem = get_jwks_pem()

    -- Store in Cache (safe_set handles out-of-memory errors gracefully)
    if pem then
        local ok, err, forcible = dict:safe_set(CACHE_KEY, pem, CACHE_TTL)
        if not ok then
            ngx.log(ngx.ERR, "Failed to update cache: ", err)
        end
        return pem
    end

    return nil
end


local PUBLIC_KEY_PEM = get_pem_from_cache()

-----------------------------------------------------
-- Extract Auth cookie
-----------------------------------------------------
local function get_jwt()
    local jwt = ngx.var.cookie_Auth
    if not jwt then
        ngx.log(ngx.ERR, "Auth cookie missing")
        return nil
    end
    return jwt:gsub("%s+", "")
end

-----------------------------------------------------
-- Verify RS256 JWT
-----------------------------------------------------
local function verify_jwt(jwt)
    if not PUBLIC_KEY_PEM then
        -- For now, just fail.
        ngx.log(ngx.ERR, "Public Key is missing (Fetch failed)")
        return false
    end

    local header_b64, payload_b64, sig_b64 =
        jwt:match("^([^.]+)%.([^.]+)%.([^.]+)$")

    if not header_b64 or not payload_b64 or not sig_b64 then
        ngx.log(ngx.ERR, "JWT split failed")
        return false
    end

    local signature = b64url_decode(sig_b64)
    if not signature then return false end

    local signing_input = header_b64 .. "." .. payload_b64

    local pub, err = pkey.new(PUBLIC_KEY_PEM)
    if not pub then
        ngx.log(ngx.ERR, "Cannot load public key: ", err)
        return false
    end

    local d = digest.new("SHA256")
    d:update(signing_input)

    local ok, err = pub:verify(signature, d)
    if not ok then
        ngx.log(ngx.ERR, "Signature verification FAILED: ", err)
        return false
    end

    return true
end

-----------------------------------------------------
-- ENTRY POINT
-----------------------------------------------------
local jwt = get_jwt()
if not jwt then
    return ngx.exit(401)
end

local ok = verify_jwt(jwt)
if not ok then
    return ngx.exit(401)
else
    ngx.log(ngx.INFO, "Signature verification PASSED")
end
