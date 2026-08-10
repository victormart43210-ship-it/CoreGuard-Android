package com.coldboar.coreguard.guardian

import com.coldboar.coreguard.truth.EvidenceClass as TruthEvidenceClass

/**
 * Canonical type alias — Guardian Intelligence uses the shared truth model from
 * [com.coldboar.coreguard.truth.EvidenceClass].
 *
 * Both enums define the same five values (OBSERVED, INFERRED, SIMULATED,
 * UNAVAILABLE, USER_REPORTED).  Using a typealias here makes the guardian package
 * the *consumer* of the shared model rather than a competing definition.
 *
 * Do NOT revert to a standalone enum: that would leave two competing truth systems.
 *
 * [userLabel] is defined directly on [com.coldboar.coreguard.truth.EvidenceClass] and
 * is therefore available without additional imports.
 */
typealias EvidenceClass = TruthEvidenceClass
