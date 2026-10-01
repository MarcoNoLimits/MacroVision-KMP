# Task Plan — backend-foundation

> Goal: Turn Fitter into a fully functioning, independent app: no client-side secrets, server-enforced quotas, account identity, sync, telemetry sink.
> Master task file: `C:\GitHub\Fitter\FITTER-BACKEND-TASK.md` (IDE-consumable; the canonical build spec).
> Baseline audit: `C:\Users\ahmed\Documents\Hermes-Workspace\Fitter\BACKEND-AUDIT.md`.

## Phases (in order; each independently shippable)

- [ ] **Phase 1: Cloudflare Worker gateway** — paid VLM keys leave the client; POST /v1/analyze-meal + recalculate; KV semantic cache; per-device rate limit; kill switch
- [ ] **Phase 2: Supabase schema + RLS + RPC** — `supabase/migrations/0001_backend_foundation.sql` (scan_quota, meals, profiles, water_intake) + `0002_analytics_events.sql`; RLS everywhere; atomic `consume_scan(p_allowance)`
- [ ] **Phase 3: Supabase Auth + KMP wiring** — supabase-kt in `:shared`; anonymous auth on cold start; upgradeable to email/Google/Apple
- [ ] **Phase 4: Server-enforced quota** — `consume_scan` RPC is the only quota authority; ScanQuotaManager becomes display cache; bonus via server RPC
- [ ] **Phase 5: Sync** — meals/profile/water upsert + tombstones; local-first outbox flush
- [ ] **Phase 6: Telemetry sink** — events → analytics_events (batch 50); ad/crash/retention mapped
- [ ] **Phase 7: RevenueCat** — premium entitlement gates ads/unlimited; $4.99/mo, $39.99/yr
- [ ] **Phase 8 (P2 defer): Semantic cache tuning + Vertex migration** — duplicate scans $0; pay-as-you-go SLA

## Constraints / tradeoffs

- Zero client-side secrets — AGENTS.md §5.8, non-negotiable.
- Monetization flow stays spec-compliant: 5/day W1 → 3/day W2+, forced interstitial (never rewarded), App Open 4h cooldown.
- Ads: AppLovin MAX + AdMob bidding; unit tests must stay green after every phase.
- JAVA_HOME for Gradle: `C:\Program Files\Android\Android Studio1\jbr` (jdk-23 env var is broken).
- Keys: `git grep -E 'AIza|sk-|gsk-'` on tracked files must = 0 at done.

## Definition of done

- All 8 phases' acceptance criteria in FITTER-BACKEND-TASK.md verified by real run output (logged in progress.md).
- `./gradlew :FitCal-UI:testDebugUnitTest` green.
- AGENTS.md Tasks 8/9/10 ticked once complete.
