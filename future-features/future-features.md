# FitCal: Future Features

This file tracks what FitCal is missing, or where it loses to competitors. Work on it after the v1 Android release is deployed.

Source: the competitive teardown of October 2026, which compared FitCal against Cal AI, MyFitnessPal, Lose It!, MacroFactor, Yazio, Lifesum, Foodvisor and Cronometer. The FitCal side came from reading the code at commit `035f705`.

**How to use this file**
- Tick the box when an item ships, and add the date and PR link.
- Anything touching ads, quotas or Premium must still follow **AGENTS.md §5** (the monetization spec). Ask the owner before you start.
- The Premium purchase item (F-01) is also in `tasks/NEXT.md` as backlog item 1. Keep both in sync.

**Priority**
- **P0**: costs revenue or loses most new users. Do these first after launch.
- **P1**: competitors all have it, and users will notice it's missing.
- **P2**: makes us stronger; not urgent.
- **P3**: nice to have.

**Effort**
- **S**: under 2 days.
- **M**: about a week.
- **L**: more than a week.

---

## 1. Monetization

### F-01 · Premium paywall and purchase flow (P0 · M)
- [ ] Add the RevenueCat SDK (Play Billing) to `:FitCal-UI` (Android).
- [ ] Build the paywall screen:
  - $4.99/mo and $39.99/yr; show the annual price as "$3.33/mo";
  - list the benefits: unlimited scans, no ads, plus whatever ships from this list (export, progress insights);
  - a restore-purchases button.
- [ ] Add entry points from the quota card, the Ads & Perks screen, Settings, and the end of onboarding (F-10).
- [ ] Wire up the existing `SubscriptionManager.purchasePlan(productId, gatewayUrl, jwtProvider)`, which polls the gateway.

**Why:** the backend side is already built: the RevenueCat webhook, `/v1/entitlements` and `SubscriptionManager`. There's no way to pay in the app, so Premium revenue is $0 today. AGENTS.md marks Task 10 as DONE, but in the app it isn't.

### F-02 · Quota-reached choice card (P1 · S)
- [ ] When free scans run out, show one calm card before the forced interstitial: "Out of free scans today: watch an ad, wait until tomorrow, or go Premium for $3.33/mo".
- [ ] Check with the owner first. AGENTS.md §5.2 currently says "no prompt dialog" before the interstitial, so this item needs a spec change.

**Why:** right now users hit an ad with no option to pay. Offering the choice at the moment of need is where competitors convert most subscribers.

---

## 2. Onboarding and goals

### F-10 · Onboarding quiz with a personal plan (P0 · M)
- [ ] Add a short quiz, one question per screen, with a progress bar:
  - goal (lose, maintain or gain);
  - sex, age, height and weight;
  - activity level;
  - target pace;
  - plate size, using the existing setting.
- [ ] End on a "Your plan" reveal: daily calories and macro split, a projected goal date, and an optional Premium pitch (F-01).
- [ ] Always show a **Skip**. Fast time-to-first-scan is our edge over Cal AI's long funnel, so don't lose it.
- [ ] Save the answers to `UserProfile`, and sync them for accounts.

**Why:** today every new user starts at 2,000 kcal, 70 kg and 175 cm, and only finds the real settings in Settings. Every competitor personalizes from day one, and that's what makes users feel invested.

### F-11 · Activity level and better TDEE (P1 · S)
- [ ] Replace the fixed `activeBurnMultiplier = 1.2f` in `DashboardScreen.kt` with the user's activity level: ×1.2, ×1.375, ×1.55, ×1.725 or ×1.9.
- [ ] Turn the "BMR Deficit" label into plain language, for example "312 kcal under maintenance".

### F-12 · Adaptive calorie target (P2 · L)
- [ ] Recalculate maintenance weekly from logged intake and weight trend, like MacroFactor does. This depends on F-20 (weight log).

---

## 3. Logging methods

### F-15 · Barcode scanner (P1 · M)
- [ ] Use ML Kit barcode scanning and look products up in Open Food Facts (free, open data).
- [ ] Add a mode tab on the camera screen: **Scan food · Barcode · Describe**.
- [ ] Barcode lookups don't use AI, so they shouldn't count against the scan quota.

**Why:** Cal AI, Lose It! and MacroFactor all have one. Packaged food is a large share of what people eat, and photos estimate it badly.

### F-16 · Text logging ("Describe your meal") (P1 · S–M)
- [ ] Log a meal from text, for example "two eggs, toast with butter, black coffee", using the gateway's text-only route, which already exists for `/v1/recalculate`.
- [ ] Decide whether text logs use quota. They cost less than image scans.

### F-17 · Voice logging (P3 · S, after F-16)
- [ ] Use Android speech-to-text to feed the text logging flow.

