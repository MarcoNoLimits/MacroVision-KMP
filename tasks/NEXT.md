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

**2026-10-07 (later), live: edge function v8, migrations 0008 + 0009:**
- The live gateway was missing two routes the app calls; the old Worker had them:
  - `/v1/account/delete`: Play requires working in-app deletion, so this one is critical;
  - `/v1/reward/ad-earned`: rewarded ads never actually granted scans server-side.

  Both are added to `supabase/functions/analyze-meal/index.ts`, backed by migration `0008_account_delete_and_reward_claim.sql`.
- Deletion is safe for this shared Supabase project:
  - FitCal rows are deleted in one transaction;
  - the login itself is deleted only if no other app (`yinyang`, `crm`, storage) uses it.
- Reward claims are capped at 10 bonus scans/day, atomically in the database.
- Settings shows "Deleting…" and an error if deletion fails. Before, it did nothing. Consent is withdrawn only after the server confirms.
- `site/` (`index.html`, `privacy.html`, `app-ads.txt`) is ready to host; `privacy.html` moved there.
- Policy now has a `#delete` section (the web deletion link Play asks for). Dropped a stale "cached for 30 days" claim, since the gateway no longer caches.

- Tested end to end in production with throwaway guests (since deleted):
  - no token gives 401;
  - rewards grant 2, 4, 6, 8, 10, then 429, even with 10 parallel claims;
  - delete removes meals, water, quota and the login, and the old token then gets 401;
  - your CRM admin and yinyang user were untouched.
- `0009` fixes a bug the test found: the `crm.handle_new_user` trigger gives every login a CRM `member` row, which made every FitCal login look "used elsewhere" so it was never deleted. Untouched default rows are now ignored.

