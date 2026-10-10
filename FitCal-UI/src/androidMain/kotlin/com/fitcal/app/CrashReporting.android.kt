package com.fitcal.app

import io.sentry.Sentry
import io.sentry.android.core.SentryAndroid
import io.sentry.protocol.User

// Sentry crash/ANR reporting. Off until SENTRY_DSN is set in local.properties, and
// only running while PrivacyConsent is accepted and the user shares usage analytics.
// No screenshots, view hierarchy, session replay or default PII: meal photos and
// nutrition data never go to Sentry.

private var sentryStarted = false

actual fun setCrashReportingEnabled(enabled: Boolean) {
    val dsn = BuildConfig.SENTRY_DSN
    if (dsn.isBlank()) return
    if (enabled && !sentryStarted) {
        SentryAndroid.init(appContext) { options ->
            options.dsn = dsn
            options.environment = if (BuildConfig.DEBUG) "debug" else "production"
            options.release = "com.fitcal.app@${BuildConfig.VERSION_NAME}+${BuildConfig.VERSION_CODE}"
            options.isSendDefaultPii = false
            options.isAttachScreenshot = false
            options.isAttachViewHierarchy = false
            options.tracesSampleRate = 0.0
            options.isAnrEnabled = true
        }
        sentryStarted = true
    } else if (!enabled && sentryStarted) {
        Sentry.close()
        sentryStarted = false
    }
}

actual fun setCrashReportingUser(userId: String?) {
    if (!sentryStarted) return
    Sentry.setUser(userId?.let { User().apply { id = it } })
}

actual fun reportNonFatal(throwable: Throwable, tag: String) {
    if (!sentryStarted) return
    Sentry.withScope { scope ->
        scope.setTag("source", tag)
        Sentry.captureException(throwable)
    }
}
