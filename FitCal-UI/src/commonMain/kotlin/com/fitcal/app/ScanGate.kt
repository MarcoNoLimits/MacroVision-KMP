package com.fitcal.app

/**
 * Phase 10 — Scan gate: typed readiness states for the camera scan action.
 *
 * The gate is evaluated via [ensureReadyForScan] before scanning,
 * and CameraScreen/Dashboard shows appropriate UI based on the returned state.
 *
 * States:
 *   - [Ready]: Proceed with camera capture and analysis. Also used when auth
 *       failed but network is present — the GatewayNutritionClient re-auths
 *       on 401 via its reAuthenticator, so boot-auth failure is non-blocking.
 *   - [AuthPending]: Auth session is being established (initial sign-in in-flight).
 *   - [AuthFailure]: Network is reachable but Supabase Auth failed. Kept for
 *       telemetry/diagnostics only; [ensureReadyForScan] maps this to [Ready]
 *       so scanning is never blocked by an auth service hiccup.
 *   - [Offline]: Device has no network connectivity — hard-blocks scanning.
 *   - [QuotaExhausted]: Daily scan quota is exhausted (when hard-blocking is active).
 */
sealed class ScanReadiness {
    object Ready : ScanReadiness()
    object AuthPending : ScanReadiness()
    /** Auth call failed even though network is present. Non-blocking — maps to Ready. */
    object AuthFailure : ScanReadiness()
    object Offline : ScanReadiness()
    object QuotaExhausted : ScanReadiness()
}

/**
 * Determines the current scan readiness.
 *
 * Auth failure with an active network is treated as [ScanReadiness.Ready] because
 * [com.fitcal.shared.api.GatewayNutritionClient] carries a [reAuthenticator] that
 * re-establishes the Supabase session on 401. A failed boot sign-in must never
 * prevent the user from scanning.
 *
 * @param authReady       `null` = pending, `true` = auth confirmed, `false` = failed
 * @param networkOnline   `true` if the device has network connectivity (default `true`)
 * @param scansRemaining  current remaining scan count for today
 * @param hardBlockQuota  whether quota exhaustion should hard-block (default false per monetization spec:
 *                        free quota exhaustion triggers forced interstitial rather than hard stop)
 */
fun ensureReadyForScan(
    authReady: Boolean?,
    scansRemaining: Int = 1,
    hardBlockQuota: Boolean = false,
    networkOnline: Boolean = true
): ScanReadiness {
    if (authReady == null) return ScanReadiness.AuthPending
    if (authReady == false && !networkOnline) return ScanReadiness.Offline
    // authReady == false but network is present → non-blocking; gateway re-auths on demand
    if (hardBlockQuota && scansRemaining <= 0) return ScanReadiness.QuotaExhausted
    return ScanReadiness.Ready
}

/**
 * Maps a non-ready state to a string reason for the `scan_blocked_reason` telemetry event.
 * AuthFailure is informational only and is never returned by [ensureReadyForScan].
 */
fun getScanBlockedReason(readiness: ScanReadiness): String? = when (readiness) {
    is ScanReadiness.Ready -> null
    is ScanReadiness.AuthPending -> "auth_pending"
    is ScanReadiness.AuthFailure -> "auth_failure"
    is ScanReadiness.Offline -> "offline"
    is ScanReadiness.QuotaExhausted -> "quota_exhausted"
}
