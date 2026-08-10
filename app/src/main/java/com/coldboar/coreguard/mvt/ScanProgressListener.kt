package com.coldboar.coreguard.mvt

enum class ScanStageId(val label: String) {
    PREPARING("Preparing scanner"),
    LOADING_INDICATORS("Loading indicators"),
    ENUMERATING_PACKAGES("Enumerating installed packages"),
    CHECKING_PACKAGE_METADATA("Checking package metadata"),
    CHECKING_INSTALLER_SOURCES("Checking installer sources"),
    CHECKING_CERTIFICATES("Checking certificates"),
    CHECKING_PROCESSES("Checking visible processes"),
    CHECKING_ACCESSIBLE_FILES("Checking app-accessible files"),
    CORRELATING_INDICATORS("Correlating indicators"),
    BUILDING_FINDINGS("Building findings"),
    COMPLETED("Completed"),
    CANCELLED("Cancelled"),
    FAILED("Failed")
}

data class ScanStageEvent(
    val stageId: ScanStageId,
    val label: String = stageId.label,
    val completedUnits: Int? = null,
    val totalUnits: Int? = null,
    val timestampMs: Long = System.currentTimeMillis(),
    val visibilityLimitation: String? = null
)

interface ScanProgressListener {
    fun onStage(event: ScanStageEvent)
}
