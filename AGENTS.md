# AGENTS.md — Fitter (AI Food Nutrition Scanner)

This file is the authoritative instruction set for any agent (AI or human) working in this repository. Read it fully before making changes.

---

## 1. Project Overview

**Fitter** is a Kotlin Multiplatform (KMP) app that scans meal photos with a Vision-Language Model (VLM) and estimates ingredients, weights, and macronutrients with an interactive correction workflow.

- **Modules**: `:shared` (HTTP clients, models, VLM logic), `:MacroVision-UI` (Compose Multiplatform UI + ads + quotas), `iosApp/` (native iOS wrapper).
- **VLM Pipeline**: OpenRouter (Qwen2.5-VL) → Google Gemini → Groq failover.
- **Monetization state**: Google AdMob integrated (test IDs in `AdConfig`), daily scan quota system live (`ScanQuotaManager`).

## 2. Non-Negotiable Rules

1. **Do not read or expose `.env`** — it contains production API keys. Never commit it.
2. **Do not change monetization behavior** without following Section 5 of this file.
3. **Do not put ads on the Camera/Scan view.** This is a hard product decision.
4. Build must pass: `./gradlew :MacroVision-UI:testDebugUnitTest`.

## 3. Existing Features (Do Not Regress)

- Camera capture + on-device image optimization (resize/compress before upload)
- VLM failover sequencing (OpenRouter → Gemini → Groq)
- Interactive corrections & local recalculation (~10x faster than re-upload)
- Local food database grounding + Search-to-Swap overlay
- Daily scan quota (`ScanQuotaManager`): currently 3 free scans/day, +2 bonus scans per rewarded ad
- Ad layer: `AdManager` interface + `AndroidAdManager` (AdMob test units)

## 4. Task List

| # | Task | Status | Notes |
|---|------|--------|-------|
| 1 | Onboarding scan policy (Week 1 vs Week 2+) | **DONE** | Implemented 5 vs 3 in `ScanQuotaManager` |
| 2 | Forced interstitial on scan #4+ | **DONE** | Scan never blocked; routes to `showScanProcessingAd` |
| 3 | Re-integrate AdMob test units → live AppLovin MAX + AdMob bidding | **DONE** | Configured in `AdConfig` with MAX SDK & placement keys |
| 4 | App Open Ads with grace period + cooldown | **DONE** | `AppOpenAdManager` (session > 3, 4h cooldown) |
| 5 | Adaptive banners on secondary tabs only | **DONE** | Isolated to Dashboard & Settings; zero ads on Camera/Review |
| 6 | Brand-safety ad filtering | **DONE** | Codified blocked/allowed categories in `AdConfig` |
| 7 | Keep `ScanQuotaManagerTest` green (expanded to 9 tests) | **DONE** | 9/9 passing |

## 5. THE MONETIZATION TASK (Canonical Specification)

> **⚠️ Where the full source of truth lives:** Before implementing ANY monetization change, read these two documents in full:
> 1. `C:\Users\ahmed\Documents\Hermes-Workspace\App-Monetization-Masterfile.md` — **Sections 2, 4, 5, 6** (formulas, code plan, developer specs, AdMob-vs-MAX).
> 2. Hermes skill `app-business-financial-engineering` (run `skill_view(name='app-business-financial-engineering')`) — the financial model behind every decision.
>
> Deviating from this specification produces rejected changes. Ask before inventing alternatives.

### 5.1 Onboarding Scan Policy (Task 1)

- **Week 1 (first 7 days after install)**: **5 free AI scans/day**, zero ad interruption. Goal = habit formation.
- **Week 2+ (day 8 onward)**: **3 free AI scans/day**.
- Implement in `ScanQuotaManager`: the free limit is higher for users whose `firstInstallDate < 7 days`. Persist `firstInstallDate` in preferences.

### 5.2 Forced Interstitial on Scan #4+ (Task 2)

When the user has exhausted their free daily quota and attempts another scan:

1. **No prompt dialog. No "watch to continue" button. No rewarded ad.**
2. The scan is **allowed** (quota is not hard-blocked), but an **Interstitial Video/Playable ad** is shown **before** the AI processes the photo:
   ```kotlin
   adManager.showScanProcessingAd {
       processMealWithAI(photo)  // runs only after ad closes
   }
   ```
3. When the ad closes/dismisses → AI analysis runs → results display normally.
4. **Policy compliance**: forced ads must be **Interstitials**, NEVER Rewarded (Rewarded requires explicit user opt-in click).
5. Bonus: the ad plays during the "analysis wait" — UX stays smooth.

### 5.3 Unified Ad Mediation: AppLovin MAX + AdMob Bidding (Task 3)

- Keep Google AdMob demand, but run it **inside AppLovin MAX** as a real-time bidder.
- Add bidding adapters: **Meta Audience Network, Unity Ads, Mintegral** (playable-format demand).
- Why: MAX unified bidding lifts eCPM 30–40% vs. single-network AdMob; playable/interactive creatives earn the highest eCPMs ($35–$90 Tier 1).
- Replace `ANDROID_TEST_*` / `IOS_TEST_*` IDs with live production units (keep test IDs in debug builds).

### 5.4 App Open Ads (Task 4)

- Show App Open ad **only when**: `sessionCount > 3` AND `timeSinceLastAppOpenAd >= 4 hours`.
- No App Open ad during sessions 1–3 (grace period). Frequency cap: 1 per 24h.
- Store `sessionCount` and `lastAppOpenAdTimestamp` in preferences.

### 5.5 Banner Ads (Task 5)

- **Adaptive banners ONLY** on passive screens: Macro History, Profile/Settings, Recipe Detail.
- **NEVER** on the Camera/Scan view or the scan results flow.
- Banners are a low-eCPM baseline floor ($0.50–$2.50); they must never block the primary action.

### 5.6 Brand Safety (Task 6)

- In MAX/AdMob dashboards enable **strict category blocking**: Block Gambling, Dating, Politics, Religion, Cosmetic Surgery, Sexual Health, Unverified Apps, Low-quality Clickbait.
- Allow only: Fitness, Food & Beverage, Sports, Technology, Productivity, Mobile Games.
- Users must never see disturbing/repulsive ads — this is the explicit failure condition.

### 5.7 Definition of Done

- [x] All 5 `ScanQuotaManagerTest` tests pass, plus new tests for: Week-1 limit (5), Week-2 limit (3), forced-interstitial trigger on scan #4+ (no dialog).
- [x] `App.kt` gate logic routes: quota available → process; quota exhausted → `showScanProcessingAd { process }`.
- [x] No ad units on Camera/Scan composable.
- [x] App Open ad respects `sessionCount > 3` + 4h cooldown.
- [ ] Merge via PR with the monetization spec referenced in the description.

---

## 6. Verification

```bash
./gradlew :MacroVision-UI:testDebugUnitTest   # must be green
./gradlew :MacroVision-UI:installDebug        # smoke-test on device/emulator
```