# Fitter Frontend Account & Optional Login — Round 3 Antigravity Task Spec

> **Execution directive:** Continue the work in the Fitter repo (`C:\GitHub\Fitter`, Kotlin Multiplatform / Jetpack Compose, Android + iOS). Rounds 1 (`frontend_polish_tasks.md`, F0–F5) and 2 (`frontend_visual_polish_tasks.md`, G1–G6) are DONE and verified. Implement **all tasks in phases A1 → A4**, in order.

## Product & Design Principles For This Round

- **Login is 100% optional:** The app must be fully usable as a guest (anonymous session). Never show a login gate on cold start or before Camera / Review / Dashboard.
- **Guest-mode ad parity:** Guests and signed-in users experience the **exact same ad load**. Ad visibility gates solely on `!SubscriptionManager.isPremiumUser()` — never penalize guests with extra ads or new ad surfaces.
- **Token & component discipline:** Reuse `FitterTextField`, `BrandTypography` (`ScreenTitle`, `CardTitle`, `SectionTitle`, `Body`, `BodySmall`, `Micro`), radii (`RadiusS`, `RadiusM`, `RadiusL`), `PressableBox`, `REDUCED_MOTION_ENABLED`, and 44dp minimum touch targets. Zero raw hex `Color(0x...)` in `ui/screens/*`.
- **Single auth provider:** Use `supabase-kt` (`io.github.jan.supabase.auth.providers.builtin.Email`) via `SupabaseAuthService` for email/password sign-in, sign-up, and sign-out. Zero client-side secrets.
- **Guard rule:** If the app doesn't compile because of `App.kt` / `GatewayNutritionClient` auth wiring, stop and report — do not paper over it.

---

## A1 — Account Section in Settings (`SettingsScreen.kt`)

**Objective:** Surface account state and optional login/sign-out controls inside `SettingsScreen.kt`.

**Contract:**
- Add an `ACCOUNT` card to `SettingsScreen.kt` (`CardBackground`, `RadiusL`, 1dp `BorderColor`, `shadow(2.dp, RoundedCornerShape(RadiusL))`):
  - Card title: `Text("ACCOUNT", style = BrandTypography.CardTitle, color = MutedTextColor)`
  - **Guest state (`userEmail == null`):**
    - Status row: `"Guest Mode"` (`BrandTypography.SectionTitle`, `TextColor`)
    - Supporting copy: `"Using Fitter as a guest. Sign in with email to back up and sync your meals and goals across devices — login is optional."` (`BrandTypography.BodySmall`, `TextColor`)
    - CTA button: `"Sign In / Create Account"` (`Button`, `heightIn(min = 48.dp)`, `RoundedCornerShape(RadiusM)`, `PrimaryAccent`) → calls `onNavigateToAuth()`
  - **Signed-in state (`userEmail != null`):**
    - Profile row showing `"Signed in as"` (`BrandTypography.BodySmall`, `MutedTextColor`) and `userEmail` (`BrandTypography.SectionTitle`, `TextColor`)
    - Status copy: `"Cloud sync active across devices."` (`BrandTypography.BodySmall`, `TextColor`)
    - `"Sign Out"` button (`OutlinedButton`, `heightIn(min = 48.dp)`, `RoundedCornerShape(RadiusM)`, `BorderStroke(1.dp, BorderColor)`) → calls `onSignOut()`
- Extend `SettingsScreen` signature with default-valued parameters so existing callers stay source-compatible:
  - `userEmail: String? = null`
  - `onNavigateToAuth: () -> Unit = {}`
  - `onSignOut: () -> Unit = {}`

- **Acceptance:**
  - `grep -n "\"ACCOUNT\"" FitCal-UI/src/commonMain/kotlin/com/fitter/app/ui/screens/settings/SettingsScreen.kt` → present
  - `grep -n "onNavigateToAuth" FitCal-UI/src/commonMain/kotlin/com/fitter/app/ui/screens/settings/SettingsScreen.kt` → present
  - `grep -n "onSignOut" FitCal-UI/src/commonMain/kotlin/com/fitter/app/ui/screens/settings/SettingsScreen.kt` → present

---

## A2 — `AuthScreen` & Email/Password Auth (`AuthScreen.kt`, `NavRoutes.kt`, `SupabaseAuthService.kt`)

**Objective:** Dedicated optional email/password authentication screen built with `FitterTextField` and `supabase-kt`.

**Contract:**
- `NavRoutes.kt`: add `@Serializable object AuthDestination`.
- `FitterTextField.kt`: add optional `visualTransformation: VisualTransformation = VisualTransformation.None` parameter (default preserves all existing call sites) so password fields mask input cleanly.
- `SupabaseAuthService.kt`: expose `signInWithEmail(email, pass)`, `signUpWithEmail(email, pass)`, `signOut()`, `getCurrentUserEmail()`, and `isGuest()` using `io.github.jan.supabase.auth.providers.builtin.Email`. On `signOut()`, sign out of the email session and restore an anonymous guest session (`signInAnonymously()`).
- Create `FitCal-UI/src/commonMain/kotlin/com/fitter/app/ui/screens/auth/AuthScreen.kt`:
  - Top bar: 44dp `IconButton` (`Icons.Default.ArrowBack`) + `BrandTypography.ScreenTitle` (`"Sign In"` / `"Create Account"`).
  - Mode toggle (`PressableBox` pills, `heightIn(min = 44.dp)`, `RadiusS`) switching between **Sign In** and **Create Account**.
  - Card with `BrandTypography.CardTitle` (`"EMAIL & PASSWORD"`), two `FitterTextField` inputs:
    - Email (`KeyboardType.Email`)
    - Password (`KeyboardType.Password`, `PasswordVisualTransformation()`)
  - Unit-testable validation helpers `isValidEmail(email: String): Boolean` and `isValidPassword(password: String): Boolean` (min 6 chars).
  - Inline error banner using `DangerSoft`, `DangerBorder`, `DangerTextStrong`, `BrandTypography.BodySmall`.
  - Primary submit `Button` (`height(54.dp)`, `RadiusM`, `shadow(2.dp, RoundedCornerShape(RadiusM))`, `BrandTypography.CardTitle`) with loading state (`isLoading`).
  - **"Continue as Guest"** escape button (`OutlinedButton`, `heightIn(min = 48.dp)`, `RadiusM`) → dismisses back to the app without signing in.

