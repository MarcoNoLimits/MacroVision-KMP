# FitCal — where to pick up

Scope decision: **Android-only for v1.** iOS code stays in the repo but isn't a release target.

## Done
**2026-10-06**
- Live `analyze-meal` edge function is at version 7:
  - medical-safety prompt;
  - Groq fallback switched to `qwen/qwen3.8-27b`;
  - client can't pick the AI model;
  - plate size validated;
  - server-side allowance with +1 scan/day for accounts.
- Migration `0007` applied (`consume_scan` cap raised to 6).
- `worker/` removed. Only your local `worker/.dev.vars` remains; delete it once the keys are confirmed in Supabase function secrets.

**2026-10-07**
- Removed AppLovin MAX and App Open ads. Ads are AdMob only:
  - banner, interstitial at quota, and rewarded all verified on the emulator;
  - AdMob paid-event revenue now feeds `AdTelemetryManager`.
- Removed the "Provider/Diagnostics" card from Ads & Perks. It showed "Official Test Units" to real users.
- Privacy policy (in-app and `privacy.html`) now names Supabase (EU) as the backend and drops Cloudflare and AppLovin; dated October 2026.
- Docs updated: `AGENTS.md`, `STORE-SUBMISSION-CHECKLIST.md`, `ADS-OPERATIONAL-LINKS.md` (rewritten for AdMob), `SECURITY-REMEDIATION.md` (update note), `FITCAL-BACKEND-TASK.md` (marked superseded), plus code comments.
- Tests: 126 passing (74 app + 52 shared).

## Production setup (only you can do these)
- [ ] **Domain + email** (see the decision below), then custom SMTP in Supabase → Auth → SMTP.
- [ ] Then let Claude apply the auth settings:
  - turn on manual linking;
  - require email confirmation;
  - passwords: 8+ characters with mixed case and a digit;
  - email templates that show `{{ .Token }}` codes;
  - add `fitcal` to Exposed schemas.
- [ ] Publish `privacy.html` and `app-ads.txt` on the same domain; AdMob needs `app-ads.txt` there.
- [ ] Google OAuth clients: Web, plus Android with debug and release SHA-1. Put `GOOGLE_WEB_CLIENT_ID` in `local.properties`.
- [ ] Do one real scan on a device to confirm edge function v7 works end to end.
- [ ] Decide on the Gemini model. `gemini-2.5-flash` is restricted to existing users; Google recommends `gemini-3.8-flash`. Changing it affects cost and output.

## Add (backlog, in priority order)
1. **Premium purchase:**
   - add the RevenueCat SDK and a paywall screen;
   - create the `entitlements` table the gateway reads (it doesn't exist);
   - set `REVENUECAT_WEBHOOK_SECRET`.

   Premium is unbuyable today.
2. **Crash reporting.** Sentry is available. Today's crash logging only stays on the device.
3. **Abuse and spend protection:**
   - provider budget caps (OpenRouter credit limit, Gemini budget alert);
   - a rate limit on `/v1/recalculate`;
   - a scheduled cleanup of anonymous users with no data older than 30 days;
   - later, Play Integrity checks.
4. **Barcode scanning.** Exact data for packaged foods, cheap to run, top competitor feature.
