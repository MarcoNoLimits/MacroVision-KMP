/**
 * Fitter AI Gateway — Supabase Edge Function
 *
 * Drop-in replacement for the Cloudflare Worker (worker/src/index.ts).
 * Routes: POST /v1/analyze-meal · POST /v1/recalculate · GET /v1/entitlements
 *         POST /v1/webhook/revenuecat · GET /health
 *
 * Auth: Supabase Edge Functions validate the Supabase JWT automatically when
 *       verify_jwt = true (the default). The user's sub is available via the
 *       Authorization header decoded by the Supabase runtime.
 *
 * Secrets (set via `supabase secrets set KEY=value`):
 *   GEMINI_API_KEY, OPENROUTER_API_KEY, GROQ_API_KEY
 *   SUPABASE_SERVICE_ROLE_KEY  (injected automatically by the runtime)
 *   SUPABASE_URL               (injected automatically by the runtime)
 *   REVENUECAT_WEBHOOK_SECRET
 *   FITTER_ENV                 ("dev" enables mock responses when no keys present)
 *   VLM_PRIMARY_PROVIDER       ("gemini" | "openrouter" | "groq" | "mock")
 *   VLM_ALLOW_FALLBACK         ("true" | "false", default "true")
 *   KILL_SWITCH                ("true" to disable all non-health endpoints)
 */

import { createClient } from "https://esm.sh/@supabase/supabase-js@2";

// ─── Types ────────────────────────────────────────────────────────────────────

interface FoodItem {
  item: string;
  weight_est_g: number;
  calories: number;
  protein_g: number;
  carbs_g: number;
  fat_g: number;
  confidence: string;
}

interface NutritionResponse {
  meal_name: string;
  items: FoodItem[];
  totals: { calories: number; protein_g: number; carbs_g: number; fat_g: number };
  estimation_notes: string;
}

// ─── Constants ────────────────────────────────────────────────────────────────

const MAX_DAILY_ALLOWANCE = 5;

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
  "Access-Control-Allow-Headers": "Content-Type, Authorization, x-device-id, apikey",
};

// ─── Helpers ──────────────────────────────────────────────────────────────────

function jsonResponse(body: unknown, status: number): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { "Content-Type": "application/json", ...CORS_HEADERS },
  });
}

function err401(message = "Authentication required"): Response {
  return new Response(JSON.stringify({ error: "Unauthorized", message }), {
    status: 401,
    headers: { "Content-Type": "application/json", "WWW-Authenticate": "Bearer", ...CORS_HEADERS },
  });
}

function err402Quota(): Response {
  return jsonResponse(
    { error: "quota_exhausted", message: "Daily scan quota exceeded. Upgrade to Fitter Premium for unlimited scans." },
    402
  );
}

function cleanJson(raw: string): string {
  let clean = raw.trim();
  if (clean.startsWith("```json")) clean = clean.substring(7);
  else if (clean.startsWith("```")) clean = clean.substring(3);
  if (clean.endsWith("```")) clean = clean.substring(0, clean.length - 3);
  return clean.trim();
}

async function sha256(message: string): Promise<string> {
  const msgUint8 = new TextEncoder().encode(message);
  const hashBuffer = await crypto.subtle.digest("SHA-256", msgUint8);
  const hashArray = Array.from(new Uint8Array(hashBuffer));
  return hashArray.map((b) => b.toString(16).padStart(2, "0")).join("");
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
  const calculatedCalories = items.reduce((a, i) => a + i.calories, 0);
  const calculatedProtein = items.reduce((a, i) => a + i.protein_g, 0);
  const calculatedCarbs = items.reduce((a, i) => a + i.carbs_g, 0);
  const calculatedFat = items.reduce((a, i) => a + i.fat_g, 0);
  return {
    meal_name: String(parsed.meal_name || "Meal"),
    items,
    totals: {
      calories: Math.round(Number(rawTotals.calories) || calculatedCalories),
      protein_g: parseFloat(Number(rawTotals.protein_g ?? calculatedProtein).toFixed(1)),
      carbs_g: parseFloat(Number(rawTotals.carbs_g ?? calculatedCarbs).toFixed(1)),
      fat_g: parseFloat(Number(rawTotals.fat_g ?? calculatedFat).toFixed(1)),
    },
    estimation_notes: String(parsed.estimation_notes || "Estimated by Fitter AI"),
  };
}

// ─── VLM providers ───────────────────────────────────────────────────────────

