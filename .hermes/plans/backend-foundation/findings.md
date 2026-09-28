# Findings — backend-foundation

> Append-only. Discoveries, research results, decisions, corrections.

## 2026-09-20 — Design session (Cypher + user)

### Decision: Cloudflare Worker as VLM gateway (not Supabase Edge Function)
- Worker = standalone, free 100k req/day, deploys independently, KV built-in for semantic cache. Supabase Edge Functions are fine but tie gateway to Supabase org; Worker keeps the cost firewall separate from the data plane. Either acceptable; Worker chosen for cache + rate-limit ergonomics.

### Decision: Supabase = data/identity/quota plane, NOT the VLM proxy
- Client → Worker for inference; client → Supabase (postgrest) for meals/profile/water/quota/events. Separation of concerns: Worker holds paid keys; Supabase holds RLS-scoped user data. No service_role key ever ships to client.

### Decision: anonymous-auth-first
- Cold start → anonymous JWT before any sync/quota work; later upgrade to email/Google/Apple preserves `user_id`. No login wall at onboarding (friction kills a nutrition app).

### Decision: quota authority = server RPC, client = display cache
- `consume_scan(p_allowance)` atomic upsert, security definer, search_path pinned; returns boolean allowance check. Client prefs cannot inflate quota. Bonus scans must go through server RPC (`grant_bonus_scan()`), never local writes.
- Week-1 vs Week-2 allowance computed client-side from install date but consumed server-side: server trusts `p_allowance` param for now (documented tradeoff; a future hardening step reads `first_install_timestamp` from a server table instead of trusting the client).

### KMP library: supabase-kt (io.github.jan-tennert.supabase)
- auth-kt + postgrest-kt + realtime-kt plugins, version 2.x. Replaces direct Ktor calls to postgrest; keeps MealRepository/UserRepository interfaces unchanged (swap implementation underneath).

### Sync model: outbox + updated_at merge + tombstones
- Local writes queue in outbox; flush on connectivity. `deleted_at` tombstone drives server-side prune. Reads always local-first (no latency regression).

### Telemetry: batch 50 → analytics_events insert-only RLS
- Server is source of truth; local buffers become cache. Retention D1/D7/D14/D30/D90 and ARPDAU computable from Supabase queries.

### Cost math (documented in audit)
- Supabase Free until ~500MB/50k MAU; Pro $25/mo w/ $10 credit. Worker free 100k req/day. Vertex Gemini Flash ≈ $0.00017/scan → 1000 DAU × 3 scans/day ≈ $15/mo.
## 2026-09-20 — Antigravity implementation review (Cypher)

### What landed (all uncommitted; no new git commit)
- worker/ — real Cloudflare Worker: KV semantic cache, rate limit 20 req/min, kill switch, x-goog-api-key header (no URL keys), RevenueCat webhook stub, CORS, mock response when no keys. tsc --noEmit clean.
- supabase/migrations/0001_backend_foundation.sql + 0002_analytics_events.sql — matches spec; RLS on all 4 tables + analytics insert-only policy.
- shared: supabase-kt 3.1.4 (auth-kt/postgrest-kt/realtime-kt) wired via gradle; SupabaseAuthService (anon auth), SupabaseQuotaManager, SyncEngine (outbox), SupabaseMealRepository, SupabaseUserRepository, TelemetryUploader, SubscriptionManager (RevenueCat stub).
- Wired into App.kt: anon auth on boot, server quota sync, outbox flush, telemetry flush, pullRemote.
- ScanQuotaManager: remoteQuotaManager hook, consumeScanServer(), syncQuotaFromServer(), premium bypass.

