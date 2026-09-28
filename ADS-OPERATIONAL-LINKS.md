# FITTER — ADS OPERATIONAL LINKS & LAUNCH CHECKLIST

**Status:** Monetization is implemented, mandatory (no opt-out), and builds green.
**State:** Code uses **Google OFFICIAL TEST units** (`ca-app-pub-3940256099942544/*`) and
`AdConfig.isProductionMediationEnabled = false`. Production = replace IDs + flip flag.

---

## 1. ⚡ Priority: make ads REAL (4 steps)

| # | Step | Where | Link |
|---|------|-------|------|
| 1 | Create AdMob account | Google account required; country+currency are **locked after signup**; account review ≤24h (rarely 2wk) | https://admob.google.com/ |
| 2 | Add Fitter app → get **App ID** (`ca-app-pub-XXX~YYY`) | AdMob → Apps → Add App (link Play Store listing if published) | https://admob.google.com/home |
| 3 | Create **5 ad units** (keep our 5 placeholders in `AdManager.kt`): banner, interstitial, rewarded, app-open (+ iOS set if iOS ships) | AdMob → Apps → [Fitter] → Ad Units → Add | https://apps.admob.google.com/ |
| 4 | Verify ownership via **app-ads.txt** — REQUIRED for apps added after Jan 2025, else limited serving | Host `app-ads.txt` on your developer site | https://support.google.com/admob/answer/15948559?hl=en |

> ⚠️ Never tap/click your own live ads — account ban + invalid-traffic flags. Test with
> Google's official test units (already in code) until launch day.

---

## 2. Google AdMob (primary direct network)

- Sign up / dashboard: https://admob.google.com/
- Getting started guide (account → app → app-ads.txt → ad units): https://support.google.com/admob/answer/15948559?hl=en
- **Android SDK** (Manifest App ID + ad unit integration): https://developers.google.com/admob/android/quick-start
- **iOS SDK** (App ID in Info.plist): https://developers.google.com/admob/apple/quick-start
- App Open Ads docs: in your AdMob dashboard under Apps → [App] → Ad Units → App Open
- Rewarded ads policy (Play/Premium only, must benefit user, no deceptive rewards): https://support.google.com/admob/answer/6544120?hl=en
- App-ads.txt spec (who may sell your inventory): https://iabtechlab.github.io/ads-txt/
- Payments/payout minimum & thresholds (AdMob pays ~1–2 days after $100+ balance, net-30): https://support.google.com/admob/answer/1307237?hl=en

## 3. AppLovin MAX (mediation layer — OUR PRIMARY BIDDING config)

- **Dashboard/login: https://dash.applovin.com/** (`#keys` = SDK Key; `#general` = Ad Review Key)
- Getting started (account → developer domain → SDK → networks): https://support.applovin.com/en/max/getting-started
- Android SDK integration (Gradle, min SDK 24 — matches our `com.applovin:applovin-sdk:13.6.4`): https://support.applovin.com/en/max/android/overview/integration
- Manual integration: https://support.applovin.com/en/max/android/overview/manual-integration
- Android MAX SDK GitHub (release notes, subscribe): https://github.com/AppLovin/AppLovin-MAX-SDK-Android
- Mediation matrix (which networks are mediated via MAX bidding): https://support.applovin.com/en/max/mediated-network-guides/mediation-matrix
- **Unified Bidding** (Google + Meta + Unity + more in ONE request — what `AdConfig.isProductionMediationEnabled` unlocks): https://www.applovin.com/max/
- app-ads.txt / IAB supply-chain validation in MAX: https://support.applovin.com/en/max/max-dashboard/account/iab-supply-chain-validation
- **Register networks individually first**: each mediated network (Google AdMob, Meta Audience Network, Unity Ads) needs its own account + app + ad-unit approval BEFORE MAX can bid on it.

## 4. Mediated networks to register (for MAX unified bidding)

| Network | Signup | Notes |
|---------|--------|-------|
| Google AdMob | https://admob.google.com/ | step 1 above |
| Meta Audience Network | https://www.facebook.com/business/help/1598141225905911 | requires approved FB Business + app |
| Unity Ads | https://dashboard.unityads.unity3d.com/ | strong for interstitial/rewarded on mobile games & utilities |

## 5. Code hook points (Fitter repo)

| Variable | File | Action |
|----------|------|--------|
| `ANDROID_TEST_APP_ID` + 4 test unit IDs | `MacroVision-UI/src/commonMain/kotlin/com/fitter/app/ads/AdManager.kt:16-27` | replace with YOUR 5 production IDs |
| `isProductionMediationEnabled = false` | same file, `:30` | → `true` when MAX account ready (shows debugger; switches provider label to "AppLovin MAX Unified Bidding") |
| App ID in AndroidManifest | `MacroVision-UI/src/androidMain/AndroidManifest.xml:32` | swap test → production App ID |
| iOS App ID | `MacroVision-UI/src/iosMain/.../PlatformConfig.ios.kt` | set when iOS ships |
| SDK deps | `MacroVision-UI/build.gradle.kts:69-70` | already present ✅ |

## 6. Go-live gate (ALL must be true)

- [ ] AdMob account approved, app added, App ID real
- [ ] 5 ad units created, IDs swapped into `AdManager.kt`
- [ ] app-ads.txt live on your domain + verified in AdMob & MAX
- [ ] AppLovin MAX account created, developer domain added, SDK key in dashboard
- [ ] AdMob + Meta + Unity accounts approved so MAX unified bidding fills
- [ ] Privacy Policy mentions third-party ads (Play/iOS requirement)
- [ ] Live QA on a real device: banner in tabs, interstitial at scan #4, rewarded +2, app-open on session 4+ (4h cooldown), mediation debugger shows bids
- [ ] `export JAVA_HOME="C:\Program Files\Android\Android Studio1\jbr" && ./gradlew :MacroVision-UI:testDebugUnitTest` → all green (40 tests)

## 7. Revenue expectations (2026 sane baselines, health-check later)

- **eCPM**: banner $0.50–$2.00 · interstitial $3–$10 · rewarded $8–$20 · app-open $1–$5 (varies hard by geo)
- **Fill rate** target: >85% (MAX unified bidding on top of AdMob)
- **ARPDAU from ads** target: $0.04–$0.15 (matches our LTV model: 3–5 scans/day free tier)
- Watch: if fill <70% or eCPM tanks, check app-ads.txt verification + brand-safety filter strictness + geo mix (Angola/PALOP fills cheaper than EU/US; keep bids on — CPC floor via MAX settings)