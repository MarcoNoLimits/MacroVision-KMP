# Play Console & AdMob — Store Readiness Checklist

Everything here is **your** work; the code side is done in the repo. Copy this into your
Play Console / AdMob account setup.

---

## 1. Play Console — required declarations

### App content
| Field | Value | Why |
|---|---|---|
| Privacy policy URL | *(host `privacy.html` — see §4)* | **Mandatory.** Rejection without it. |
| Ads | Yes | AdMob SDKs are bundled |
| Target audience — age group | **18 and over only** | Fitter is a calorie/nutrition app. Declaring 13–17 triggers the **Families policy** and mandatory child-directed treatment (Certified Ads Program, no personalized ads, restricted SDKs). Declaring 18+ is honest *and* far cheaper to satisfy. |
| News app | No | |
| COVID-19 app | No | |
| Content rating questionnaire | Declare: user-generated content **No**, ads **Yes**, health **No** | Anything resembling a health claim escalates review. Do not claim diagnosis/treatment. |
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
| Can users request data deletion? | **Yes** — in-app: Settings → Privacy → "Delete my account and data" |
| Data shared with third parties | **Yes** — advertising partners (Google AdMob / AppLovin MAX), and AI inference providers (OpenRouter, Google, Groq) |
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
| Consent banner (CMP) for EEA/UK/Switzerland | Implemented — UMP via AppLovin CMP, awaited before ad init |
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
Fitter turns your camera into a nutrition calculator.

Snap a photo of any meal and Fitter estimates its calories and macronutrients in
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
numbers follow — Fitter gives you a starting point, and you stay in control.

PRIVACY
• Meal photos are processed to generate your estimate, then stored with your account
• Personalized ads are OFF by default; your device asks separately
• One-tap deletion of your account and all data
• We never sell your data

Fitter provides automated estimates only and is not medical advice. It is intended
for adults 18+. It is not a substitute for professional medical or dietary advice.
```

---

## 4. Host the privacy policy (do this first)

The in-app text lives at `MacroVision-UI/src/commonMain/kotlin/com/fitter/app/ui/screens/privacy/PrivacyPolicyScreen.kt`.
For the store listing you also need a **public URL**. Fitter supports this out of the box:

- **Gateway Worker Route (Default):** The Cloudflare Worker directly serves `GET /privacy` and `GET /privacy.html` at `https://<GATEWAY_URL>/privacy` (zero extra hosting setup required).
- **Static Hosting (Optional Alternative):**
```bash
# From the repo root — one file, no build step
cp privacy.html public/index.html
npx vercel deploy public --prod     # or: netlify deploy --prod --dir=public
```

Replace every placeholder before publishing:
- `privacy@fitter.app` — use a mailbox you actually monitor (reviewers test it)
- `Last updated: February 2026` — set the real publish date
- Add your legal entity name and jurisdiction if you have one

**The hosted page and the in-app text must agree.** If they diverge, that is itself a
compliance finding.

---

## 5. Test account for reviewers

Both Play and AdMob reviewers want to try the app without signing up. Add a note to the
listing:

> **Reviewer access:** Email `review@fitter.app` / password `FitterReview2026!`.
> (Or: the app works fully as a guest — no account required.)

Prefer the **guest path** if it works end to end: fewer credentials to leak, and it proves
the onboarding claim.

---

## 6. Final pre-submission checklist

- [x] `./gradlew :MacroVision-UI:testDebugUnitTest` and `:MacroVision-UI:bundleRelease` pass (fully compiler-verified & release AAB generated)
- [ ] `cd worker && npx wrangler deploy` — hardened Worker is live
- [ ] `supabase db push` — **migration 0005**, or the quota bypass is exploitable
- [ ] `REVENUECAT_WEBHOOK_SECRET` set in the Worker (entitlements fail closed without it)
- [ ] Real iOS AdMob app id in `iosApp/Configuration/Config.xcconfig`
- [ ] Privacy policy published at a public URL
- [ ] Data safety form completed per §1
- [ ] Target audience set to **18+** (NOT 13–17)
- [ ] Consent screen → age gate → app verified on a real device
- [ ] "Delete my account and data" confirmed to actually delete
- [ ] Personalized ads off by default; CMP appears for an EEA emulator/IP