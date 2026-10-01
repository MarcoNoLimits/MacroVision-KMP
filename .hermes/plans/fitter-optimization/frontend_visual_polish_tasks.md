# Fitter Frontend Visual Polish — Round 2 Antigravity Task Spec

> **Execution directive:** Continue the work in the Fitter repo (`C:\GitHub\Fitter`, Kotlin Multiplatform / Jetpack Compose, Android + iOS — NOT React/Tailwind/HTML). Round 1 (`frontend_polish_tasks.md`, phases F0–F5) is DONE and verified. This round is driven by a fresh design audit (visual critique of real screenshots) + requested product change. Implement **all tasks in phases G1 → G6**, in order.

## Design Principles For This Round (from the Apple-design skill)

- **Containment over flatness:** a focused field must visibly "hold" input — a defined border that darkens on focus, a label that lifts and tints the accent color. Never rely on the keyboard's focus ring alone.
- **Touch ergonomics:** every editable surface ≥ 44dp tall hit area; value text ≥ 16sp inside fields.
- **Consistent radii:** input surfaces use `RadiusS` (12dp) — a "squircle-lite" — not the default M3 pill, not sharp corners.
- **Secondary text earns its place:** descriptions that sell/explain must pass 4.5:1 at their size; bump anything rendered at 11sp `MutedTextColor` up, don't just darken it.
- **Single source of truth for units:** the **VLM API contract is inches** (`analyzeMealImage(base64, plateSizeInches)` — see `CameraScreen.kt:45`, `App.kt:210`, shared `UserProfile.defaultPlateSize`). The UI shall work in **centimeters** but **convert back to inches at the persistence boundary**. Do NOT change the API, the shared model, or the Supabase sync (`default_plate_size`).
- **Don't regress Round 1:** keep `REDUCED_MOTION_ENABLED`, tokens (`PrimaryAccent`, `SurfaceTint`, `BorderColor`, radii, `BrandTypography`), `PressableBox`, 44dp minimums, `FilterChip` semantics, and the ad-free review flow intact.

---

## G1 — Branded `FitterTextField` Component

**Objective:** One reusable input-field composable, used everywhere, replacing all raw `OutlinedTextField` / `BasicTextField` usages.

**Contract:**
- Create `FitCal-UI/src/commonMain/kotlin/com/fitter/app/ui/components/FitterTextField.kt`:
  - Wraps `OutlinedTextField`. Suppresses the default M3 border/shape; provides:
    - `shape = RoundedCornerShape(RadiusS)` (12dp)
    - Resting border 1dp `InputBorder` (new token, see G1e), value `TextStyle` 16sp `TextColor`
    - **Focused state:** border 2dp `PrimaryAccent`, label `PrimaryAccent`, `Modifier.shadow(2.dp, RoundedCornerShape(RadiusS))`
    - Min height `Modifier.heightIn(min = 56.dp)`
    - Parameter `keyboardType: KeyboardType = KeyboardType.Number` (string fields pass `KeyboardType.Text`), `label: String`, `unit: String? = null`
    - If `unit` is set, render `Text(unit, 13sp, MutedTextColor)` in a trailing `Row` cell — the unit **outside** the field border, right-aligned (e.g. `[ field ] cm`).
  - `@Composable fun FitterTextField(value, onValueChange, label, keyboardType = KeyboardType.Number, unit: String? = null, modifier: Modifier = Modifier)`

- **Replace every input surface** (no raw `OutlinedTextField`/`BasicTextField` may remain outside `FitterTextField.kt`):
  | File | Lines | Fields | Units |
  |---|---|---|---|
  | `SettingsScreen.kt` | 141–167 | Height, Weight, Age | cm, kg, yrs |
  | `SettingsScreen.kt` | 369–398 | Calories Budget, Protein, Carbs, Fat | kcal, g, g, g |
  | `ReviewScreen.kt` | 527–571 | Ingredient Name, Weight, Calories, Protein, Carbs, Fat | —, g, kcal, g, g, g |
  | `ReviewScreen.kt` | 741 / 850 / 906 | (search / swap / add dialogs) | match surrounding labels |
  | `ReviewComponents.kt` | 38–77 (`WeightInputPill`) | weight grams | g |

- **Acceptance:**
  - `grep -rn "OutlinedTextField(" FitCal-UI/src/commonMain/kotlin --include="*.kt"` → only `FitterTextField.kt`
  - `grep -rn "BasicTextField" FitCal-UI/src/commonMain/kotlin --include="*.kt"` → only `FitterTextField.kt`(internals, if used)
  - `grep -rn "FitterTextField(" FitCal-UI/src/commonMain/kotlin --include="*.kt"` → Settings ≥ 7, Review ≥ 9
  - Build green.

