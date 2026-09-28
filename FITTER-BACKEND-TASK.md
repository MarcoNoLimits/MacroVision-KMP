# FITTER — Backend Foundation Task (v2)

> **Repo:** `C:\GitHub\Fitter` (Kotlin Multiplatform: `:shared`, `:MacroVision-UI`, `iosApp`)
> **Goal:** Turn Fitter into a fully functioning, independent app with **proper authentication** — no client-side secrets, authenticated gateway calls, server-enforced quotas, account identity, sync, telemetry.
> **Status:** v2 (auth-first) was implemented by a prior IDE run: Worker JWT auth, server-side quota, key removal, GatewayNutritionClient all landed and **compile green (44/44 tasks) with 69 tests passing**. Remaining: the `consume_scan(p_user_id)` service-role plumbing bug (P1), live-credentials verification, and this doc's **Phase 2b (dedicated `fitter` schema DB)**, which is NOT yet implemented.
>
> **v3:** committed `fa29160` — dedicated `fitter` schema applied to HOSTED Supabase + quota bug fixed; verified live (6 tables, RLS, RPCs, smoke test). Full backend foundation is live.
>
> **v4 (current):** production incident 2026-09-23 — scan showed "Authentication required" (worker 401, JWT missing at scan time). Phases 9 + 10 added: all image scans route via **OpenRouter**, and the auth chain is hardened (no scan without JWT, auto re-auth + retry, explicit offline mode, build-time required config).

---

## 1. Non-negotiable architecture

```
┌─ Android / iOS (KMP, offline-first)
│  ├─ :shared GatewayNutritionClient  ← posts to Worker with Supabase JWT (Bearer)
│  ├─ supabase-kt Auth (anon → upgradeable to email/Google/Apple)
│  ├─ Local store stays primary (SQLDelight later; SharedPreferences acceptable now)
│  └─ Sync engine (outbox → Supabase PostgREST, authenticated)
│
├─ Cloudflare Worker  ← the ONLY holder of paid VLM keys + quota authority
│  POST /v1/analyze-meal   (requires valid Supabase JWT)
│  POST /v1/recalculate    (requires valid Supabase JWT)
│  GET  /v1/entitlements   (requires valid Supabase JWT)
│  POST /v1/webhook/revenuecat (verified webhook secret, not user JWT)
│  → validates JWT against Supabase JWKS
│  → checks entitlement from KV (RevenueCat webhook writes; NEVER trusts client header)
│  → consumes server-side quota via Supabase service_role (RPC consume_scan)
│  → rate limits per user_id + device_id
│  → semantic KV cache → VLM call (x-goog-api-key header) → validated JSON response
│
└─ Supabase
   ├─ Auth: anonymous sign-in (JWT), optional upgrade to email/OAuth
   ├─ Postgres: dedicated "fitter" schema holding ALL app tables —
   │  fitter.scan_quota, fitter.meals, fitter.profiles, fitter.water_intake,
   │  fitter.analytics_events, fitter.user_meta  (+ RLS on every table)
   │  client (supabase-kt Postgrest) pins schema = "fitter"; public is untouched
   └─ service_role key: used ONLY by the Worker (never ships in app)
```

**Authentication contract (the part v1 got wrong):**
- The mobile app authenticates **anonymously** at first launch (no email wall). Supabase issues a JWT bound to a real `auth.users` row.
- **Every** worker call carries `Authorization: Bearer <supabase JWT>`. The Worker verifies the JWT signature against the project's JWKS (`/.well-known/jwks.json`) and rejects without it (401).
- The **client never talks to the VLM providers.** It only ever talks to the Worker (authenticated) and Supabase PostgREST (JWT, RLS-scoped).
- **Entitlement truth is the RevenueCat webhook → KV cache.** The Worker reads entitlement from KV only. There is NO `x-fitter-premium` header trust, no client-set premium boolean that grants server privileges.
- Device attestation (Google Play Integrity / Apple App Attest via Firebase App Check) is a **stretch goal** — wire the shape now (`x-device-attestation` header accepted + logged), enforce later.

---

## 2. Current state (v1, verified by review)

