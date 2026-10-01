# Fitter Frontend Polish — Antigravity Task Specification

> **Execution directive:** This document is the frontend craft/UX pass for the Fitter app (Kotlin Multiplatform, Jetpack Compose — Android + iOS, **not** React/HTML/Tailwind). Implement tasks in order P0 → P3. Every task carries a concrete contract and a **verifiable acceptance criterion** that the next reviewer greps for. Zero ambiguity, zero orphan changes.
>
> **Author:** Nexus @frontend (frontend audit, 2026-09-16) · **Baseline:** `:shared:testDebugUnitTest :FitCal-UI:testDebugUnitTest` green before this pass.

---

## Hard Constraints (non-negotiable)

1. **Do NOT touch** monetization behavior: `ScanQuotaManager`, `AdConfig`, `AdManager`/`AndroidAdManager`, ad placement policy (no ads on Review flow; interlocks shown as interstitial via `showScanProcessingAd`, never as blocking dialog; banners only on secondary tabs). Read-only access to their getters is allowed.
2. Do NOT hardcode API keys/secrets; nothing outside `ui/theme/` may declare raw `Color(0x...)` after **F1.1** (allow `Color.White`/`Color.Black` built-ins).
3. Preserve existing functionality and the E2E suite (`E2EJourneyTest.kt` must stay green).
4. On this machine Gradle needs `JAVA_HOME="C:/Program Files/Android/Android Studio1/jbr"` (system JDK-23 path is broken).
5. Do not write this spec's ideas into README claims; if material/glass claims are untrue after this pass, adjust README copy to match reality.
6. Use KMP's actual API surface. For animation, prefer `androidx.compose.animation` (e.g. `AnimatedVisibility`, `fadeIn/fadeOut`, `scaleIn/scaleOut`) and `androidx.compose.animation.core` springs. If a suggested component doesn't exist in the pinned Compose version, implement the intent with the closest available primitive and note the substitution in your report.

---

# PHASE F0 — Data Integrity & Deletion Safety (P0)

## F0.1 Unique Meal IDs (delete-the-wrong-meal bug)

- **Problem:** In `FitCal-UI/src/commonMain/kotlin/com/fitter/app/App.kt` (~line 197), a `LoggedMeal.id` is derived from `"${mealName}_${epochMillis}_${size}"`. Two meals logged in the same second (name+time+size identical) collide; `DashboardScreen` uses `indexOfFirst { id == }` and can resolve to the **first** match, deleting the wrong meal.
- **Contract:**
  - Introduce a monotonic, collision-proof ID for `LoggedMeal` (UUID v4 via a small `fun newMealId(): String`, or a persisted monotonic counter). Apply wherever meals are created (log flow + any test fixtures that construct ids by hand).
  - Keep `id` a `String` (no serialization schema migration beyond id format; old ids remain valid, just non-generated going forward).
- **Acceptance criteria:**
  - `grep -n "newMealId" FitCal-UI/src/commonMain/kotlin/com/fitter/app/App.kt` → present and used for meal creation.
  - A unit test proves two meals created in the same millisecond get distinct ids (add to `commonTest`, e.g. `MealIdTest.kt`).
  - `./gradlew :shared:testDebugUnitTest :FitCal-UI:testDebugUnitTest` green.

## F0.2 Undo on Meal Delete

