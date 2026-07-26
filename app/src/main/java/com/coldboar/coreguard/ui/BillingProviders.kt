package com.coldboar.coreguard.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.coldboar.coreguard.BillingModule
import com.coldboar.coreguard.BillingProvider
import com.coldboar.coreguard.FailClosedBillingProvider

/**
 * Resolves the Play Billing provider for Compose UI.
 *
 * Production path: [BillingModule.provider] (Play Billing from Application).
 * If Application/billing is unavailable, fail closed (non-premium) — never
 * silently unlock via [com.coldboar.coreguard.DemoBillingProvider].
 */
@Composable
fun rememberAppBillingProvider(
    override: BillingProvider? = null
): BillingProvider {
    return override ?: remember {
        runCatching { BillingModule.provider() }.getOrElse { FailClosedBillingProvider() }
    }
}