| Area | State |
|---|---|
| `worker/src/index.ts` | ✅ Real Worker: KV cache, rate limit, kill switch, `x-goog-api-key`, RevenueCat webhook stub, CORS, mock mode. `tsc --noEmit` clean |
| `supabase/migrations/0001` + `0002` | ✅ RLS on scan_quota/meals/profiles/water_intake/analytics_events; consume_scan/grant_bonus_scan/get_scan_quota RPCs |
| supabase-kt 3.1.4 | ✅ In `:shared` (auth-kt, postgrest-kt, realtime-kt) |
| `SupabaseAuthService`, `SupabaseQuotaManager`, `SyncEngine`, `SupabaseMealRepository`, `SupabaseUserRepository`, `TelemetryUploader`, `SubscriptionManager` | ✅ Scaffolded, compiled 33/33 tasks |
| Wired into `App.kt` | ✅ anon auth on boot, quota sync, outbox flush, telemetry flush, pullRemote |
| **Gateway client** | ✅ `GatewayNutritionClient` with typed `AnalyzeResult`; wired into `App.kt` (v2) |
| **Worker auth** | ✅ `auth.ts` (JWKS/RS256/exp/aud) + 401; `x-fitter-premium` removed (v2) |
| **Server-side quota authority** | ⚠️ Worker calls `consume_scan` via service_role, but sends fake `X-Supabase-Auth-User-Id` header (P1 bug) — RPC must take `p_user_id` param |
| **Dedicated schema DB** | ❌ **MISSING (v3)** — all tables still in `public`; must move to `fitter` schema (Phase 2b) |

---

## 3. Defects to fix (from formal review)

1. **P0 — Client still embeds paid keys** (`BuildConfig.GEMINI_API_KEY` etc. → APK; iOS `Config.xcconfig` → Info.plist). Phase 1 of v1 never finished: no `GatewayNutritionClient` exists.
2. **P0 — Worker has no authentication.** Add Supabase JWT verification; reject unauthenticated inference; remove `x-fitter-premium` header trust entirely (entitlement from KV only).
3. **P1 — Worker does not enforce quota.** `consume_scan` is client-callable; a modified client bypasses it. Worker must call the RPC with service_role before spending VLM money; client display becomes optimistic.
4. **P1 — Sync corrupts dates.** `SupabaseMealRepository.pullRemote` sets `timestamp="12:00 PM"`, `date=dateKey` for every row; `eaten_at` never mapped; unbounded select.
5. **P1 — RLS tenant gap.** `SupabaseWaterRow`/`SupabaseProfileRow` lack `user_id`; only `meals` has `default auth.uid()`. Water/profile upserts violate `with check (user_id = auth.uid())`.
6. **P1 — Premium entitlement is local theater.** `SubscriptionManager.purchasePlan()` sets a local pref. RevenueCat must be the source of truth; local state is a cache, never authority.
7. **P2 — Silent fail-open.** `SupabaseQuotaManager.consumeScan` returns `true` on ANY exception; worker mock-meal mode active when keys missing → gate mock behind `FITTER_ENV=dev`.
8. **P2 — Bonus race.** `addBonusScans` writes local bonus before server confirms; acceptable offline-first, but server confirm must reconcile on next fetch.

---

## 4. Phases (execute IN ORDER, one at a time)

### Phase 1 — Worker: authentication + server quota authority  [P0]
**Acceptance:** Unauthenticated request → 401. Authenticated user consumes server-side quota before VLM spend. No header-based entitlement.
- [ ] `worker/src/auth.ts`: fetch Supabase JWKS (`https://<project-ref>.supabase.co/auth/v1/.well-known/jwks.json`), cache keys, verify JWT (RS256, `aud=authenticated`, expiry); return `{ user_id, email?, role }`.
- [ ] `worker/src/index.ts`: add `authMiddleware` to `/v1/analyze-meal`, `/v1/recalculate`, `/v1/entitlements`. 401 JSON on missing/invalid token.
- [ ] Env `SUPABASE_URL` + `SUPABASE_SERVICE_ROLE_KEY` (worker secrets, never in repo).
- [ ] Rate limit keyed by **user_id** (primary) + device_id (secondary): `vlm:rl:user:<uid>` 20 req/min.
- [ ] **Entitlement:** delete all reads of `x-fitter-premium`. Only `VLM_CACHE.get("entitlement:<user_id>:fitter_premium") === "active"` counts as premium.
- [ ] **Quota before spend:** on non-premium analyze, call service_role `rpc(consume_scan, {p_allowance: 3})` first; false → 402-style JSON `{error:"quota_exhausted"}`, do NOT call VLM. (Week-1 allowance 5: read `first_install` from a new `user_meta` table or pass computed allowance from client metadata — document the choice.)
- [ ] Mock mode only when `FITTER_ENV === "dev"`; otherwise missing keys → 500, never fake data.
- **Verify:** `npx tsc --noEmit`; `npx wrangler dev`; curl 401 without JWT; curl with a real Supabase test JWT → quota consumed row in DB.

