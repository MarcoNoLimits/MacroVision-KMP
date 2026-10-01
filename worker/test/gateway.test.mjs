import { test } from "node:test";
import assert from "node:assert/strict";
import gateway, { checkQuotaServerSide, consumeScanServerSide, executeVlmFailover } from "../src/index.ts";

/**
 * In-memory KVNamespace implementation for testing worker endpoints.
 */
class MockKV {
  constructor() {
    this.store = new Map();
    this.expirations = new Map();
  }

  async get(key) {
    return this.store.get(key) || null;
  }

  async put(key, value, options) {
    this.store.set(key, value);
    if (options?.expirationTtl) {
      this.expirations.set(key, options.expirationTtl);
    }
  }

  async delete(key) {
    this.store.delete(key);
    this.expirations.delete(key);
  }
}

function createTestEnv(overrides = {}) {
  const kv = new MockKV();
  const envVal = overrides.FITCAL_ENV || overrides.FITTER_ENV || "dev";
  const killSwitchVal = overrides.FITCAL_KILL_SWITCH || overrides.FITTER_KILL_SWITCH;
  return {
    VLM_CACHE: kv,
    SUPABASE_URL: "https://test-project.supabase.co",
    SUPABASE_SERVICE_ROLE_KEY: "test-service-role-key",
    REVENUECAT_WEBHOOK_SECRET: "test-revenuecat-secret",
    FITCAL_ENV: envVal,
    FITTER_ENV: envVal,
    ...(killSwitchVal ? { FITCAL_KILL_SWITCH: killSwitchVal, FITTER_KILL_SWITCH: killSwitchVal } : {}),
    ...overrides,
  };
}

// ─── Tests ───────────────────────────────────────────────────────────────────

test("GET /health returns 200 and healthy status without leaking internals", async () => {
  const env = createTestEnv();
  const req = new Request("https://gateway.fitcal.app/health");
  const res = await gateway.fetch(req, env);

  assert.equal(res.status, 200);
  const data = await res.json();
  assert.equal(data.status, "healthy");
  // Health is unauthenticated, so it must not disclose deployment metadata.
  assert.equal(data.service, undefined);
  assert.equal(data.env, undefined);
});

test("GET /privacy returns 200 with HTML privacy policy", async () => {
  const env = createTestEnv();
  const req = new Request("https://gateway.fitcal.app/privacy");
  const res = await gateway.fetch(req, env);

  assert.equal(res.status, 200);
  assert.match(res.headers.get("Content-Type") || "", /text\/html/);
  const body = await res.text();
  assert.match(body, /Privacy Policy/);
  assert.match(body, /Not medical advice/);
});

test("GET /health does not reflect a wildcard CORS origin", async () => {
  const env = createTestEnv();
  const req = new Request("https://gateway.fitcal.app/health", {
    headers: { Origin: "https://evil.example" },
  });
  const res = await gateway.fetch(req, env);

  assert.notEqual(res.headers.get("Access-Control-Allow-Origin"), "*");
  assert.equal(res.headers.get("Access-Control-Allow-Origin"), null);
});

test("GET /health reflects an explicitly allowlisted origin", async () => {
  const env = createTestEnv({ ALLOWED_ORIGINS: "https://app.fitcal.app" });
  const req = new Request("https://gateway.fitcal.app/health", {
    headers: { Origin: "https://app.fitcal.app" },
  });
  const res = await gateway.fetch(req, env);

  assert.equal(res.headers.get("Access-Control-Allow-Origin"), "https://app.fitcal.app");
  assert.equal(res.headers.get("Vary"), "Origin");
});

test("RevenueCat webhook fails closed when the secret is missing in production", async () => {
  const env = createTestEnv({ FITCAL_ENV: "production" });
  delete env.REVENUECAT_WEBHOOK_SECRET;
  const req = new Request("https://gateway.fitcal.app/v1/webhook/revenuecat", {
    method: "POST",
    body: JSON.stringify({
      event: { app_user_id: "attacker", entitlement_id: "fitcal_premium", type: "INITIAL_PURCHASE" },
    }),
  });
  const res = await gateway.fetch(req, env);

  assert.equal(res.status, 503);
  // The forged entitlement must NOT have been written.
  assert.equal(await env.VLM_CACHE.get(`entitlement:attacker:fitcal_premium`), null);
});

