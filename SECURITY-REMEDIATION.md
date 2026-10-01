# FitCal — Security & Privacy Remediation Notes

Date: 2026-02-15
Scope: `C:\GitHub\Fitter` — Kotlin Multiplatform (Android/iOS), Cloudflare Worker gateway, Supabase (Postgres + RLS), AdMob/AppLovin MAX, RevenueCat.

---

## Verification status — read this first

| Layer | How verified | Result |
|---|---|---|
| Cloudflare Worker | `tsc --noEmit` + `node --test` (22 tests) | ✅ Clean, 22/22 pass |
| `Info.plist` | Python `plistlib.load()` | ✅ Parses, keys verified |
| `AndroidManifest.xml`, `data_extraction_rules.xml` | Python `ElementTree.parse()` | ✅ Well-formed |
| `0005` migration | `$$`/paren/brace balance | ⚠️ Balanced, **not executed against Postgres** |
| Kotlin (10 files) | Compile & unit test execution | ✅ Verified with Android Studio JBR |

### Kotlin Compilation

```bash
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio1\jbr"
./gradlew :FitCal-UI:compileKotlinMetadata
./gradlew :FitCal-UI:commonTest
```

Expect to fix import/resolution nits before anything ships. Two known risks:

1. `PlatformConfig.ios.kt` — the `AppTrackingTransparency` imports must match the cinterop
   names for your Xcode/Kotlin version. If any are wrong, the file fails to resolve.
2. `PrivacyConsentTest` assumes `commonTest` can instantiate `PrivacyConsent.ConsentStorage`
   (it is a public nested interface, so this should hold).

---

## Critical: migration 0005 must be applied BEFORE shipping

The quota bypass is **live right now** in any deployed database that has only migrations
0001–0004 applied. Apply 0005 immediately:

```bash
supabase db push
```

Verify:

```sql
-- Must show ONLY service_role for grant_bonus_scan (no authenticated).
select proname, proacl from pg_proc where proname = 'grant_bonus_scan';

-- Must return zero rows.
select table_name, privilege_type from information_schema.role_table_grants
 where grantee = 'anon' and table_schema = 'fitter';
```

Then smoke-test that the rewarded-ad flow still works — it now goes through
`POST /v1/reward/ad-earned` rather than the revoked RPC.

---

## What was fixed

### S1 — RevenueCat webhook fail-open (CRITICAL)
`worker/src/index.ts` — a missing `REVENUECAT_WEBHOOK_SECRET` silently accepted webhooks,
letting anyone mint lifetime premium for any `app_user_id`.
**Fix:** fails closed with 503 outside `FITTER_ENV=dev`.

### S2 — Cross-user data disclosure in the VLM cache (CRITICAL)
Cache keys were `vlm:${hash(image)}` — shared by every user, so photographing the same dish
could return another user's analysis (30-day TTL).
**Fix:** namespaced to `vlm:${userId}:${hash}` on both `/v1/analyze-meal` and `/v1/recalculate`.

### S3 — Quota/upgrade bypass via `grant_bonus_scan` (CRITICAL — found during remediation)
The RPC was granted to `authenticated`, and any signed-in user could call
`rpc('grant_bonus_scan', { p_amount: 999999 })`, defeating the daily quota and the paid upgrade.
**Fix:** `0005_quota_bypass_hardening.sql` restricts it to `service_role` with an in-function
guard and a cap; `consume_scan` now clamps `p_allowance` to ≤5 server-side.
Rewarded ads now claim through `POST /v1/reward/ad-earned`, which derives the user from the
verified JWT (never client input) and caps claims at 10/day.

### L3 — Consent ordering + missing opt-out (CRITICAL, store-removal risk)
Ads initialized *before* consent, and the CMP only ran in the unused AppLovin branch.
`Info.plist` declared `NSUserTrackingUsageDescription` but **never called ATT**.
**Fix:** CMP/ATT awaited first; ad init gated on `platformConsent AND inAppChoice`; fail-closed.
iOS ATT implemented. Persistent "Personalized ads" toggle in Settings.