### Phase 2 — Migrations: tenant defaults + auth-hardened policies  [P1]
**Acceptance:** schema enforces ownership; `auth.uid()` defaults everywhere; analytics requires authenticated insert.
- [ ] `supabase/migrations/0003_auth_hardening.sql`:
  - `alter table public.water_intake alter column user_id set default auth.uid();`
  - `alter table public.profiles alter column user_id set default auth.uid();`
  - Add `user_id` default to `analytics_events` too, and change insert policy to `with check (user_id = auth.uid())` (device-only events inherit via auth context; remove the `user_id is null` escape hatch).
  - Add `meals.eaten_at` index: `create index meals_user_eaten_idx on public.meals (user_id, eaten_at desc);`
  - Add `consume_scan_v2(p_allowance int, p_bonus int default 0)` or reuse existing but add `get_scan_quota` returning `first_install_date` for week-1 logic.
- [ ] Grant service_role (via `service_role` role, always allowed) and `authenticated` only; keep `revoke ... from anon`.
- **Verify:** local `supabase db reset` + `supabase db lint`; insert tests as anon must fail, as authenticated must pass.

### Phase 2b — Dedicated `fitter` schema DB (all tables in one schema)  [P1]
**Acceptance:** ALL app tables live in the `fitter` schema (never `public`); client + worker both resolve the schema; grants are minimal and explicit.
- [ ] New `supabase/migrations/0004_schema_fitter.sql`:
  - `create schema if not exists fitter;`
  - **Move OR declare** every app table in `fitter`: `scan_quota`, `meals`, `profiles`, `water_intake`, `analytics_events`, `user_meta` — with the same columns/RLS/policies as 0001–0003 (recreated here, or `alter table ... set schema fitter` from the temp public copies — pick one approach and make it idempotent for `db reset`).
  - Require ownership: after move, `alter table fitter.* owner to postgres;` so RLS + security definer RPCs keep working.
  - **Grants (minimal):** `grant usage on schema fitter to authenticated, anon, service_role;` then `authenticated` gets select/insert/update/delete on the 5 user tables, `anon` gets NOTHING except `execute` on nothing (revoke all), `service_role` inherits all via role bypass. `consume_scan`, `grant_bonus_scan`, `get_scan_quota` RPCs: `set search_path = fitter`, `revoke from anon`, `grant execute to authenticated`.
  - `auth.uid()` defaults stay on `fitter.*` (from 0003) — verify they survived the move.
- [ ] `config.toml`: `[api] schemas = ["fitter"]` (or `["public","fitter"]` if you keep public reads) so `supabase db`/local API resolves the right schema.
- [ ] Client pins schema: `SupabaseClientFactory` → `createSupabaseClient(...) { install(Postgrest) { ... } }` with `db { schema = "fitter" }` equivalent for supabase-kt (set on the Postgrest plugin config), so every `from("meals")` hits `fitter.meals`.
- [ ] Worker `consumeScanServerSide` + any raw SQL URLs: point at `fitter` schema explicitly in the RPC call (PostgREST `Accept-Profile: fitter` header or schema-qualified RPC name) — no ambiguity.
- **Verify:** `npx supabase db reset && npx supabase db lint`; `psql ... \dt fitter.*` shows 6 tables; insert as `anon` into `fitter.analytics_events` fails, as `authenticated` passes; client unit test asserts `Postgrest` schema config == "fitter".

