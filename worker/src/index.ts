import { verifySupabaseJwt, extractBearerToken, type AuthClaims } from "./auth.ts";

// ─── Environment ─────────────────────────────────────────────────────────────

export interface Env {
  VLM_CACHE: KVNamespace;
  // VLM provider keys (Worker secrets — never in mobile app)
  GEMINI_API_KEY?: string;
  OPENROUTER_API_KEY?: string;
  GROQ_API_KEY?: string;
  // Supabase (Worker secrets — service_role key never ships to app)
  SUPABASE_URL?: string;
  SUPABASE_SERVICE_ROLE_KEY?: string;
  // RevenueCat webhook verification
  REVENUECAT_WEBHOOK_SECRET?: string;
  // Operational flags
  FITTER_KILL_SWITCH?: string;
  KILL_SWITCH?: string;
  // "dev" → mock responses allowed when keys missing; any other value → 500 on missing keys
  FITTER_ENV?: string;
  // Phase 9: Provider routing
  // Primary VLM provider: "openrouter" (default) | "gemini" | "groq" | "mock"
  VLM_PRIMARY_PROVIDER?: string;
  // Allow fallback to secondary providers when primary fails: "true" (default) | "false"
  VLM_ALLOW_FALLBACK?: string;
}

// ─── Data shapes ─────────────────────────────────────────────────────────────

export interface FoodItem {
  item: string;
  weight_est_g: number;
  calories: number;
  protein_g: number;
  carbs_g: number;
  fat_g: number;
  confidence: string;
}

export interface Totals {
  calories: number;
  protein_g: number;
  carbs_g: number;
  fat_g: number;
}

export interface NutritionResponse {
  meal_name: string;
  items: FoodItem[];
  totals: Totals;
  estimation_notes: string;
}

// ─── Constants ────────────────────────────────────────────────────────────────

const SYSTEM_PROMPT = `You are a professional nutritionist. Analyze the food in this image.
Return ONLY a valid JSON object — no prose, no markdown fences, no explanation.
Use this exact structure:
{
  "meal_name": "string",
  "items": [
    {
      "item": "string",
      "weight_est_g": number,
      "calories": number,
      "protein_g": number,
      "carbs_g": number,
      "fat_g": number,
      "confidence": "high" | "medium" | "low"
    }
  ],
  "totals": {
    "calories": number,
    "protein_g": number,
    "carbs_g": number,
    "fat_g": number
  },
  "estimation_notes": "string"
}`;

const CORS_HEADERS = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Methods": "GET, POST, OPTIONS",
  "Access-Control-Allow-Headers": "Content-Type, Authorization, x-device-id, x-device-attestation",
};

// Max daily_allowance the client may claim (prevents a modified client sending 999)
const MAX_DAILY_ALLOWANCE = 5;

// ─── Helpers ──────────────────────────────────────────────────────────────────

async function sha256(message: string): Promise<string> {
  const msgUint8 = new TextEncoder().encode(message);
  const hashBuffer = await crypto.subtle.digest("SHA-256", msgUint8);
  const hashArray = Array.from(new Uint8Array(hashBuffer));
  return hashArray.map((b) => b.toString(16).padStart(2, "0")).join("");
}

function cleanJson(raw: string): string {
  let clean = raw.trim();
  if (clean.startsWith("```json")) clean = clean.substring(7);
  else if (clean.startsWith("```")) clean = clean.substring(3);
  if (clean.endsWith("```")) clean = clean.substring(0, clean.length - 3);
  return clean.trim();
}

function jsonResponse(body: unknown, status: number): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { "Content-Type": "application/json", ...CORS_HEADERS },
  });
}

function err401(message = "Authentication required"): Response {
  return new Response(JSON.stringify({ error: "Unauthorized", message }), {
    status: 401,
    headers: {
      "Content-Type": "application/json",
      // WWW-Authenticate: Bearer — RFC 6750; X-Debug-Code lets the mobile client
      // distinguish a real 401 from a proxy error and trigger deterministic re-auth.
      "WWW-Authenticate": "Bearer",
      "X-Debug-Code": "auth_required",
      ...CORS_HEADERS,
    },
  });
}