test("Kill switch returns 503 when active", async () => {
  const env = createTestEnv({ FITCAL_KILL_SWITCH: "true" });
  const req = new Request("https://gateway.fitcal.app/health");
  const res = await gateway.fetch(req, env);

  assert.equal(res.status, 503);
  const data = await res.json();
  assert.equal(data.error, "Service temporarily unavailable");
});

test("POST /v1/analyze-meal rejects unauthenticated request with 401", async () => {
  const env = createTestEnv();
  const req = new Request("https://gateway.fitcal.app/v1/analyze-meal", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ image_base64: "base64data" }),
  });
  const res = await gateway.fetch(req, env);

  assert.equal(res.status, 401);
  const data = await res.json();
  assert.equal(data.error, "Unauthorized");
});

test("POST /v1/recalculate rejects unauthenticated request with 401", async () => {
  const env = createTestEnv();
  const req = new Request("https://gateway.fitcal.app/v1/recalculate", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ items: [{ name: "Oats", grams: 100 }] }),
  });
  const res = await gateway.fetch(req, env);

  assert.equal(res.status, 401);
  const data = await res.json();
  assert.equal(data.error, "Unauthorized");
});

test("POST /v1/account/delete requires authentication (Play account-deletion path)", async () => {
  const env = createTestEnv();
  const req = new Request("https://gateway.fitcal.app/v1/account/delete", {
    method: "POST",
    body: JSON.stringify({ confirm: true }),
  });
  const res = await gateway.fetch(req, env);

  assert.equal(res.status, 401);
});

test("POST /v1/account/delete rejects a forged user_id in the body", async () => {
  const env = createTestEnv();
  const req = new Request("https://gateway.fitcal.app/v1/account/delete", {
    method: "POST",
    headers: { Authorization: "Bearer malformed.jwt.token" },
    body: JSON.stringify({ confirm: true, user_id: "someone-else" }),
  });
  const res = await gateway.fetch(req, env);

  // The body value must never be trusted; an unverifiable JWT is rejected outright.
  assert.equal(res.status, 401);
});

test("GET /v1/entitlements rejects unauthenticated request with 401", async () => {
  const env = createTestEnv();
  const req = new Request("https://gateway.fitcal.app/v1/entitlements");
  const res = await gateway.fetch(req, env);

  assert.equal(res.status, 401);
  const data = await res.json();
  assert.equal(data.error, "Unauthorized");
});

test("Malformed JWT is rejected with 401", async () => {
  const env = createTestEnv();
  const req = new Request("https://gateway.fitcal.app/v1/entitlements", {
    headers: { Authorization: "Bearer this.is.an.invalid.token" },
  });
  const res = await gateway.fetch(req, env);

  assert.equal(res.status, 401);
  const data = await res.json();
  assert.equal(data.error, "Unauthorized");
});

test("Spoofed x-fitcal-premium header is NOT trusted (rejected with 401)", async () => {
  const env = createTestEnv();
  const req = new Request("https://gateway.fitcal.app/v1/analyze-meal", {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
      "x-fitcal-premium": "true", // Spoofed client header
    },
    body: JSON.stringify({ image_base64: "base64data" }),
  });
  const res = await gateway.fetch(req, env);

  // Must still be 401 unauthorized — client header cannot bypass auth
  assert.equal(res.status, 401);
});

test("RevenueCat webhook rejects request with invalid signature", async () => {
  const env = createTestEnv();
  const req = new Request("https://gateway.fitcal.app/v1/webhook/revenuecat", {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
      "X-RevenueCat-Signature": "invalid-signature-here",
    },
    body: JSON.stringify({
      event: {
        type: "INITIAL_PURCHASE",
        app_user_id: "user-123",
        entitlement_id: "fitcal_premium",
      },
    }),
  });
  const res = await gateway.fetch(req, env);

  assert.equal(res.status, 401);
  const data = await res.json();
  assert.equal(data.error, "Unauthorized");
});