### Phase 2c — Apply migrations to the HOSTED Supabase project + live verification  [P0-DEPLOY]
> **Target is the ONLINE project (the app has no local/Supabase stack; do NOT spin up Docker or a local instance). Use the IDE's Supabase tooling: the Supabase MCP server (https://mcp.supabase.com/mcp) if connected, else the Supabase CLI (`supabase link` + `supabase db push`) with a real access token.**
**Acceptance:** the hosted project actually contains the `fitter` schema with all 6 tables, RLS enabled, and the 3 RPCs; evidence captured from the live DB.
- [ ] **Identify the project**: list Supabase projects (MCP `list_projects` / `supabase projects list`); pick the Fitter project (or the one the user names) and record its `project_ref`. If multiple, STOP and ask which is the Fitter project — never guess.
- [ ] **Check PG version first** (live `select version();`) — 0004 uses PG16-only syntax (`revoke ... on all routines`, `alter default privileges ... revoke all on routines`). If PG < 16, patch 0004 to the PG15-safe forms (`on all functions`, `on all procedures`) BEFORE pushing.
- [ ] **Check migration state**: query `supabase_migrations.schema_migrations` (or the MCP equivalent) — if 0001–0004 are already recorded, do nothing destructive and report; if tables exist but the history is empty (hand-applied SQL), reconcile so 0004's move-to-fitter is idempotent from reality.
- [ ] **Apply migrations in order** 0001 → 0004 as one push (MCP SQL tool per file, or `supabase db push`). Never partially apply 0004: the schema move must complete or roll back cleanly.
- [ ] **Verify live**: 6 tables in `fitter` (scan_quota, meals, profiles, water_intake, analytics_events, user_meta); `rowsecurity` enabled on each; RPCs `consume_scan`, `grant_bonus_scan`, `get_scan_quota` exist in `fitter` with signature `(p_user_id uuid default auth.uid(), p_allowance int default 3)`; `anon` role has no grants on `fitter` tables.
- [ ] **Safety smoke test**: call `select fitter.get_scan_quota()` with a throwaway test user OR a signed-in JWT — read-only, no real data writes. Do not create production user rows.
- **Verify:** paste the live outputs (project_ref, `\dt fitter.*` equivalent, RPC signatures, policy names) into the progress log; if anything fails, capture the exact error and STOP (report), rather than editing migrations on the fly against prod.

### Phase 3 — Client: GatewayNutritionClient + key removal + real auth boot  [P0]
**Acceptance:** `FailoverNutritionClient` retired from production path; zero VLM keys in the artifact; anonymous JWT present before any server call.
- [ ] New `shared/src/commonMain/kotlin/com/fitter/shared/api/GatewayNutritionClient.kt` implementing `NutritionClient`:
  - `POST {GATEWAY_URL}/v1/analyze-meal` with `Authorization: Bearer <jwt>`, JSON `{image_base64, plate_size_inches, model_hint?}`, `x-device-id`.
  - Timeouts: connect 5s / request 60s. Parse `NutritionResponse` from worker JSON. On 401 → surface auth error; on `quota_exhausted` → typed result, NOT exception (UI shows ad path).
  - Add a `result` wrapper (`sealed class AnalyzeResult { Success, QuotaExhausted, AuthRequired, Failed }`) so the ad gate can branch on `QuotaExhausted`.
