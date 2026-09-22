/**
 * Supabase JWT verification for Cloudflare Workers.
 * Uses the Web Crypto API (RS256) — no npm dependencies.
 *
 * Fetches JWKS from https://<project-ref>.supabase.co/auth/v1/.well-known/jwks.json
 * Caches the key material in KV for 1 hour to avoid JWKS rate limits.
 */

export interface AuthClaims {
  user_id: string;
  email?: string;
  role: string;
  aud: string;
  exp: number;
}

interface JwkKey {
  kid: string;
  kty: string;
  alg: string;
  n: string;
  e: string;
  use: string;
}

interface JwksResponse {
  keys: JwkKey[];
}

const JWKS_CACHE_TTL_SECONDS = 3600; // 1 hour
const JWKS_CACHE_KEY = "fitter:jwks_cache";

/**
 * Base64url decode to Uint8Array.
 */
function base64urlDecode(input: string): Uint8Array {
  const base64 = input.replace(/-/g, "+").replace(/_/g, "/");
  const padded = base64.padEnd(base64.length + ((4 - (base64.length % 4)) % 4), "=");
  const binary = atob(padded);
  const bytes = new Uint8Array(binary.length);
  for (let i = 0; i < binary.length; i++) {
    bytes[i] = binary.charCodeAt(i);
  }
  return bytes;
}

/**
 * Parse a JWT string into its three parts (header, payload, signature).
 * Returns null if malformed.
 */
function parseJwt(token: string): { header: any; payload: any; signingInput: string; signature: Uint8Array } | null {
  const parts = token.split(".");
  if (parts.length !== 3) return null;
  try {
    const header = JSON.parse(atob(parts[0].replace(/-/g, "+").replace(/_/g, "/")));
    // Payload needs padding before atob
    const payloadB64 = parts[1].replace(/-/g, "+").replace(/_/g, "/");
    const padded = payloadB64.padEnd(payloadB64.length + ((4 - (payloadB64.length % 4)) % 4), "=");
    const payload = JSON.parse(atob(padded));
    const signingInput = `${parts[0]}.${parts[1]}`;
    const signature = base64urlDecode(parts[2]);
    return { header, payload, signingInput, signature };
  } catch {
    return null;
  }
}

/**
 * Fetch and cache JWKS from Supabase.
 */
async function fetchJwks(supabaseUrl: string, kvCache: KVNamespace): Promise<JwksResponse> {
  // Try cache first
  const cached = await kvCache.get(JWKS_CACHE_KEY);
  if (cached) {
    try {
      return JSON.parse(cached) as JwksResponse;
    } catch {
      // cache corrupt, fall through to fetch
    }
  }

  const jwksUrl = `${supabaseUrl.replace(/\/$/, "")}/auth/v1/.well-known/jwks.json`;
  const resp = await fetch(jwksUrl, {
    headers: { "Accept": "application/json" },
  });
  if (!resp.ok) {
    throw new Error(`Failed to fetch JWKS from ${jwksUrl}: HTTP ${resp.status}`);
  }
  const jwks = (await resp.json()) as JwksResponse;

  // Cache for 1 hour
  await kvCache.put(JWKS_CACHE_KEY, JSON.stringify(jwks), { expirationTtl: JWKS_CACHE_TTL_SECONDS });
  return jwks;
}

/**
 * Import a JWK RSA public key for RS256 verification.
 */
async function importRsaPublicKey(jwk: JwkKey): Promise<CryptoKey> {
  return crypto.subtle.importKey(
    "jwk",
    {
      kty: jwk.kty,
      n: jwk.n,
      e: jwk.e,
      alg: "RS256",
      use: "sig",
    },
    { name: "RSASSA-PKCS1-v1_5", hash: "SHA-256" },
    false,
    ["verify"]
  );
}

/**
 * Verify a Supabase-issued JWT (RS256).
 * Returns AuthClaims on success.
 * Throws on invalid/expired/missing token.
 */
export async function verifySupabaseJwt(
  token: string,
  supabaseUrl: string,
  kvCache: KVNamespace
): Promise<AuthClaims> {
  const parsed = parseJwt(token);
  if (!parsed) {
    throw new Error("Malformed JWT");
  }

  const { header, payload, signingInput, signature } = parsed;

  // Check algorithm
  if (header.alg !== "RS256") {
    throw new Error(`Unsupported JWT algorithm: ${header.alg}`);
  }

  // Check expiry
  const now = Math.floor(Date.now() / 1000);
  if (typeof payload.exp !== "number" || payload.exp <= now) {
    throw new Error("JWT has expired");
  }

  // Check audience
  const aud = payload.aud;
  const audValues = Array.isArray(aud) ? aud : [aud];
  if (!audValues.includes("authenticated")) {
    throw new Error(`JWT audience mismatch: expected "authenticated", got ${JSON.stringify(aud)}`);
  }

  // Fetch JWKS and find matching key by kid
  const jwks = await fetchJwks(supabaseUrl, kvCache);
  const matchingKey = header.kid
    ? jwks.keys.find((k) => k.kid === header.kid)
    : jwks.keys[0];

  if (!matchingKey) {
    // If kid not found, purge cache and retry once
    await kvCache.delete(JWKS_CACHE_KEY);
    const freshJwks = await fetchJwks(supabaseUrl, kvCache);
    const retryKey = header.kid
      ? freshJwks.keys.find((k) => k.kid === header.kid)
      : freshJwks.keys[0];
    if (!retryKey) {
      throw new Error("No matching JWKS key found for JWT kid");
    }
  }

  const jwkKey = matchingKey || jwks.keys[0];
  const cryptoKey = await importRsaPublicKey(jwkKey);

  // Verify signature
  const encoder = new TextEncoder();
  const signingInputBytes = encoder.encode(signingInput);
  const valid = await crypto.subtle.verify(
    "RSASSA-PKCS1-v1_5",
    cryptoKey,
    signature,
    signingInputBytes
  );

  if (!valid) {
    throw new Error("JWT signature verification failed");
  }

  // Extract claims
  const userId: string = payload.sub || payload.user_id;
  if (!userId) {
    throw new Error("JWT missing sub/user_id claim");
  }

  return {
    user_id: userId,
    email: payload.email,
    role: payload.role || "authenticated",
    aud: audValues[0],
    exp: payload.exp,
  };
}

/**
 * Extract Bearer token from Authorization header.
 * Returns null if header absent or malformed.
 */
export function extractBearerToken(request: Request): string | null {
  const authHeader = request.headers.get("Authorization");
  if (!authHeader || !authHeader.startsWith("Bearer ")) {
    return null;
  }
  const token = authHeader.slice(7).trim();
  return token.length > 0 ? token : null;
}
