package com.coldboar.coreguard.hardening

/**
 * Android-adapted device hardening / performance tips.
 *
 * Inspired by common "advanced Windows tweak" checklists (startup hygiene,
 * background limits, storage cleanup, animation snappiness, backup-first
 * safety). Each entry is rewritten for Android capabilities CoreGuard can
 * honestly recommend — Windows-only registry/power-plan tricks are omitted,
 * and convenience tweaks that weaken authentication are flipped into
 * security guidance.
 */
object DeviceHardeningGuide {

    const val SAFETY_BANNER =
        "Create a backup or note current settings before changing system options. " +
            "Do not disable services or permissions you do not understand. " +
            "Prefer reversible Settings toggles over obscure hacks."

    enum class Impact {
        SPEED,
        BATTERY,
        STORAGE,
        SECURITY,
        SAFETY
    }

    enum class SettingsDeepLink {
        NONE,
        APPS,
        BATTERY_OPTIMIZATION,
        STORAGE,
        SECURITY,
        DEVELOPER_OPTIONS_HINT
    }

    data class Tip(
        val id: String,
        val title: String,
        val summary: String,
        val steps: List<String>,
        val impact: Impact,
        val deepLink: SettingsDeepLink = SettingsDeepLink.NONE,
        /** True when this tip exists mainly to prevent a harmful "tweak". */
        val isSecurityGuardrail: Boolean = false
    )

    val tips: List<Tip> = listOf(
        Tip(
            id = "backup_first",
            title = "Backup / note settings first",
            summary = "The safest speed tweak is a reversible one. Snapshot what you change.",
            steps = listOf(
                "Turn on Google Backup (or your OEM backup) before major cleanup.",
                "Write down any animation, battery, or autostart changes you make.",
                "If something breaks, restore the last known-good setting instead of stacking more tweaks."
            ),
            impact = Impact.SAFETY,
            deepLink = SettingsDeepLink.NONE
        ),
        Tip(
            id = "startup_hygiene",
            title = "Cut startup & always-on bloat",
            summary = "Too many apps waking at boot or staying resident slows the device and widens attack surface.",
            steps = listOf(
                "Open Apps and uninstall or disable anything you never use.",
                "Review notification access and remove alerts you do not need — noisy apps often stay awake.",
                "On OEM skins with Autostart / Auto-launch managers, deny launch for non-essential apps."
            ),
            impact = Impact.SPEED,
            deepLink = SettingsDeepLink.APPS
        ),
        Tip(
            id = "background_limits",
            title = "Restrict unused background apps",
            summary = "Quiet background work reclaim RAM and battery — the Android cousin of turning off background apps.",
            steps = listOf(
                "Open Battery optimization / App battery usage.",
                "Set rarely used apps to Restricted or Optimized.",
                "Keep CoreGuard, your messenger, and critical services Unrestricted only if they must stay alive."
            ),
            impact = Impact.BATTERY,
            deepLink = SettingsDeepLink.BATTERY_OPTIMIZATION
        ),
        Tip(
            id = "storage_cleanup",
            title = "Clear temp junk & free storage",
            summary = "Full disks make everything feel slow. Prefer cache cleanup over deleting app data.",
            steps = listOf(
                "Open Settings → Storage → Free up space (or Storage Sense / Smart Storage).",
                "Clear cache for heavy apps; avoid Clear data unless you accept a reset of that app.",
                "Remove downloaded APKs, old recordings, and unused offline maps."
            ),
            impact = Impact.STORAGE,
            deepLink = SettingsDeepLink.STORAGE
        ),
        Tip(
            id = "animation_scale",
            title = "Speed up animations (optional)",
            summary = "Shorter animator scales make navigation feel snappier without registry edits.",
            steps = listOf(
                "Enable Developer options: Settings → About phone → tap Build number seven times.",
                "Open Developer options → set Window / Transition / Animator duration scale to 0.5x (or Off).",
                "Revert anytime — this is cosmetic, not a security control."
            ),
            impact = Impact.SPEED,
            deepLink = SettingsDeepLink.DEVELOPER_OPTIONS_HINT
        ),
        Tip(
            id = "power_profile",
            title = "Pick the right power profile",
            summary = "Android does not expose Windows Ultimate Performance, but battery modes still matter.",
            steps = listOf(
                "Use Adaptive Battery for daily balance.",
                "Turn Battery Saver off when you need maximum responsiveness for scans or installs.",
                "Avoid permanent Extreme/Ultra saver if it kills background security work you rely on."
            ),
            impact = Impact.BATTERY,
            deepLink = SettingsDeepLink.BATTERY_OPTIMIZATION
        ),
        Tip(
            id = "keep_screen_lock",
            title = "Keep screen lock — never auto-bypass it",
            summary = "Windows-style auto-login tweaks are a security downgrade. On Android, keep a strong lock.",
            steps = listOf(
                "Use PIN, password, or biometric + secure fallback — not swipe-only if you can avoid it.",
                "Do not install lock-screen bypass or \"skip password\" utilities.",
                "Prefer shorter auto-lock timeouts on a phone that leaves your pocket often."
            ),
            impact = Impact.SECURITY,
            deepLink = SettingsDeepLink.SECURITY,
            isSecurityGuardrail = true
        ),
        Tip(
            id = "dont_kill_core_services",
            title = "Don't disable core system services",
            summary = "Analogous to randomly killing SysMain/services.msc entries: if you do not know it, leave it.",
            steps = listOf(
                "Skip third-party \"service killer\" or force-stop automators.",
                "Force-stop an app only for diagnosis, then reopen it normally.",
                "If disk/CPU is high, identify the app in battery or developer running-services views instead of blanket disables."
            ),
            impact = Impact.SAFETY,
            deepLink = SettingsDeepLink.APPS,
            isSecurityGuardrail = true
        )
    )

    fun tip(id: String): Tip? = tips.firstOrNull { it.id == id }
}
