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
import com.fitter.app.ui.navigation.CameraDestination
import com.fitter.app.ui.navigation.DashboardDestination
import com.fitter.app.ui.navigation.ResultDestination
import com.fitter.app.ui.navigation.SettingsDestination
import com.fitter.app.ui.components.newMealId
import com.fitter.app.ui.screens.camera.CameraScreen
import com.fitter.app.ui.screens.dashboard.DashboardScreen
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

            var playAdDuringScan by remember {
                mutableStateOf(loadPreference("play_ad_during_scan", "true").toBoolean())
            }

            var scansRemainingToday by remember(selectedDateKey) {
                mutableStateOf(ScanQuotaManager.getRemainingScans(selectedDateKey))
            }

            LaunchedEffect(Unit) {
                // ── Step 1: Ensure a valid Supabase anonymous JWT exists BEFORE any server call ──
                // All boot-side effects are gated on this.
                val userId = try {
                    authService.ensureSignedIn()
                } catch (e: Exception) {
                    println("Auth boot failed: ${e.message}")
                    null
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
                    com.fitter.shared.telemetry.TelemetryUploader.triggerFlush()
                }

                // Session & Ad Lifecycle
                AppOpenAdManager.incrementSessionCount()
                adManager.preloadAds()
                adManager.showAppOpenAdIfEligible()
            }

            // GatewayNutritionClient — JWT injected from auth service at call time
            val apiClient = remember {
                GatewayNutritionClient(
                    gatewayUrl = gatewayUrl,
                    jwtProvider = { authService.currentSession()?.accessToken }
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
                        playAdDuringScan = playAdDuringScan || ScanQuotaManager.shouldForceInterstitialAd(selectedDateKey),
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

                composable<SettingsDestination> {
                    SettingsScreen(
                        profile = userProfile,
                        adManager = adManager,
                        playAdDuringScan = playAdDuringScan,
                        scansRemainingToday = scansRemainingToday,
                        currentDateKey = selectedDateKey,
                        onTogglePlayAd = { enabled ->
                            playAdDuringScan = enabled
                            savePreference("play_ad_during_scan", enabled.toString())
                        },
                        onScansUpdated = {
                            scansRemainingToday = ScanQuotaManager.getRemainingScans(selectedDateKey)
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
            }
        }
    }
}
