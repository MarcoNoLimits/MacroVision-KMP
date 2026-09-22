import { test } from "node:test";
import assert from "node:assert/strict";
import gateway, { consumeScanServerSide } from "../src/index.ts";

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
  return {
    VLM_CACHE: kv,
    SUPABASE_URL: "https://test-project.supabase.co",
    SUPABASE_SERVICE_ROLE_KEY: "test-service-role-key",
    REVENUECAT_WEBHOOK_SECRET: "test-revenuecat-secret",
    FITTER_ENV: "dev",
    ...overrides,
  };
}

// ─── Tests ───────────────────────────────────────────────────────────────────

test("GET /health returns 200 and healthy status", async () => {
  const env = createTestEnv();
  const req = new Request("https://gateway.fitter.app/health");
  const res = await gateway.fetch(req, env);

  assert.equal(res.status, 200);
  const data = await res.json();
  assert.equal(data.status, "healthy");
  assert.equal(data.service, "fitter-gateway");
});

test("Kill switch returns 503 when active", async () => {
  const env = createTestEnv({ FITTER_KILL_SWITCH: "true" });
  const req = new Request("https://gateway.fitter.app/health");
  const res = await gateway.fetch(req, env);

  assert.equal(res.status, 503);
  const data = await res.json();
  assert.equal(data.error, "Service temporarily unavailable");
});

test("POST /v1/analyze-meal rejects unauthenticated request with 401", async () => {
  const env = createTestEnv();
  const req = new Request("https://gateway.fitter.app/v1/analyze-meal", {
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
  const req = new Request("https://gateway.fitter.app/v1/recalculate", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ items: [{ name: "Oats", grams: 100 }] }),
  });
  const res = await gateway.fetch(req, env);

  assert.equal(res.status, 401);
  const data = await res.json();
  assert.equal(data.error, "Unauthorized");
});

test("GET /v1/entitlements rejects unauthenticated request with 401", async () => {
  const env = createTestEnv();
  const req = new Request("https://gateway.fitter.app/v1/entitlements");
  const res = await gateway.fetch(req, env);

  assert.equal(res.status, 401);
  const data = await res.json();
  assert.equal(data.error, "Unauthorized");
});

test("Malformed JWT is rejected with 401", async () => {
  const env = createTestEnv();
  const req = new Request("https://gateway.fitter.app/v1/entitlements", {
    headers: { Authorization: "Bearer this.is.an.invalid.token" },
  });
  const res = await gateway.fetch(req, env);

  assert.equal(res.status, 401);
  const data = await res.json();
  assert.equal(data.error, "Unauthorized");
});

test("Spoofed x-fitter-premium header is NOT trusted (rejected with 401)", async () => {
  const env = createTestEnv();
  const req = new Request("https://gateway.fitter.app/v1/analyze-meal", {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
      "x-fitter-premium": "true", // Spoofed client header
    },
    body: JSON.stringify({ image_base64: "base64data" }),
  });
  const res = await gateway.fetch(req, env);

  // Must still be 401 unauthorized — client header cannot bypass auth
  assert.equal(res.status, 401);
});

test("RevenueCat webhook rejects request with invalid signature", async () => {
  const env = createTestEnv();
  const req = new Request("https://gateway.fitter.app/v1/webhook/revenuecat", {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
      "X-RevenueCat-Signature": "invalid-signature-here",
    },
    body: JSON.stringify({
      event: {
        type: "INITIAL_PURCHASE",
        app_user_id: "user-123",
        entitlement_id: "fitter_premium",
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
      entitlement_id: "fitter_premium",
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

  const req = new Request("https://gateway.fitter.app/v1/webhook/revenuecat", {
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
  const kvValue = await env.VLM_CACHE.get("entitlement:user-sub-456:fitter_premium");
  assert.equal(kvValue, "active");
  assert.equal(env.VLM_CACHE.expirations.get("entitlement:user-sub-456:fitter_premium"), 3600);
});

test("RevenueCat webhook with cancellation event expires entitlement in KV", async () => {
  const secret = "test-secret-key-123";
  const env = createTestEnv({ REVENUECAT_WEBHOOK_SECRET: secret });

  const payload = JSON.stringify({
    event: {
      type: "CANCELLATION",
      app_user_id: "user-sub-456",
      entitlement_id: "fitter_premium",
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

  const req = new Request("https://gateway.fitter.app/v1/webhook/revenuecat", {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
      "X-RevenueCat-Signature": base64Signature,
    },
    body: payload,
  });

  const res = await gateway.fetch(req, env);
  assert.equal(res.status, 200);

  const kvValue = await env.VLM_CACHE.get("entitlement:user-sub-456:fitter_premium");
  assert.equal(kvValue, "expired");
});

test("consumeScanServerSide sends fitter profile headers, p_user_id, and no fake header", async () => {
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
    assert.equal(interceptedOptions.headers["Accept-Profile"], "fitter");
    assert.equal(interceptedOptions.headers["Content-Profile"], "fitter");
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