function err402Quota(): Response {
  return jsonResponse(
    { error: "quota_exhausted", message: "Daily scan quota exceeded. Upgrade to Fitter Premium for unlimited scans." },
    402
  );
}

function validateAndFormatNutritionResponse(parsed: any): NutritionResponse {
  if (!parsed || typeof parsed !== "object") {
    throw new Error("Invalid response format from AI provider");
  }

  const items: FoodItem[] = Array.isArray(parsed.items)
    ? parsed.items.map((item: any) => ({
        item: String(item.item || "Unknown Item"),
        weight_est_g: Math.round(Number(item.weight_est_g) || 100),
        calories: Math.round(Number(item.calories) || 0),
        protein_g: parseFloat(Number(item.protein_g || 0).toFixed(1)),
        carbs_g: parseFloat(Number(item.carbs_g || 0).toFixed(1)),
        fat_g: parseFloat(Number(item.fat_g || 0).toFixed(1)),
        confidence: String(item.confidence || "medium"),
      }))
    : [];

  const rawTotals = parsed.totals || {};
  const calculatedCalories = items.reduce((acc, it) => acc + it.calories, 0);
  const calculatedProtein = items.reduce((acc, it) => acc + it.protein_g, 0);
  const calculatedCarbs = items.reduce((acc, it) => acc + it.carbs_g, 0);
  const calculatedFat = items.reduce((acc, it) => acc + it.fat_g, 0);

  return {
    meal_name: String(parsed.meal_name || "Meal"),
    items,
    totals: {
      calories: Math.round(Number(rawTotals.calories) || calculatedCalories),
      protein_g: parseFloat(Number(rawTotals.protein_g ?? calculatedProtein).toFixed(1)),
      carbs_g: parseFloat(Number(rawTotals.carbs_g ?? calculatedCarbs).toFixed(1)),
      fat_g: parseFloat(Number(rawTotals.fat_g ?? calculatedFat).toFixed(1)),
    },
    estimation_notes: String(parsed.estimation_notes || "Estimated by Fitter AI Gateway"),
  };
}

// ─── Auth middleware ──────────────────────────────────────────────────────────

/**
 * Verifies the Supabase JWT from the Authorization header.
 * Returns AuthClaims on success, or a Response (401) on failure.
 */
async function authenticate(request: Request, env: Env): Promise<AuthClaims | Response> {
  if (!env.SUPABASE_URL) {
    console.error("SUPABASE_URL env var not set on Worker");
    return jsonResponse({ error: "Gateway misconfigured", message: "Missing SUPABASE_URL" }, 500);
  }

  const token = extractBearerToken(request);
  if (!token) {
    return err401("Missing Authorization: Bearer <token> header");
  }

  try {
    const claims = await verifySupabaseJwt(token, env.SUPABASE_URL, env.VLM_CACHE);
    return claims;
  } catch (e: any) {
    console.warn("JWT verification failed:", e.message);
    return err401(`Invalid token: ${e.message}`);
  }
}

// ─── Rate limiting ────────────────────────────────────────────────────────────

/**
 * Rate limit keyed by user_id (primary, 20 req/min per user).
 */
async function enforceRateLimit(
  env: Env,
  userId: string
): Promise<{ allowed: boolean; retryAfter?: number }> {
  if (!env.VLM_CACHE) return { allowed: true };

  const now = Date.now();
  const currentMinute = Math.floor(now / 60000);
  const key = `vlm:rl:user:${userId}:${currentMinute}`;
  const countStr = await env.VLM_CACHE.get(key);
  const currentCount = countStr ? parseInt(countStr, 10) : 0;

  if (currentCount >= 20) {
    const secondsRemaining = 60 - Math.floor((now % 60000) / 1000);
    return { allowed: false, retryAfter: secondsRemaining };
  }

  await env.VLM_CACHE.put(key, String(currentCount + 1), { expirationTtl: 120 });
  return { allowed: true };
}

// ─── Entitlement check ────────────────────────────────────────────────────────

/**
 * Check if the user has an active Fitter Premium entitlement.
 * ONLY reads from KV — never trusts any client-supplied header.
 */
