package com.coldboar.coreguard

import android.content.Context

/**
 * Tiny SharedPreferences gate for the first-run onboarding experience.
 * Intentionally separate from billing / shield state.
 */
object FirstRunStore {
    private const val PREFS = "coreguard_first_run"
    private const val KEY_COMPLETE = "onboarding_complete"

    fun isOnboardingComplete(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_COMPLETE, false)

    fun markOnboardingComplete(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_COMPLETE, true)
            .apply()
    }
}
