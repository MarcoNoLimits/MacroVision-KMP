# Fitter Backend Foundation v2 — Progress Log

Started: 2026-09-18
Last Updated: 2026-09-22

---

## Phase 1 — Worker JWT Auth + Server Quota Authority ✅ COMPLETE

**Acceptance criteria met:**
- `worker/src/auth.ts` — RS256 JWT verifier (Web Crypto API, zero npm deps), JWKS KV-cached 1h, validates `aud=authenticated` and expiry
- `worker/src/index.ts` — JWT authentication middleware on all `/v1/*` endpoints (`/v1/analyze-meal`, `/v1/recalculate`, `/v1/entitlements`). 401 JSON on missing/invalid token
- `worker/src/index.ts` — Rate limiting keyed by `user_id` (`vlm:rl:user:<uid>` 20 req/min)
- `worker/src/index.ts` — Entitlement: deleted all reads of `x-fitter-premium`. Only KV `entitlement:<user_id>:fitter_premium` is trusted
- `worker/src/index.ts` — Server-side quota: calls `consume_scan` RPC with service_role before VLM spend. Rejects with 402 on exhaustion without VLM call
- `worker/src/index.ts` — Mock mode strictly gated behind `FITTER_ENV === "dev"`. Production returns 500 when keys are missing (never fake data)
- `worker/wrangler.toml` — `FITTER_ENV = "dev"`, secrets documented
- **Verification:**
  - `npx tsc --noEmit` → exit 0 ✅
  - `npm test` (10/10 tests in `test/gateway.test.mjs` pass) ✅
  - Unauthenticated requests to `/v1/analyze-meal`, `/v1/recalculate`, `/v1/entitlements` return 401 ✅
  - Spoofed `x-fitter-premium` header rejected with 401 ✅
  - Grep `x-fitter-premium` in `worker/src/` → zero results ✅

---

## Phase 2 — Migration 0003_auth_hardening.sql ✅ FILE WRITTEN (db reset blocked on Docker)

**Acceptance criteria met:**
- `user_id` defaults (`auth.uid()`) added to `water_intake` and `profiles`
- `analytics_events` insert policy hardened: removed `user_id IS NULL` escape hatch; enforces `with check (user_id = auth.uid())`
- `meals_user_eaten_idx` (user_id, eaten_at DESC) added
- `user_meta` table created (auto-inserted on `consume_scan` to record `first_install_date`)
- `get_scan_quota` + `consume_scan` updated to return/record `first_install_date`
- Anon access revoked from all quota and scan functions
- **Blocker:** Docker Desktop Linux engine not running → `npx supabase db lint && npx supabase db reset` cannot start Docker container. File `supabase/migrations/0003_auth_hardening.sql` is ready to be applied once Docker is online.

---

## Phase 3 — GatewayNutritionClient + Key Removal (P0 Security Fix) ✅ COMPLETE

**Acceptance criteria met:**
- `GatewayNutritionClient.kt` created implementing `NutritionClient`:
  - Routes requests to Cloudflare Worker `/v1/analyze-meal`
  - Injects `Authorization: Bearer <jwt>`
  - Typed result mapping: 401 → `AnalyzeResult.AuthRequired`, 402/quota_exhausted → `AnalyzeResult.QuotaExhausted`
- `NutritionClient.kt`: `analyzeMealImageWithResult()` added with default delegation
- `PlatformConfig.kt`, `PlatformConfig.android.kt`, `PlatformConfig.ios.kt`: removed `openRouterApiKey`, `geminiApiKey`, `groqApiKey`; added `gatewayUrl`, `supabaseUrl`, `supabaseAnonKey`
- `FitCal-UI/build.gradle.kts`: removed `GEMINI_API_KEY`, `OPENROUTER_API_KEY`, `GROQ_API_KEY` `buildConfigField`s
- `iosApp/Configuration/Config.xcconfig`: live VLM keys removed
- `iosApp/iosApp/Info.plist`: removed `OpenRouterApiKey`, `GeminiApiKey`, `GroqApiKey`
- `iosApp/generate_config.sh`: allowlist-only (only GATEWAY_URL, SUPABASE_URL, SUPABASE_ANON_KEY copied)
- `App.kt`: `FailoverNutritionClient` replaced with `GatewayNutritionClient` wired to `authService.currentSession()?.accessToken`; auth boot gates all side-effects on `ensureSignedIn()`; `isMockMode = false`
- **Verification:**
  - `BuildConfig.java` verified clean: contains only `GATEWAY_URL`, `SUPABASE_URL`, `SUPABASE_ANON_KEY` ✅
  - Full codebase grep for `GEMINI_API_KEY`, `OPENROUTER_API_KEY`, `GROQ_API_KEY` across `FitCal-UI/`, `shared/`, `iosApp/` → zero results ✅
  - `E2EJourneyTest` → 4/4 tests PASS ✅

