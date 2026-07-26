package com.coldboar.coreguard.mvt

import android.content.Context

/**
 * Public module façade for Privacy Shield (on-device DNS VPN).
 *
 * Keeps VPN start/stop and live state behind a single entry point so UI does not
 * depend on [GuardVpnService] wiring details.
 */
object ShieldModule {

    val isActive: Boolean
        get() = ShieldState.isActive

    val totalBlocked: Int
        get() = ShieldState.totalBlocked

    fun start(context: Context) = NemesisShield.start(context)

    fun stop(context: Context) = NemesisShield.stop(context)

    fun addListener(listener: ShieldState.Listener) = ShieldState.addListener(listener)

    fun removeListener(listener: ShieldState.Listener) = ShieldState.removeListener(listener)
}
