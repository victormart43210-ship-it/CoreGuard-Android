package com.coldboar.coreguard.toolkit

/**
 * Curated external security / privacy web tools that complement CoreGuard's
 * on-device Nemesis scan + Privacy Shield — without wrapping third-party APIs
 * or uploading device telemetry.
 *
 * Inspired by popular "useful websites" lists (malware scanning, disposable
 * identity, evidence capture, outage checks, reverse image search, speed
 * tests). Music/PDF fluff and developer-only sandboxes are omitted.
 *
 * Each entry opens in the system browser; CoreGuard does not proxy uploads
 * or store credentials for these sites.
 */
object ExternalSecurityToolkit {

    const val SAFETY_BANNER =
        "These open trusted third-party sites in your browser — they are not " +
            "CoreGuard features and may process what you submit under their own policies. " +
            "Prefer on-device Nemesis + Shield first; use VirusTotal only for samples you " +
            "are willing to share with antivirus vendors."

    enum class Category {
        MALWARE,
        PRIVACY,
        EVIDENCE,
        NETWORK,
        OSINT
    }

    data class Tool(
        val id: String,
        val title: String,
        val host: String,
        val url: String,
        val summary: String,
        val whenToUse: String,
        val category: Category,
        val caution: String? = null
    )

    val tools: List<Tool> = listOf(
        Tool(
            id = "virustotal",
            title = "VirusTotal",
            host = "virustotal.com",
            url = "https://www.virustotal.com/gui/home/upload",
            summary = "Multi-engine malware scan for a suspicious file, APK, hash, or URL.",
            whenToUse = "After a shady download or link — complements Nemesis on-device IOCs.",
            category = Category.MALWARE,
            caution = "Uploads are shared with AV partners. Never submit private photos, chats, or work docs."
        ),
        Tool(
            id = "privnote",
            title = "Privnote",
            host = "privnote.com",
            url = "https://privnote.com/",
            summary = "Send a one-time, self-destructing text note instead of leaving secrets in chat history.",
            whenToUse = "Sharing a recovery code or password once — still verify the recipient first.",
            category = Category.PRIVACY,
            caution = "The link is the secret. Do not post it publicly; note burns after first open."
        ),
        Tool(
            id = "temp_mail",
            title = "Temp Mail",
            host = "temp-mail.org",
            url = "https://temp-mail.org/",
            summary = "Instant disposable inbox for one-off signups that do not deserve your real address.",
            whenToUse = "Low-trust newsletter / trial forms — never for banking or 2FA you care about.",
            category = Category.PRIVACY,
            caution = "Disposable mail is not anonymity. Sites can still fingerprint the device/browser."
        ),
        Tool(
            id = "file_io",
            title = "file.io",
            host = "file.io",
            url = "https://www.file.io/",
            summary = "Share a file that auto-deletes after one download.",
            whenToUse = "Passing a short-lived attachment without leaving it in Drive forever.",
            category = Category.PRIVACY,
            caution = "The operator can see the file while it exists. Encrypt sensitive payloads yourself first."
        ),
        Tool(
            id = "archive_ph",
            title = "archive.ph",
            host = "archive.ph",
            url = "https://archive.ph/",
            summary = "Permanently snapshot a webpage before it changes or disappears.",
            whenToUse = "Preserving a phishing page or scam listing as evidence for your timeline notes.",
            category = Category.EVIDENCE,
            caution = "Archiving publishes a public copy. Do not archive pages with your personal data."
        ),
        Tool(
            id = "downdetector",
            title = "Downdetector",
            host = "downdetector.com",
            url = "https://downdetector.com/",
            summary = "Check whether an outage is widespread before blaming your network or Shield.",
            whenToUse = "App or bank login fails for everyone — rule out provider downtime vs. local block.",
            category = Category.NETWORK
        ),
        Tool(
            id = "fast_com",
            title = "Fast.com",
            host = "fast.com",
            url = "https://fast.com/",
            summary = "Quick internet speed check powered by Netflix CDN.",
            whenToUse = "Baseline throughput before/after VPN or captive-portal Wi‑Fi.",
            category = Category.NETWORK
        ),
        Tool(
            id = "tineye",
            title = "TinEye",
            host = "tineye.com",
            url = "https://tineye.com/",
            summary = "Reverse image search to find where a photo first appeared.",
            whenToUse = "Romance/investment scams reusing stolen profile pictures.",
            category = Category.OSINT,
            caution = "Upload only the image you are investigating — not your private album."
        ),
        Tool(
            id = "similarsites",
            title = "SimilarSites",
            host = "similarsites.com",
            url = "https://www.similarsites.com/",
            summary = "Find alternatives to a website — useful when hunting safer substitutes.",
            whenToUse = "A site looks sketchy or is down; explore established alternatives carefully.",
            category = Category.OSINT,
            caution = "Similarity ≠ trust. Still verify HTTPS, reputation, and permissions."
        ),
        Tool(
            id = "ten_minute_mail",
            title = "10 Minute Mail",
            host = "10minutemail.com",
            url = "https://10minutemail.com/",
            summary = "Short-lived disposable inbox that expires quickly.",
            whenToUse = "Ultra-short verification codes when Temp Mail is blocked.",
            category = Category.PRIVACY,
            caution = "Do not use for accounts you need to recover later."
        )
    )

    fun tool(id: String): Tool? = tools.firstOrNull { it.id == id }

    fun toolsForCategory(category: Category): List<Tool> =
        tools.filter { it.category == category }

    /** Compact blurb Quilla can cite when users ask about external helpers. */
    fun quillaSummary(): String =
        "External Security Toolkit (${tools.size} browser tools): " +
            tools.joinToString("; ") { "${it.title} — ${it.summary}" } +
            " Open them from Tools → External Security Toolkit. " +
            "CoreGuard still prefers on-device Nemesis + Shield first."
}
