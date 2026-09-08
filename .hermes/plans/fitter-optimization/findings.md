# Findings — fitter-optimization

> Append-only. Chronological discoveries, research results, decisions, and corrections. Never rewrite history here.

## 2026-09-07

### Discovery: Legacy Mockups in Production UI Module
- **Observation:** `MacroVision-UI/` contained standalone HTML mockups (`index.html`, `macrovision-mvp.html`, and `screens/01-dashboard.html`, `02-scanning.html`, `03-review.html`).
- **Impact:** Littered the Android/KMP module root; purely historical design artifacts no longer used by Gradle.
- **Action:** Safely deleted after verifying zero code references (only one comment in `App.kt`). Verified test suite passes immediately post-deletion.

### Discovery: Monolithic Compose File (`App.kt`)
- **Observation:** `MacroVision-UI/src/commonMain/kotlin/com/fitter/app/App.kt` is 2,994 lines long.
- **Impact:** Violates clean architecture boundaries. Houses navigation, theme, user profile state, meal logging state, canvas animations, camera preview wrappers, ad triggers, and all screen composables in a single file. High risk of recomposition churn and merge conflicts.
- **Decision:** Mandate Phase 5 modularization for Nexus (@frontend) to split into dedicated packages: `ui/theme/`, `ui/screens/`, `ui/components/`, `data/repository/`.

### Discovery: Java/JDK Environment Configuration
- **Observation:** System `JAVA_HOME` was configured to non-existent JDK 23 (`C:\Program Files\Java\jdk-23`), failing Gradle invocations.
- **Resolution:** Android Studio bundled OpenJDK 21 (`C:\Program Files\Android\Android Studio1\jbr`) is fully functional. Gradle builds and runs in ~1s with configuration caching.

### Discovery: Monetization Architecture & Compliance Requirements
- **Spec Alignment:** Verified against `C:\Users\ahmed\Documents\Hermes-Workspace\App-Monetization-Masterfile.md` and `C:\GitHub\Fitter\AGENTS.md`.
- **Policy Constraint:** Forcing a Rewarded Ad is a severe Google Play policy violation (rewarded ads require voluntary user click). Therefore, forced extra scans on Scan #4+ MUST use Interstitial/Playable ads via `showScanProcessingAd`.
- **Onboarding Quota Rule:** Week 1 users (< 7 days from install) receive 5 free AI scans/day. Week 2+ users receive 3 free AI scans/day. Extra scans trigger the forced interstitial.
- **Mediation Plan:** AppLovin MAX with Google AdMob bidding adapter + Meta + Unity + Mintegral demand. App Open ads require 3-session grace period and 4-hour cooldown.

### Discovery: Backend Persistence & Network Latency Architecture (Phase 4)
- **Observation:** `App.kt` serializes the full lifetime array of `LoggedMeal` objects into a single raw JSON string on the UI thread on every meal mutation (`saveMealsList()`).
- **Bottleneck:** High main-thread I/O latency and lack of indexing for date-based retrieval.
- **Architectural Solution:** Designed clean `MealRepository`, `UserRepository`, and `KeyValueStorage` interfaces in `:shared` with mutex-protected in-memory caching and IO dispatchers.
- **VLM Pipeline Audit:** Ktor `HttpClient` instances lacked `HttpTimeout` configuration and explicit token generation bounds, allowing hung network calls to stall the failover pipeline. Fixed in specification with 15s request timeout and 1000 max output token limit.
- **Grounding Optimization:** Added normalized Map indexing to `FoodDatabase.kt` to eliminate linear scans during interactive meal corrections.
