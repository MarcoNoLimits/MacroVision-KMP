# Play Console & AdMob — Store Readiness Checklist

Everything here is **your** work; the code side is done in the repo. Copy this into your
Play Console / AdMob account setup.

---

## 1. Play Console — required declarations

### App content
| Field | Value | Why |
|---|---|---|
| Privacy policy URL | *(host `site/privacy.html` — see §4)* | **Mandatory.** Rejection without it. |
| Ads | Yes | AdMob SDKs are bundled |
| Target audience — age group | **18 and over only** | FitCal is a calorie/nutrition app. Declaring 13–17 triggers the **Families policy** and mandatory child-directed treatment (Certified Ads Program, no personalized ads, restricted SDKs). Declaring 18+ is honest *and* far cheaper to satisfy. |
| News app | No | |
| COVID-19 app | No | |
| Content rating questionnaire | Declare: user-generated content **No**, ads **Yes**, health **No** | Anything resembling a health claim escalates review. Do not claim diagnosis/treatment. |
| Health apps declaration | Health & Fitness → **Nutrition and weight management** only. Not a medical device, no medical features. | Every app must fill this in now. Calorie tracking is a health feature; ticking anything medical brings extra review and documentation. |
| Government app | No | |
| Financial features | No | |

### Data safety form (Play Console → App content → Data safety)
Answer truthfully. Meal photos + nutrition logs tied to an account are personal data;
the conservative answer protects you in review:

| Question | Answer |
|---|---|
| Does your app collect or share user data? | **Yes** |
| Data collected | Account email (optional), Photos/Pictures, Fitness/Health, App activity, Crash logs, Device IDs |
| Encrypted in transit? | **Yes** (TLS only; `usesCleartextTraffic=false`) |
| Can users request data deletion? | **Yes** — in-app: Settings → Privacy → "Delete my account and data"; web link: `…/privacy.html#delete` |
| Data shared with third parties | **Yes** — advertising partner (Google AdMob), and AI inference providers (OpenRouter, Google, Groq). Sentry (crash reports) is a service provider acting for us, which Play does not count as "sharing", but declare Crash logs + Diagnostics as *collected* |
| Feedback | Declare **Other user-generated content** (feedback messages) and **Email address** (optional reply address). Purpose: App functionality / Developer communications. Optional, user-initiated |
| App activity detail | App interactions (screens, scan funnel, corrections as counts), in-app actions. Purpose: **Analytics**. Optional: users can turn it off in Settings → Privacy → Share usage analytics |
| Purposes | Analytics, Advertising or marketing, App functionality, Personalization |

### Required store assets
- **Feature graphic** 1024×500
- **Icon** 512×512
- **Phone screenshots** — min 2, 16:9 or 9:16. Show: scan flow, Review screen with the estimate banner, Food Library, consent + age gate.
- **Short description** (≤80 chars): `Snap a meal. Get instant nutrition estimates.`
- **Full description** (≤4000 chars) — draft below.

---

## 2. AdMob — publisher requirements

| Requirement | Status |
|---|---|
| Privacy policy on a **public, non-app-hosted** URL | Required. Hosted version needed. |
| Consent banner (CMP) for EEA/UK/Switzerland | Implemented — Google UMP, awaited before ad init |
| App content must be substantial | Addressed: browsable Food Library (~113 foods, offline reference) |
| Age-appropriate content rating | 18+ only |
| No invalid traffic (self-clicked ads) | Rewarded ads require genuine user interaction — keep it |

