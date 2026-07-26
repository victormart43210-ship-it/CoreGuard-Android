package com.coldboar.coreguard.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.coldboar.coreguard.BillingModule
import com.coldboar.coreguard.BillingProvider
import com.coldboar.coreguard.DemoBillingProvider

/**
 * Resolves the Play Billing provider for Compose UI.
 *
 * Production path: [BillingModule.provider] (Play Billing from [com.coldboar.coreguard.CoreGuardApplication]).
 * Fallback [DemoBillingProvider] is only for JVM/Compose previews when the Application
 * singleton is unavailable — never construct Demo as a silent production default.
 */
@Composable
fun rememberAppBillingProvider(
    override: BillingProvider? = null
): BillingProvider {
    return override ?: remember {
        runCatching { BillingModule.provider() }.getOrElse { DemoBillingProvider() }
    }
}