async function isUserPremium(env: Env, userId: string): Promise<boolean> {
  if (!env.VLM_CACHE) return false;
  const val = await env.VLM_CACHE.get(`entitlement:${userId}:fitter_premium`);
  return val === "active";
}

// ─── Server-side quota (service_role RPC) ─────────────────────────────────────

/**
 * Check whether the user has remaining scan quota WITHOUT incrementing `used`.
 * Respects Week-1 onboarding (5 scans/day if first_install_date < 7 days ago).
 */
async function checkQuotaServerSide(
  env: Env,
  userId: string,
  allowance: number
): Promise<{ allowed: boolean; effectiveAllowance: number }> {
  if (!env.SUPABASE_URL || !env.SUPABASE_SERVICE_ROLE_KEY) {
    throw new Error("SUPABASE_URL / SUPABASE_SERVICE_ROLE_KEY not configured on gateway");
  }

  const url = `${env.SUPABASE_URL.replace(/\/$/, "")}/rest/v1/rpc/get_scan_quota`;
  const resp = await fetch(url, {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
      "Accept-Profile": "fitter",
      "Content-Profile": "fitter",
      "apikey": env.SUPABASE_SERVICE_ROLE_KEY,
      "Authorization": `Bearer ${env.SUPABASE_SERVICE_ROLE_KEY}`,
    },
    body: JSON.stringify({ p_user_id: userId, p_allowance: allowance }),
  });

  if (!resp.ok) {
    const errText = await resp.text();
    throw new Error(`get_scan_quota RPC failed (${resp.status}): ${errText}`);
  }

  const data: any = await resp.json();
  if (typeof data === "boolean") {
    return { allowed: data, effectiveAllowance: allowance };
  }

  let effectiveAllowance = allowance;
  if (data && typeof data.first_install_date === "string") {
    const installMs = Date.parse(data.first_install_date);
    if (!Number.isNaN(installMs) && Date.now() - installMs < 7 * 86400 * 1000) {
      effectiveAllowance = Math.max(allowance, MAX_DAILY_ALLOWANCE);
    }
  }

  const used = typeof data?.used === "number" ? data.used : 0;
  const bonus = typeof data?.bonus === "number" ? data.bonus : 0;
  const remaining =
    typeof data?.used === "number"
      ? Math.max(0, effectiveAllowance + bonus - used)
      : typeof data?.remaining === "number"
      ? data.remaining
      : effectiveAllowance;

  return { allowed: remaining > 0, effectiveAllowance };
}

/**
 * Consume one scan on the server via service_role.
 * Returns true if within allowance, false if exhausted.
 * Throws on network/DB error (caller should return 500).
 */
async function consumeScanServerSide(
  env: Env,
  userId: string,
  allowance: number
): Promise<boolean> {
  if (!env.SUPABASE_URL || !env.SUPABASE_SERVICE_ROLE_KEY) {
    throw new Error("SUPABASE_URL / SUPABASE_SERVICE_ROLE_KEY not configured on gateway");
  }

  const url = `${env.SUPABASE_URL.replace(/\/$/, "")}/rest/v1/rpc/consume_scan`;
  const resp = await fetch(url, {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
      "Accept-Profile": "fitter",
      "Content-Profile": "fitter",
      "apikey": env.SUPABASE_SERVICE_ROLE_KEY,
      "Authorization": `Bearer ${env.SUPABASE_SERVICE_ROLE_KEY}`,
    },
    body: JSON.stringify({ p_user_id: userId, p_allowance: allowance }),
  });

  if (!resp.ok) {
    const errText = await resp.text();
    throw new Error(`consume_scan RPC failed (${resp.status}): ${errText}`);
  }

  const result = await resp.json();
  // Postgres function returns boolean; PostgREST wraps scalar in the response
  if (typeof result === "boolean") return result;
  if (Array.isArray(result) && result.length > 0) return Boolean(result[0]);
  return Boolean(result);
}

// ─── RevenueCat webhook signature verification ────────────────────────────────