### F-18 · Bigger food database (P2 · M)
- [ ] Expand the 113-item `FoodDatabase.kt` with USDA FoodData Central (generic foods) and Open Food Facts (branded).
- [ ] Search it from a standalone "Add food" flow, not only inside the review screen.
- [ ] Use it for grounding: map AI items to database entries and calculate macros from them.

### F-19 · Quick re-logging (P1 · M, after F-30)
- [ ] Add a "Recent" list and "Favourites" (star a meal).
- [ ] Add "Log again" on any past meal.
- [ ] Add "Copy yesterday's breakfast".
- [ ] Re-logging doesn't use a scan.

**Why:** people eat the same meals most days. Competitors make that one tap; we make users spend a scan.

---

## 4. Progress and retention

### F-20 · Weight log and progress tab (P0 · M)
- [ ] Add a **Progress** tab with:
  - weight entries and a trend line with the goal line;
  - BMI;
  - 7- and 30-day bars for calories and protein against target;
  - weekly averages.
- [ ] Banner ads are allowed here, since it's a passive screen per AGENTS.md §5.5.

**Why:** retention in this category comes from seeing progress. Today FitCal shows nothing beyond the current day, and the date strip only covers the last 7 days.

### F-21 · Streaks and milestones (P1 · S)
- [ ] Count the logging streak (days with at least one meal) and show it on the dashboard.
- [ ] Add milestone moments: 7, 30 and 100 days, first goal reached, and so on.
- [ ] Hook the streak into meal reminders, for example "Don't lose your 12-day streak".

### F-22 · Full history calendar (P1 · S)
- [ ] Let users browse any past day, not just the last 7 (`CalendarStrip.getLastSevenDays()`). Add a month picker.

### F-23 · Health Connect integration (P2 · M)
- [ ] Read steps and workouts and add active calories to the daily budget.
- [ ] Optionally write nutrition to Health Connect.

### F-24 · Home-screen widget (P2 · M)
- [ ] Show calories left and macro rings, with a one-tap "Scan" shortcut (Glance).

### F-25 · Weekly summary (P2 · S)
- [ ] Every week, show a recap card or notification: average calories, protein hit-rate, best day, weight change.

---

## 5. Meal data and accuracy

### F-30 · Store full meal detail on logged meals (P0 · M)
- [ ] Extend `LoggedMeal` with:
  - photo (thumbnail in a Supabase Storage bucket, plus a local cache);
  - the list of ingredient items;
  - meal slot;
  - the source (photo, barcode, text or manual).
- [ ] Add a Supabase migration for the new columns and the storage bucket with RLS.

**Why:** today only the name and totals are saved, and the ingredients are thrown away when the user logs. That blocks editing a logged meal, photo thumbnails, "log again" (F-19) and meal slots (F-32).

### F-31 · Edit a meal after logging (P1 · S, after F-30)
- [ ] Tapping a meal in history reopens the review screen with its items, then saves the changes.

### F-32 · Meal slots (P1 · S, after F-30)
- [ ] Group the day into Breakfast, Lunch, Dinner and Snacks.
- [ ] Auto-pick the slot from the time of day, and let the user change it.

### F-33 · Text "fix" on the review screen (P1 · S)
- [ ] Add a field such as "Tell FitCal what's off: 'it was cooked in butter', 'that's turkey, not chicken'". It re-runs the text-only recalculation with the hint.

**Why:** this is Cal AI's main correction tool, and our review screen is already deeper. Adding it makes ours the best correction flow in the market.

### F-34 · More nutrients (P2 · S–M)
- [ ] Ask the AI for fibre, sugar and sodium too, and show them per meal and per day.
- [ ] Optionally show micronutrients for Premium.

### F-35 · Nutrition label scan (P3 · M)
- [ ] Photograph a nutrition facts panel and read it into a custom food (OCR plus the AI).

---

## 6. UI and UX improvements

### Dashboard

#### U-01 · Lead with "calories left" (P1 · S)
- [ ] Make the main number "1,240 left" with consumed and target as secondary text. Cal AI, MyFitnessPal and Lose It! all lead with remaining calories.

#### U-02 · Floating scan button (P1 · S)
- [ ] Replace the 240dp "Snap and log" card with a floating camera button that's always reachable. Today the main action is below the fold on most phones.
- [ ] Remove the remote Unsplash stock photo from that card. It costs a network request, and it shows someone else's food on the user's dashboard.

#### U-03 · Clear header icons (P1 · S)
- [ ] The Food Library uses a hamburger icon (`Icons.Default.Menu`) and Ads & Perks uses a star. Use a book or search icon for the library, and a "gift" or "+ scans" chip that shows the remaining scan count.
- [ ] Consider a bottom navigation bar: **Today · Progress · Library · Settings**.

#### U-04 · Meal photos in history (P1 · S, after F-30)
- [ ] Show the user's own food photo instead of the keyword-guessed emoji.