test("RevenueCat webhook with valid HMAC-SHA256 signature updates entitlement in KV", async () => {
  const secret = "test-secret-key-123";
  const env = createTestEnv({ REVENUECAT_WEBHOOK_SECRET: secret });

  const payload = JSON.stringify({
    event: {
      type: "INITIAL_PURCHASE",
      app_user_id: "user-sub-456",
      entitlement_id: "fitcal_premium",
    },
  });

  // Generate valid HMAC-SHA256 signature matching RevenueCat
  const encoder = new TextEncoder();
  const key = await crypto.subtle.importKey(
    "raw",
    encoder.encode(secret),
    { name: "HMAC", hash: "SHA-256" },
    false,
    ["sign"]
  );
  const signatureBytes = await crypto.subtle.sign("HMAC", key, encoder.encode(payload));
  const base64Signature = btoa(String.fromCharCode(...new Uint8Array(signatureBytes)));

  const req = new Request("https://gateway.fitcal.app/v1/webhook/revenuecat", {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
      "X-RevenueCat-Signature": base64Signature,
    },
    body: payload,
  });

  const res = await gateway.fetch(req, env);
  assert.equal(res.status, 200);

  // Check KV cache was updated with 1h TTL
  const kvValue = await env.VLM_CACHE.get("entitlement:user-sub-456:fitcal_premium");
  assert.equal(kvValue, "active");
  assert.equal(env.VLM_CACHE.expirations.get("entitlement:user-sub-456:fitcal_premium"), 3600);
});

test("RevenueCat webhook with cancellation event expires entitlement in KV", async () => {
  const secret = "test-secret-key-123";
  const env = createTestEnv({ REVENUECAT_WEBHOOK_SECRET: secret });

  const payload = JSON.stringify({
    event: {
      type: "CANCELLATION",
      app_user_id: "user-sub-456",
      entitlement_id: "fitcal_premium",
    },
  });

  const encoder = new TextEncoder();
  const key = await crypto.subtle.importKey(
    "raw",
    encoder.encode(secret),
    { name: "HMAC", hash: "SHA-256" },
    false,
    ["sign"]
  );
  const signatureBytes = await crypto.subtle.sign("HMAC", key, encoder.encode(payload));
  const base64Signature = btoa(String.fromCharCode(...new Uint8Array(signatureBytes)));

  const req = new Request("https://gateway.fitcal.app/v1/webhook/revenuecat", {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
      "X-RevenueCat-Signature": base64Signature,
    },
    body: payload,
  });

  const res = await gateway.fetch(req, env);
  assert.equal(res.status, 200);

  const kvValue = await env.VLM_CACHE.get("entitlement:user-sub-456:fitcal_premium");
  assert.equal(kvValue, "expired");
});

test("consumeScanServerSide sends fitcal profile headers, p_user_id, and no fake header", async () => {
  const env = createTestEnv();
  const originalFetch = globalThis.fetch;
  let interceptedUrl = null;
  let interceptedOptions = null;

  globalThis.fetch = async (url, options) => {
    interceptedUrl = url;
    interceptedOptions = options;
    return new Response(JSON.stringify(true), {
      status: 200,
      headers: { "Content-Type": "application/json" },
    });
  };

  try {
    const result = await consumeScanServerSide(env, "target-user-123", 5);
    assert.equal(result, true);
    assert.equal(interceptedUrl, "https://test-project.supabase.co/rest/v1/rpc/consume_scan");
    assert.equal(interceptedOptions.headers["Accept-Profile"], "fitcal");
    assert.equal(interceptedOptions.headers["Content-Profile"], "fitcal");
    assert.equal(interceptedOptions.headers["X-Supabase-Auth-User-Id"], undefined);
    assert.equal(interceptedOptions.headers["apikey"], "test-service-role-key");
    assert.equal(interceptedOptions.headers["Authorization"], "Bearer test-service-role-key");

    const parsedBody = JSON.parse(interceptedOptions.body);
    assert.equal(parsedBody.p_user_id, "target-user-123");
    assert.equal(parsedBody.p_allowance, 5);
  } finally {
    globalThis.fetch = originalFetch;
  }
});

// ─── Phase 9: VLM Provider Routing Tests ─────────────────────────────────────

/**
 * (a) VLM_PRIMARY_PROVIDER=openrouter + GEMINI+OPENROUTER keys set →
 *     OpenRouter URL is called FIRST before Gemini.
 */