- **Acceptance:**
  - `find FitCal-UI -name "AuthScreen.kt"` → exists
  - `grep -n "AuthDestination" FitCal-UI/src/commonMain/kotlin/com/fitter/app/ui/navigation/NavRoutes.kt` → present
  - `grep -rn "FitterTextField(" FitCal-UI/src/commonMain/kotlin/com/fitter/app/ui/screens/auth/ --include="*.kt"` → ≥ 2
  - `grep -rn "Continue as Guest" FitCal-UI/src/commonMain/kotlin/com/fitter/app/ui/screens/auth/ --include="*.kt"` → present

---

## A3 — Guest-Mode Parity & Wiring (`App.kt`)

**Objective:** Ensure the app is 100% usable signed out, sign-out seamlessly returns to guest mode, and ad load is identical between guests and signed-in users.

**Contract:**
- `App.kt`:
  - Track `userEmail: String?` state initialized from `authService.getCurrentUserEmail()` and updated on auth state changes, sign-in/sign-up completion, and sign-out.
  - Wire `SettingsScreen` with `userEmail`, `onNavigateToAuth = { navController.navigate(AuthDestination) }`, and `onSignOut` (calls `authService.signOut()`, resets `userEmail = null`, keeps `authReady = true`).
  - Wire `composable<AuthDestination>` in `NavHost` to `AuthScreen`:
    - `onSignIn`: calls `authService.signInWithEmail(email, password)`, updates `userEmail`, triggers profile/meal sync, and pops back.
    - `onSignUp`: calls `authService.signUpWithEmail(email, password)`, updates `userEmail`, triggers profile/meal sync, and pops back.
    - `onContinueAsGuest` / `onBack`: pops back immediately.
  - **Guest parity:**
    - `startDestination` remains `DashboardDestination` (no auth wall).
    - `showAdsForUser` remains `!SubscriptionManager.isPremiumUser()` — zero reference to `userEmail` or `isGuest` in ad gating or `ScanQuotaManager`.
- Add unit tests in `FitCal-UI/src/commonTest/kotlin/com/fitter/app/AccountAuthTest.kt` covering:
  - Email & password validation helpers (`isValidEmail`, `isValidPassword`)
  - Guest-mode scan readiness & ad-load parity (guests get identical free daily limit and ad gating as email-authenticated users)

- **Acceptance:**
  - `grep -n "composable<AuthDestination>" FitCal-UI/src/commonMain/kotlin/com/fitter/app/App.kt` → present
  - `grep -n "showAdsForUser" FitCal-UI/src/commonMain/kotlin/com/fitter/app/App.kt` → gated only on `isPremiumUser()`
  - `find FitCal-UI -name "AccountAuthTest.kt"` → exists and passes

---

## A4 — Token Purity & Non-Regression

**Objective:** Zero regression on Rounds 1 & 2 design tokens, input containment, motion gate, and ad-free review flow.

**Contract:**
- No raw `OutlinedTextField(` or `BasicTextField(` outside `FitterTextField.kt`.
- No raw `Color(0x` in `FitCal-UI/src/commonMain/kotlin/com/fitter/app/ui/screens/` (comments exempt).
- No `AdBanner` on `CameraScreen` or `ReviewScreen`.
- `REDUCED_MOTION_ENABLED`, `PressableBox`, `InputBorder`, `CardTitle`, and `PlateSizeTest` remain intact.

- **Acceptance:**
  - `grep -rn "OutlinedTextField(" FitCal-UI/src/commonMain/kotlin --include="*.kt"` → only `FitterTextField.kt`
  - `grep -rn "BasicTextField" FitCal-UI/src/commonMain/kotlin --include="*.kt"` → only `FitterTextField.kt`
  - `grep -rn "Color(0x" FitCal-UI/src/commonMain/kotlin/com/fitter/app/ui/screens/ --include="*.kt"` → 0 (excluding comments)
  - `grep -rn "AdBanner" FitCal-UI/src/commonMain/kotlin/com/fitter/app/ui/screens/camera FitCal-UI/src/commonMain/kotlin/com/fitter/app/ui/screens/review --include="*.kt"` → 0

---

## Definition of Done (run in order, report each)

1. `export JAVA_HOME="C:/Program Files/Android/Android Studio1/jbr"` then `./gradlew :shared:testDebugUnitTest :FitCal-UI:testDebugUnitTest` → **BUILD SUCCESSFUL**
2. Every acceptance grep above → report `OK`/`FAIL` per line
3. `git status --short` → list only intended files
4. Note any KMP API substitutions or findings clearly.

## Report Template (reply with)

```
A1 Settings Account Section: Done/Partial — guest + signed-in states wired
A2 AuthScreen (email/password): Done/Partial — FitterTextField count, validation + guest escape
A3 Guest-Mode Parity: Done/Partial — startDestination, ad-gate check, sign-out session recovery
A4 Token Purity & Non-Regression: Done/Partial — raw TextField/hex/ad checks
Build: PASS/FAIL (test counts)
Deviations/notes:
```
