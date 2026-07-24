package com.coldboar.coreguard.domain.quilla

import com.coldboar.coreguard.data.local.dao.QuillaLearningDao
import com.coldboar.coreguard.data.local.entity.QuillaHypothesisEntity
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/** Describes a network observation for a single package during one scan. */
data class NetworkEvent(
    val packageName: String,
    val isUntrustedNetwork: Boolean,
    val bytesTransferred: Long
)

/** Describes a RASP (Runtime Application Self-Protection) observation for a single package. */
data class RaspEvent(
    val packageName: String,
    val isDynamicCodeLoaded: Boolean,
    val isRootDetected: Boolean
)

/**
 * Correlates weak signals across App State, RASP, and Network Shield to form
 * actionable hypotheses that are persisted for later review.
 *
 * No automatic remediation actions are taken; hypotheses are surfaced to the
 * user via [com.coldboar.coreguard.presentation.quilla.QuillaProfileViewModel].
 */
@Singleton
class QuillaCorrelationEngine @Inject constructor(
    private val dao: QuillaLearningDao
) {

    /**
     * Evaluates a combined RASP + network observation and stores any resulting
     * hypotheses in the local database.
     */
    suspend fun correlateSignals(
        packageName: String,
        rasp: RaspEvent?,
        network: NetworkEvent?
    ) {
        if (rasp?.isDynamicCodeLoaded == true && network?.isUntrustedNetwork == true) {
            dao.upsertHypothesis(
                QuillaHypothesisEntity(
                    id = UUID.randomUUID().toString(),
                    hypothesisType = "DYNAMIC_LOAD_UNTRUSTED_NET",
                    summary = "Package $packageName executed dynamic code loading while " +
                        "connected to an untrusted network environment.",
                    evidenceJson = """{"dynamicCode":true,"untrustedNetwork":true}""",
                    confidence = 0.82f,
                    status = "ACTIVE"
                )
            )
        }

        if (rasp?.isRootDetected == true) {
            dao.upsertHypothesis(
                QuillaHypothesisEntity(
                    id = UUID.randomUUID().toString(),
                    hypothesisType = "ROOT_DETECTED",
                    summary = "Package $packageName was observed running on a rooted device.",
                    evidenceJson = """{"rootDetected":true}""",
                    confidence = 0.95f,
                    status = "ACTIVE"
                )
            )
        }
    }
}
