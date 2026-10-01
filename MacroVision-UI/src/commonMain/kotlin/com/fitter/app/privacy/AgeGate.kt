package com.fitter.app.privacy

/**
 * Age eligibility gate.
 *
 * Play Store's **Families policy** and AdMob both treat a nutrition/health app as
 * adult content by default: without an explicit gate, Play expects the target-audience
 * questionnaire to declare "18+" and requires Families-compliant handling.
 *
 * This app deliberately declares **18+ only** (no children audience), which is the
 * lowest-risk posture and requires no child-directed changes. That declaration is only
 * honest if the app actually enforces it, hence this gate.
 *
 * Eating-disorder screening is included because the app outputs calorie targets to
 * users, which is the specific harm pattern (ANorexia Nervosa / Bulimia) that regulators
 * and both stores have enforced against calorie-tracking apps.
 */
object AgeGate {

    /** Store-declared minimum age. Keep in sync with the Play Console target audience. */
    const val MINIMUM_AGE = 18

    /** Users below this may use the app but get a reduced, safety-first calorie display. */
    const val CAUTION_AGE = 16

    private const val KEY_AGE_ACKNOWLEDGED = "age_gate_acknowledged_v1"

    private fun get(key: String, default: Boolean): Boolean = PrivacyConsent.runIfBound { it.getBoolean(key, default) }

    private fun set(key: String, value: Boolean) {
        PrivacyConsent.runIfBound {
            it.putBoolean(key, value)
            true
        }
    }

    /** True once the user has completed the age attestation. */
    fun hasAcknowledged(): Boolean = get(KEY_AGE_ACKNOWLEDGED, false)

    fun setAcknowledged(acknowledged: Boolean) {
        set(KEY_AGE_ACKNOWLEDGED, acknowledged)
    }

    /** Forces the gate to run again (e.g. after account deletion clears consent). */
    fun reset() {
        set(KEY_AGE_ACKNOWLEDGED, false)
    }

    /**
     * True when the profile age indicates a user we should apply extra caution to.
     * Used to soften calorie-goal presentation, not to block usage.
     */
    fun requiresEatingDisorderCaution(age: Int): Boolean = age < CAUTION_AGE
}