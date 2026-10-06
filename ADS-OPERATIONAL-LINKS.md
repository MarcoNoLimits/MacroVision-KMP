# FITCAL — ADS OPERATIONAL LINKS & LAUNCH CHECKLIST

**Setup:** Google AdMob only (AppLovin MAX and App Open ads were removed on 2026-10-07).
Formats: adaptive **banner** on passive screens, **interstitial** when the free daily quota is
used up (scan never blocked), and **rewarded** (+2 scans, user opt-in). No ads on the camera/scan view.

**Ad unit IDs:** production IDs are in `FitCal-UI/src/commonMain/kotlin/com/fitcal/app/ads/AdManager.kt`
(`AdConfig.ANDROID_PROD_*`); debug builds always use Google's official test units. The AdMob
App ID is in `FitCal-UI/src/androidMain/AndroidManifest.xml`.

> ⚠️ Never tap your own live ads — account ban and invalid-traffic flags. Test on debug builds.

---

## 1. Before launch
| # | Step | Link |
|---|------|------|
| 1 | AdMob account approved; FitCal app added (App ID already in the manifest) | https://admob.google.com/ |
| 2 | Banner, interstitial and rewarded ad units exist and match `AdConfig.ANDROID_PROD_*` | https://apps.admob.google.com/ |
| 3 | **app-ads.txt** published on your developer website domain and verified in AdMob (required for apps added after Jan 2025, otherwise limited serving) | https://support.google.com/admob/answer/15948559?hl=en |
| 4 | Link the Play Store listing to the AdMob app once published | AdMob → Apps → FitCal → App settings |
| 5 | Brand-safety blocking set in AdMob → Blocking controls (categories in `AdConfig.BLOCKED_AD_CATEGORIES`) | https://admob.google.com/ |
| 6 | EU consent message configured in AdMob → Privacy & messaging (the app shows it via Google UMP before any ad request) | https://support.google.com/admob/answer/10113207 |

## 2. Reference
- Android quick start: https://developers.google.com/admob/android/quick-start
- Rewarded ads policy (must benefit the user, opt-in only): https://support.google.com/admob/answer/6544120?hl=en
- app-ads.txt spec: https://iabtechlab.github.io/ads-txt/
- Payments (paid after a $100 balance, net-30): https://support.google.com/admob/answer/1307237?hl=en

## 3. Go-live gate (all must be true)
- [ ] AdMob account approved and app-ads.txt verified
- [ ] Privacy policy (public URL) mentions Google AdMob
- [ ] Live QA on a real device: banner on Dashboard/Settings, interstitial when the quota is used up, rewarded +2 scans, nothing on the camera screen
- [ ] `./gradlew :FitCal-UI:testDebugUnitTest` green

## 4. Revenue expectations (rough baselines; vary a lot by country)
- **eCPM**: banner $0.50–$2.00 · interstitial $3–$10 · rewarded $8–$20
- **ARPDAU from ads** target: $0.04–$0.15
- Revenue per impression is recorded from AdMob `OnPaidEventListener` into `AdTelemetryManager`.
- If fill rate drops below ~70% or eCPM falls, check app-ads.txt verification, brand-safety strictness and country mix first.