**2026-10-10 (later), live: edge function v10, migrations 0011 + 0012:**
- **Rename finished:** schema `fitter` was renamed to `fitcal`. Before, the app talked to `fitcal`, which the API never exposed, so meal, profile and water sync had **never worked** in production. Now tested end to end: upserts, soft delete, quota RPCs, isolation between users.
- Created the missing `fitcal.entitlements` table. The RevenueCat webhook now writes it and returns 503 on failure, so RevenueCat retries instead of losing the purchase.
- Account deletion's "login used by another app?" check now skips `fitcal` (it would otherwise never delete logins after the rename).
- Dropped the leftover `public.consume_scan` / `get_scan_quota` / `grant_bonus_scan` functions.
- **User feedback:** thumbs up/down with reasons under every AI result, plus Settings → "Send feedback or report a problem". Stored in `fitcal.feedback` via `POST /v1/feedback`; read it with `select * from fitcal_analytics.feedback_inbox;` and `fitcal_analytics.scan_ratings`.
- [ ] **Your step:** Dashboard → Data API → Exposed schemas: remove `fitter` (it's now an empty placeholder), then let Claude run `drop schema fitter;`.

**2026-10-10, live: edge function v9, migration 0010 — analytics (see `ANALYTICS.md`):**
- The old telemetry had never stored a single event (wrong column names). Events now go app → gateway `POST /v1/events` → `fitter.log_events`. They are queued on the device, survive restarts, and upload every 60 s, on background and at boot.
- Full funnel: consent, app open/sessions, screen views, scan started/succeeded/failed, review corrections, meal logged, quota wall, rewarded ads, auth, settings. The gateway adds its own AI-provider latency/fallback events.
- Dashboards: `fitcal_analytics.*` views (SQL editor only).
- Settings → Privacy → "Share usage analytics" switch (opt-out). Privacy policy (in-app and `site/`) updated.
- Sentry crash reporting (`sentry-android-core`) is wired but **off until `SENTRY_DSN` is set in `local.properties`**.
- Tested end to end in production with a throwaway guest (since deleted).

## ⚠ Other apps on this Supabase project (not FitCal code, your call)
- Every CRM table (`companies`, `contacts`, `deals`, `tasks`, `activities`, `profiles`, `pipeline_stages`) has RLS policies allowing **all operations for any `authenticated` user**. Anonymous FitCal guests count as `authenticated`.
- It isn't reachable today: `crm` is not in Exposed schemas, so the API answers 406. It becomes a full read/write leak the moment `crm` is exposed. Fix: scope those policies to real CRM users (for example a `crm.profiles.role` check, or excluding `auth.jwt()->>'is_anonymous' = 'true'`).
- `on_auth_user_created` creates a CRM `member` profile for every FitCal guest, which pollutes the CRM. Consider skipping anonymous users in `crm.handle_new_user()` (`if NEW.is_anonymous then return NEW; end if;`).
- Longer term, give FitCal its own Supabase project so apps can't affect each other's logins.

## Production setup (only you can do these)
Domain: **`tecaa.xyz`** (Name.com). FitCal lives at **`fitcal.tecaa.xyz`**. Contact: **`fitcal@tecaa.xyz`**. Auth emails are sent from **`no-reply@tecaa.xyz`** via Resend. DNS records go in Name.com → Manage DNS Records; the Host field is relative (`fitcal`, not `fitcal.tecaa.xyz`).

1. [ ] **Website.** Add DNS `CNAME fitcal → cname.vercel-dns.com`, then deploy `site/` to Vercel with `fitcal.tecaa.xyz` as its domain (Claude can do the Vercel part if you say so). Check that `https://fitcal.tecaa.xyz/app-ads.txt` loads.
2. [ ] **Receive mail at `fitcal@tecaa.xyz`**, the one FitCal contact address: support, privacy and deletion requests, the Play Console developer email, and DMARC reports. Set it up in Name.com → tecaa.xyz → Email Forwarding → `fitcal` → your inbox. Name.com adds the MX records itself. Reviewers do email it.
3. [x] **Resend domain verified** (2026-10-10): root domain `tecaa.xyz` (not `fitcal.tecaa.xyz`), region eu-west-1. DKIM `resend._domainkey` and SPF/MX on `send.tecaa.xyz` are live.
   - [ ] **Add DMARC** in Name.com: Host `_dmarc`, type TXT, value `v=DMARC1; p=none; rua=mailto:fitcal@tecaa.xyz`. It matters more on `.xyz`. After ~2 clean weeks, change `p=none` to `p=quarantine`.
   - [ ] **Supabase → Authentication → Emails → SMTP Settings** (you enter the key; Claude never handles it): enable custom SMTP, host `smtp.resend.com`, port `465`, username `resend`, password = a Resend API key with **sending access only** for `tecaa.xyz`, sender email `no-reply@tecaa.xyz`, sender name `FitCal`.
4. [ ] **Auth settings** (dashboard only; there's no API access from here). The repo copy is `supabase/config.toml`.
   - Authentication → Sign In / Providers → Email: **Confirm email ON**, **Secure email change ON**, Email OTP length **6**, expiry **3600**.
   - Same page → Passwords: minimum length **8**, requirements **lowercase, uppercase letters and digits**. Also turn on **leaked password protection** (the security advisor flags it).
   - Authentication → Sign In / Providers: **Allow manual linking ON** (needed for Google linking to a guest).
   - Authentication → URL Configuration: Site URL `https://fitcal.tecaa.xyz`.
   - Authentication → Emails → Templates: paste `supabase/templates/email_change.html` into **Change email address** (subject `Your FitCal code: {{ .Token }}`), and `recovery.html` into **Reset password** (subject `Your FitCal password reset code: {{ .Token }}`). Optionally `confirmation.html` into **Confirm signup**. The default templates send links, but the app asks for a 6-digit code, so **account creation and password reset don't work until this is done**.
   - Authentication → Rate limits: raise "emails per hour" from the default to about 100 once SMTP is on.
   - [x] ~~add `fitcal` to Exposed schemas~~ (done). [ ] Remove `fitter` from Exposed schemas, then Claude drops the empty placeholder.
   - Then let Claude run a real end-to-end test: guest → create account with a code → sign out → reset password.
5. [ ] **Play Console:**
   - privacy policy `https://fitcal.tecaa.xyz/privacy.html`;
   - deletion URL `…/privacy.html#delete`;
   - developer website `https://fitcal.tecaa.xyz/`;
   - Data safety;
   - Health apps declaration (Nutrition and weight management);
   - target audience 18+.
6. [ ] **AdMob:** set the app's developer website once it's linked to the Play listing, then wait up to 24 h for app-ads.txt verification.
7. [ ] Google OAuth clients: Web, plus Android with debug and release SHA-1. Put `GOOGLE_WEB_CLIENT_ID` in `local.properties`.
8. [ ] Do one real scan on a device to confirm the edge function works end to end.
9. [ ] Decide on the Gemini model. `gemini-2.5-flash` is restricted to existing users; Google recommends `gemini-3.8-flash`. Changing it affects cost and output.

## Add (backlog, in priority order)
1. **Premium purchase:**
   - add the RevenueCat SDK and a paywall screen;
   - ~~create the `entitlements` table the gateway reads~~ (done in migration 0011, `fitcal.entitlements`);
   - set RevenueCat's app user ID to the Supabase user ID (the webhook ignores anonymous RevenueCat IDs);
   - set `REVENUECAT_WEBHOOK_SECRET`.

   Premium is unbuyable today.
2. **Crash reporting.** Wired up (Sentry); only needs `SENTRY_DSN` in `local.properties`. See `ANALYTICS.md`.
3. **Abuse and spend protection:**
   - provider budget caps (OpenRouter credit limit, Gemini budget alert);
   - AdMob server-side verification (SSV) for rewarded ads, so `/v1/reward/ad-earned` can only be claimed after a real ad. Today the 10/day cap limits the damage;
   - a rate limit on `/v1/recalculate`;
   - a scheduled cleanup of anonymous users with no data older than 30 days;
   - later, Play Integrity checks.
4. **Barcode scanning.** Exact data for packaged foods, cheap to run, top competitor feature.
5. **Home-screen widgets** (Android first, Jetpack Glance):
   - *Today* widget: calorie ring + protein/carbs/fat remaining, read from the local meal store (works offline, no network);
   - *Quick scan* widget/shortcut: one tap opens the camera straight from the home screen;
   - *Water* widget: +1 glass button that writes through the same repository as the dashboard;
   - refresh on meal logged / water changed / midnight, and respect the privacy rules (no data on the lock screen unless the user opts in);
   - add `widget_added`, `widget_removed` and `widget_tap` analytics events (see `ANALYTICS.md`);
   - later: iOS WidgetKit once iOS is a release target.
6. **Feedback follow-ups:** a simple way to read and answer `fitcal_analytics.feedback_inbox` (email reply or a small admin page), and the Play in-app review prompt after a few positive scan ratings.
