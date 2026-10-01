package com.fitcal.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.fitcal.app.ads.AdManager
import com.fitcal.app.privacy.PrivacyConsent
import com.fitcal.app.ui.screens.privacy.PrivacyConsentScreen
import com.fitcal.app.ads.AppOpenAdManager
import com.fitcal.app.ads.ScanQuotaManager
import com.fitcal.app.data.PreferenceKeyValueStorage
import com.fitcal.app.telemetry.CohortRetentionTracker
import com.fitcal.app.ui.navigation.AuthDestination
import com.fitcal.app.ui.navigation.CameraDestination
import com.fitcal.app.ui.navigation.DashboardDestination
import com.fitcal.app.ui.navigation.MonetizationDestination
import com.fitcal.app.ui.navigation.ResultDestination
import com.fitcal.app.ui.navigation.SettingsDestination
import com.fitcal.app.ui.navigation.FoodLibraryDestination
import com.fitcal.app.ui.navigation.PrivacyPolicyDestination
import com.fitcal.app.ui.navigation.TermsDestination
import com.fitcal.app.ui.screens.privacy.PrivacyPolicyScreen
import com.fitcal.app.ui.screens.privacy.AgeGateScreen
import com.fitcal.app.ui.screens.library.FoodLibraryScreen
import com.fitcal.app.privacy.AgeGate
import com.fitcal.app.ui.components.newMealId
import com.fitcal.app.ui.screens.auth.AuthScreen
import com.fitcal.app.ui.screens.camera.CameraScreen
import com.fitcal.app.ui.screens.dashboard.DashboardScreen
import com.fitcal.app.ui.screens.monetization.MonetizationScreen
import com.fitcal.app.ui.screens.review.ResultScreen
import com.fitcal.app.ui.screens.settings.SettingsScreen
import com.fitcal.app.ui.theme.BgColor
import com.fitcal.app.ui.theme.FitCalTheme
import com.fitcal.shared.api.GatewayNutritionClient
import com.fitcal.shared.auth.SupabaseAuthService
import com.fitcal.shared.auth.SupabaseClientFactory
import com.fitcal.shared.data.LocalMealRepository
import com.fitcal.shared.data.LocalUserRepository
import com.fitcal.shared.data.MealRepository
import com.fitcal.shared.data.UserRepository
import com.fitcal.shared.model.LoggedMeal
import com.fitcal.shared.model.NutritionResponse
import com.fitcal.shared.model.UserProfile
import com.fitcal.app.telemetry.DiagnosticLevel
import com.fitcal.app.telemetry.DiagnosticsCrashHook
import com.fitcal.shared.telemetry.TelemetryUploader
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json