### Verified
- ./gradlew :shared:compileDebugKotlinAndroid :MacroVision-UI:compileDebugKotlinAndroid --rerun-tasks → BUILD SUCCESSFUL, 33/33 executed.
- ./gradlew :MacroVision-UI:testDebugUnitTest → green (UP-TO-DATE after Antigravity's own run).
- worker tsc --noEmit → clean. No tracked real keys. .wrangler/ + .env ignored correctly.

### CRITICAL GAP (P0): client was NOT re-routed to the gateway
- App.kt:163 still `FailoverNutritionClient(openRouterKey, geminiKey, groqKey)` — paid keys STILL compiled into the client (BuildConfig → APK). The Worker is dead code from the app's perspective. Phase 1 half done: gateway built, client rewrite skipped.

### Bugs found (incl. from code review)
1. P0 — keys still in client; gateway unreachable from app (see above). Also no GatewayNutritionClient implementation.
2. P1 — SupabaseMealRepository.pullRemote ignores eaten_at: maps every row to timestamp="12:00 PM" and date=dateKey → multi-day data corruption on pull. Also unbounded select (full table) — needs date filter + limit.
3. P1 — upsert rows lack user_id (SupabaseWaterRow has no user_id; profiles/meals rely on RLS check user_id=auth.uid()) — RLS with check will likely reject insert or write wrong tenant; needs explicit user_id or rely on default auth.uid() (meals has default; water/profiles don't).
4. P1 — SubscriptionManager.purchasePlan just sets local pref → premium entitlement is client-side theater again (task spec: RevenueCat is source of truth).
5. P2 — worker trusts `x-fitter-premium: true` header for rate-limit bypass — trivially spoofable; must verify entitlement from KV only.
6. P2 — consumeScan fallback returns true on ANY exception (offline → quota bypass ok locally, but masks auth errors).
7. P2 — worker mock meal response when no keys — fine for dev, dangerous if deployed without secrets.
8. P2 — ScanQuotaManager.addBonusScans still writes local bonus AND async server grant — race: local shows bonus before server confirms; acceptable offline-first but note it.

## 2026-09-20 — v3: dedicated `fitter` schema requirement (user directive)
- User asked: "make sure supabase has a schema DB with the necessary tables."
- Decision: ALL app tables move from `public` into a dedicated `fitter` schema (scan_quota, meals, profiles, water_intake, analytics_events, user_meta). Mirrors CRM-Tecaa's dedicated `crm` schema pattern.
- Implementation: migration 0004_schema_fitter.sql (create schema + move/recreate + ownership postgres + minimal grants), config.toml api.schemas=["fitter"], supabase-kt Postgrest pins schema="fitter", Worker resolves same schema explicitly.
- Known P1 bug (pre-existing, must fix in same run): worker consumeScanServerSide sends non-existent X-Supabase-Auth-User-Id header; consume_scan must take p_user_id param instead (auth.uid() is NULL under service_role).

## 2026-09-22 — v3 implementation review (Cypher)
- 0004_schema_fitter.sql landed: fitter schema + 6 tables moved/declared, owner postgres, minimal grants, config.toml schemas=["fitter","public"], extra_search_path includes fitter.
- Client pins schema: SupabaseClientFactory defaultSchema="fitter"; new SupabaseClientFactoryTest asserts it (1 test, green).
- P1 quota bug FIXED correctly: worker posts {p_user_id, p_allowance}; RPC honors p_user_id ONLY when auth.role()='service_role' else auth.uid(); header X-Supabase-Auth-User-Id count=0.
- Build: 44/44 tasks executed, BUILD SUCCESSFUL in 16s (Android toolchain); tsc --noEmit clean.
- Tests: 70 total, 0 failures/errors (12 suites incl. new SupabaseClientFactoryTest).
- OPEN RISK: 0004 uses PG16-only syntax 'revoke ... on all routines' / 'alter default privileges ... revoke all on routines'. Supabase local PG15/PostgREST image may reject → verify with `supabase db reset` once Docker daemon is up (was down during this review). Also live-cloud verification (curl 401, webhook) still pending credentials.

## 2026-09-22 — v3.1: backend deploys to HOSTED Supabase (user directive)
- Migration target is the ONLINE Supabase project, not a local stack. Docker/local `db reset` paths removed from spec.
- New Phase 2c: MCP / `supabase link` + `db push` against hosted project; PG version check first (0004 uses PG16 `routines` syntax); idempotency against hand-applied history; live verification of fitter.* tables, RLS, RPC signatures.
- Alistair's profile owns the Supabase MCP (mcp.supabase.com) but user prefers Antigravity (IDE) to execute; Alistair stood down.

## 2026-09-23 — Production incident: "Authentication required" + v4 plan (Cypher)
- Screenshot OCR'd (vision provider broken; used RapidOCR): "Could not analyze image / API DIAGNOSTICS LOG / Authentication required / RetakePhoto ResendPhoto Cancel".
- ROOT CAUSE (verified in code): App.kt:123-131 boot ensureSignedIn() fails -> catch swallows -> userId=null -> app continues; App.kt:160 jwtProvider=currentSession()?.accessToken=null; GatewayNutritionClient:137-138 null JWT -> AnalyzeResult.AuthRequired -> "Authentication required". The 401 came from the worker rejecting a scan with no/placeholder JWT (also likely placeholder SUPABASE_URL in build since anon sign-in never completed).
- SECONDARY: worker provider order was Gemini-first, OpenRouter fallback — inverse of product desire.
- DECISION: v4 Phases 9+10. Phase 9: VLM_PRIMARY_PROVIDER=openrouter env-driven priority, VLM_ALLOW_FALLBACK, X-Provider response header, provider-order worker tests. Phase 10: boot gate+queue with backoff, OfflineMode UI state, auto re-auth + retry on 401 in GatewayNutritionClient (reAuthenticator callback), ensureReadyForScan() status enum, kill placeholder defaults (build fails on placeholder), X-Debug-Code header.

## 2026-09-23 — v4 review (Cypher): Antigravity did Phases 9+10; I found+fixed the wiring gap
- AI did: worker env routing (VLM_PRIMARY_PROVIDER/VLM_ALLOW_FALLBACK, X-Provider, provider-order tests 11->14), App.kt boot gate w/ backoff + authReady + recovery observer, ScanGate.kt + CameraScreen states, ensureSignedIn(maxAttempts,backoffs,onAttemptFailed), client reAuth+retry(1) logic, X-Debug-Code: auth_required, Gradle fails on placeholder configs, SupabaseClientFactory  require() against placeholder.
- NEW TESTS: GatewayNutritionClientTest (6: 401->reauth->retry, reauth-fail->AuthRequired, null-JWT no-callback->immediate), SupabaseAuthServiceTest (4), ScanGateTest (5). Worker 14/14, Gradle 44/44, total 88 tests 0 fail.
- GAP FOUND + FIXED BY ME: App.kt constructed GatewayNutritionClient WITHOUT reAuthenticator — re-auth machinery was dead code; a null/expired JWT still dead-ended at "Authentication required" exactly like the incident. FIX: pass reAuthenticator = { authService.ensureSignedIn() } (App.kt ~line 240). Verified BUILD SUCCESSFUL with the wiring; 88/0/0.