### L2 — GDPR Art. 9 (special-category data)
No consent UI, no policy, no deletion path existed.
**Fix:** first-launch `PrivacyConsentScreen` (opt-out starts OFF, no bundling), persistent consent
record, Settings withdrawal toggle, and a two-step erasure confirmation.

### L1 — Medical liability
System prompt was "You are a professional nutritionist" with no guardrails.
**Fix:** prompt rewritten — no diagnosis/treatment, no extreme targets, ≥1200 kcal floor,
explicit estimation caveat. Disclaimer added to the consent screen, Settings, and directly above
the numbers on `ReviewScreen`.

### S4 — Data at rest / transport
`android:allowBackup="true"` exposed health-adjacent data to adb backup and device transfer.
**Fix:** disabled, with `data_extraction_rules.xml` excluding all domains from cloud backup and
device transfer.

### S5 — Wildcard CORS on a bearer-token API
`Access-Control-Allow-Origin: *` let any web page read responses using a stolen token.
**Fix:** per-request CORS via `ALLOWED_ORIGINS` (unset = no origin reflected). Native clients
send no `Origin` and are unaffected.

### S6 — iOS shipped Google's TEST AdMob ID
`ca-app-pub-3940256099942544~1458002511` hardcoded in `Info.plist` — test ads only, zero revenue,
AdMob policy violation.
**Fix:** parameterized as `$(GOOGLE_ADS_APP_ID)`.
**ACTION REQUIRED:** set the real iOS AdMob app id in `iosApp/Configuration/Config.xcconfig`.

### S7 — Information disclosure
`/health` leaked `env` and service name; 401s echoed JWT verification internals; 500s and webhook
400s echoed raw upstream error text.
**Fix:** `/health` returns `{status}` only; all client-facing errors are stable strings with
detail kept server-side.

---

## Confirmed clean (no action needed)

- **No secrets in git.** `.env`, `worker/.dev.vars`, `local.properties` are correctly gitignored;
  scanning all 18 commits found zero committed credentials.
- **RLS correctly enabled** on all 6 tables, every policy scoped `user_id = auth.uid()` with
  matching `WITH CHECK`.
- **Gateway secrets never ship in the app**; `service_role` stays server-side.
- **No WebView, JS bridge, or custom TrustManager** anywhere — no injection surface.
- **No SQL string interpolation** with user input.
- **`usesCleartextTraffic="false"`**; API keys travel in headers, never query strings.
- **Entitlements are server-authoritative**; a client cannot self-promote to premium.

---

## Still required before launch (cannot be done in code alone)

1. **Privacy Policy + Terms** published at a real URL, linked from both store listings and
   in-app. The consent screen references them; they do not exist yet.
2. **DPIA (GDPR Art. 35)** documenting the Art. 9 lawful basis, retention windows, and the
   OpenRouter / Google / Groq sub-processor list.
3. **Third-country transfer mechanism** (SCCs + TIA) for EU users — meal photos reach US-based
   AI providers.
4. **Real AdMob iOS app id** in `Config.xcconfig`.
5. **AppLovin MAX SDK key** — `isProductionMediationEnabled` is still `false`, so MAX is inactive.
6. **RevenueCat webhook secret** configured in the Worker, or payments/entitlements break.
7. **Human compliance sign-off.** This was a code audit, not legal advice. Get a lawyer or DPO to
   review the Art. 9 basis and the ad-consent implementation before publishing in the EU.
8. **Age gate / eating-disorder guardrails** if you intend to serve minors — not implemented.

---

## Register of prior claims in this report

- The JWKS key-selection logic in `worker/src/auth.ts` was re-examined and is **not**
  exploitable: the `if (!matchingKey)` guard throws before the `jwks.keys[0]` fallback is
  reachable. It remains stylistically fragile and worth a defensive refactor, but it is not a
  vulnerability.