@Composable
fun App() {
    val coroutineScope = rememberCoroutineScope()
    val storage = remember {
        PreferenceKeyValueStorage().also {
            com.fitcal.shared.subscription.SubscriptionManager.initialize(it)
        }
    }
    val supabaseClient = remember { SupabaseClientFactory.getOrCreate(supabaseUrl, supabaseAnonKey) }
    val authService = remember {
        SupabaseAuthService(supabaseUrl, supabaseAnonKey)
    }
    val quotaManager = remember {
        com.fitcal.shared.quota.SupabaseQuotaManager(supabaseClient).apply {
            // Rewarded-ad bonus grants must be server-authoritative: the client used
            // to call grant_bonus_scan directly, which let any user mint unlimited
            // scans. The gateway derives the user from the verified JWT instead.
            gatewayClient = GatewayNutritionClient(
                gatewayUrl = gatewayUrl,
                jwtProvider = { authService.currentSession()?.accessToken },
                reAuthenticator = { authService.ensureSignedIn() },
                dailyAllowanceProvider = { ScanQuotaManager.getDailyFreeLimit() },
            )
        }
    }
    val syncEngine = remember {
        com.fitcal.shared.sync.SyncEngine(storage, supabaseClient)
    }
    val mealRepository: MealRepository = remember {
        com.fitcal.shared.data.SupabaseMealRepository(
            LocalMealRepository(storage),
            syncEngine,
            supabaseClient
        )
    }
    val userRepository: UserRepository = remember {
        com.fitcal.shared.data.SupabaseUserRepository(
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

    // ── Privacy consent gate ──────────────────────────────────────────────────
    // Nothing below this line may run before consent: no Supabase session, no ad
    // preload, no camera, no gateway call. initPrivacyConsentStore() is invoked by
    // the platform entry point (MainActivity / iOS main) before setContent.
    var privacyAccepted by remember { mutableStateOf(PrivacyConsent.isPrivacyAccepted()) }
    var showLegalDocument by remember { mutableStateOf(false) }
    var ageAcknowledged by remember { mutableStateOf(AgeGate.hasAcknowledged()) }

    // Set when the user confirms the eating-disorder caution at the age gate.
    // Suppresses calorie-goal emphasis for younger adult profiles.
    var userProfileCautionAccepted by remember { mutableStateOf(false) }

    // Legal docs must be readable BEFORE consent — a user cannot accept terms
    // they are unable to read. This early return keeps it a full-screen overlay
    // rather than composing both screens at once.
    if (showLegalDocument) {
        FitCalTheme {
            PrivacyPolicyScreen(onClose = { showLegalDocument = false })
        }
        return
    }

    if (!privacyAccepted) {
        FitCalTheme {
            PrivacyConsentScreen(
                onDecisionComplete = { privacyAccepted = true },
                onOpenPrivacyPolicy = { showLegalDocument = true },
                onOpenTerms = { showLegalDocument = true },
            )
        }
        return
    }

    // ── Age gate (Play Families / AdMob adult-content policy) ───────────────
    // Runs AFTER consent but BEFORE any app surface. FitCal is declared 18+ only;
    // this makes the declaration enforceable rather than a store-listing promise.
    if (!ageAcknowledged) {
        FitCalTheme {
            AgeGateScreen(
                onComplete = {
                    AgeGate.setAcknowledged(true)
                    ageAcknowledged = true
                    userProfileCautionAccepted = it
                },
            )
        }
        return
    }

    FitCalTheme {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(BgColor)
        ) {
            val navController = rememberNavController()
            val adManager = remember { getPlatformAdManager() }

            // Ad visibility = premium status AND an accepted privacy decision.
            // Personalized ad *requests* are additionally gated on the CMP/ATT
            // signal in MainActivity — showing a non-personalized ad is always
            // allowed once the privacy notice has been accepted.
            val showAdsForUser = !com.fitcal.shared.subscription.SubscriptionManager.isPremiumUser() &&
                PrivacyConsent.canRequestAds()

            // Phase 10 — auth readiness gate:
            // null = boot in-flight | true = auth confirmed | false = offline (all retries failed)
            var authReady by remember { mutableStateOf<Boolean?>(null) }

            // Optional account email (null when using FitCal as an anonymous guest)
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
                    ScanQuotaManager.remoteQuotaManager = quotaManager
                    ScanQuotaManager.syncQuotaFromServer(selectedDateKey)
                    scansRemainingToday = ScanQuotaManager.getRemainingScans(selectedDateKey)
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
                    // Background sync
                    (mealRepository as? com.fitcal.shared.data.SupabaseMealRepository)?.pullRemote(selectedDateKey)
                    (userRepository as? com.fitcal.shared.data.SupabaseUserRepository)?.pullRemote(selectedDateKey)
                    syncEngine.flushOutbox()
                    TelemetryUploader.triggerFlush()
                }

                // Session & Ad Lifecycle + Meal Scan Reminder Notifications
                AppOpenAdManager.incrementSessionCount()
                adManager.preloadAds()
                adManager.showAppOpenAdIfEligible()
                com.fitcal.app.notifications.MealReminderManager.syncNotifications()
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
                            // Trigger server sync now that we're back online before unlocking scan gate
                            ScanQuotaManager.remoteQuotaManager = quotaManager
                            ScanQuotaManager.syncQuotaFromServer(selectedDateKey)
                            scansRemainingToday = ScanQuotaManager.getRemainingScans(selectedDateKey)
                            authReady = true
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
                    reAuthenticator = { authService.ensureSignedIn() },
                    dailyAllowanceProvider = { ScanQuotaManager.getDailyFreeLimit() }
                )
            }

            // Client-side mock mode is retired. The Worker controls mock responses via FITCAL_ENV=dev.
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
                        onOpenFoodLibrary = {
                            navController.navigate(FoodLibraryDestination)
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
                            // Record local consumption immediately for responsive UI; the Gateway already
                            // consumed 1 server-side quota after VLM inference succeeded, so sync from
                            // server instead of calling consumeScanServer (which would double-increment).
                            ScanQuotaManager.consumeScan(selectedDateKey)
                            scansRemainingToday = ScanQuotaManager.getRemainingScans(selectedDateKey)
                            coroutineScope.launch {
                                ScanQuotaManager.syncQuotaFromServer(selectedDateKey)
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
                        scanReadiness = ensureReadyForScan(authReady, scansRemainingToday),
                        scansRemaining = scansRemainingToday,
                        onBonusScansEarned = {
                            ScanQuotaManager.addBonusScansSuspend(selectedDateKey, 2)
                            scansRemainingToday = ScanQuotaManager.getRemainingScans(selectedDateKey)
                        }
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
                        },
                        onDeleteAllData = {
                            // Play requires a real account-deletion path, and GDPR
                            // Art. 17 requires the request actually be honoured.
                            coroutineScope.launch {
                                val confirmed = apiClient.deleteAccount()
                                if (confirmed) {
                                    PrivacyConsent.clearAll()
                                    AgeGate.reset()
                                    privacyAccepted = false
                                    ageAcknowledged = false
                                    authService.signOut()
                                    userEmail = null
                                }
                            }
                        },
                        onOpenPrivacyPolicy = {
                            navController.navigate(PrivacyPolicyDestination)
                        },
                        onOpenTerms = {
                            navController.navigate(TermsDestination)
                        },
                    )
                }

                composable<PrivacyPolicyDestination> {
                    PrivacyPolicyScreen(
                        onClose = { navController.popBackStack() },
                        showTermsFirst = false,
                    )
                }

                composable<TermsDestination> {
                    PrivacyPolicyScreen(
                        onClose = { navController.popBackStack() },
                        showTermsFirst = true,
                    )
                }

                composable<FoodLibraryDestination> {
                    FoodLibraryScreen(
                        onBack = { navController.popBackStack() },
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
                                (mealRepository as? com.fitcal.shared.data.SupabaseMealRepository)?.pullRemote(selectedDateKey)
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