- [ ] `shared/.../api/NutritionClient.kt`: keep interface; add `suspend fun analyzeMealImageWithResult(...): AnalyzeResult` default delegating to throwing impl (or change interface — pick one, keep `E2EJourneyTest` compiling).
- [ ] `PlatformConfig`: replace `openRouterApiKey/geminiApiKey/groqApiKey` expects with `gatewayUrl: String` + `supabaseUrl`/`supabaseAnonKey` (already added). Delete `BuildConfig` fields for VLM keys in `MacroVision-UI/build.gradle.kts`; delete keys from `Config.xcconfig`/`generate_config.sh`.
- [ ] `App.kt:163`: construct `GatewayNutritionClient()` (holding the JWT provider) instead of `FailoverNutritionClient(...)`. Keep `FailoverNutritionClient` in the codebase for tests only.
- [ ] **Auth boot in App.kt LaunchedEffect:** `val userId = authService.ensureSignedIn()`; gate worker/quota/sync calls on JWT presence; on `AuthRequired` retry once with fresh anonymous sign-in.
- [ ] Whitelist `interceptor` layer: every PostgREST call already carries the user JWT via supabase-kt `install(Auth)`; do not reintroduce anon-key-only calls for user data.
- **Verify:** `./gradlew :shared:compileDebugKotlinAndroid :MacroVision-UI:compileDebugKotlinAndroid --rerun-tasks`; grep the merged APK/`BuildConfig.java` for `GEMINI_API_KEY` → must be absent; tests green.

### Phase 4 — Server-authoritative quota in client  [P1]
**Acceptance:** display quota = server snapshot; bonus scans reconcile server-side after grant.
- [ ] `SupabaseQuotaManager.consumeScan`: do NOT return `true` on any exception — return `null` (unknown) on network failure, `false` on hard denial, `true` on success.
- [ ] `ScanQuotaManager.syncQuotaFromServer`: after fetch, reconcile local `quota_used`/`quota_bonus` to server snapshot (drop stale local overcount).
- [ ] Keep local prefs as UI cache; primary source is `get_scan_quota`; when server unreachable → show cached values with offline banner (do not silently change entitlement).
- [ ] Ad gate: `shouldForceInterstitialAd` only when NOT premium AND server snapshot says exhausted (or offline + cached exhausted).
- **Verify:** `ScanQuotaManagerTest` extended: offline-unknown, hard-denial, bonus reconciliation — all green.

### Phase 5 — Sync correctness  [P1]
**Acceptance:** pulled meals keep their real `eaten_at`/date; water/profile remain tenant-safe; bounded pulls.
- [ ] `SupabaseMealRow`: add `eaten_at: String? = null`; map to `LoggedMeal.timestamp` (derive display `date` from `eaten_at`, not `dateKey`).
- [ ] `pullRemote(dateKey)`: query `?eaten_at=gte.<dayStart>&eaten_at=lt.<dayEnd>` (or `eq(date, dateKey)` if you add a `date` column), `limit=500`, order `eaten_at desc`; never full-table.
- [ ] `UpsertWater`/`UpsertProfile` rows: include `user_id = auth.uid()` explicitly (or confirm PostgREST default applies) so RLS check passes; add a `SupabaseMealRepositoryTest` proving date preservation locally.
- **Verify:** unit test that pulls a JDBC/JSON fixture with 3 different eaten_at values and asserts local dates differ.

### Phase 6 — Telemetry with auth  [P2]
**Acceptance:** events carry `user_id` via auth context; insert-only RLS; batch flush retries with backoff.
- [ ] `TelemetryUploader`: attach `user_id` from auth session when present; keep `device_id` for anonymous diagnostics.
- [ ] Retry with backoff (1s, 5s, 30s) instead of drop-on-fail; cap buffer at 500.
- **Verify:** `TelemetryUploaderTest` covers flush-success, flush-failure-retry, buffer cap.

### Phase 7 — RevenueCat entitlement as source of truth  [P1]
**Acceptance:** premium can only become true via webhook; client reads entitlement from `/v1/entitlements` or KV-backed state.
- [ ] Worker `/v1/webhook/revenuecat`: verify `Authorization: Bearer <webhook_secret>` (RevenueCat signed payload `X-RevenueCat-Signature`), write `entitlement:<user_id>:fitter_premium = active|inactive` to KV with TTL 1h.
- [ ] Worker `GET /v1/entitlements`: JWT → read KV → `{premium: bool, product_id?}`.
- [ ] `SubscriptionManager`: remove local boolean as authority. Add `refreshFromServer()` calling the worker endpoint; `isPremiumUser()` = last known server state (cache in prefs, but stamped `source=server` + timestamp; stale > 24h → treated false for gating, refresh on boot).
- [ ] `purchasePlan`/`restorePurchases`: call RevenueCat SDK (KMP wrapper stub) OR document explicitly that store purchase → webhook updates KV → client refresh. No path sets premium locally.
- **Verify:** unit: server says false → local true prefs are ignored; worker test: spoofed header rejected, signed webhook accepted.

