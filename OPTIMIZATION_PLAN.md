# Fitter Master Optimization & Monetization Plan (Antigravity Blueprint)

> **Target Repository:** `C:\GitHub\Fitter` (Kotlin Multiplatform / Compose Multiplatform)  
> **Authoring Team:** Core Dev Team (Alistair, Vance, Cypher, Nexus, Aegis)  
> **Status:** Ready for Antigravity Execution (Zero Code Changes Made in Chat — Pure Specification)

---

## 1. Executive Summary & Architectural Overview

Fitter (MacroVision) is a Kotlin Multiplatform app that scans meal photos using Vision-Language Models (VLMs) to provide real-time macronutrient breakdown and interactive ingredient editing.

### Core Objectives for Antigravity:
1. **Monetization Engine:** Implement a high-LTV hybrid monetization model (AppLovin MAX + AdMob unified bidding, onboarding scan policy, forced interstitials on scan #4+, App Open ads, brand safety).
2. **Architecture Refactoring:** Modularize the monolithic 2,994-line `App.kt` into clean, maintainable Compose packages.
3. **Data Layer Decoupling:** Extract meal logging and user profile storage from SharedPreferences into an isolated local repository layer.
4. **VLM Pipeline Hardening:** Optimize the OpenRouter $\to$ Gemini $\to$ Groq failover sequence for sub-2s latency and reduced token overhead.
5. **Quality Gates & Policy Compliance:** Zero credential leaks, ProGuard preservation, and 100% test pass rate.

---

## 2. Finance Specification (Vance) — Monetization Engine

### 2.1 Onboarding Daily Scan Quotas
- **Week 1 (Days 1–7):** **5 free AI scans/day**. Zero ad friction to maximize user onboarding and habit formation.
- **Week 2+ (Day 8 onward):** **3 free AI scans/day**.
- **Implementation Target:** `MacroVision-UI/src/commonMain/kotlin/com/fitter/app/ads/AdManager.kt` (`ScanQuotaManager`):
  - Store `first_install_timestamp` (epoch millis) on first launch.
  - Calculate user age in days: `val isWeekOne = (now - installTimestamp) < 7 * 24 * 3600 * 1000L`.
  - Dynamic daily limit: `if (isWeekOne) 5 else 3`.

### 2.2 Forced Interstitial on Scan #4+ (Replacing Rewarded Modal)
- **Policy Compliance:** Google Play and AppLovin MAX explicitly ban forced Rewarded Ads (rewarded ads require voluntary user opt-in). Forced ads **must be Interstitial or Playable ads**.
- **Trigger Logic:** When `ScanQuotaManager.hasQuota(today)` is false and user initiates a meal scan:
  - Do **not** show an interruption modal or "watch ad" prompt.
  - Immediately invoke `adManager.showScanProcessingAd { processMealWithAI(photo) }`.
  - The ad displays during the analysis wait; upon ad dismissal, the AI analysis completes and renders the review screen.

### 2.3 Unified Mediation: AppLovin MAX + AdMob Bidding
- Migrate from raw AdMob SDK to AppLovin MAX unified header bidding.
- Integrate Google AdMob as an in-app bidding demand partner inside MAX alongside Meta Audience Network, Unity Ads, and Mintegral.
- Expected outcome: 30–40% eCPM lift ($25–$45+ Tier 1 rewarded/interstitial eCPM).

### 2.4 App Open Ads with Cooldown & Grace Period
- **Grace Period:** Suppress App Open ads for sessions 1, 2, and 3.
- **Cooldown:** Minimum 4-hour cooldown between App Open ad displays.
- **Storage:** Persist `session_count` and `last_app_open_ad_timestamp`.

### 2.5 Banner Placement & Brand Safety
- **Placement:** Adaptive banners strictly on passive tabs (Macro History, Settings). **Zero ads on Camera/Scan and Review views.**
- **Brand Safety:** Configure strict category blocks (Gambling, Dating, Politics, Cosmetic Surgery, Unverified Apps, Clickbait).

---

## 3. Frontend Specification (Nexus) — Compose Modularization

### 3.1 Dismantling `App.kt` (2,994 Lines)
Split `MacroVision-UI/src/commonMain/kotlin/com/fitter/app/App.kt` into the following package structure:

```
MacroVision-UI/src/commonMain/kotlin/com/fitter/app/
├── ui/
│   ├── theme/
│   │   ├── Color.kt             (Tokens from brand-spec.md: Slate 50, Emerald, Slate 900)
│   │   ├── Type.kt              (Typography & font definitions)
│   │   └── Theme.kt             (FitterTheme wrapper)
│   ├── navigation/
│   │   └── NavRoutes.kt         (DashboardDestination, CameraDestination, ResultDestination, SettingsDestination)
│   ├── screens/
│   │   ├── dashboard/
│   │   │   ├── DashboardScreen.kt
│   │   │   └── components/DailyMacroCard.kt, CalorieRing.kt
│   │   ├── camera/
│   │   │   └── CameraScreen.kt  (Strictly banner-free)
│   │   ├── review/
│   │   │   ├── ReviewScreen.kt
│   │   │   └── components/FoodItemCard.kt, SwapSearchOverlay.kt
│   │   └── settings/
│   │       └── SettingsScreen.kt
│   └── components/
│       ├── CommonButtons.kt
│       └── MacroBar.kt
└── App.kt                       (Root NavHost shell < 80 lines)
```

### 3.2 Performance Targets
- Sub-100ms first composition.
- Zero layout shift (CLS = 0) during image loading and ad delivery.
- State hoisting: Extract all business and meal-logging state into ViewModels/StateHolders.

---

## 4. Backend & Data Specification (Cypher) — Pipeline & Storage

### 4.1 Local Persistence Architecture & Repository Pattern
Move `UserProfile` and `LoggedMeal` out of raw main-thread SharedPreferences JSON strings into an asynchronous, cached repository pattern in `:shared`:

```kotlin
// shared/src/commonMain/kotlin/com/fitter/shared/data/KeyValueStorage.kt
package com.fitter.shared.data

interface KeyValueStorage {
    fun getString(key: String, defaultValue: String = ""): String
    fun putString(key: String, value: String)
    fun getInt(key: String, defaultValue: Int = 0): Int
    fun putInt(key: String, value: Int)
    fun remove(key: String)
}

// shared/src/commonMain/kotlin/com/fitter/shared/data/MealRepository.kt
package com.fitter.shared.data

import com.fitter.shared.model.LoggedMeal
import kotlinx.coroutines.flow.Flow

interface MealRepository {
    suspend fun getMealsForDate(date: String): List<LoggedMeal>
    suspend fun getAllMeals(): List<LoggedMeal>
    suspend fun saveMeal(meal: LoggedMeal)
    suspend fun deleteMeal(mealId: String)
    suspend fun updateMeal(meal: LoggedMeal)
    fun observeMealsForDate(date: String): Flow<List<LoggedMeal>>
}

// shared/src/commonMain/kotlin/com/fitter/shared/data/UserRepository.kt
package com.fitter.shared.data

import com.fitter.shared.model.UserProfile
import kotlinx.coroutines.flow.Flow

interface UserRepository {
    suspend fun getUserProfile(): UserProfile
    suspend fun saveUserProfile(profile: UserProfile)
    suspend fun getWaterIntake(date: String): Int
    suspend fun setWaterIntake(date: String, amountMl: Int)
    fun observeWaterIntake(date: String): Flow<Int>
}
```

- **In-Memory Cache:** Protect active lists with Kotlin `Mutex` and dispatch disk writes to `Dispatchers.IO`.
- **Backward Compatibility:** Provide migration reader so existing `user_profile` and `logged_meals` JSON in preferences are read once on initial launch and converted.

### 4.2 VLM Failover Pipeline Optimization (`:shared`)
- **Pipeline:** Tier 1: Gemini 3.5 Flash Lite $\to$ Tier 2: Gemini 3.1 Flash Lite $\to$ Tier 3: OpenRouter (Qwen2.5-VL / Minimax) $\to$ Tier 4: Groq.
- **Ktor Network Timeout Hardening:** Apply `HttpTimeout` to all client instances (`GeminiClient`, `OpenRouterClient`, `GroqClient`):
  ```kotlin
  import io.ktor.client.plugins.HttpTimeout

  private val client = HttpClient {
      install(ContentNegotiation) { json(json) }
      install(HttpTimeout) {
          requestTimeoutMillis = 15_000L  // 15s request limit
          connectTimeoutMillis = 5_000L   // 5s connection handshake
          socketTimeoutMillis = 15_000L   // 15s socket read timeout
      }
  }
  ```
- **Gemini Token Bounding:** Enforce `maxOutputTokens = 1000` and `temperature = 0.2f` in `GeminiGenerationConfig` to guarantee deterministic, rapid JSON output.
- **Image Preprocessing:** Verify downsampling in `PlatformConfig` enforces 768px max dimension and JPEG 80% compression (< 180KB payload) before Base64 encoding.

### 4.3 Grounding & Local Search Optimization (`FoodDatabase.kt`)
Replace linear scans with indexed hash map lookups:
```kotlin
private val foodIndex: Map<String, FoodItem> by lazy {
    foodList.associateBy { it.name.lowercase().trim() }
}
```
Expose instant prefix search for interactive corrections:
```kotlin
fun searchFoods(query: String, limit: Int = 10): List<FoodItem> {
    val normalized = query.lowercase().trim()
    if (normalized.isEmpty()) return emptyList()
    return foodList.asSequence()
        .filter { it.name.lowercase().contains(normalized) }
        .take(limit)
        .toList()
}
```


---

## 5. Security & Quality Specification (Aegis) — Quality Gates

### 5.1 Credential Isolation
- Verify `.env` is listed in `.gitignore` and never bundled into version control.
- In `build.gradle.kts`, ensure API keys are injected via BuildConfig exclusively.

### 5.2 ProGuard / R8 Rules (`proguard-rules.pro`)
- Keep rules for Kotlinx Serialization (`com.fitter.shared.model.**`).
- Keep rules for AppLovin MAX and Google Mobile Ads SDKs.

### 5.3 Test Suite Expansion (`ScanQuotaManagerTest.kt`)
Add tests verifying:
1. `testWeekOneUserGetsFiveScans()`
2. `testWeekTwoUserGetsThreeScans()`
3. `testScanFourTriggersProcessingAdWithoutBlocking()`
4. `testAppOpenAdRespectsSessionGracePeriodAndCooldown()`

---

## 6. Antigravity Prompting & Execution Steps

Feed the following sequence into Antigravity:

1. **Step 1:** Implement Section 2 (Finance/Monetization) in `ScanQuotaManager.kt` and expand `ScanQuotaManagerTest.kt`. Verify tests pass with `./gradlew :MacroVision-UI:testDebugUnitTest`.
2. **Step 2:** Implement Section 4 (Backend Repository) in `:shared` to decouple meal logging from UI preferences.
3. **Step 3:** Implement Section 3 (Frontend Modularization) by breaking down `App.kt` into the specified `ui/` packages.
4. **Step 4:** Implement Section 5 (ProGuard and Ad SDK build configurations) and verify clean release build.