**Why the original application was rejected** (Google doesn't disclose specifics; treat these as the levers):
1. Policy gaps — consent ordering, no privacy policy, no data deletion, medical claims. **All now fixed in code.**
2. Thin content. **Now partially addressed** — add more if reapplying.
3. New account / no traffic history. **Only time + real installs fix this.**

**Reapply:** wait 30+ days, ship the Play build, collect DAU/install numbers, then reapply with
the privacy policy live and content demonstrated.

---

## 3. Play Store full description (draft)

```
FitCal turns your camera into a nutrition calculator.

Snap a photo of any meal and FitCal estimates its calories and macronutrients in
seconds — no searching, no weighing, no database hunting.

WHAT YOU GET
• Instant calorie and macro estimates from a single photo
• Editable portions — correct a weight and totals recalculate instantly
• Search-to-swap: replace any item with one of 100+ foods
• Built-in Food Library: browse verified macros for 100+ everyday foods, offline
• Water tracking, weight logging, and meal history
• Daily scan quota, extendable with rewarded ads
• Optional Premium: unlimited scans, ad-free

BUILT FOR ACCURACY
Every estimate is editable. If the portion looks off, change the weight and the
numbers follow — FitCal gives you a starting point, and you stay in control.

PRIVACY
• Meal photos are processed to generate your estimate, then stored with your account
• Personalized ads are OFF by default; your device asks separately
• One-tap deletion of your account and all data
• We never sell your data

FitCal provides automated estimates only and is not medical advice. It is intended
for adults 18+. It is not a substitute for professional medical or dietary advice.
```

---

## 4. Host the privacy policy and app-ads.txt (do this first)

`site/` is ready to publish as-is, with no build step:
- `site/index.html`: a small landing page (it can be your Play "developer website").
- `site/privacy.html`: the privacy policy and terms. The **`#delete`** section is the
  "delete account" web link Play asks for in the Data safety form.
- `site/app-ads.txt`: your AdMob seller line (publisher id from the App ID in the manifest).

Host it at **`https://fitcal.tecaa.xyz`** (AdMob reads `app-ads.txt` from the exact host of
the developer website; only `www.`/`m.` are stripped, so a subdomain works):
```bash
npx vercel deploy site --prod        # then add fitcal.tecaa.xyz as the project's domain
```
DNS at your registrar: `CNAME fitcal → cname.vercel-dns.com`.

Play Console / AdMob URLs:
- Developer website: `https://fitcal.tecaa.xyz/`
- Privacy policy: `https://fitcal.tecaa.xyz/privacy.html`
- Account deletion: `https://fitcal.tecaa.xyz/privacy.html#delete`
- app-ads.txt (AdMob checks it): `https://fitcal.tecaa.xyz/app-ads.txt`

Replace every placeholder before publishing:
- `fitcal@tecaa.xyz` is the single FitCal contact (support, privacy, deletion requests, Play developer
  email). It must actually receive mail before you publish, because reviewers test it: use Name.com
  Email Forwarding. Keep `site/privacy.html` and `PrivacyPolicyScreen.kt` in sync if it changes.
- `Last updated: October 2026`: set the real publish date.
- Add your legal entity name and jurisdiction if you have one.

**The hosted page and the in-app text must agree.** If they diverge, that is itself a
compliance finding.

## 5. Test account for reviewers

Both Play and AdMob reviewers want to try the app without signing up. Add a note to the
listing:

> **Reviewer access:** no account required — the app works fully as a guest.

Prefer the **guest path** if it works end to end: fewer credentials to leak, and it proves
the onboarding claim.

---

## 6. Final pre-submission checklist

- [x] `./gradlew :FitCal-UI:testDebugUnitTest` and `:FitCal-UI:bundleRelease` pass (fully compiler-verified & release AAB generated)
- [x] Hardened `analyze-meal` Edge Function deployed (v8, 2026-10-07)
- [x] Migrations 0005–0009 applied to production
- [ ] `REVENUECAT_WEBHOOK_SECRET` set in Supabase Edge Function secrets (entitlements fail closed without it)
- [ ] ~~Real iOS AdMob app id~~ — iOS is out of v1
- [ ] Privacy policy published at a public URL
- [ ] Data safety form completed per §1
- [ ] Target audience set to **18+** (NOT 13–17)
- [ ] Consent screen → age gate → app verified on a real device
- [x] Gateway routes `/v1/account/delete` and `/v1/reward/ad-earned` deployed (migrations 0008–0009, function v8; tested 2026-10-07)
- [ ] "Delete my account and data" confirmed to actually delete
- [ ] Personalized ads off by default; CMP appears for an EEA emulator/IP