async function verifyRevenueCatSignature(
  request: Request,
  body: string,
  secret: string
): Promise<boolean> {
  const sig = request.headers.get("X-RevenueCat-Signature") || "";
  if (!sig) return false;
  try {
    const encoder = new TextEncoder();
    const key = await crypto.subtle.importKey(
      "raw",
      encoder.encode(secret),
      { name: "HMAC", hash: "SHA-256" },
      false,
      ["verify"]
    );
    // RevenueCat v2 uses HMAC-SHA256 of the raw body
    const sigBytes = base64ToUint8Array(sig);
    return crypto.subtle.verify("HMAC", key, sigBytes, encoder.encode(body));
  } catch {
    return false;
  }
}

function base64ToUint8Array(b64: string): Uint8Array {
  const binary = atob(b64);
  const bytes = new Uint8Array(binary.length);
  for (let i = 0; i < binary.length; i++) bytes[i] = binary.charCodeAt(i);
  return bytes;
}

// ─── VLM provider calls ───────────────────────────────────────────────────────

async function callGemini(
  env: Env,
  prompt: string,
  base64Image?: string,
  model = "gemini-2.5-flash"
): Promise<NutritionResponse> {
  const apiKey = env.GEMINI_API_KEY;
  if (!apiKey) throw new Error("GEMINI_API_KEY not configured on gateway");

  const parts: any[] = [{ text: prompt }];
  if (base64Image) {
    parts.push({ inlineData: { mimeType: "image/jpeg", data: base64Image } });
  }

  const payload = {
    contents: [{ parts }],
    systemInstruction: { parts: [{ text: SYSTEM_PROMPT }] },
    generationConfig: { responseMimeType: "application/json", temperature: 0.2, maxOutputTokens: 4096 },
  };

  // HARD RULE: x-goog-api-key header — NEVER put key in URL
  const response = await fetch(
    `https://generativelanguage.googleapis.com/v1beta/models/${model}:generateContent`,
    {
      method: "POST",
      headers: { "Content-Type": "application/json", "x-goog-api-key": apiKey },
      body: JSON.stringify(payload),
    }
  );

  if (!response.ok) {
    const errorText = await response.text();
    throw new Error(`Gemini API error (${response.status}): ${errorText}`);
  }

  const data: any = await response.json();
  const text = data.candidates?.[0]?.content?.parts?.[0]?.text;
  if (!text) throw new Error("Empty response from Gemini");
  return validateAndFormatNutritionResponse(JSON.parse(cleanJson(text)));
}

async function callOpenRouter(
  env: Env,
  prompt: string,
  base64Image?: string,
  // Default: google/gemma-4-31b-it:free — multimodal, 256K ctx, free tier.
  // Replaces defunct qwen/qwen-2.5-vl-72b-instruct:free.
  model = "google/gemma-4-31b-it:free"
): Promise<NutritionResponse> {
  const apiKey = env.OPENROUTER_API_KEY;
  if (!apiKey) throw new Error("OPENROUTER_API_KEY not configured on gateway");

  const content: any[] = [{ type: "text", text: `${SYSTEM_PROMPT}\n\n${prompt}` }];
  if (base64Image) {
    content.push({ type: "image_url", image_url: { url: `data:image/jpeg;base64,${base64Image}` } });
  }

  const response = await fetch("https://openrouter.ai/api/v1/chat/completions", {
    method: "POST",
    headers: { "Content-Type": "application/json", Authorization: `Bearer ${apiKey}` },
    body: JSON.stringify({ model, messages: [{ role: "user", content }], max_tokens: 1000 }),
  });

  if (!response.ok) {
    const errorText = await response.text();
    throw new Error(`OpenRouter error (${response.status}): ${errorText}`);
  }

  const data: any = await response.json();
  const text = data.choices?.[0]?.message?.content;
  if (!text) throw new Error("Empty response from OpenRouter");
  return validateAndFormatNutritionResponse(JSON.parse(cleanJson(text)));
}

