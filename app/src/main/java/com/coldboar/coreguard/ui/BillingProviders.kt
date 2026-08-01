package com.coldboar.coreguard.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.coldboar.coreguard.BillingModule
import com.coldboar.coreguard.BillingProvider
import com.coldboar.coreguard.FailClosedBillingProvider

/**
 * Fail-closed billing resolver for edge Compose hosts that cannot receive an
 * injected [BillingProvider] from [com.coldboar.coreguard.MainActivity].
 *
 * Production screens must take an explicit [BillingProvider] parameter instead
 * of calling this. Never unlocks via [com.coldboar.coreguard.DemoBillingProvider].
 */
@Composable
fun rememberFailClosedBillingProvider(): BillingProvider {
    return remember {
        runCatching { BillingModule.provider() }.getOrElse { FailClosedBillingProvider() }
    }
}
