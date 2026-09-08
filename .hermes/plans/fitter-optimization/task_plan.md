# Fitter Optimization & Master Architecture Plan (Antigravity Specification)

> **Execution Directive:** This document is the comprehensive, production-ready specification for Antigravity. All designs include concrete interface contracts, data models, and configuration targets so Antigravity can execute with zero ambiguity.

---

## Phase 4: Backend, Data Layer & VLM Pipeline Optimization (Cypher)

- **Objective:** Eliminate the main-thread SharedPreferences anti-pattern, establish a decoupled repository architecture with caching, enforce deterministic Ktor network timeouts, and optimize the VLM inference pipeline.
- **Target Files:**
  - `shared/src/commonMain/kotlin/com/fitter/shared/data/MealRepository.kt` (New)
  - `shared/src/commonMain/kotlin/com/fitter/shared/data/UserRepository.kt` (New)
  - `shared/src/commonMain/kotlin/com/fitter/shared/data/KeyValueStorage.kt` (New)
  - `shared/src/commonMain/kotlin/com/fitter/shared/api/GeminiClient.kt`
  - `shared/src/commonMain/kotlin/com/fitter/shared/api/OpenRouterClient.kt`
  - `shared/src/commonMain/kotlin/com/fitter/shared/api/FailoverNutritionClient.kt`
  - `shared/src/commonMain/kotlin/com/fitter/shared/api/FoodDatabase.kt`

### 1. Repository Pattern & Decoupled Storage Layer

#### Problem in Current Codebase
In `MacroVision-UI/src/commonMain/kotlin/com/fitter/app/App.kt` (lines 140–195), the entire meal history is serialized as a single monolithic JSON string to `SharedPreferences` on every save (`saveMealsList`). Linear scans are performed over all history to filter a single day's intake.

#### Antigravity Implementation Contract

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
```

```kotlin
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
```

```kotlin
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

#### Memory Caching & Dispatcher Policy
- Repository implementations must maintain an in-memory mutable cache guarded by Kotlin coroutine `Mutex`.
- All disk reads and writes must execute on `Dispatchers.IO` (or default background coroutine dispatcher in KMP).
- Provide migration scaffolding so existing JSON stored in `macrovision_prefs` / `user_profile` / `logged_meals` is read once on first launch and preserved.

---

### 2. VLM Inference Pipeline Hardening (`:shared/api/`)

#### Problem in Current Codebase
`GeminiClient`, `OpenRouterClient`, and `GroqClient` instantiate Ktor's `HttpClient` without the `HttpTimeout` plugin. If an upstream gateway hangs or network drops, requests hang indefinitely. Additionally, Gemini requests do not set `maxOutputTokens`, allowing unpredictable output token generation.

#### Antigravity Implementation Contract

1. **Ktor Client Timeout Configuration (Apply across all clients):**
   ```kotlin
   import io.ktor.client.plugins.HttpTimeout

   private val client = HttpClient {
       install(ContentNegotiation) {
           json(json)
       }
       install(HttpTimeout) {
           requestTimeoutMillis = 15_000L  // 15s max per request
           connectTimeoutMillis = 5_000L   // 5s connection handshake
           socketTimeoutMillis = 15_000L   // 15s socket read timeout
       }
   }
   ```

2. **Gemini Generation Configuration Hardening (`GeminiClient.kt`):**
   ```kotlin
   @Serializable
   internal data class GeminiGenerationConfig(
       val responseMimeType: String? = "application/json",
       val maxOutputTokens: Int? = 1000,
       val temperature: Float? = 0.2f
   )
   ```

3. **Deterministic Failover Loop (`FailoverNutritionClient.kt`):**
   - Ensure exceptions caught during Tier 1 (`gemini-3.5-flash-lite`) immediately trip to Tier 2 (`gemini-3.1-flash-lite`), then Tier 3 (`OpenRouter`), then Tier 4 (`Groq`) without delay.
   - Aggregate error messages with HTTP status codes for precise UI feedback.

---

### 3. Food Database Grounding Optimization (`FoodDatabase.kt`)

#### Problem in Current Codebase
`FoodDatabase.kt` performs unindexed linear scans over lists of food items during search and portion estimation.

#### Antigravity Implementation Contract
- Index the in-memory database with a normalized key map:
  ```kotlin
  private val foodIndex: Map<String, FoodItem> by lazy {
      foodList.associateBy { it.name.lowercase().trim() }
  }
  ```
- Expose an instant prefix search method:
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

## Phase 6: Aegis Quality Gates, Security & ProGuard Hardening

- **Objective:** Establish bulletproof security policies, robust ProGuard/R8 rules for KMP serialization, and comprehensive unit testing for quota/monetization logic.
- **Target Files:**
  - `MacroVision-UI/proguard-rules.pro`
  - `MacroVision-UI/src/commonTest/kotlin/com/fitter/app/ScanQuotaManagerTest.kt`
  - `shared/src/commonMain/kotlin/com/fitter/shared/security/`

### 1. Security & Credential Isolation
- Ensure all API keys (`GEMINI_API_KEY`, `GROQ_API_KEY`, `OPENROUTER_API_KEY`, `AD_MOB_APP_ID`) are injected via local environment properties or BuildConfig/BuildConfigFields, never hardcoded in source code or committed to git.
- Validate network traffic enforcement (HTTPS only, cleartext traffic disabled in AndroidManifest.xml).

### 2. ProGuard / R8 Rules for Kotlin Multiplatform Serialization
- Add strict keep rules for kotlinx.serialization and shared data classes to prevent obfuscation runtime crashes in production builds:
  ```pro
  -keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod
  -keepclassmembers class * {
      @kotlinx.serialization.Serializable <fields>;
  }
  -keep,allowaccessmodification class com.fitter.** { *; }
  ```

### 3. Comprehensive Test Coverage Suite (`ScanQuotaManagerTest`)
- **Test Case 1:** Week-1 onboarding grace period (installs within 7 days) correctly allows 5 free scans/day.
- **Test Case 2:** Week-2+ standard usage correctly restricts free limit to 3 scans/day.
- **Test Case 3:** Scan #4+ triggers forced interstitial ad flow (`showScanProcessingAd`) without throwing blocking reward modal dialogs.
- **Test Case 4:** Rewarded ad bonus credits correctly increment daily quota when applicable.