### Phase 8 — (Defer unless asked) Semantic cache tuning + Vertex AI migration

### Phase 9 — All image scans route via OpenRouter (provider priority)  [P0-PROD]
> **Why:** production incident 2026-09-23 — user hit "Could not analyze image / Authentication required" mid-scan. Beyond the auth bug (Phase 10), the worker was still trying Gemini FIRST and OpenRouter only as fallback. Product decision: **all `analyze-meal` image scans go through OpenRouter** — one provider, one billing relationship, predictable per-scan cost.
**Acceptance:** EVERY `/v1/analyze-meal` call with valid JWT is sent to OpenRouter before any other provider; priority is environment-driven, not hardcoded.
- [x] Worker `executeVlmFailover()`: read `env.VLM_PRIMARY_PROVIDER` (values: `openrouter` default, `gemini`, `groq`, `mock`) and `env.VLM_ALLOW_FALLBACK` (`"true"`/`"false"`, default `"true"`). For `openrouter` primary: call `callOpenRouter()` FIRST for `analyze-meal`; only if it throws AND `VLM_ALLOW_FALLBACK=true` try Gemini → Groq as last resort. Keep `recalculate` (text-only, cheap) on the same ordering for consistency.
- [x] `requestedModel`/`model_hint` must not bypass primary: worker logs `[Provider] primary=openrouter model=<hint>` and passes the hint through to OpenRouter's `model` param; invalid hint → default `minimax/m2-mini-flash` or whatever the OpenRouter default is — never silently switch providers.
- [x] wrangler.toml `[vars]`: `VLM_PRIMARY_PROVIDER = "openrouter"`, `VLM_ALLOW_FALLBACK = "true"`; `.env.example` documents both.
- [x] Worker test (`worker/test/gateway.test.mjs`): (a) with `VLM_PRIMARY_PROVIDER=openrouter` and only GEMINI + OPENROUTER keys set, a valid request hits OpenRouter code path first (spy/order assert); (b) `VLM_ALLOW_FALLBACK=false` → Gemini never called; (c) missing OpenRouter key + fallback allowed → Gemini used.
- **Verify:** `cd worker && npm test && npx tsc --noEmit`; live curl with JWT → response `X-Provider: openrouter` header (add it) + `X-Cache: MISS`.

### Phase 10 — Auth-chain reliability: no scan without a valid JWT  [P0-PROD]
> **Why:** the production error. Root cause chain (verified): `App.kt:123-131` boot `ensureSignedIn()` fails → `catch` swallows it → `userId = null` → app continues; `jwtProvider = currentSession()?.accessToken` returns null; `GatewayNutritionClient` maps null JWT → `AnalyzeResult.AuthRequired` → user sees "Authentication required". The app must NEVER reach the camera flow without a JWT, and when it gets a 401 it must re-auth and retry, not dead-end.
**Acceptance:** a scan with a null/expired JWT results in automatic re-authentication and retry (up to N attempts) before any error reaches the UI; boot never proceeds to the camera without an authenticated session (or an explicit, honest offline state).
- [x] `App.kt` boot: replace swallow-and-continue with a **gate + queue**: loop `ensureSignedIn()` with bounded backoff (e.g. 3 attempts, 1s/2s/4s); if still failing, enter explicit `OfflineMode` state — camera scan availability is gated on auth (button disabled + "Connecting… / Offline" label), recovery observer re-runs `ensureSignedIn()` when network returns. Log via `DiagnosticsCrashHook` + `TelemetryUploader.trackDiagnostic("auth", ...)`.
- [x] `GatewayNutritionClient`: on `AnalyzeResult.AuthRequired`, automatically trigger re-auth (`authService.ensureSignedIn()` via injected `reAuthenticator` callback) and **retry the request once**; only if re-auth fails surface `AuthRequired` to the UI. Add bounded retry for transient network (1 retry, idempotent-safe).
- [x] `CameraScreen`/scan gate: call a single `ensureReadyForScan()` that returns `Ready | AuthPending | Offline | QuotaExhausted`; UI shows exact state instead of "Could not analyze image". Add telemetry event `scan_blocked_reason`.
- [x] **Remove placeholder-value footgun:** `PlatformConfig.android.kt` / `.ios.kt` / `SupabaseClientFactory.kt` defaults (`"https://placeholder-project.supabase.co"`, `"eyJhbG...VCJ9.e30.anon"`) — replace with build-time REQUIRED values: `build.gradle.kts` fails the build if `SUPABASE_URL`/`SUPABASE_ANON_KEY`/`GATEWAY_URL` are missing or contain `placeholder`. No default that looks real in a release build.
- [x] Worker stays strict: any request without a valid JWT → 401 with `WWW-Authenticate: Bearer` (already) — but ALSO add `X-Debug-Code: auth_required` so the client can distinguish and re-auth deterministically.
- **Verify:** new tests — `SupabaseAuthServiceTest` (ensureSignedIn retries, throws after exhaustion), `GatewayNutritionClientTest` (401 → re-auth callback invoked → retry succeeds; re-auth fails → AuthRequired surfaced), `App`-level test for OfflineMode gating; full gate (`./gradlew ... --rerun-tasks`); manual: airplane-mode boot → "Offline" → enable network → auto-recover → scan works.