test("Phase 9 (a): VLM_PRIMARY_PROVIDER=openrouter calls OpenRouter before Gemini", async () => {
  const env = createTestEnv({
    GEMINI_API_KEY: "test-gemini-key",
    OPENROUTER_API_KEY: "test-openrouter-key",
    VLM_PRIMARY_PROVIDER: "openrouter",
    VLM_ALLOW_FALLBACK: "true",
    FITCAL_ENV: "dev",
  });

  const originalFetch = globalThis.fetch;
  const calledUrls = [];

  const mockNutritionResponse = JSON.stringify({
    meal_name: "Test Meal",
    items: [],
    totals: { calories: 0, protein_g: 0, carbs_g: 0, fat_g: 0 },
    estimation_notes: "test",
    choices: [{ message: { content: JSON.stringify({
      meal_name: "Test Meal",
      items: [],
      totals: { calories: 0, protein_g: 0, carbs_g: 0, fat_g: 0 },
      estimation_notes: "test"
    }) } }]
  });

  globalThis.fetch = async (url, options) => {
    calledUrls.push(url);
    // Return mock OpenRouter response structure
    return new Response(JSON.stringify({
      choices: [{ message: { content: JSON.stringify({
        meal_name: "Test Meal",
        items: [{ item: "Test", weight_est_g: 100, calories: 100, protein_g: 5, carbs_g: 10, fat_g: 2, confidence: "high" }],
        totals: { calories: 100, protein_g: 5, carbs_g: 10, fat_g: 2 },
        estimation_notes: "test"
      }) } }]
    }), {
      status: 200,
      headers: { "Content-Type": "application/json" },
    });
  };

  try {
    const { provider } = await executeVlmFailover(env, "test prompt", undefined, undefined);

    // OpenRouter should be called first (and succeeds → no Gemini call needed)
    assert.ok(calledUrls.length >= 1, "At least one fetch call should have been made");
    assert.ok(
      calledUrls[0].includes("openrouter.ai"),
      `First URL called should be OpenRouter, got: ${calledUrls[0]}`
    );
    assert.equal(provider, "openrouter", "Provider should be openrouter");

    // Gemini should NOT have been called since OpenRouter succeeded
    const geminiCalls = calledUrls.filter(u => u.includes("googleapis.com"));
    assert.equal(geminiCalls.length, 0, "Gemini should not be called when OpenRouter succeeds");
  } finally {
    globalThis.fetch = originalFetch;
  }
});

/**
 * (b) VLM_ALLOW_FALLBACK=false + OpenRouter fails → Gemini NEVER called.
 */
test("Phase 9 (b): VLM_ALLOW_FALLBACK=false prevents Gemini from being called on OpenRouter failure", async () => {
  const env = createTestEnv({
    GEMINI_API_KEY: "test-gemini-key",
    OPENROUTER_API_KEY: "test-openrouter-key",
    VLM_PRIMARY_PROVIDER: "openrouter",
    VLM_ALLOW_FALLBACK: "false",
    FITCAL_ENV: "dev",
  });

  const originalFetch = globalThis.fetch;
  const calledUrls = [];

  globalThis.fetch = async (url, options) => {
    calledUrls.push(url);
    if (url.includes("openrouter.ai")) {
      // OpenRouter fails
      return new Response(JSON.stringify({ error: "Service unavailable" }), {
        status: 503,
        headers: { "Content-Type": "application/json" },
      });
    }
    // Gemini would succeed if called (but it must NOT be called)
    return new Response(JSON.stringify({
      candidates: [{ content: { parts: [{ text: JSON.stringify({
        meal_name: "Gemini Meal", items: [], totals: { calories: 0, protein_g: 0, carbs_g: 0, fat_g: 0 }, estimation_notes: "test"
      }) }] } }]
    }), {
      status: 200,
      headers: { "Content-Type": "application/json" },
    });
  };

  try {
    // Should throw because fallback is disabled and primary failed
    await assert.rejects(
      async () => executeVlmFailover(env, "test prompt", undefined, undefined),
      (err) => {
        assert.ok(
          err.message.includes("VLM_ALLOW_FALLBACK=false"),
          `Error should mention VLM_ALLOW_FALLBACK=false, got: ${err.message}`
        );
        return true;
      }
    );

    // Gemini must never have been called
    const geminiCalls = calledUrls.filter(u => u.includes("googleapis.com"));
    assert.equal(geminiCalls.length, 0, "Gemini should never be called when VLM_ALLOW_FALLBACK=false");
  } finally {
    globalThis.fetch = originalFetch;
  }
});