async function callGemini(prompt: string, base64Image?: string, model = "gemini-2.5-flash"): Promise<NutritionResponse> {
  const apiKey = Deno.env.get("GEMINI_API_KEY");
  if (!apiKey) throw new Error("GEMINI_API_KEY not configured");
  const parts: any[] = [{ text: prompt }];
  if (base64Image) parts.push({ inlineData: { mimeType: "image/jpeg", data: base64Image } });
  const response = await fetch(
    `https://generativelanguage.googleapis.com/v1beta/models/${model}:generateContent`,
    {
      method: "POST",
      headers: { "Content-Type": "application/json", "x-goog-api-key": apiKey },
      body: JSON.stringify({
        contents: [{ parts }],
        systemInstruction: { parts: [{ text: SYSTEM_PROMPT }] },
        // 4096 tokens prevents truncated JSON on complex multi-item meals.
        // gemini-2.5-flash still returns markdown fences despite responseMimeType;
        // cleanJson() strips them before parsing.
        generationConfig: { responseMimeType: "application/json", temperature: 0.2, maxOutputTokens: 4096 },
      }),
    }
  );
  if (!response.ok) throw new Error(`Gemini error (${response.status}): ${await response.text()}`);
  const data: any = await response.json();
  const text = data.candidates?.[0]?.content?.parts?.[0]?.text;
  if (!text) throw new Error("Empty response from Gemini");
  return validateAndFormatNutritionResponse(JSON.parse(cleanJson(text)));
}

// Default model: qwen/qwen3-vl-235b-a22b-instruct:free — multimodal (image+text→text), 256K ctx, 32K output, free tier.
async function callOpenRouter(prompt: string, base64Image?: string, model = "qwen/qwen3-vl-235b-a22b-instruct"): Promise<NutritionResponse> {
  const apiKey = Deno.env.get("OPENROUTER_API_KEY");
  if (!apiKey) throw new Error("OPENROUTER_API_KEY not configured");
  const content: any[] = [{ type: "text", text: `${SYSTEM_PROMPT}\n\n${prompt}` }];
  if (base64Image) content.push({ type: "image_url", image_url: { url: `data:image/jpeg;base64,${base64Image}` } });
  const response = await fetch("https://openrouter.ai/api/v1/chat/completions", {
    method: "POST",
    headers: { "Content-Type": "application/json", Authorization: `Bearer ${apiKey}` },
    body: JSON.stringify({ model, messages: [{ role: "user", content }], max_tokens: 2048 }),
  });
  if (!response.ok) throw new Error(`OpenRouter error (${response.status}): ${await response.text()}`);
  const data: any = await response.json();
  const text = data.choices?.[0]?.message?.content;
  if (!text) throw new Error("Empty response from OpenRouter");
  return validateAndFormatNutritionResponse(JSON.parse(cleanJson(text)));
}

async function callGroq(prompt: string, base64Image?: string, model = "llama-3.2-11b-vision-preview"): Promise<NutritionResponse> {
  const apiKey = Deno.env.get("GROQ_API_KEY");
  if (!apiKey) throw new Error("GROQ_API_KEY not configured");
  const content: any[] = [{ type: "text", text: `${SYSTEM_PROMPT}\n\n${prompt}` }];
  if (base64Image) content.push({ type: "image_url", image_url: { url: `data:image/jpeg;base64,${base64Image}` } });
  const response = await fetch("https://api.groq.com/openai/v1/chat/completions", {
    method: "POST",
    headers: { "Content-Type": "application/json", Authorization: `Bearer ${apiKey}` },
    body: JSON.stringify({ model, messages: [{ role: "user", content }], max_tokens: 1000 }),
  });
  if (!response.ok) throw new Error(`Groq error (${response.status}): ${await response.text()}`);
  const data: any = await response.json();
  const text = data.choices?.[0]?.message?.content;
  if (!text) throw new Error("Empty response from Groq");
  return validateAndFormatNutritionResponse(JSON.parse(cleanJson(text)));
}