---

## 5. Hard rules

- **ZERO paid provider keys in the mobile repo/build/artifact.** Only Worker secrets hold them. Supabase anon key is fine in the app.
- **Dedicated `fitter` schema is the single source of truth.** Every app table lives in `fitter.*` (never `public`); the client Postgrest plugin pins `schema = "fitter"`; the Worker resolves the same schema. No table is referenced unqualified from `public`.
- **Every worker inference endpoint requires a valid Supabase JWT.** No anonymous inference, ever.
- **Entitlement comes from the RevenueCat → KV chain only.** Delete `x-fitter-premium` trust.
- **Monetization spec stays:** 5 scans/day W1 → 3/day W2+, forced INTERSTITIAL (never rewarded) on exhaustion, App Open 4h cooldown, banners only on passive screens.
- **Do not change `MealRepository`/`UserRepository` public interfaces** — swap implementations underneath; keep `E2EJourneyTest` and `ScanQuotaManagerTest` green (extend, don't delete).
- `JAVA_HOME="C:\Program Files\Android\Android Studio1\jbr"` (jdk-23 env var is broken).
- **Auth requirement:** do NOT ship a build where a server call can happen before an anonymous JWT exists; gate all boot-side effects on `ensureSignedIn()`. **No scan without a valid JWT — app re-auths and retries on 401, and offline mode is an explicit UI state, never a silent continue.**
- **OpenRouter is the production image-scan provider.** All `/v1/analyze-meal` calls route OpenRouter-first (env `VLM_PRIMARY_PROVIDER=openrouter`); fallback only when `VLM_ALLOW_FALLBACK=true`; no silent provider switching in release builds.
- **No placeholder-looking values in release builds.** `placeholder-project.supabase.co`, `eyJhbG...VCJ9.e30.anon`, `your_*_key_here` → build must FAIL if present.

---

## 6. Verification gate (run after EACH phase)

```bash
cd /c/GitHub/Fitter
export JAVA_HOME="C:\Program Files\Android\Android Studio1\jbr"
export PATH="$JAVA_HOME/bin:$PATH"
./gradlew :MacroVision-UI:testDebugUnitTest
# Phase 1-2 additionally:
cd worker && npx tsc --noEmit && npx wrangler dev   # curl: no-JWT→401; JWT→quota consumed
cd /c/GitHub/Fitter && npx supabase db lint   # lint migrations locally (no DB needed)
# Phase 2c (hosted deploy) additionally:
npx supabase link --project-ref <REF> && npx supabase db push   # ONLINE project
# then verify live via MCP/SQL editor: \dt fitter.* → 6 tables; RLS on; RPC signatures
```

**Definition of done:** every checkbox above ticked with real output logged in `.hermes/plans/backend-foundation/progress.md`; no open assumptions; green tests; merged APK contains zero VLM keys; unauthenticated curl to worker yields 401.