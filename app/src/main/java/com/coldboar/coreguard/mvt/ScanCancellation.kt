package com.coldboar.coreguard.mvt

import kotlinx.coroutines.CancellationException

fun interface ScanCancellation {
    fun isCancelled(): Boolean

    fun throwIfCancelled() {
        if (isCancelled()) {
            throw CancellationException("Scan cancelled")
        }
    }
}

