package com.coldboar.coreguard

/** Shorthand for a completed native acquisition carrying real evidence. */
internal fun <T> available(value: T): NativeAcquisition<T> = NativeAcquisition.Available(value)

/** Shorthand for a native acquisition that never completed. */
internal fun unavailableAcquisition(
    reason: NativeUnavailableReason = NativeUnavailableReason.SOURCE_READ_FAILED,
): NativeAcquisition<Nothing> = NativeAcquisition.Unavailable(reason)