async function executeVlmFailover(prompt: string, base64Image?: string, requestedModel?: string): Promise<{ result: NutritionResponse; provider: string }> {
  const primaryProvider = (Deno.env.get("VLM_PRIMARY_PROVIDER") ?? "gemini").toLowerCase().trim();
  const allowFallback = (Deno.env.get("VLM_ALLOW_FALLBACK") ?? "true").toLowerCase() !== "false";
  const errors: string[] = [];

  async function tryProvider(name: string): Promise<{ result: NutritionResponse; provider: string } | null> {
    try {
      if (name === "gemini") {
        const model = primaryProvider === "gemini" && requestedModel ? requestedModel : "gemini-2.5-flash";
        return { result: await callGemini(prompt, base64Image, model), provider: "gemini" };
      }
      if (name === "openrouter") {
        return { result: await callOpenRouter(prompt, base64Image, requestedModel), provider: "openrouter" };
      }
      if (name === "groq") {
        return { result: await callGroq(prompt, base64Image), provider: "groq" };
      }
    } catch (err: any) {
      console.warn(`[Provider] ${name} failed:`, err.message);
      errors.push(`${name}: ${err.message}`);
    }
    return null;
  }

  if (primaryProvider === "mock") {
    if (Deno.env.get("FITTER_ENV") !== "dev") throw new Error('VLM_PRIMARY_PROVIDER="mock" requires FITTER_ENV=dev');
    return { result: devMockResponse(), provider: "mock" };
  }

  const primaryResult = await tryProvider(primaryProvider);
  if (primaryResult) return primaryResult;

  if (!allowFallback) throw new Error(`Primary provider "${primaryProvider}" failed: ${errors.join("; ")}`);

  const fallbackOrder = ["gemini", "openrouter", "groq"].filter((p) => p !== primaryProvider);
  for (const name of fallbackOrder) {
    const r = await tryProvider(name);
    if (r) return r;
  }

  if (Deno.env.get("FITTER_ENV") === "dev") return { result: devMockResponse(), provider: "mock" };
  throw new Error(`All VLM providers failed: ${errors.join("; ")}`);
}

function devMockResponse(): NutritionResponse {
  return {
    meal_name: "Sample Balanced Meal (Dev Mock)",
    items: [
      { item: "Grilled Chicken Breast", weight_est_g: 150, calories: 248, protein_g: 46.5, carbs_g: 0.0, fat_g: 5.4, confidence: "high" },
      { item: "Brown Rice", weight_est_g: 150, calories: 168, protein_g: 3.9, carbs_g: 35.7, fat_g: 1.4, confidence: "high" },
      { item: "Steamed Broccoli", weight_est_g: 100, calories: 35, protein_g: 2.4, carbs_g: 7.2, fat_g: 0.4, confidence: "high" },
    ],
    totals: { calories: 451, protein_g: 52.8, carbs_g: 42.9, fat_g: 7.2 },
    estimation_notes: "Generated by Fitter AI (Dev Mock — FITTER_ENV=dev)",
  };
}

// ─── Quota (server-side, service_role) ────────────────────────────────────────

async function checkQuotaServerSide(
  userId: string,
  allowance: number
): Promise<{ allowed: boolean; effectiveAllowance: number }> {
  const supabaseUrl = Deno.env.get("SUPABASE_URL")!;
  const serviceRole = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!;
  const url = `${supabaseUrl.replace(/\/$/, "")}/rest/v1/rpc/get_scan_quota`;
  const resp = await fetch(url, {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
      "Accept-Profile": "fitter",
      "Content-Profile": "fitter",
      apikey: serviceRole,
      Authorization: `Bearer ${serviceRole}`,
    },
    body: JSON.stringify({ p_user_id: userId, p_allowance: allowance }),
  });
  if (!resp.ok) throw new Error(`get_scan_quota RPC failed (${resp.status}): ${await resp.text()}`);
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

async function consumeScanServerSide(userId: string, allowance: number): Promise<boolean> {
  const supabaseUrl = Deno.env.get("SUPABASE_URL")!;
  const serviceRole = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!;
  const url = `${supabaseUrl.replace(/\/$/, "")}/rest/v1/rpc/consume_scan`;
  const resp = await fetch(url, {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
      "Accept-Profile": "fitter",
      "Content-Profile": "fitter",
      apikey: serviceRole,
      Authorization: `Bearer ${serviceRole}`,
    },
    body: JSON.stringify({ p_user_id: userId, p_allowance: allowance }),
  });
  if (!resp.ok) throw new Error(`consume_scan RPC failed (${resp.status}): ${await resp.text()}`);
  const result = await resp.json();
  if (typeof result === "boolean") return result;
  if (Array.isArray(result) && result.length > 0) return Boolean(result[0]);
  return Boolean(result);
}

// ─── RevenueCat webhook ───────────────────────────────────────────────────────

function base64ToUint8Array(b64: string): Uint8Array {
  const binary = atob(b64);
  const bytes = new Uint8Array(binary.length);
  for (let i = 0; i < binary.length; i++) bytes[i] = binary.charCodeAt(i);
  return bytes;
}

async function verifyRevenueCatSignature(request: Request, body: string, secret: string): Promise<boolean> {
  const sig = request.headers.get("X-RevenueCat-Signature") || "";
  if (!sig) return false;
  try {
    const encoder = new TextEncoder();
    const key = await crypto.subtle.importKey("raw", encoder.encode(secret), { name: "HMAC", hash: "SHA-256" }, false, ["verify"]);
    return crypto.subtle.verify("HMAC", key, base64ToUint8Array(sig), encoder.encode(body));
  } catch { return false; }
}