async function callGroq(
  env: Env,
  prompt: string,
  base64Image?: string,
  model = "llama-3.2-11b-vision-preview"
): Promise<NutritionResponse> {
  const apiKey = env.GROQ_API_KEY;
  if (!apiKey) throw new Error("GROQ_API_KEY not configured on gateway");

  const content: any[] = [{ type: "text", text: `${SYSTEM_PROMPT}\n\n${prompt}` }];
  if (base64Image) {
    content.push({ type: "image_url", image_url: { url: `data:image/jpeg;base64,${base64Image}` } });
  }

  const response = await fetch("https://api.groq.com/openai/v1/chat/completions", {
    method: "POST",
    headers: { "Content-Type": "application/json", Authorization: `Bearer ${apiKey}` },
    body: JSON.stringify({ model, messages: [{ role: "user", content }], max_tokens: 1000 }),
  });

  if (!response.ok) {
    const errorText = await response.text();
    throw new Error(`Groq error (${response.status}): ${errorText}`);
  }

  const data: any = await response.json();
  const text = data.choices?.[0]?.message?.content;
  if (!text) throw new Error("Empty response from Groq");
  return validateAndFormatNutritionResponse(JSON.parse(cleanJson(text)));
}

/**
 * VLM failover with environment-driven provider priority.
 *
 * Phase 9 spec:
 * - env.VLM_PRIMARY_PROVIDER: "openrouter" (default) | "gemini" | "groq" | "mock"
 * - env.VLM_ALLOW_FALLBACK:   "true" (default) | "false"
 *
 * For "openrouter" primary:
 *   1. Call callOpenRouter() FIRST for /v1/analyze-meal and /v1/recalculate.
 *   2. requestedModel/model_hint passes through to OpenRouter's model param.
 *      Invalid hint → OpenRouter default; never silently switch providers.
 *   3. If OpenRouter throws AND VLM_ALLOW_FALLBACK=true → try Gemini → Groq.
 *   4. If OpenRouter key missing AND VLM_ALLOW_FALLBACK=true → skip to Gemini.
 *   5. If VLM_ALLOW_FALLBACK=false → throw immediately on primary failure.
 *
 * Returns { result, provider } where provider is the name that succeeded.
 */
async function executeVlmFailover(
  env: Env,
  prompt: string,
  base64Image?: string,
  requestedModel?: string
): Promise<{ result: NutritionResponse; provider: string }> {
  const primaryProvider = (env.VLM_PRIMARY_PROVIDER ?? "openrouter").toLowerCase().trim();
  const allowFallback = (env.VLM_ALLOW_FALLBACK ?? "true").toLowerCase().trim() !== "false";

  console.log(`[Provider] primary=${primaryProvider} model=${requestedModel ?? "default"} fallback=${allowFallback}`);

  const errors: string[] = [];

  // ── Helper: try a named provider ─────────────────────────────────────────────
  async function tryProvider(name: string): Promise<{ result: NutritionResponse; provider: string } | null> {
    if (name === "openrouter") {
      if (!env.OPENROUTER_API_KEY) {
        // Key missing — not an error of the provider itself, just skip
        console.warn("[Provider] OpenRouter key missing — skipping");
        return null;
      }
      try {
        // Pass model hint through to OpenRouter; callOpenRouter uses its own default if undefined
        const result = await callOpenRouter(env, prompt, base64Image, requestedModel);
        return { result, provider: "openrouter" };
      } catch (err: any) {
        console.warn("[Provider] OpenRouter failed:", err.message);
        errors.push(`OpenRouter: ${err.message}`);
        return null;
      }
    }

    if (name === "gemini") {
      if (!env.GEMINI_API_KEY) {
        console.warn("[Provider] Gemini key missing — skipping");
        return null;
      }
      try {
        // For gemini primary, pass model hint only when the primary itself is gemini.
        // For gemini fallback, always use the safe default model.
        const model = primaryProvider === "gemini" && requestedModel ? requestedModel : "gemini-2.5-flash";
        const result = await callGemini(env, prompt, base64Image, model);
        return { result, provider: "gemini" };
      } catch (err: any) {
        console.warn("[Provider] Gemini failed:", err.message);
        errors.push(`Gemini: ${err.message}`);
        return null;
      }
    }

    if (name === "groq") {
      if (!env.GROQ_API_KEY) {
        console.warn("[Provider] Groq key missing — skipping");
        return null;
      }
      try {
        const result = await callGroq(env, prompt, base64Image);
        return { result, provider: "groq" };
      } catch (err: any) {
        console.warn("[Provider] Groq failed:", err.message);
        errors.push(`Groq: ${err.message}`);
        return null;
      }
    }

    return null;
  }

  // ── Build provider execution order ───────────────────────────────────────────
  // Primary first; fallback order is the remaining providers.
  const fallbackOrder: string[] = ["openrouter", "gemini", "groq"].filter((p) => p !== primaryProvider);

  // ── Try primary ───────────────────────────────────────────────────────────────
  if (primaryProvider === "mock") {
    // "mock" primary only allowed in dev mode
    if (env.FITTER_ENV !== "dev") {
      throw new Error('VLM_PRIMARY_PROVIDER="mock" requires FITTER_ENV=dev');
    }
    return { result: devMockResponse(), provider: "mock" };
  }

  const primaryResult = await tryProvider(primaryProvider);
  if (primaryResult) return primaryResult;

  // ── Primary failed or key missing ────────────────────────────────────────────
  if (!allowFallback) {
    throw new Error(
      `VLM primary provider "${primaryProvider}" failed and VLM_ALLOW_FALLBACK=false. Errors: ${errors.join("; ")}`
    );
  }

  // ── Try fallbacks in order ────────────────────────────────────────────────────
  for (const fallback of fallbackOrder) {
    const fallbackResult = await tryProvider(fallback);
    if (fallbackResult) {
      console.log(`[Provider] Fell back to ${fallback} after ${primaryProvider} failed`);
      return fallbackResult;
    }
  }

  // ── Mock ONLY in dev mode (last resort when no keys are configured) ───────────
  if (env.FITTER_ENV === "dev") {
    console.log("[DEV MODE] No VLM keys configured — returning mock response");
    return { result: devMockResponse(), provider: "mock" };
  }

  throw new Error(`All VLM providers failed: ${errors.join("; ")}`);
}

