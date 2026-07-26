package com.coldboar.coreguard.guardian

/** Finding taxonomy (Blueprint §5). Glyphs in UI must always pair with this text. */
enum class FindingCategory {
    DEVICE_INTEGRITY,
    APP_PERMISSION,
    ACCESSIBILITY,
    DEVICE_ADMIN,
    PACKAGE_CHANGE,
    NETWORK_CONFIGURATION,
    CERTIFICATE,
    DEBUGGING,
    ROOT_INDICATOR,
    SIGNATURE,
    OPERATING_SYSTEM,
    PRIVACY,
    SIMULATION,
    CORRELATION,
    HARDENING,
    BASELINE_DEVIATION,
    INSTALLATION
}