## G1e — New Theme Token: `InputBorder`

- In `ui/theme/Color.kt` add: `val InputBorder = Color(0xFFCBD5E1)` (Slate 300 — one notch darker than `BorderColor` Slate 200 `#E2E8F0`, so resting fields are visible; still soft).
- Focus ring color = `PrimaryAccent` (no new token).
- Acceptance: `grep -n "InputBorder" ui/theme/Color.kt` → present; no raw `Color(0x` anywhere in `ui/screens/*` (comments exempt).

## G2 — Plate Settings in Centimeters

**Objective:** The plate-size slider and readouts display **cm** (requested product change). Storage/API stay inches — convert at the boundary.

**Contract (`SettingsScreen.kt`, Plate Settings card, lines 312–347):**
- Keep the slider bound to inches internally (`plateSizeInches`), convert for display:
  - `private val CM_PER_INCH = 2.54f`
  - Display string: `"Default Plate Size: ${"%.1f".format(plateSizeInches * CM_PER_INCH)} cm"` (e.g. default 9.0 in → **22.9 cm**)
  - Slider range in cm terms: **16.0 cm … 31.0 cm** = inch range `16.0f/CM_PER_INCH .. 31.0f/CM_PER_INCH` (~6.3–12.2 in); on change snap to 0.5 cm (`kotlin.math.round(cm * 2)/2` then back to inches)
  - Add min/max captions under the slider: `Text("16 cm", Micro, MutedTextColor)` & `Text("31 cm", Micro, MutedTextColor)` at each end
  - `.shadow(1.dp, RoundedCornerShape(RadiusM))` on the thumb; activeTrack `PrimaryAccent`, inactiveTrack `BorderColor`