function devMockResponse(): NutritionResponse {
  return {
    meal_name: "Sample Balanced Meal (Gateway Dev Mock)",
    items: [
      { item: "Grilled Chicken Breast", weight_est_g: 150, calories: 248, protein_g: 46.5, carbs_g: 0.0, fat_g: 5.4, confidence: "high" },
      { item: "Brown Rice", weight_est_g: 150, calories: 168, protein_g: 3.9, carbs_g: 35.7, fat_g: 1.4, confidence: "high" },
      { item: "Steamed Broccoli", weight_est_g: 100, calories: 35, protein_g: 2.4, carbs_g: 7.2, fat_g: 0.4, confidence: "high" },
    ],
    totals: { calories: 451, protein_g: 52.8, carbs_g: 42.9, fat_g: 7.2 },
    estimation_notes: "Generated by Fitter Gateway (Dev Mock — FITTER_ENV=dev)",
  };
}


// ─── Main handler ─────────────────────────────────────────────────────────────

export default {
  async fetch(request: Request, env: Env): Promise<Response> {
    const url = new URL(request.url);

    // CORS preflight
    if (request.method === "OPTIONS") {
      return new Response(null, { headers: CORS_HEADERS });
    }

    // Kill switch (unauthenticated; checked early before any endpoint)
    if (env.KILL_SWITCH === "true" || env.FITTER_KILL_SWITCH === "true") {
      return jsonResponse(
        { error: "Service temporarily unavailable", message: "Kill switch is active." },
        503
      );
    }

    // Health check (unauthenticated)
    if (url.pathname === "/health" || url.pathname === "/") {
      return jsonResponse({ status: "healthy", service: "fitter-gateway", env: env.FITTER_ENV || "production" }, 200);
    }

    // ── RevenueCat Webhook (Phase 7) ──────────────────────────────────────────
    // Uses webhook secret, NOT user JWT
    if (request.method === "POST" && url.pathname === "/v1/webhook/revenuecat") {
      try {
        const body = await request.text();

        // Verify RevenueCat signature
        if (env.REVENUECAT_WEBHOOK_SECRET) {
          const valid = await verifyRevenueCatSignature(request, body, env.REVENUECAT_WEBHOOK_SECRET);
          if (!valid) {
            console.warn("RevenueCat webhook: invalid signature");
            return jsonResponse({ error: "Unauthorized", message: "Invalid webhook signature" }, 401);
          }
        } else {
          console.warn("REVENUECAT_WEBHOOK_SECRET not set — skipping signature check (dev only)");
        }

        const event = (JSON.parse(body) as any).event || {};
        const appUserId = event.app_user_id || event.original_app_user_id;
        const entitlementId = event.entitlement_id || "fitter_premium";
        const type: string = event.type || "";

        if (appUserId && env.VLM_CACHE) {
          const isEligible = ["INITIAL_PURCHASE", "RENEWAL", "NON_RENEWING_PURCHASE"].includes(type);
          const kvValue = isEligible ? "active" : "expired";
          // TTL 1h as per spec (Phase 7); client must refresh periodically
          await env.VLM_CACHE.put(`entitlement:${appUserId}:${entitlementId}`, kvValue, {
            expirationTtl: 3600,
          });
          console.log(`RevenueCat webhook: user=${appUserId} entitlement=${entitlementId} → ${kvValue}`);
        }

        return jsonResponse({ status: "received", processed: Boolean(appUserId) }, 200);
      } catch (e: any) {
        return jsonResponse({ error: e.message }, 400);
      }
    }

    // ── All /v1/... endpoints require JWT authentication ───────────────────────
    const authResult = await authenticate(request, env);
    if (authResult instanceof Response) {
      return authResult; // 401
    }
    const claims = authResult;
    const userId = claims.user_id;

    // Device ID for secondary rate limit tracking/logging
    const deviceId =
      request.headers.get("x-device-id") ||
      request.headers.get("cf-connecting-ip") ||
      "unknown-device";

    // Log device attestation header (enforcement is a stretch goal per spec)
    const attestation = request.headers.get("x-device-attestation");
    if (attestation) {
      console.log(`[Attestation] user=${userId} token_present=true`);
    }

    // ── Rate limit by user_id ─────────────────────────────────────────────────
    const premium = await isUserPremium(env, userId);
    if (!premium) {
      const rateLimit = await enforceRateLimit(env, userId);
      if (!rateLimit.allowed) {
        return new Response(
          JSON.stringify({
            error: "Too Many Requests",
            message: "Rate limit exceeded (20 req/min). Upgrade to Fitter Premium for higher limits.",
          }),
          {
            status: 429,
            headers: {
              "Content-Type": "application/json",
              "Retry-After": String(rateLimit.retryAfter ?? 60),
              ...CORS_HEADERS,
            },
          }
        );
      }
    }

    try {
      // ── GET /v1/entitlements ─────────────────────────────────────────────────
      if (request.method === "GET" && url.pathname === "/v1/entitlements") {
        return jsonResponse({ premium, user_id: userId }, 200);
      }

      // ── POST /v1/analyze-meal ─────────────────────────────────────────────────
      if (request.method === "POST" && url.pathname === "/v1/analyze-meal") {
        const body: any = await request.json();
        const imageBase64 = body.image_base64 || body.imageBase64;
        const plateSizeInches = body.plate_size_inches || body.plateSizeInches;
        const requestedModel = body.model_hint || body.model;

        // Client-supplied daily allowance (default 3; Week-1 client sends 5)
        // Hard cap at MAX_DAILY_ALLOWANCE=5 to prevent abuse
        const clientAllowance = typeof body.daily_allowance === "number"
          ? Math.min(Math.max(1, body.daily_allowance), MAX_DAILY_ALLOWANCE)
          : 3;

        if (!imageBase64 || typeof imageBase64 !== "string") {
          return jsonResponse({ error: "Bad Request", message: "image_base64 is required" }, 400);
        }

        // ── Quota pre-check BEFORE VLM spend (does not consume quota on VLM error) ──
        let effectiveAllowance = clientAllowance;
        if (!premium) {
          try {
            const quotaCheck = await checkQuotaServerSide(env, userId, clientAllowance);
            effectiveAllowance = quotaCheck.effectiveAllowance;
            if (!quotaCheck.allowed) {
              return err402Quota();
            }
          } catch (quotaErr: any) {
            console.error("get_scan_quota RPC error:", quotaErr.message);
            // If Supabase is down: fail safe — do NOT allow VLM spend
            return jsonResponse(
              { error: "Service temporarily unavailable", message: "Could not verify quota. Try again." },
              503
            );
          }
        }
        // Premium users skip quota; scan still logged for analytics if desired

        let userPrompt = "What is in this meal? Please estimate its nutritional contents.";
        if (plateSizeInches) {
          userPrompt += `\nNOTE: The user's plate size is exactly ${plateSizeInches} inches. Calibrate portion sizes accordingly.`;
        }

        // Semantic KV cache
        const semanticHash = await sha256(`analyze:${userPrompt}:${imageBase64.substring(0, 10000)}:${imageBase64.length}`);
        const cacheKey = `vlm:${semanticHash}`;

        if (env.VLM_CACHE) {
          const cachedResult = await env.VLM_CACHE.get(cacheKey);
          if (cachedResult) {
            console.log(`[Cache HIT] user=${userId} key=${cacheKey}`);
            if (!premium) {
              try {
                await consumeScanServerSide(env, userId, effectiveAllowance);
              } catch (consumeErr: any) {
                console.error("Post-scan consume_scan RPC error:", consumeErr.message);
              }
            }
            return new Response(cachedResult, {
              status: 200,
              headers: { "Content-Type": "application/json", "X-Cache": "HIT", "X-Provider": "cache", ...CORS_HEADERS },
            });
          }
        }

        console.log(`[Cache MISS] user=${userId} key=${cacheKey} allowance=${effectiveAllowance}`);
        const { result, provider } = await executeVlmFailover(env, userPrompt, imageBase64, requestedModel);
        const resultJson = JSON.stringify(result);

        // Consume 1 scan quota ONLY after VLM inference succeeded
        if (!premium) {
          try {
            await consumeScanServerSide(env, userId, effectiveAllowance);
          } catch (consumeErr: any) {
            console.error("Post-scan consume_scan RPC error:", consumeErr.message);
          }
        }

        if (env.VLM_CACHE) {
          await env.VLM_CACHE.put(cacheKey, resultJson, { expirationTtl: 2592000 });
        }

        return new Response(resultJson, {
          status: 200,
          headers: { "Content-Type": "application/json", "X-Cache": "MISS", "X-Provider": provider, ...CORS_HEADERS },
        });
      }


      // ── POST /v1/recalculate ──────────────────────────────────────────────────
      if (request.method === "POST" && url.pathname === "/v1/recalculate") {
        const body: any = await request.json();
        const items = body.items;

        if (!Array.isArray(items) || items.length === 0) {
          return jsonResponse({ error: "Bad Request", message: "items array is required with {name, grams}" }, 400);
        }

        const itemsPrompt = items
          .map((it: any) => `- ${it.name || it.item}: ${it.grams || it.weight_est_g}g`)
          .join("\n");
        const prompt = `Analyze these food items and estimate their nutritional contents based on the given weights.\nItems:\n${itemsPrompt}`;

        const semanticHash = await sha256(`recalc:${prompt}`);
        const cacheKey = `vlm:${semanticHash}`;

        if (env.VLM_CACHE) {
          const cached = await env.VLM_CACHE.get(cacheKey);
          if (cached) {
            console.log(`[Cache HIT] recalc user=${userId} key=${cacheKey}`);
            return new Response(cached, {
              status: 200,
              headers: { "Content-Type": "application/json", "X-Cache": "HIT", "X-Provider": "cache", ...CORS_HEADERS },
            });
          }
        }

        console.log(`[Cache MISS] recalc user=${userId}`);
        const { result: recalcResult, provider: recalcProvider } = await executeVlmFailover(env, prompt);
        const resultJson = JSON.stringify(recalcResult);

        if (env.VLM_CACHE) {
          await env.VLM_CACHE.put(cacheKey, resultJson, { expirationTtl: 2592000 });
        }

        return new Response(resultJson, {
          status: 200,
          headers: { "Content-Type": "application/json", "X-Cache": "MISS", "X-Provider": recalcProvider, ...CORS_HEADERS },
        });
      }

      return jsonResponse({ error: "Not Found" }, 404);
    } catch (error: any) {
      console.error("Gateway request handling error:", error);
      return jsonResponse(
        { error: "Internal Server Error", message: error.message || "An unexpected error occurred" },
        500
      );
    }
  },
};

export { checkQuotaServerSide, consumeScanServerSide, executeVlmFailover };

