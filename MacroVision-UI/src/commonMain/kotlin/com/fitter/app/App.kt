package com.fitter.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.fitter.app.ads.AdManager
import com.fitter.app.ads.AppOpenAdManager
import com.fitter.app.ads.ScanQuotaManager
import com.fitter.app.data.PreferenceKeyValueStorage
import com.fitter.app.telemetry.CohortRetentionTracker
import com.fitter.app.ui.navigation.AuthDestination
import com.fitter.app.ui.navigation.CameraDestination
import com.fitter.app.ui.navigation.DashboardDestination
import com.fitter.app.ui.navigation.MonetizationDestination
import com.fitter.app.ui.navigation.ResultDestination
import com.fitter.app.ui.navigation.SettingsDestination
import com.fitter.app.ui.components.newMealId
import com.fitter.app.ui.screens.auth.AuthScreen
import com.fitter.app.ui.screens.camera.CameraScreen
import com.fitter.app.ui.screens.dashboard.DashboardScreen
import com.fitter.app.ui.screens.monetization.MonetizationScreen
import com.fitter.app.ui.screens.review.ResultScreen
import com.fitter.app.ui.screens.settings.SettingsScreen
import com.fitter.app.ui.theme.BgColor
import com.fitter.app.ui.theme.FitterTheme
import com.fitter.shared.api.GatewayNutritionClient
import com.fitter.shared.auth.SupabaseAuthService
import com.fitter.shared.auth.SupabaseClientFactory
import com.fitter.shared.data.LocalMealRepository
import com.fitter.shared.data.LocalUserRepository
import com.fitter.shared.data.MealRepository
import com.fitter.shared.data.UserRepository
import com.fitter.shared.model.LoggedMeal
import com.fitter.shared.model.NutritionResponse
import com.fitter.shared.model.UserProfile
import com.fitter.app.telemetry.DiagnosticLevel
import com.fitter.app.telemetry.DiagnosticsCrashHook
import com.fitter.shared.telemetry.TelemetryUploader
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json

