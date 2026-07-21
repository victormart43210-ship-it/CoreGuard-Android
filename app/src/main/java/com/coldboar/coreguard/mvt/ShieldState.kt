package com.coldboar.coreguard.mvt

import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

/**
 * Observable, process-wide state for the Pegasus domain-blocking shield.
 *
 * The VPN sinkhole updates these counters; the UI observes them.
 */
object ShieldState {

    fun interface Listener {
        fun onShieldStateChanged()
    }

    private val active = AtomicBoolean(false)
    private val blockedCount = AtomicInteger(0)
    private val lastBlocked = AtomicReference<String?>(null)
    private val listeners = CopyOnWriteArrayList<Listener>()

    val isActive: Boolean get() = active.get()
    val totalBlocked: Int get() = blockedCount.get()
    val lastBlockedDomain: String? get() = lastBlocked.get()

    fun setActive(value: Boolean) {
        active.set(value)
        notifyListeners()
    }

    fun recordBlocked(domain: String) {
        blockedCount.incrementAndGet()
        lastBlocked.set(domain)
        notifyListeners()
    }

    fun reset() {
        blockedCount.set(0)
        lastBlocked.set(null)
        notifyListeners()
    }

    fun addListener(listener: Listener) {
        listeners.addIfAbsent(listener)
    }

    fun removeListener(listener: Listener) {
        listeners.remove(listener)
    }

    private fun notifyListeners() {
        listeners.forEach { it.onShieldStateChanged() }
    }
}