- **Problem:** Deleting a logged meal is instant and irreversible (`DashboardScreen.kt` delete icon → immediate removal), a data-loss trap in a nutrition-history app.
- **Contract:**
  - On delete: remove the meal from the list **and** show a lightweight, non-modal toast: `"Meal deleted · Undo"` (primary action `Undo`), auto-dismissing after 5s.
  - `Undo` restores the meal at its original position (or appends if order can't be recovered) and cancels the toast.
  - A second removal while a toast is pending replaces/refreshes the toast and its 5s timer; oldest-delete is **not** kept (one undo slot only — document choice in a comment).
  - Depends on F0.1 (restore by stable id).
- **Acceptance criteria:**
  - `grep -in "undo" FitCal-UI/src/commonMain/kotlin/com/fitter/app/ui/screens/dashboard/DashboardScreen.kt` → present.
  - Manual/E2E path: delete → meal disappears → Undo → meal reappears with same macros/date.
  - Build green.

## F0.3 Touch Targets ≥ 44×44dp (Fitts)

- **Problem:** Multiple tappable elements are far below the 44dp minimum: delete icons 16–32dp (`DashboardScreen.kt:468`, `ReviewScreen.kt:302`), water ± buttons `height(28.dp)` (`WaterTrackerCard.kt:61,70`), water cups rendered as bare emoji (`WaterTrackerCard.kt:76-89`), gender/goal chips ≈30px tall (`SettingsScreen.kt:178-190, 225-237`).
- **Contract:**
  - Every interactive surface must expose a **hit area ≥ 44×44dp** (visual may stay smaller; expand hit area via invisible padding/`Box` wrapper).
  - Water cups: replace bare-emoji interaction with real tappable buttons (Material icon cup, or emoji *inside* a `Button`/`IconButton` with `contentDescription = "Add water 250ml"`), selected/filled state preserved.
  - Keep visual rhythm (8/12px gaps) — hit areas are allowed to overlap visually with padding space.
- **Acceptance criteria:**
  - `grep -rn "size(16.dp)\|size(32.dp)\|height(28.dp)"` on `dashboard/` and `review/` → 0 for interactive elements (non-interactive decorations exempt).
  - Water cups are no longer `Text("🥛", modifier = clickable { ... })`; they are buttons with accessibility descriptions.
  - Build green.

---

# PHASE F1 — Design-Token Consolidation (P1)

## F1.1 Semantic Color Tokens — Kill the 46 raw hexes

- **Problem:** 46 raw `Color(0x...)` literals across `ui/`; screens re-declare theme values inline (`0xFFF8FAFC` == `BgColor`, `0xFF0F172A` == `TextColor`, `0xFFE2E8F0` == `BorderColor`, `0xFF64748B` == `MutedTextColor`); orphans include water accent `0xFF38BDF8`, error reds `0xFFEF4444/B91C1C/7F1D1D/FCA5A5/FEF2F2`, and the rogue blue recalculate button `0xFF3B82F6` (only blue primary in the app).
- **Contract (extend `ui/theme/Color.kt`):**
  - Add semantic tokens:
    - `DangerColor = Color(0xFFEF4444)`, `DangerSoft = Color(0xFFFEF2F2)`, `DangerBorder = Color(0xFFFCA5A5)`, `DangerTextStrong = Color(0xFFB91C1C)`, `DangerTextDeep = Color(0xFF7F1D1D)`
    - `WaterAccent = Color(0xFF38BDF8)`
    - `SurfaceTint = Color(0xFFF1F5F9)` (the light-gray used for chips/pills/track backgrounds)
    - `InfoPanel = Color(0xFFF8FAFC)`
  - **Recalculate button color changes from blue to `PrimaryAccent`** (emerald) in `ReviewScreen.kt:422` — one consistent primary across the app.
  - Replace every raw literal in `ui/` with the token; `ui/theme/*` is the only place allowed to hold raw `Color(0x...)`.
- **Acceptance criteria:**
  - `grep -rn "Color(0x" FitCal-UI/src/commonMain/kotlin/com/fitter/app/ui --include="*.kt"` → matches **only** under `ui/theme/`.
  - `ReviewScreen.kt` contains no `0xFF3B82F6`.
  - Build green.

## F1.2 Corner-Radius Tokens

- **Problem:** Radii roulette: 8/12/16/20/24/28/32dp mixed across screens (`RoundedCornerShape(...)` everywhere).
- **Contract (new `ui/theme/Radii.kt` or in `Theme.kt`):**
  - `RadiusXS = 8.dp` (chips, pills, tiny surfaces), `RadiusS = 12.dp` (rows, inputs), `RadiusM = 16.dp` (buttons, macro cards), `RadiusL = 24.dp` (cards), `RadiusXL = 32.dp` (hero card only, ReviewScreen image card may keep 32 top).
  - Map all usages onto these five; drop 20/28.
- **Acceptance criteria:**
  - `grep -rn "RoundedCornerShape(" --include="*.kt" ui/` → only token references or the five constants.
  - Build green.

---

# PHASE F2 — Typography Scale & Contrast (P1)

## F2.1 One Typographic Scale

- **Problem:** No shared scale: eyebrow labels at 9sp/10sp/11sp for the same role; H1s at 28sp (Dashboard), `headlineSmall` (Review), 22sp (Settings); body floats 11/12/13/14sp; `letterSpacing` copy-pasted per label.
- **Contract (define in `Theme.kt` as a small `BrandTypography` block of named styles on top of `MaterialTheme.typography`):**
  - `Eyebrow = 10sp · Bold · letterSpacing 1sp` — all section labels ("BODY PARAMETERS", "DETECTED INGREDIENTS", "CALCULATED MACROS", "WATER INTAKE", "METABOLIC SUGGESTION", "AD MONETIZATION…", calendar "TODAY").
  - `ScreenTitle = 24sp · Bold` — the three screen H1s converge here (Dashboard "Today", Review `headlineSmall` override -> 24, Settings "Goals & Parameters").
  - `SectionTitle = 14sp · SemiBold` — card titles, item names emphasis.
  - `Body = 13sp`, `BodySmall = 12sp`, `Micro = 11sp`. Nothing renders below 10sp except the diagnostics monospace (CameraScreen) which stays 11sp.
  - KPI numerals keep `FontFamily.Monospace` (already correct in `DailyMacroCard`) and extend to Review macro values.
- **Acceptance criteria:**
  - `grep -rn "fontSize = 9.sp\|fontSize = 10.sp" --include="*.kt" ui/` → 0 after migration (10sp eyebrow becomes token; 9sp removed entirely).
  - All three screens use the token styles for their roles.
  - Build green.

## F2.2 Contrast Legibility

- **Problem:** 12sp `MutedTextColor` (#64748B) on white ≈ 4.7:1 (AA edge), 9sp labels worse; 10sp uppercase red diagnostics header strained.
- **Contract:** After F2.1, audit remaining muted-on-white combos; **nothing below 12sp renders in `MutedTextColor`** on white (use `TextColor` at smaller sizes). Keep error diagnostics ≥ 11sp with `DangerTextStrong`.
- **Acceptance criteria:** grep audit shows no `MutedTextColor` at < 12sp; build green.

---

# PHASE F3 — Motion & Feedback (P2, Apple benchmark)

> Requirement to honor reduced motion: wrap every new animation in a single gate constant `val REDUCED_MOTION_ENABLED = false` (kotlin constant, `ui/theme/Motion.kt`); when true, animations degrade to instant state changes. The gate is compile-time for this pass (F4.4 may surface a setting).

## F3.1 Press Feedback on Every Custom-Tap Surface

- **Problem:** All bespoke `.clickable { }` boxes (calendar strip, camera CTA, water cups, chips, swap rows, add-item row, gender/goal chips) react with nothing on press.
- **Contract:**
  - Create one reusable `PressableBox(content, onTap, pressScale = 0.97f)` composable in `ui/components/` that wraps pointer-down scale feedback using a spring (damping ~1.0, response ~0.2s) and fires `onTap` on release; respects the reduced-motion gate.
  - Replace raw `.clickable { }` blocks in `DashboardScreen`, `CameraScreen`, `SettingsScreen`, `ReviewScreen` and the components with `PressableBox` (navigation-only surfaces like the settings back button may keep Material `IconButton` ripple).
  - No behavior changes — same handlers, same conditions.
- **Acceptance criteria:**
  - `grep -rn "\.clickable" --include="*.kt" ui/` → 0 (all routed through `PressableBox` or Material buttons).
  - Build green.

## F3.2 Dialog / Surface / List Transitions

- **Contract:** Add `AnimatedVisibility` (fade + slight scaleIn 0.98→1, spring ~`duration 0.3s`) to: the three Review dialogs + success dialog, the Camera error card, and the Camera "Analyzing" card. Animate meal-row removal in Review with a collapse-fade before item removal (only when the removal list is short; keep it snappy).
- **Acceptance criteria:** `grep -rn "AnimatedVisibility" --include="*.kt" ui/` → ≥ 1 use per touched dialog/surface; everything gated on `REDUCED_MOTION_ENABLED`; build green.

## F3.3 Async Flow Continuity & Escape Hatch (CameraScreen)

- **Problem:** Analyzing → ad → result has no progress continuity (ad can outlast the request; user stuck up to 15s+ with no cancel on the ad view; the analysis overlay is a hard full-screen swap).
- **Contract:**
  - While an interstitial plays, show a persistent, non-interactive bottom chip: `"Analyzing meal in background…"` (small, `SurfaceTint` background).
  - Add a **Cancel** affordance on the ad overlay: tapping Cancel closes the ad, discards the in-flight request, and returns to the live camera (`onCancel` path — the same handler the error card's Cancel uses). Update the ad-completion callback so a cancelled state doesn't later navigate to a result.
  - Keep the existing `isAnalyzing` spinner for the non-ad path.
- **Acceptance criteria:** `grep -in "cancel" CameraScreen.kt` → cancel available in both error **and** ad overlays; the background-analysis chip exists; build green.

---

# PHASE F4 — Accessibility & Semantics (P3)

## F4.1 Correct Back Icon (Settings)

- **Problem:** `SettingsScreen.kt:97-110` uses the **Close ✕ glyph with `contentDescription = "Back"`** — wrong shape, wrong semantics.
- **Contract:** Use `Icons.Default.ArrowBack` with `contentDescription = "Back"` (mirror `NavRoutes` semantics; adjust import — `ArrowBack` is already imported in the file).
- **Acceptance criteria:** `grep -n "ArrowBack" SettingsScreen.kt` → used for the top-left control; build green.

## F4.2 Kill Emoji-As-Interactive-Surface

- **Problem:** `🥛` cups are interactive text emoji (SRs get nothing, tap semantics undefined); `🔍` swap is text; meals/coach emojis double as data icons.
- **Contract:**
  - Water cups → real buttons (F0.3) with `contentDescription` ("Add water 250ml" / "Remove water 250ml") and a filled/empty visual state.
  - The `🔍 Swap` affordance becomes a proper icon button (`Icons.Default.Search` or similar, `contentDescription = "Swap food item"`, ≥44dp hit area) — visual layout unchanged.
  - Other emoji (meals, coach) may remain as **non-interactive** decoration only; if they convey status, add a text label.
- **Acceptance criteria:** `grep -rn "clickable" WaterTrackerCard.kt` → 0 (cups are buttons); `grep -n "🔍" ReviewScreen.kt` → 0; build green.

## F4.3 Selection Semantics (Gender / Goal Chips)

- **Problem:** Gender/goal chips are plain `Box`es — no focus ring, no selected semantics for keyboard/SR.
- **Contract:** If the pinned Compose M3 provides a selectable chip/segmented control (e.g. `Button` with toggle colors, `FilterChip`), use it with explicit selected style + `contentDescription`; otherwise keep the visual but add: selected announce via `contentDescription` update and a 2dp primary-colored focus ring on keyboard focus. Ensure selected state remains distinct (currently emerald vs `SurfaceTint`).
- **Acceptance criteria:** chips expose a selected state indistinguishable only by color (text weight + ring + description); build green.

## F4.4 Reduced-Motion Gate (with F3)

- **Contract:** `ui/theme/Motion.kt` with `val REDUCED_MOTION_ENABLED = false`; every F3 animation checks it; when flipped `true`, all motion collapses to instant visibility toggles (state correctness preserved).
- **Acceptance criteria:** every `AnimatedVisibility`/animation call site references the gate; build green.

---

# PHASE F5 — Charm & Empty States (P3, low risk)

## F5.1 Dashboard Empty State

- **Problem:** "No meals logged" is a plain gray box (`DashboardScreen.kt` ~395).
- **Contract:** Replace with a friendly invitation: small plate emoji/illustration (non-interactive), headline "No meals yet", body "Snap a photo of your next meal and Fitter will log it for you.", accent-colored primary action button "Start Scanning" that navigates to Camera (reuse existing nav).
- **Acceptance criteria:** empty state contains illustration + CTA; build green.

## F5.2 Success Moment

- **Problem:** "Meal Logged!" dialog is static (`ReviewScreen.kt:609-665`).
- **Contract:** Add the F3.2 entrance animation + a quick check-icon pop (animate the 32dp check scale 0→1 with spring, ~0.25s, gated). **No confetti/audio.**
- **Acceptance criteria:** success dialog animated via gate; build green.

---

# Definition of Done (run these before reporting back)

1. `JAVA_HOME="C:/Program Files/Android/Android Studio1/jbr" ./gradlew :shared:testDebugUnitTest :FitCal-UI:testDebugUnitTest` → green.
2. Acceptance greps from every task pass (list them in your report with `→ OK`/`→ FAIL`).
3. `git status` shows only intended files changed (no stray build artifacts, no secrets).
4. Report format, per task: **Done / Partial / Skipped** + files touched + one-line rationale for any deviation.

## Antigravity report template (paste back to user)

```markdown
## Fitter Frontend Polish — Delivery Report
- Build: PASS/FAIL (paste final gradle line)
- F0.1: Done/Partial/Skipped — <files> — <notes>
- F0.2: …
- F1.1: …
- F1.2: …
- F2.1: …
- F2.2: …
- F3.1: …
- F3.2: …
- F3.3: …
- F4.1: …
- F4.2: …
- F4.3: …
- F4.4: …
- F5.1: …
- F5.2: …
- Deviations / substitution notes: …
```

---

*This spec is reviewable by Nexus @frontend. After implementation, the audit re-runs against the acceptance criteria above verbatim.*
