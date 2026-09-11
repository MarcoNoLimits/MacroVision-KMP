# Phase 7: Release Readiness & Post-Deployment Monitoring

## Final Deliverables

### 1. Full E2E Testing Coverage [DONE]
- Validate all user journeys: onboarding, ad monetization via ScanQuota, scan logic, and fail-safe error recovery.
- Target areas covered in `E2EJourneyTest.kt`:
  - Journey 1: Fresh install Onboarding -> Week 1 allowance check (5 scans/day) -> Consuming scans -> Scan 6+ forced interstitial ad trigger without modal dialog -> Simulated ad dismissal -> Process meal -> Persist in LocalMealRepository -> Verify meal query returns logged meal.
  - Journey 2: Week 2+ user (installed 10 days ago) -> 3 scans/day -> Quota exhaustion -> Scan #4 forced interstitial -> Rewarded video bonus scan addition (+2 scans) -> Quota restored -> Meal logged.
  - Journey 3: VLM failover pipeline mock journey -> Primary Gemini timeout/error -> Secondary Gemini fallback -> OpenRouter -> Groq -> Grounding in FoodDatabase.
  - Journey 4: Multi-day quota isolation & reset test across date boundaries.
  
### 2. Deployment Checklist (App Store Finalization) [DONE]
- [x] Ensure privacy consent dialogs align with international regulations (AppLovin CMP / Google UMP integration in `MainActivity.kt`, iOS `NSUserTrackingUsageDescription` in `Info.plist`, explicit `android:usesCleartextTraffic="false"` and `ACCESS_NETWORK_STATE` in `AndroidManifest.xml`).
- [x] Fully test and prepare Ad mediation dashboards for AppLovin MAX settings (`applovin_max_admob_certification_guide.md` runbook verified).
- [x] Production release build signed and verified (`signingConfigs.release` configured in `build.gradle.kts`, `:MacroVision-UI:assembleRelease` passing).
  
### 3. Post-Production Observability Targets
- [x] Automate telemetry on App Open ad fill rate via MAX SDK backend data (`AdTelemetryManager` with live request, loaded, impression, fill rate, and MAX `MaxAdRevenueListener` bridging to eCPM/ARPDAU metrics).
- [x] Monitor startup-stage and runtime diagnostic crash analytics (`DiagnosticsCrashHook` with in-memory circular buffering and persistent error logging hooked into `FailoverNutritionClient`, `CameraScreen`, and `AndroidAdManager`).
- [x] Log install cohort retention curve (Day 1, 7, 14, 30, 90) relative to `first_install_timestamp` with deduplicated active daily sessions (`CohortRetentionTracker`).