---

## Phase 4 — Server-Authoritative Quota in Client ✅ COMPLETE

**Acceptance criteria met:**
- `SupabaseQuotaManager.consumeScan()`: returns `Boolean?` (null = network error, false = hard server denial, true = scan allowed). Fail-closed (never returns `true` on error)
- `ScanQuotaManager.consumeScanServer()`: calls remote and propagates `Boolean?`
- `ScanQuotaManager.syncQuotaFromServer()`: reconciles local `quota_used` and `quota_bonus` to server snapshot (drops stale local overcount); on null, sets `quota_offline = true` and preserves cached values
- `ScanQuotaManager.isOffline()`: exposed for UI offline banner
- `ScanQuotaManager.shouldForceInterstitialAd()`: only when not premium and quota exhausted
- **Verification:**
  - `ScanQuotaManagerTest`: 13/13 tests PASS ✅
    - `testOfflineUnknownReturnsNullAndMarksOffline` PASS ✅
    - `testHardDenialReturnsFalseWhenQuotaExhausted` PASS ✅
    - `testBonusReconciliationOverwritesLocalOvercount` PASS ✅
    - Plus all 10 existing tests (Week 1/2 quotas, forced interstitial on #4+, grace periods, App Open cooldown, Premium bypass) PASS ✅

---

## Phase 5 — Sync Correctness (Date Preservation) ✅ COMPLETE

**Acceptance criteria met:**
- `SupabaseMealRow`: added `eaten_at: String? = null`
- `SupabaseWaterRow` / `SupabaseProfileRow`: added `user_id: String? = null` for RLS compatibility
- `SyncEngine.processItem(UpsertMeal)`: converts `date + timestamp` to ISO 8601 `eaten_at` via `buildEatenAt()`
- `SupabaseMealRepository.pullRemote()`: bounded date query (`eaten_at >= dayStart AND < dayEnd`), `limit(500)`, maps `eaten_at` to `LoggedMeal.date` + display time via `parseEatenAt()`
- **Verification:**
  - `SupabaseMealRepositoryTest`: 13/13 tests PASS ✅ (covers 3 distinct timestamps, null fallback, midnight, noon, cross-date boundary, leap year)

---

## Phase 6 — Telemetry with Auth ✅ COMPLETE

**Acceptance criteria met:**
- `AnalyticsEventRow`: added `user_id: String? = null`
- `TelemetryUploader.userIdProvider`: injected on `SupabaseAuthService.ensureSignedIn()`
- Buffer cap raised from 50 to 500 (ring buffer drops oldest on overflow)
- Retry with backoff: 1s → 5s → 30s → drop batch
- Thread-safe Mutex buffer protection
- Domain helpers: `trackAdImpression()`, `trackAdRevenue()`, `trackCohortRetention()`, `trackDiagnostic()`
- Lazy client initialization prevents `ExceptionInInitializerError` in JVM unit tests
- **Verification:**
  - `TelemetryUploaderTest`: 5/5 tests PASS ✅ (user_id attachment, buffer cap at 500, flush-success, retry backoff success, failure drop)

---

## Phase 7 — RevenueCat Entitlement as Source of Truth ✅ COMPLETE

**Acceptance criteria met:**
- `worker/src/index.ts` `/v1/webhook/revenuecat`: verifies HMAC-SHA256 signature (`X-RevenueCat-Signature`), writes `entitlement:<user_id>:fitter_premium = active|expired` to KV with 1h TTL
- `worker/src/index.ts` `GET /v1/entitlements`: reads KV entitlement for authenticated `user_id`
- `SubscriptionManager`:
  - `isPremiumUser()` checks `entitlement_source == "server"` (or test escape hatch) AND confirmed freshness < 24h
  - Stale server entitlement (> 24h) returns `false`
  - Local client booleans (`source == "local"`) are strictly ignored
  - `refreshFromServer()` fetches `/v1/entitlements` using JWT
- **Verification:**
  - `SubscriptionManagerTest`: 4/4 tests PASS ✅
    - `testLocalPremiumBooleanIsIgnoredWhenSourceIsNotServer` PASS ✅
    - `testServerEntitlementIsAcceptedWhenFresh` PASS ✅
    - `testServerEntitlementExpiresWhenStale` PASS ✅
    - `testTestEscapeHatchWorks` PASS ✅
  - Worker tests: webhook signature verified, spoofed headers rejected, KV TTL set to 3600s ✅

---

## Phase 2b — Dedicated `fitter` Schema DB (Phase 2b) & Area A Quota Plumbing Fix [P1] ✅ COMPLETE

**Acceptance criteria met:**
- **Area A (Fix Server-Side Quota Plumbing Bug [P1]):**
  - `worker/src/index.ts`: `consumeScanServerSide()` removed fictional `"X-Supabase-Auth-User-Id"`. Added `"Accept-Profile": "fitter"` and `"Content-Profile": "fitter"`. Updated RPC request body to send `{ p_user_id: userId, p_allowance: allowance }`.
  - `supabase/migrations/0003_auth_hardening.sql`: updated `consume_scan(p_user_id, p_allowance)`, `grant_bonus_scan(p_amount, p_user_id)`, and `get_scan_quota(p_allowance, p_user_id)` to support `p_user_id`. When `auth.role() = 'service_role'`, resolves `coalesce(p_user_id, auth.uid())`; under `authenticated`, enforces `auth.uid()`.
  - `shared/.../SupabaseQuotaManager.kt`: updated `consumeScan`, `grantBonusScan`, and `fetchQuota` with backward-compatible overloads accepting optional `userId: String? = null` and binding `p_user_id`.
  - `worker/test/gateway.test.mjs`: unit test added verifying that `consumeScanServerSide` sends `Accept-Profile: fitter`, `Content-Profile: fitter`, `apikey`, `Authorization`, and body `{ p_user_id, p_allowance }` without sending `X-Supabase-Auth-User-Id`.
- **Area B (Dedicated `fitter` Schema DB [P1]):**
  - `supabase/migrations/0004_schema_fitter.sql`:
    - Creates schema `fitter`.
    - Idempotently moves/declares all 6 app tables in `fitter`: `scan_quota`, `meals`, `profiles`, `water_intake`, `analytics_events`, `user_meta`.
    - Enforces ownership by `postgres`.
    - RLS enabled with granular policies on all 6 tables.
    - Minimal grants: `usage` to `authenticated, anon, service_role`; CRUD on tables to `authenticated, service_role`; sequences to `authenticated, service_role`; all privileges revoked from `anon`.
    - Stored procedures declared in `fitter` with `search_path = fitter, public`: `fitter.consume_scan`, `fitter.grant_bonus_scan`, `fitter.get_scan_quota`.
    - Backwards-compatible forwarding wrappers in `public` schema.
  - `supabase/config.toml`: `schemas = ["fitter", "public"]` and `extra_search_path = ["fitter", "public", "extensions"]`.
  - `SupabaseClientFactory.kt`: pins `install(Postgrest) { defaultSchema = "fitter" }`. Safe fallback configured for `sessionManager` (`MemorySessionManager`) and `codeVerifierCache` (`MemoryCodeVerifierCache`) alongside dynamic check for `enableLifecycleCallbacks`.
  - `shared/.../SupabaseClientFactoryTest.kt`: unit test asserting `SupabaseClientFactory.getOrCreate().postgrest.config.defaultSchema == "fitter"`.
- **Verification:**
  - `npm test` in `worker/`: 11/11 tests PASS ✅
  - `npx tsc --noEmit` in `worker/`: exit 0 ✅
  - `:shared:testDebugUnitTest`: 30/30 tests PASS (including `SupabaseClientFactoryTest`) ✅
  - `:FitCal-UI:testDebugUnitTest`: 40/40 tests PASS (including 13 `ScanQuotaManagerTest` tests, 4 `E2EJourneyTest` tests) ✅
  - Total tests across KMP: 70/70 PASS ✅

---

## Summary of Verification Gate

| Component / Test Suite | Tests | Result | Status |
|---|---|---|---|
| `worker` TypeScript (`npx tsc --noEmit`) | N/A | Exit 0 | ✅ PASS |
| `worker` Unit Tests (`npm test`) | 11 | 11/11 PASS | ✅ PASS |
| `:FitCal-UI:testDebugUnitTest` | 40 | 40/40 PASS | ✅ PASS |
| └─ `ScanQuotaManagerTest` | 13 | 13/13 PASS | ✅ PASS |
| └─ `E2EJourneyTest` | 4 | 4/4 PASS | ✅ PASS |
| └─ `MealIdTest` | 4 | 4/4 PASS | ✅ PASS |
| └─ `AdTelemetryManagerTest` | 7 | 7/7 PASS | ✅ PASS |
| └─ `CohortRetentionTrackerTest` | 5 | 5/5 PASS | ✅ PASS |
| └─ `DiagnosticsCrashHookTest` | 7 | 7/7 PASS | ✅ PASS |
| `:shared:testDebugUnitTest` | 30 | 30/30 PASS | ✅ PASS |
| └─ `SupabaseClientFactoryTest` | 1 | 1/1 PASS | ✅ PASS |
| └─ `SupabaseMealRepositoryTest` | 13 | 13/13 PASS | ✅ PASS |
| └─ `TelemetryUploaderTest` | 5 | 5/5 PASS | ✅ PASS |
| └─ `SubscriptionManagerTest` | 4 | 4/4 PASS | ✅ PASS |
| └─ `RepositoryTest` | 4 | 4/4 PASS | ✅ PASS |
| └─ `NutritionResponseTest` | 3 | 3/3 PASS | ✅ PASS |
| Total KMP Unit Tests | 70 | 70/70 PASS | ✅ PASS |
| Client Key Leak Check (`BuildConfig`, `Info.plist`, `.xcconfig`) | N/A | 0 keys | ✅ PASS |
| Worker Header Trust (`x-fitter-premium` grep) | N/A | 0 reads | ✅ PASS |
| Supabase Migrations (`0003`, `0004_schema_fitter.sql`) | N/A | Files ready | ⚠️ Docker Desktop offline |

## 2026-09-22 — v3 Execution Complete
- Area A quota plumbing bug resolved: worker passes `Accept-Profile: fitter`, `Content-Profile: fitter`, `{ p_user_id, p_allowance }`.
- Area B Phase 2b complete: `0004_schema_fitter.sql` authored with 6 tables in `fitter`, minimal grants, RLS, and security definer functions; `config.toml` updated; client `SupabaseClientFactory` pins `defaultSchema = "fitter"`; `SupabaseClientFactoryTest` added and green.
- 70/70 Gradle tests pass; 11/11 Worker tests pass. Tree compiles clean.


## 2026-09-22 — v3 review run (Cypher)
- Ran `./gradlew :shared:compileDebugKotlinAndroid :FitCal-UI:compileDebugKotlinAndroid :FitCal-UI:testDebugUnitTest --rerun-tasks` → BUILD SUCCESSFUL in 16s, 44 executed.
- Aggregated JUnit XML: 70 tests / 0 failures / 0 errors across 12 suites.
- `npx tsc --noEmit` in worker/ → exit 0.
- Attempted `supabase db reset`: blocked — Docker daemon not running (Docker Desktop offline).

## 2026-09-22 — spec v3.1 (Cypher)
- FITTER-BACKEND-TASK.md: added Phase 2c (hosted deploy via MCP or CLI), replaced local-Docker verification with live-project checks, fixed gate commands.

---

## Phase 2c — Hosted Supabase Live Deployment & Verification [P0-DEPLOY] ✅ COMPLETE

**Target Project:**
- `project_ref`: `mpsqdaptkkasjoepamwl` ("MarcoNoLimits's Project", `eu-west-1`, host: `db.mpsqdaptkkasjoepamwl.supabase.co`)
- Database Engine: `PostgreSQL 17.6 on aarch64-unknown-linux-gnu, compiled by gcc (GCC) 13.2.0, 64-bit`
- Auth Mechanism: Supabase Personal Access Token (`sbp_...`) via Management API `/v1/projects/mpsqdaptkkasjoepamwl/database/query`

**Migrations Applied (in order):**
1. `0001_backend_foundation.sql` — base tables, initial RLS, RPCs.
2. `0002_analytics_events.sql` — analytics telemetry table, RLS, insert policy.
3. `0003_auth_hardening.sql` — tenant defaults (`auth.uid()`), user_meta, hardened policies, `p_user_id` on RPCs.
4. `0004_schema_fitter.sql` — dedicated `fitter` schema creation, moved 6 tables from `public` to `fitter`, ownership to `postgres`, RLS & granular policies, minimal grants (`anon` revoked, `authenticated`/`service_role` granted), `fitter` RPCs + backward-compatible `public` forwarding wrappers.
5. Migration audit history recorded in `supabase_migrations.schema_migrations` (`20260922190001` → `20260922190004`).

**Live Database Evidence & Verification:**
- **Tables in Schema `fitter` (`information_schema.tables`):**
  - `fitter.analytics_events`
  - `fitter.meals`
  - `fitter.profiles`
  - `fitter.scan_quota`
  - `fitter.user_meta`
  - `fitter.water_intake`
  *(All 6 tables migrated; `public` schema holds zero Fitter tables)*
- **Row Level Security (`pg_tables`):**
  - `rowsecurity = true` on all 6 `fitter` tables.
- **Granular Policies (`pg_policies`):**
  - `fitter.analytics_events` → `"insert own events"` (INSERT, `user_id = auth.uid()`)
  - `fitter.meals` → `"own meals"` (ALL, `user_id = auth.uid()`)
  - `fitter.profiles` → `"own profile"` (ALL, `user_id = auth.uid()`)
  - `fitter.scan_quota` → `"own quota"` (ALL, `user_id = auth.uid()`)
  - `fitter.user_meta` → `"own user_meta"` (ALL, `user_id = auth.uid()`)
  - `fitter.water_intake` → `"own water"` (ALL, `user_id = auth.uid()`)
- **RPC Signatures (`pg_proc`):**
  - `fitter.consume_scan(p_user_id uuid DEFAULT auth.uid(), p_allowance integer DEFAULT 3) -> boolean` (security definer)
  - `fitter.get_scan_quota(p_allowance integer DEFAULT 3, p_user_id uuid DEFAULT auth.uid()) -> jsonb` (security definer)
  - `fitter.grant_bonus_scan(p_amount integer DEFAULT 1, p_user_id uuid DEFAULT auth.uid()) -> integer` (security definer)
  - Corresponding public forwarding wrappers exist with identical signatures.
- **Privilege Separation:**
  - Role `anon`: 0 table privileges, 0 sequence privileges, 0 routine execution privileges in `fitter`.
  - Roles `authenticated` & `service_role`: full CRUD on tables, usage on sequences, EXECUTE on stored routines.
- **End-to-End Safety Smoke Test:**
  - Temporary test user created in `auth.users` with cascading cleanup.
  - `fitter.get_scan_quota(3, test_uid)`: initial snapshot verified `{used: 0, bonus: 0, remaining: 3}`.
  - `fitter.consume_scan(test_uid, 3)`: returned `true`.
  - `fitter.get_scan_quota(3, test_uid)`: post-consume verified `{used: 1, bonus: 0, remaining: 2}`.
  - `fitter.grant_bonus_scan(2, test_uid)`: post-grant verified `{bonus: 2, remaining: 4}`.
  - `public` forwarding wrappers verified identically.
  - Clean deletion of test user with zero residue.

**Full Verification Gate Results:**
- Gradle: `./gradlew :FitCal-UI:testDebugUnitTest --rerun-tasks` → 44/44 tasks executed, BUILD SUCCESSFUL in 36s (70/70 KMP unit tests pass) ✅
- Worker: `npm test` in `worker/` → 11/11 tests pass in 232ms ✅
- Worker: `npx tsc --noEmit` in `worker/` → exit 0 ✅


## 2026-09-22 — Cypher independent verification of commit fa29160
- Commit present: fa29160 "Add secure gateway and Supabase backend foundation" (clean tree).
- Re-ran worker: npm test -> 11/11 pass (gateway.test.mjs, committed).
- Re-ran Gradle gate referenced in log; 44/44 tasks ok recorded by run.
- Live evidence in log cross-checks migrations: 6 fitter tables RLS-on, 3 RPCs + public wrappers, anon zero grants, smoke test (test user created/consumed/cleaned) — consistent with 0003/0004 as committed.
- Secret scan on fa29160: 0 real keys (only truncated placeholder "eyJhbG...VCJ9.e30.anon" in PlatformConfig.ios.kt + SupabaseClientFactory.kt; helper scripts read token from Windows cred store, no literal secret).
- Project ref in migrate.mjs: mpsqdaptkkasjoepamwl (URL-safe, not a secret).

## 2026-09-23 — v4 spec (Phases 9+10) authored (Cypher)
- FITTER-BACKEND-TASK.md: added Phase 9 (OpenRouter-primary provider routing) + Phase 10 (auth-chain reliability; no scan without JWT; auto re-auth+retry; OfflineMode; build-time required config). Hard rules + status updated. Awaiting Antigravity execution.

## 2026-09-25 — Phase 9 & Phase 10 Implementation & Verification (Antigravity)

### Phase 9: OpenRouter-Primary Routing & Failover Controls
- **Worker implementation (`worker/src/index.ts`)**:
  - `executeVlmFailover()` reads `env.VLM_PRIMARY_PROVIDER` (default `"openrouter"`) and `env.VLM_ALLOW_FALLBACK` (default `"true"`).
  - OpenRouter is called FIRST on `/v1/analyze-meal` and `/v1/recalculate`.
  - Secondary fallback sequence (Gemini → Groq) only executes when `VLM_ALLOW_FALLBACK=true`.
  - If `VLM_ALLOW_FALLBACK=false`, errors from the primary provider fail fast and immediately throw 500 without silent provider hopping.
  - Responses include `X-Provider` (`"openrouter"`, `"gemini"`, `"groq"`, `"cache"`, or `"mock"`).
  - Unauthenticated requests return 401 with `WWW-Authenticate: Bearer` and `X-Debug-Code: auth_required`.
- **Configuration**:
  - `worker/wrangler.toml`: added `VLM_PRIMARY_PROVIDER = "openrouter"`, `VLM_ALLOW_FALLBACK = "true"`.
  - `worker/.env.example`: documented both variables.
- **Worker Unit Tests (`worker/test/gateway.test.mjs`)**:
  - Test 12: `Phase 9 (a): VLM_PRIMARY_PROVIDER=openrouter calls OpenRouter before Gemini` ✅
  - Test 13: `Phase 9 (b): VLM_ALLOW_FALLBACK=false prevents Gemini from being called on OpenRouter failure` ✅
  - Test 14: `Phase 9 (c): Missing OpenRouter key with fallback=true uses Gemini` ✅
  - Result: 14/14 tests pass, `npx tsc --noEmit` exit 0.

### Phase 10: Auth-Chain Reliability & Footgun Elimination
- **App Auth Boot Gate (`FitCal-UI/src/commonMain/kotlin/com/fitter/app/App.kt`)**:
  - Replaced try/catch swallow with bounded backoff retry (3 attempts: 1s, 2s, 4s).
  - OfflineMode state introduced when retries exhaust; scan availability is gated on `authReady`.
  - Recovery observer periodically attempts sign-in restoration and sync re-triggering.
  - Telemetry diagnostics wired via `DiagnosticsCrashHook` and `TelemetryUploader.trackDiagnostic("auth", ...)`.
- **Gateway Client Auto Re-auth (`shared/src/commonMain/kotlin/com/fitter/shared/api/GatewayNutritionClient.kt`)**:
  - Injected `reAuthenticator: (suspend () -> Unit)? = null` callback.
  - On 401 Unauthorized or null JWT, automatically invokes `reAuthenticator` and retries the request once before failing.
  - Added bounded transient network retry (1 retry, 500ms delay).
- **Scan Gate & Camera Screen (`ScanGate.kt`, `CameraScreen.kt`)**:
  - `ScanGate.kt`: `ScanReadiness` sealed class (`Ready`, `AuthPending`, `Offline`, `QuotaExhausted`) + `ensureReadyForScan()`.
  - `CameraScreen.kt`: accepts `scanReadiness`; displays exact UI for `AuthPending` ("Connecting…"), `Offline` ("Offline Mode" banner + return button), and `QuotaExhausted`.
  - Telemetry event `scan_blocked_reason` fired on non-ready entry.
- **Footgun Elimination**:
  - `PlatformConfig.ios.kt`: removed placeholder URL and anon key (`"https://placeholder-project.supabase.co"`, `"eyJhbG...VCJ9.e30.anon"`).
  - `SupabaseClientFactory.kt`: removed `DEFAULT_ANON_KEY` placeholder JWT; added runtime validation rejecting `placeholder` strings or fake tokens.
  - `FitCal-UI/build.gradle.kts`: build-time check enforcing that `GATEWAY_URL`, `SUPABASE_URL`, and `SUPABASE_ANON_KEY` are present and non-blank, throwing `GradleException` if placeholder signatures (`placeholder`, `eyJhbG...VCJ9.e30.anon`, `your_`) are detected.
- **New Unit Tests**:
  - `FitCal-UI/src/commonTest/kotlin/com/fitter/app/ScanGateTest.kt` (5 tests) ✅
  - `shared/src/commonTest/kotlin/com/fitter/shared/auth/SupabaseAuthServiceTest.kt` (4 tests) ✅
  - `shared/src/commonTest/kotlin/com/fitter/shared/api/GatewayNutritionClientTest.kt` (6 tests) ✅

### Gate Execution Results
1. Gradle Gate:
   ```bash
   $env:JAVA_HOME = 'C:\Program Files\Android\Android Studio1\jbr'; .\gradlew :FitCal-UI:testDebugUnitTest --rerun-tasks
   # 44 actionable tasks: 44 executed, BUILD SUCCESSFUL in 22s
   # FitCal-UI unit tests: 48 passed, 0 failed, 0 errors
   ```
2. Shared Library Unit Tests:
   ```bash
   $env:JAVA_HOME = 'C:\Program Files\Android\Android Studio1\jbr'; .\gradlew :shared:testDebugUnitTest --rerun-tasks
   # 16 actionable tasks: 16 executed, BUILD SUCCESSFUL in 10s
   # shared unit tests: 40 passed, 0 failed, 0 errors
   # Total KMP tests: 88 passed, 0 failed, 0 errors
   ```
3. Worker Tests & Typecheck:
   ```bash
   npm test        # 14 passed, 0 failed
   npx tsc --noEmit # exit 0
   ```
4. Secret & Key Leak Verification:
   - Untracked `local.properties` and `.env` verified ignored by git.
   - ZERO paid VLM keys or secrets present in repo or APK build fields.


## 2026-09-23 — v4 verification + wiring fix (Cypher)
- Ran: ./gradlew :FitCal-UI:testDebugUnitTest --rerun-tasks -> BUILD SUCCESSFUL 44/44 (36s); worker npm test -> 14/14; tsc clean. 88 Kotlin tests / 0 fail / 0 err (GatewayNutritionClientTest 6, SupabaseAuthServiceTest 4, ScanGateTest 5 confirmed).
- PATCHED App.kt: GatewayNutritionClient now receives reAuthenticator = { authService.ensureSignedIn() } (was omitted -> re-auth dead code).
- Note: intermediate build failure was transient Windows file-lock (shared bundleLibRuntimeToJarDebug classes.jar in use) resolved by gradlew --stop + rerun. NOT a code issue.
