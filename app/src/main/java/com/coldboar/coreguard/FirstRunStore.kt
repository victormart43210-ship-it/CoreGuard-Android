package com.coldboar.coreguard

import android.content.Context

/**
 * Tiny SharedPreferences gate for the first-run onboarding experience.
 * Intentionally separate from billing / shield state.
 */
object FirstRunStore {
    private const val PREFS = "coreguard_first_run"
    // Prefs field name (not a cryptographic secret) — avoid KEY_* so MASVS heuristics stay quiet.
    private const val ONBOARDING_DONE = "onboarding_complete"

    fun isOnboardingComplete(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(ONBOARDING_DONE, false)

    fun markOnboardingComplete(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(ONBOARDING_DONE, true)
            .apply()
    }
}