#### U-05 · Rename or upgrade "AI Coach" (P2 · S)
- [ ] `getAiCoachFeedback()` returns one of five fixed rule-based messages, so the "AI" label overpromises. Rename it to "Daily tip", or make it AI-generated (Premium) using the day's meals.

#### U-06 · Configurable water goal (P3 · S)
- [ ] Don't hard-code 2,000 ml in `WaterTrackerCard`. Derive the goal from body weight, or let the user set it, and allow quick add amounts such as 250 and 500 ml.

### Capture and analysis

#### U-10 · Better waiting state (P1 · S)
- [ ] Show the captured photo with a scanning shimmer and staged messages: "Finding foods…", then "Estimating portions…", then "Calculating macros…".
- [ ] Make the existing "analyzing in background" path the default, so users can go back to the dashboard while a card fills in.

#### U-11 · Camera mode tabs (P1 · S, with F-15/F-16)
- [ ] Add the **Scan food · Barcode · Describe** strip at the bottom of the camera, the same pattern MyFitnessPal and Cal AI use.

#### U-12 · Hide the diagnostics log in release builds (P1 · S)
- [ ] The error screen in `CameraScreen.kt` shows an "API DIAGNOSTICS LOG", and I couldn't find a release-build check around it. Show it only in debug builds, and show users a short message with a retry button.

### Review screen

#### U-15 · Gram stepper and slider (P1 · S)
- [ ] Replace typing grams in a text field with ± buttons (±10 g), a slider, and portion presets (½, 1, 1½).

#### U-16 · Animated totals (P2 · S)
- [ ] Count totals up when results arrive, and animate changes when weights change. Cal AI uses this kind of small delight well.

#### U-17 · Swipe to delete ingredient (P2 · S)
- [ ] Remove an ingredient row by swiping, with undo.

### Visual design

#### U-20 · Dark mode (P1 · S–M)
- [ ] `Color.kt` is light-only. Add a dark palette and follow the system setting. Every competitor has dark mode.

#### U-21 · Store-screenshot-ready visual pass (P2 · M)
- [ ] Review the overall look against the bold monochrome style of Cal AI and the 2026 AI-app wave:
  - bigger numerals;
  - fewer bordered cards;
  - food photos as the main colour.
- [ ] Keep the emerald brand, but give the dashboard one hero number. Today the dashboard has many cards of equal weight.

#### U-22 · Accessibility pass (P2 · S)
- [ ] Check contrast of the muted slate text on white, add TalkBack labels on the ring and macro cards, and confirm the layout holds at 200% font scale.

---

## 7. Platform and reach

### F-40 · Localization (P2 · M)
- [ ] Move all strings to resources, then translate for the first target markets.
- [ ] Support metric and imperial units throughout (lb, oz, ft/in).

**Why:** a free AI scanner wins in price-sensitive markets, which are mostly non-English.

### F-41 · Data export (P2 · S)
- [ ] Export meals and weight as CSV (Premium). AGENTS.md §5.9 already lists "macro export" as a Premium benefit.

### F-42 · Referral program (P3 · M)
- [ ] "Invite a friend, both get +N scans", using the existing server-side allowance. Cal AI grew partly through referrals.

### F-43 · iOS release (P3 · L)
- [ ] Out of scope for v1, but the KMP code is there. Revisit once Android shows traction.

---

## 8. Housekeeping found during the audit

- [ ] **H-01:** AGENTS.md says Gemini 2.0 Flash, but `supabase/functions/analyze-meal/index.ts` calls `gemini-2.5-flash`. Align the doc after the model decision in `tasks/NEXT.md`.
- [ ] **H-02:** AGENTS.md Task 10 says RevenueCat is DONE. Change it to "backend done, client paywall pending" until F-01 ships.
- [ ] **H-03:** `competitor_analysis.md` and `competitor_analysis_detailed.md` (June 2026) are out of date:
  - they say FitCal has no database or Search-to-Swap;
  - they don't mention Cal AI, which MyFitnessPal bought in 2026.
  - Update them, or replace them with a pointer to this file.
- [ ] **H-04:** `README.md` still describes client-side API keys in `.env` and an OpenRouter-first pipeline. Update it to the gateway architecture.

---

## Where FitCal already wins (don't regress)

Keep these in mind while building the items above. They're the reasons a user picks FitCal:

- **Free daily AI photo scans.** Most competitors charge for photo logging.
- **No sign-up wall.** Users scan as a guest and upgrade in place.
- **A deep review screen:**
  - per-item confidence;
  - swap or add from the database;
  - instant local recalculation;
  - AI re-check without re-uploading.
- **Plate-size calibration.** No mainstream competitor has it.
- **Three-provider AI failover** behind a secure gateway.
- **Fair monetization:**
  - no hidden-trial billing;
  - no ads on the Camera or Review screens.