- `onSave` still writes `defaultPlateSize = plateSize` (inches) to `UserProfile` — **unchanged model/API contract**. Add one comment: `// G2: stored in inches; VLM API expects inches (analyzeMealImage)`. 
- Add `FitCal-UI/src/commonTest/kotlin/com/fitter/app/PlateSizeTest.kt`: round-trip test — 9.0 in → 22.86 cm → back to 9.0 in (within 0.01); and 23.0 cm → 9.055 in → 23.0 cm. Use a small `plateSizeCmToInches`/`plateSizeInchesToCm` helper (top-level funs in `FitterTextField.kt`'s sibling `ui/components/Units.kt` or inline math in SettingsScreen — your choice, but the helpers must be plain functions unit-testable without a UI session).

- **Acceptance:**
  - `grep -n "inches" ui/screens/settings/SettingsScreen.kt` → 0 (the word must not appear in any user-visible string)
  - `grep -n "2.54" ui/screens/settings/SettingsScreen.kt` → present
  - `find FitCal-UI -name "PlateSizeTest.kt"` → exists; `:FitCal-UI:testDebugUnitTest` runs it green
  - App strings elsewhere unchanged (camera/quota screens keep their wording).

## G3 — Secondary & Descriptor Text Legibility

**Objective:** every descriptive/selling line reads comfortably — 12sp+ and 4.5:1+.

**Contract:**
- `MonetizationScreen.kt`:
  - "Hit your daily limit? Watch a short sponsored video…" (line 141): `BodySmall`(12sp) stays, but color → `TextColor` (primary) — it's the main pitch; **not** `MutedTextColor`
  - "Provider: Google AdMob (Official Test Units)" / MAX variant (line 190): upgrade `Micro`(11sp) → `BodySmall`(12sp) `MutedTextColor`
- `SettingsScreen.kt` "Based on BMR, your recommended intake is…" (metabolic suggestion): keep `BodySmall` bold `TextColor` (already AA); the trailing `👉 Tap here to automatically apply…` line: `Micro` → `BodySmall` (12sp) `TextColor`
- Any other string currently at `BrandTypography.Micro` with `MutedTextColor` that is a *complete explanatory sentence* → bump to `BodySmall` `TextColor`. (Micro stays for true microcopy: "Free daily allowance: N scans", seat-of-pants numbers.)

- **Acceptance:** `grep -rn "BrandTypography.Micro" ui/screens/ --include="*.kt"` → only one-line microcopy usages (no sentences).

## G4 — Card Title Hierarchy Token

**Objective:** section headers (BODY PARAMETERS / FITNESS GOAL / PLATE SETTINGS / CUSTOM TARGETS / FREE DAILY ALLOWANCE / AD DELIVERY / GET MORE SCANS) read as *card titles*, distinct from screen title and body.

**Contract:**
- In `Theme.kt` add `val CardTitle = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.sp, fontSizeUnit = FontSizeUnit.Em, lineHeightUnit = Em)`. If it must take over the uppercase-eyebrow look, keep uppercase by styling the *text*, not adding a new case-transforming mechanic (Compose has no `textTransform` — if you need caps, keep the existing all-caps strings and just bump 10sp → 13sp SemiBold).
- Replace the card-section `BrandTypography.Eyebrow` usages in `SettingsScreen.kt` (BODY PARAMETERS 134, FITNESS GOAL 230, PLATE SETTINGS 325, CUSTOM TARGETS 363), `MonetizationScreen.kt` (FREE DAILY ALLOWANCE 90, GET MORE SCANS 135, AD DELIVERY 184) with a new `BrandTypography.CardTitle`. Keep `Eyebrow` for true eyebrow context (screen-level, e.g. under-screen labels) — home screen & camera unaffected.
- **Acceptance:** `grep -rn "BrandTypography.CardTitle" ui/ --include="*.kt"` → ≥ 7; `grep -rn "BrandTypography.Eyebrow" ui/screens/settings ui/screens/monetization --include="*.kt"` → 0.

## G5 — Slider, Toggle & Primary Button Polish

**Contract:**
- **Plate slider (G2 +):** value readout pill (`Box` with `SurfaceTint`, `RadiusM`, 1dp `BorderColor` border) showing current `22.9 cm` **above** the track; thumb `.shadow(1.dp, CircleShape)`.
- **Earn-a-scan OutlinedButton** (`MonetizationScreen.kt:146`): shape `RadiusM`, `minHeight 48.dp`, keep outline PrimaryAccent; on-press use `PressableBox` wrapper if trivial (else ensure Material pressed state shows).
- **Save button** (`SettingsScreen.kt:417`): keeps `RadiusM` + 54dp; add `.shadow(2.dp, RoundedCornerShape(RadiusM))` for the containment feel; ensure text uses `CardTitle` (13sp SemiBold) not `SectionTitle`(14sp) — visual weight, not size, sells the action.
- **Acceptance:** grep shows the captions ("16 cm"/"31 cm") and `.shadow(2.dp` on Save; no raw hex added.

## G6 — Macro Row Breathing Room

**Contract:**
- Custom Targets trio (`SettingsScreen.kt:377`), Add-Ingredient quartets (`ReviewScreen.kt:533/549`), Body Parameters pair (`SettingsScreen.kt:140`): `spacedBy(12.dp)` → `spacedBy(16.dp)`.
- `WeightInputPill` (`ReviewComponents.kt:43-48`): width `36.dp` → `56.dp`; `minHeight 44.dp`; border → `InputBorder`; keep 12sp bold centered; keep `basicTextField` digit filter (digits + "." only — allow a decimal point).
- **Acceptance:** `grep -rn "spacedBy(16.dp)" ui/screens/settings/SettingsScreen.kt ui/screens/review/ReviewScreen.kt --include="*.kt"` → rows present; `grep -n "width(56.dp)" ui/screens/review/components/ReviewComponents.kt` → present.

---

## Do NOT Touch

- `ScanQuotaManager`, `AdConfig`, `AdManager`/`AndroidAdManager`, any ad *logic* (placement, cooldowns, brand-safety, mediation) — presentation only. The screenshots show an ad/monetization section; style the existing `MonetizationScreen.kt` as it exists today, do not move or re-add toggles anywhere.
- `shared/` module, `iosApp/`, Supabase sync, VLM prompts, `plateSizeInches` API contract.
- Round-1 tokens/components/motion gate (see Design Principles).

## Definition of Done (run in order, report each)

1. `export JAVA_HOME="C:/Program Files/Android/Android Studio1/jbr"` then `./gradlew :shared:testDebugUnitTest :FitCal-UI:testDebugUnitTest` → **BUILD SUCCESSFUL**
2. Every acceptance grep above → report `OK`/`FAIL` per line
3. `git status --short` → list only intended files (no stray renames, no `.hermes` churn beyond new files you author)
4. Visual sanity: for any spot you couldn't verify statically, say so explicitly.

## Report Template (reply with)

```
G1 FitterTextField: Done/Partial — files, substitutions count
G1e InputBorder: Done
G2 Plate cm: Done — conversion helper location, round-trip test result
G3 Legibility: Done — lines changed
G4 CardTitle: Done — token + replacements
G5 Slider/Button: Done
G6 Spacing: Done
Build: PASS/FAIL (test counts)
Deviations/notes:
```
