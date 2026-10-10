# FitCal Analytics

Product analytics go to our own Supabase database through the `analyze-meal` gateway. Crash reports go to Sentry. No third-party analytics SDK is used.

```
App ─ Analytics.track() ─▶ on-device queue (survives restarts, max 500)
    └─ every 60 s, on background, at boot ─▶ POST /v1/events (JWT) ─▶ fitcal.log_events()
Gateway ─ its own events for each AI call, quota denial and reward claim (source = 'server')
Database ─▶ fitcal.analytics_events ─▶ views in schema fitcal_analytics (SQL editor only)
Crashes ─▶ Sentry (Android, after consent, off if the user opts out)
```

## Privacy rules (do not break)

- Nothing is recorded before the privacy notice is accepted (`PrivacyConsent`), and nothing while **Settings → Privacy → Share usage analytics** is off. Turning it off also drops the queue and stops Sentry.
- Properties are **behaviour only**: counts, durations, screen names, error classes, booleans. **Never** send meal names, food names, calories/macros, weights, body data, email or free text.
- Account deletion erases the user's events (`fitcal.delete_account_data`).
- Retention: `diagnostic` events 90 days, everything else 400 days (purged opportunistically by `log_events`).
- Any new event type must keep these rules. If it adds a new *kind* of data, update the privacy policy (in-app `PrivacyPolicyScreen.kt` and `site/privacy.html`) and the Play Data safety form.

## Event catalogue

Every client event also carries `session_id`, `client_ts`, `app_version` and `platform`. The gateway adds `user_id` from the verified token.

### Lifecycle and onboarding
| Event | Properties | Where |
|---|---|---|
| `consent_completed` | `ads_personalized` | consent screen accepted |
| `age_gate_completed` | | age gate |
| `app_open` | `online`, `is_guest`, `premium`, `days_since_install`, `free_scan_limit`, `scans_remaining`, `meals_logged_total` | cold start, after sign-in attempt |
| `app_foreground` | `away_s`, `new_session` | app returns from the background (new session after 30 min) |
| `app_background` | | app leaves the foreground (triggers upload) |
| `screen_view` | `screen` (`dashboard`, `camera`, `result`, `settings`, `monetization`, `foodlibrary`, `auth`, …) | every navigation |
| `cohort_retention` | `day_number`, `total_active_days`, `date_key` | first open of each calendar day |

### Scan funnel
| Event | Properties |
|---|---|
| `scan_blocked` | `reason`: `auth_pending`, `offline`, `auth_failure`, `quota_exhausted` |
| `quota_exhausted` | `can_create_account` |
| `photo_captured` | `raw_kb`, `compressed_kb` |
| `scan_started` | `attempt` (`first` / `resend`), `with_ad`, `image_kb`, `plate_size_set` |
| `scan_succeeded` | `attempt`, `latency_ms`, `items`, `low_confidence_items`, `no_food` |
| `scan_failed` | `attempt`, `latency_ms`, `error` (`quota`, `auth`, `timeout`, `network`, `http_5xx`, exception class) |
| `scan_cancelled` | |

### Review and logging
| Event | Properties |
|---|---|
| `meal_logged` | `edited`, `items_ai`, `items_final`, `items_removed`, `items_added`, `items_swapped`, `library_adds`, `custom_adds`, `weights_changed`, `weight_change_pct`, `low_confidence_items`, `recalculations`, `review_seconds` |
| `review_abandoned` | `items_ai`, `items_final`, `review_seconds` |
| `recalculate_succeeded` / `recalculate_failed` | `items` / `error`, `latency_ms` |
| `scan_rated` | `positive`, `reasons` (`wrong_food`, `portion_off`, `missed_item`, `nutrition_off`), `items_ai` |
| `first_meal_logged` | (activation milestone) |
| `meal_deleted`, `meal_delete_undone` | |
| `water_changed` | `increased` |
| `date_selected` | `is_today` |

### Monetization
| Event | Properties |
|---|---|
| `ad_impression` | `format` (`INTERSTITIAL`, `REWARDED`, `BANNER`) |
| `ad_revenue` | `revenue` (USD), `format`, `network`, `ad_unit_id`, `placement` |
| `rewarded_ad_requested` / `rewarded_ad_earned` / `rewarded_ad_closed_early` | `source` (`quota_wall`, `scan_error`, `perks_screen`) |