/**
 * (c) Missing OpenRouter key + VLM_ALLOW_FALLBACK=true → Gemini used as fallback.
 */
test("Phase 9 (c): Missing OpenRouter key with fallback=true uses Gemini", async () => {
  const env = createTestEnv({
    GEMINI_API_KEY: "test-gemini-key",
    // OPENROUTER_API_KEY intentionally absent
    VLM_PRIMARY_PROVIDER: "openrouter",
    VLM_ALLOW_FALLBACK: "true",
    FITCAL_ENV: "dev",
  });

  const originalFetch = globalThis.fetch;
  const calledUrls = [];

  globalThis.fetch = async (url, options) => {
    calledUrls.push(url);
    if (url.includes("googleapis.com")) {
      // Gemini succeeds
      return new Response(JSON.stringify({
        candidates: [{ content: { parts: [{ text: JSON.stringify({
          meal_name: "Gemini Fallback Meal",
          items: [{ item: "Rice", weight_est_g: 100, calories: 130, protein_g: 2.7, carbs_g: 28, fat_g: 0.3, confidence: "high" }],
          totals: { calories: 130, protein_g: 2.7, carbs_g: 28, fat_g: 0.3 },
          estimation_notes: "Gemini fallback"
        }) }] } }]
      }), {
        status: 200,
        headers: { "Content-Type": "application/json" },
      });
    }
    // Unexpected URL
    return new Response("Not found", { status: 404 });
  };

  try {
    const { provider } = await executeVlmFailover(env, "test prompt", undefined, undefined);

    // OpenRouter should NOT have been fetched (no key → skipped)
    const openRouterCalls = calledUrls.filter(u => u.includes("openrouter.ai"));
    assert.equal(openRouterCalls.length, 0, "OpenRouter should not be called when key is missing");

    // Gemini should have been used as fallback
    const geminiCalls = calledUrls.filter(u => u.includes("googleapis.com"));
    assert.ok(geminiCalls.length >= 1, "Gemini should be called as fallback");
    assert.equal(provider, "gemini", "Provider should be gemini (fallback)");
  } finally {
    globalThis.fetch = originalFetch;
  }
});

test("checkQuotaServerSide grants Week-1 allowance (5) when first_install_date is within 7 days", async () => {
  const env = createTestEnv();
  const originalFetch = globalThis.fetch;

  globalThis.fetch = async (url, options) => {
    assert.equal(url, "https://test-project.supabase.co/rest/v1/rpc/get_scan_quota");
    assert.equal(options.headers["Accept-Profile"], "fitcal");
    return new Response(
      JSON.stringify({
        used: 3,
        bonus: 0,
        remaining: 0,
        first_install_date: new Date(Date.now() - 2 * 86400 * 1000).toISOString(),
      }),
      { status: 200, headers: { "Content-Type": "application/json" } }
    );
  };

  try {
    const check = await checkQuotaServerSide(env, "week1-user", 3);
    assert.equal(check.allowed, true);
    assert.equal(check.effectiveAllowance, 5);
  } finally {
    globalThis.fetch = originalFetch;
  }
});

test("checkQuotaServerSide denies scan when quota is exhausted", async () => {
  const env = createTestEnv();
  const originalFetch = globalThis.fetch;

  globalThis.fetch = async () => {
    return new Response(
      JSON.stringify({
        used: 5,
        bonus: 0,
        remaining: 0,
        first_install_date: new Date(Date.now() - 2 * 86400 * 1000).toISOString(),
      }),
      { status: 200, headers: { "Content-Type": "application/json" } }
    );
  };

  try {
    const check = await checkQuotaServerSide(env, "week1-user", 5);
    assert.equal(check.allowed, false);
    assert.equal(check.effectiveAllowance, 5);
  } finally {
    globalThis.fetch = originalFetch;
  }
});