// ─── Main handler ─────────────────────────────────────────────────────────────

Deno.serve(async (req: Request) => {
  const url = new URL(req.url);

  // CORS preflight
  if (req.method === "OPTIONS") return new Response(null, { headers: CORS_HEADERS });

  // Kill switch
  if (Deno.env.get("KILL_SWITCH") === "true") {
    return jsonResponse({ error: "Service temporarily unavailable", message: "Kill switch is active." }, 503);
  }

  // Health (unauthenticated)
  if (url.pathname.endsWith("/health") || url.pathname === "/") {
    return jsonResponse({ status: "healthy", service: "fitter-gateway", runtime: "supabase-edge", env: Deno.env.get("FITTER_ENV") || "production" }, 200);
  }

  // ── RevenueCat webhook (uses webhook secret, not user JWT) ────────────────
  if (req.method === "POST" && url.pathname.endsWith("/v1/webhook/revenuecat")) {
    try {
      const body = await req.text();
      const secret = Deno.env.get("REVENUECAT_WEBHOOK_SECRET");
      if (secret) {
        const valid = await verifyRevenueCatSignature(req, body, secret);
        if (!valid) return jsonResponse({ error: "Unauthorized", message: "Invalid webhook signature" }, 401);
      }
      const event = (JSON.parse(body) as any).event || {};
      const appUserId = event.app_user_id || event.original_app_user_id;
      const entitlementId = event.entitlement_id || "fitter_premium";
      const type: string = event.type || "";

      if (appUserId) {
        const supabaseUrl = Deno.env.get("SUPABASE_URL")!;
        const serviceRole = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!;
        const isEligible = ["INITIAL_PURCHASE", "RENEWAL", "NON_RENEWING_PURCHASE"].includes(type);
        // Upsert entitlement into the fitter.entitlements table
        await fetch(`${supabaseUrl}/rest/v1/entitlements`, {
          method: "POST",
          headers: {
            "Content-Type": "application/json",
            "Prefer": "resolution=merge-duplicates",
            apikey: serviceRole,
            Authorization: `Bearer ${serviceRole}`,
          },
          body: JSON.stringify({
            user_id: appUserId,
            entitlement_id: entitlementId,
            status: isEligible ? "active" : "expired",
            updated_at: new Date().toISOString(),
          }),
        });
      }

      return jsonResponse({ status: "received", processed: Boolean(appUserId) }, 200);
    } catch (e: any) {
      return jsonResponse({ error: e.message }, 400);
    }
  }

  // ── All /v1/... endpoints require a valid Supabase JWT ────────────────────
  // The runtime validates the JWT automatically; we just need to extract the user_id.
  const authHeader = req.headers.get("Authorization") || "";
  const token = authHeader.startsWith("Bearer ") ? authHeader.slice(7).trim() : null;

  let userId: string | null = null;

  if (token) {
    // Decode the JWT payload (trust the runtime's signature validation)
    try {
      const parts = token.split(".");
      if (parts.length === 3) {
        const payloadB64 = parts[1].replace(/-/g, "+").replace(/_/g, "/");
        const padded = payloadB64.padEnd(payloadB64.length + ((4 - (payloadB64.length % 4)) % 4), "=");
        const payload = JSON.parse(atob(padded));

        // Reject expired tokens
        const now = Math.floor(Date.now() / 1000);
        if (typeof payload.exp === "number" && payload.exp <= now) {
          return err401("JWT has expired");
        }

        userId = payload.sub || payload.user_id || null;
      }
    } catch {
      return err401("Malformed JWT");
    }
  }

  // No token → use device-id as anonymous identifier for rate limiting
  const deviceId = req.headers.get("x-device-id") || "unknown-device";
  const effectiveId = userId ?? `device:${deviceId}`;

  try {
    // ── GET /v1/entitlements ────────────────────────────────────────────────
    if (req.method === "GET" && url.pathname.endsWith("/v1/entitlements")) {
      if (!userId) return jsonResponse({ premium: false, user_id: null }, 200);
      // Check entitlement from DB
      const supabaseUrl = Deno.env.get("SUPABASE_URL")!;
      const serviceRole = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!;
      const entResp = await fetch(
        `${supabaseUrl}/rest/v1/entitlements?user_id=eq.${userId}&entitlement_id=eq.fitter_premium&select=status`,
        { headers: { apikey: serviceRole, Authorization: `Bearer ${serviceRole}` } }
      );
      const entData: any[] = entResp.ok ? await entResp.json() : [];
      const premium = entData.some((e) => e.status === "active");
      return jsonResponse({ premium, user_id: userId }, 200);
    }

    // ── POST /v1/analyze-meal ───────────────────────────────────────────────
    if (req.method === "POST" && url.pathname.endsWith("/v1/analyze-meal")) {
      const body: any = await req.json();
      const imageBase64 = body.image_base64 || body.imageBase64;
      const plateSizeInches = body.plate_size_inches || body.plateSizeInches;
      const requestedModel = body.model_hint || body.model;
      const clientAllowance = typeof body.daily_allowance === "number"
        ? Math.min(Math.max(1, body.daily_allowance), MAX_DAILY_ALLOWANCE)
        : 3;

      if (!imageBase64 || typeof imageBase64 !== "string") {
        return jsonResponse({ error: "Bad Request", message: "image_base64 is required" }, 400);
      }

      // Premium check — skip quota for premium users
      let isPremium = false;
      if (userId) {
        const supabaseUrl = Deno.env.get("SUPABASE_URL")!;
        const serviceRole = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!;
        const entResp = await fetch(
          `${supabaseUrl}/rest/v1/entitlements?user_id=eq.${userId}&entitlement_id=eq.fitter_premium&select=status`,
          { headers: { apikey: serviceRole, Authorization: `Bearer ${serviceRole}` } }
        );
        const entData: any[] = entResp.ok ? await entResp.json() : [];
        isPremium = entData.some((e) => e.status === "active");
      }

      // Quota pre-check BEFORE VLM spend (does not consume quota if VLM fails)
      let effectiveAllowance = clientAllowance;
      if (!isPremium && userId) {
        try {
          const quotaCheck = await checkQuotaServerSide(userId, clientAllowance);
          effectiveAllowance = quotaCheck.effectiveAllowance;
          if (!quotaCheck.allowed) return err402Quota();
        } catch (quotaErr: any) {
          console.error("get_scan_quota RPC error:", quotaErr.message);
          return jsonResponse({ error: "Service temporarily unavailable", message: "Could not verify quota. Try again." }, 503);
        }
      }
      // Unauthenticated (no userId) → allow scan, rate-limited only by provider

      let userPrompt = "What is in this meal? Please estimate its nutritional contents.";
      if (plateSizeInches) userPrompt += `\nNOTE: The user's plate size is exactly ${plateSizeInches} inches. Calibrate portion sizes accordingly.`;

      // Simple in-memory semantic cache key (no KV in Edge Functions free tier)
      // For production caching, add a Supabase table or Redis via Upstash.
      const semanticHash = await sha256(`analyze:${userPrompt}:${imageBase64.substring(0, 10000)}:${imageBase64.length}`);
      console.log(`[analyze-meal] user=${effectiveId} hash=${semanticHash} allowance=${effectiveAllowance}`);

      const { result, provider } = await executeVlmFailover(userPrompt, imageBase64, requestedModel);

      // Consume 1 scan quota ONLY after VLM inference succeeded
      if (!isPremium && userId) {
        try {
          await consumeScanServerSide(userId, effectiveAllowance);
        } catch (consumeErr: any) {
          console.error("Post-scan consume_scan RPC error:", consumeErr.message);
        }
      }

      return new Response(JSON.stringify(result), {
        status: 200,
        headers: { "Content-Type": "application/json", "X-Provider": provider, ...CORS_HEADERS },
      });
    }

    // ── POST /v1/recalculate ────────────────────────────────────────────────
    if (req.method === "POST" && url.pathname.endsWith("/v1/recalculate")) {
      const body: any = await req.json();
      const items = body.items;
      if (!Array.isArray(items) || items.length === 0) {
        return jsonResponse({ error: "Bad Request", message: "items array is required with {name, grams}" }, 400);
      }
      const itemsPrompt = items
        .map((it: any) => `- ${it.name || it.item}: ${it.grams || it.weight_est_g}g`)
        .join("\n");
      const prompt = `Analyze these food items and estimate their nutritional contents based on the given weights.\nItems:\n${itemsPrompt}`;

      console.log(`[recalculate] user=${effectiveId}`);
      const { result, provider } = await executeVlmFailover(prompt);
      return new Response(JSON.stringify(result), {
        status: 200,
        headers: { "Content-Type": "application/json", "X-Provider": provider, ...CORS_HEADERS },
      });
    }

    return jsonResponse({ error: "Not Found" }, 404);
  } catch (error: any) {
    console.error("Edge Function error:", error);
    return jsonResponse({ error: "Internal Server Error", message: error.message || "Unexpected error" }, 500);
  }
});