### Account
| Event | Properties |
|---|---|
| `sign_in_prompt_shown` / `_dismissed` / `_accepted` | `meals_logged` (shown) |
| `auth_started` | `method` (`google`, `apple`) |
| `sign_up_started` | `outcome` (`CodeSent`, `Completed`, `EmailAlreadyRegistered`) |
| `auth_failed` | `method`, `flow`, `error` (exception class) |
| `auth_cancelled` | `method` |
| `account_created` | `meals_logged` (guest upgraded in place) |
| `signed_in` | `was_guest` (signed into another existing account) |
| `signed_out`, `password_reset_requested`, `account_delete_failed` | |

### Settings
`profile_saved` (`goals_changed`, `goal_type`, `plate_size_changed`), `reminders_toggled` (`enabled`), `ads_personalization_changed` (`enabled`), `analytics_opted_in`.

### Feedback
`feedback_opened` (`source`), `feedback_sent` (`kind`, `result`, `with_email`). The feedback itself is stored in `fitcal.feedback`, not in analytics (see below).

### Errors
`diagnostic` (`level`, `tag`, `message`, `details`): ERROR/FATAL entries from `DiagnosticsCrashHook`. Unexpected exceptions also go to Sentry with a stack trace.

### Server events (gateway, `source = 'server'`)
| Event | Properties |
|---|---|
| `vlm_analyze` | `ok`, `provider`, `fallback`, `failed_providers`, `latency_ms`, `items`, `low_confidence_items`, `premium`, `plate_size_set`, `image_kb`, `error` |
| `vlm_recalculate` | `ok`, `provider`, `fallback`, `failed_providers`, `latency_ms`, `items`, `error` |
| `quota_denied` | `allowance` |
| `reward_claimed` | `requested`, `granted`, `capped` |
| `subscription_event` | `type` (RevenueCat event), `entitlement`, `active` |
| `account_deleted` | `login_deleted` (stored **without** a user ID, so deletions can be counted after the user's own events are erased) |

## Dashboards

Open Supabase → SQL editor and run, for example:

```sql
select * from fitcal_analytics.daily_kpis limit 14;      -- users, scans, meals, ads, revenue, errors
select * from fitcal_analytics.scan_funnel limit 14;     -- camera → photo → result → logged
select * from fitcal_analytics.ai_accuracy limit 14;     -- how much users correct the AI
select * from fitcal_analytics.provider_health limit 30; -- Gemini/OpenRouter/Groq success, fallback, p50/p95
select * from fitcal_analytics.retention;                -- weekly cohorts, D1/D7/D30
select * from fitcal_analytics.top_errors;               -- last 7 days
```

Ad-hoc questions use `fitcal_analytics.events` (`ts` = device time when plausible, otherwise receipt time):

```sql
-- Where do people drop between opening the camera and logging?
select payload->>'error' as error, count(*) from fitcal_analytics.events
 where event = 'scan_failed' and ts > now() - interval '7 days' group by 1 order by 2 desc;

-- Does the forced interstitial hurt logging?
select (payload->>'with_ad')::boolean as with_ad, count(*) from fitcal_analytics.events
 where event = 'scan_started' group by 1;
```

The views are in a schema that the API does not expose and only `service_role` can read. App users cannot reach them.

## User feedback

Two kinds, both sent through the gateway (`POST /v1/feedback` → `fitcal.submit_feedback`, max 30 per user per day):

- **Scan accuracy:** a thumbs up/down under every AI result. Thumbs down offers optional reason chips. Stored as `kind = 'scan_accuracy'`, `rating` 5 or 1, and `context.reasons`. There's no free text.
- **Settings → Send feedback:** Problem / Idea / Other, a message (up to 2000 characters) and an optional reply email.

```sql
select * from fitcal_analytics.feedback_inbox;   -- newest first; update fitcal.feedback set status = 'done' where id = …
select * from fitcal_analytics.scan_ratings;     -- % positive per day + most common complaint
```

Feedback is erased with the account and after 24 months.

## Adding an event

```kotlin
Analytics.track("thing_happened", "count" to 3, "source" to "dashboard")
```

Names are `snake_case`, at most 64 chars (the database rejects anything else). Add the event to the catalogue above in the same change.

## Sentry setup (one-time)

1. Create a Sentry account and an **Android** project. Choose the **EU data region** (`de.sentry.io`) to match the privacy policy's EU-first stance.
2. In the project settings: **Security & Privacy → turn on "Prevent Storing of IP Addresses"** and keep **Data Scrubber** on.
3. Put the DSN in `local.properties`: `SENTRY_DSN=https://…@o….ingest.de.sentry.io/…`. Without it, crash reporting stays off.
4. Release builds are not minified (`isMinifyEnabled = false`), so stack traces are readable without uploading mapping files. If minification is turned on later, add the Sentry Gradle plugin to upload ProGuard mappings.