@Composable
fun App() {
    val coroutineScope = rememberCoroutineScope()
    val storage = remember {
        PreferenceKeyValueStorage().also {
            com.fitter.shared.subscription.SubscriptionManager.initialize(it)
        }
    }
    val supabaseClient = remember { SupabaseClientFactory.getOrCreate(supabaseUrl, supabaseAnonKey) }
    val authService = remember {
        SupabaseAuthService(supabaseUrl, supabaseAnonKey)
    }
    val quotaManager = remember {
        com.fitter.shared.quota.SupabaseQuotaManager(supabaseClient)
    }
    val syncEngine = remember {
        com.fitter.shared.sync.SyncEngine(storage, supabaseClient)
    }
    val mealRepository: MealRepository = remember {
        com.fitter.shared.data.SupabaseMealRepository(
            LocalMealRepository(storage),
            syncEngine,
            supabaseClient
        )
    }
    val userRepository: UserRepository = remember {
        com.fitter.shared.data.SupabaseUserRepository(
            LocalUserRepository(storage),
            syncEngine,
            supabaseClient
        )
    }

    var userProfile by remember {
        mutableStateOf(
            UserProfile(
                weight = 70f,
                height = 175f,
                calGoal = 2000,
                proteinGoal = 150,
                carbsGoal = 200,
                fatGoal = 65
            )
        )
    }

    var selectedDateKey by remember { mutableStateOf(getCurrentDateString()) }

    val mealsFlow = remember(selectedDateKey, mealRepository) {
        mealRepository.observeMealsForDate(selectedDateKey)
    }
    val loggedMeals by mealsFlow.collectAsState(initial = emptyList())

    val waterFlow = remember(selectedDateKey, userRepository) {
        userRepository.observeWaterIntake(selectedDateKey)
    }
    val waterLoggedToday by waterFlow.collectAsState(initial = 0)

    LaunchedEffect(Unit) {
        userProfile = userRepository.getUserProfile()
    }

    // Keep track of the last captured image bytes to render on ResultScreen
    var lastCapturedImageBytes by remember { mutableStateOf<ByteArray?>(null) }

    FitterTheme {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(BgColor)
        ) {
            val navController = rememberNavController()
                        val adManager = remember { getPlatformAdManager() }

                        // Ads are MANDATORY on the free tier: no user-facing opt-out switch.
                        // Premium users are gated ad-free by SubscriptionManager (both here and
                        // inside ScanQuotaManager). Keeping a single named boolean documents intent.
                        val showAdsForUser = !com.fitter.shared.subscription.SubscriptionManager.isPremiumUser()

            // Phase 10 — auth readiness gate:
            // null = boot in-flight | true = auth confirmed | false = offline (all retries failed)
            var authReady by remember { mutableStateOf<Boolean?>(null) }

            // Optional account email (null when using Fitter as an anonymous guest)
            var userEmail by remember { mutableStateOf(authService.getCurrentUserEmail()) }

            var scansRemainingToday by remember(selectedDateKey) {
                mutableStateOf(ScanQuotaManager.getRemainingScans(selectedDateKey))
            }

            // ── Primary boot: retry ensureSignedIn up to 3 times before declaring OfflineMode ─
            LaunchedEffect(Unit) {
                // ── Step 1: Ensure a valid Supabase anonymous JWT exists BEFORE any server call ──
                // Phase 10: bounded backoff retry — 3 attempts (1s / 2s / 4s delay between tries).
                // If all exhausted → authReady = false (OfflineMode); scan button is disabled.
                val backoffs = listOf(1_000L, 2_000L, 4_000L)
                var userId: String? = null
                try {
                    userId = authService.ensureSignedIn(
                        maxAttempts = 3,
                        backoffsMs = backoffs,
                        onAttemptFailed = { attempt, e ->
                            DiagnosticsCrashHook.log(
                                level = DiagnosticLevel.WARN,
                                tag = "Auth",
                                message = "ensureSignedIn attempt $attempt/${backoffs.size} failed: ${e.message}",
                                throwable = e
                            )
                            TelemetryUploader.trackDiagnostic(
                                level = "WARN",
                                tag = "auth",
                                message = "boot_retry_$attempt: ${e.message ?: "unknown"}",
                                details = null
                            )
                        }
                    )
                    userEmail = authService.getCurrentUserEmail()
                    authReady = true
                } catch (e: Exception) {
                    authReady = false
                    DiagnosticsCrashHook.log(
                        level = DiagnosticLevel.ERROR,
                        tag = "Auth",
                        message = "Boot auth failed after ${backoffs.size} attempts — OfflineMode active",
                        throwable = e
                    )
                    TelemetryUploader.trackDiagnostic(
                        level = "ERROR",
                        tag = "auth",
                        message = "boot_offline: 3 attempts exhausted",
                        details = null
                    )
                }


                // Record active retention session for cohort tracking
                CohortRetentionTracker.recordActiveDailySession(selectedDateKey)

                // ── Step 2: Boot-side effects (only after JWT confirmed) ─────────────────
                if (userId != null) {
                    // Supabase Server Quota sync
                    ScanQuotaManager.remoteQuotaManager = quotaManager
                    ScanQuotaManager.syncQuotaFromServer(selectedDateKey)
                    scansRemainingToday = ScanQuotaManager.getRemainingScans(selectedDateKey)

                    // Background sync
                    (mealRepository as? com.fitter.shared.data.SupabaseMealRepository)?.pullRemote(selectedDateKey)
                    (userRepository as? com.fitter.shared.data.SupabaseUserRepository)?.pullRemote(selectedDateKey)
                    syncEngine.flushOutbox()
                    TelemetryUploader.triggerFlush()
                }

                // Session & Ad Lifecycle
                AppOpenAdManager.incrementSessionCount()
                adManager.preloadAds()
                adManager.showAppOpenAdIfEligible()
            }

            // ── Recovery observer: when auth goes offline, periodically retry sign-in ─────────
            // Re-runs whenever authReady transitions to false. Uses exponential backoff so we
            // don't hammer the network while offline. Once auth is restored, gates re-open.
            LaunchedEffect(authReady) {
                if (authReady == false) {
                    val recoveryBackoffs = listOf(5_000L, 15_000L, 30_000L, 60_000L)
                    for (recoveryDelay in recoveryBackoffs) {
                        delay(recoveryDelay)
                        try {
                            authService.ensureSignedIn()
                            authReady = true
                            // Trigger server sync now that we're back online
                            ScanQuotaManager.remoteQuotaManager = quotaManager
                            ScanQuotaManager.syncQuotaFromServer(selectedDateKey)
                            scansRemainingToday = ScanQuotaManager.getRemainingScans(selectedDateKey)
                            DiagnosticsCrashHook.log(
                                level = DiagnosticLevel.INFO,
                                tag = "Auth",
                                message = "Auth recovered after offline period"
                            )
                            TelemetryUploader.trackDiagnostic(
                                level = "INFO",
                                tag = "auth",
                                message = "boot_recovered",
                                details = null
                            )
                            break
                        } catch (_: Exception) {
                            // Still offline — continue waiting
                        }
                    }
                }
            }

            // GatewayNutritionClient — JWT injected from auth service at call time.
            // Phase 10: reAuthenticator re-establishes the session on 401/missing JWT
            // so a stale/expired token triggers re-auth + one retry instead of the
            // "Authentication required" dead-end.
            val apiClient = remember {
                GatewayNutritionClient(
                    gatewayUrl = gatewayUrl,
                    jwtProvider = { authService.currentSession()?.accessToken },
                    reAuthenticator = { authService.ensureSignedIn() }
                )
            }

            // Client-side mock mode is retired. The Worker controls mock responses via FITTER_ENV=dev.
            // Keep isMockMode = false so CameraScreen/ResultScreen compile without changes.
            val isMockMode = false

            NavHost(
                navController = navController,
                startDestination = DashboardDestination,
                modifier = Modifier.fillMaxSize()
            ) {
                composable<DashboardDestination> {
                    DashboardScreen(
                        meals = loggedMeals,
                        profile = userProfile,
                        selectedDate = selectedDateKey,
                        waterLogged = waterLoggedToday,
                        scansRemaining = scansRemainingToday,
                        onDateSelected = { newDate ->
                            selectedDateKey = newDate
                        },
                        onWaterChanged = { newWater ->
                            coroutineScope.launch {
                                userRepository.setWaterIntake(selectedDateKey, newWater)
                            }
                        },
                        onScanClicked = {
                            navController.navigate(CameraDestination)
                        },
                        onSettingsClicked = {
                            navController.navigate(SettingsDestination)
                        },
                        onMonetizationClicked = {
                            navController.navigate(MonetizationDestination)
                        },
                        onDeleteMeal = { mealToDelete ->
                            coroutineScope.launch {
                                mealRepository.deleteMeal(mealToDelete.id)
                            }
                        },
                        onRestoreMeal = { mealToRestore ->
                            coroutineScope.launch {
                                mealRepository.saveMeal(mealToRestore)
                            }
                        }
                    )
                }

                composable<CameraDestination> {
                    CameraScreen(
                        apiClient = apiClient,
                        isMockMode = isMockMode,
                        plateSizeInches = userProfile.defaultPlateSize,
                        adManager = adManager,
                        playAdDuringScan = showAdsForUser || ScanQuotaManager.shouldForceInterstitialAd(selectedDateKey),
                        onScanConsumed = {
                            coroutineScope.launch {
                                ScanQuotaManager.consumeScanServer(selectedDateKey)
                                scansRemainingToday = ScanQuotaManager.getRemainingScans(selectedDateKey)
                            }
                        },
                        onPhotoCaptured = { bytes ->
                            lastCapturedImageBytes = bytes
                        },
                        onResultObtained = { responseJson ->
                            navController.navigate(ResultDestination(responseJson)) {
                                popUpTo(DashboardDestination) { saveState = false }
                            }
                        },
                        onNavigateBack = {
                            navController.popBackStack()
                        },
                        scanReadiness = ensureReadyForScan(authReady, scansRemainingToday)
                    )
                }


                composable<ResultDestination> { backStackEntry ->
                    val destination = backStackEntry.toRoute<ResultDestination>()
                    val nutritionData = remember(destination.responseJson) {
                        Json.decodeFromString<NutritionResponse>(destination.responseJson)
                    }
                    ResultScreen(
                        apiClient = apiClient,
                        data = nutritionData,
                        isMock = isMockMode,
                        capturedImageBytes = lastCapturedImageBytes,
                        onMealLogged = { mealName, cal, p, c, f ->
                            val newMeal = LoggedMeal(
                                id = newMealId(), // F0.1: collision-proof UUID v4
                                name = mealName,
                                calories = cal,
                                protein = p,
                                carbs = c,
                                fat = f,
                                timestamp = getCurrentTimeString(),
                                date = selectedDateKey
                            )
                            coroutineScope.launch {
                                mealRepository.saveMeal(newMeal)
                            }
                        },
                        onLogAgain = {
                            lastCapturedImageBytes = null
                            navController.navigate(CameraDestination) {
                                popUpTo(DashboardDestination)
                            }
                        }
                    )
                }

                composable<MonetizationDestination> {
                    MonetizationScreen(
                        adManager = adManager,
                        scansRemainingToday = scansRemainingToday,
                        currentDateKey = selectedDateKey,
                        onScansUpdated = {
                            scansRemainingToday = ScanQuotaManager.getRemainingScans(selectedDateKey)
                        },
                        onBack = {
                            navController.popBackStack()
                        }
                    )
                }

                composable<SettingsDestination> {
                    SettingsScreen(
                        profile = userProfile,
                        userEmail = userEmail,
                        onNavigateToAuth = {
                            navController.navigate(AuthDestination)
                        },
                        onSignOut = {
                            coroutineScope.launch {
                                authService.signOut()
                                userEmail = authService.getCurrentUserEmail()
                                authReady = true
                            }
                        },
                        onSave = { updated ->
                            coroutineScope.launch {
                                userRepository.saveUserProfile(updated)
                                userProfile = updated
                            }
                            navController.popBackStack()
                        },
                        onBack = {
                            navController.popBackStack()
                        }
                    )
                }

                composable<AuthDestination> {
                    AuthScreen(
                        onSignIn = { email, pass ->
                            authService.signInWithEmail(email, pass)
                            userEmail = authService.getCurrentUserEmail() ?: email
                            authReady = true
                            try {
                                syncEngine.flushOutbox()
                                (mealRepository as? com.fitter.shared.data.SupabaseMealRepository)?.pullRemote(selectedDateKey)
                            } catch (_: Throwable) {
                                // Non-fatal sync error
                            }
                            navController.popBackStack()
                        },
                        onSignUp = { email, pass ->
                            authService.signUpWithEmail(email, pass)
                            userEmail = authService.getCurrentUserEmail() ?: email
                            authReady = true
                            try {
                                syncEngine.flushOutbox()
                            } catch (_: Throwable) {
                                // Non-fatal sync error
                            }
                            navController.popBackStack()
                        },
                        onContinueAsGuest = {
                            navController.popBackStack()
                        },
                        onBack = {
                            navController.popBackStack()
                        }
                    )
                }
            }
        }
    }
}
