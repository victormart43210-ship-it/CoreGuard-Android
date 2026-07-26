package com.coldboar.coreguard.quilla

import com.coldboar.coreguard.EntitlementPolicy
import com.coldboar.coreguard.mvt.ScanReport
import com.coldboar.coreguard.mvt.ScanVerdict

/**
 * Deterministic Quilla coach that turns device context into next actions and
 * honest Premium upsells. No cloud LLM required.
 */
object QuillaSalesCoach {

    data class DeviceContext(
        val isPremium: Boolean,
        val lastScan: ScanReport? = null,
        val timelineCount: Int = 0,
        val shieldActive: Boolean = false,
        val shieldBlocked: Int = 0
    )

    data class CoachAnswer(
        val text: String,
        val suggestPremium: Boolean,
        val premiumPitch: String? = null
    )

    fun answer(prompt: String, ctx: DeviceContext): CoachAnswer {
        val p = prompt.trim().lowercase()
        if (p.isBlank()) {
            return CoachAnswer(
                text = "Ask me how to raise your score, run a scan, use the shield, export a report, or whether Premium is worth it for you.",
                suggestPremium = !ctx.isPremium,
                premiumPitch = if (ctx.isPremium) null else premiumValuePitch()
            )
        }

        return when {
            p.contains("premium") || p.contains("upgrade") || p.contains("subscribe") ||
                p.contains("pay") || p.contains("worth") -> premiumQuestion(ctx)

            p.contains("export") || p.contains("report") || p.contains("json") ||
                p.contains("compliance") -> exportCoach(ctx)

            p.contains("signature") || p.contains("refresh") || p.contains("update") ||
                p.contains("ioc") || p.contains("feed") -> signatureCoach(ctx)

            p.contains("timeline") || p.contains("history") -> timelineCoach(ctx)

            p.contains("shield") || p.contains("vpn") || p.contains("block") -> shieldCoach(ctx)

            p.contains("scan") || p.contains("nemesis") || p.contains("safe") ||
                p.contains("risk") || p.contains("score") -> statusCoach(ctx, prompt)

            else -> generalCoach(ctx, prompt)
        }
    }

    private fun premiumQuestion(ctx: DeviceContext): CoachAnswer {
        if (ctx.isPremium) {
            return CoachAnswer(
                text = "You're already Premium. Use signature refresh on Scanner, export Compliance JSON, " +
                    "and keep the full ${EntitlementPolicy.PREMIUM_TIMELINE_ENTRIES}-scan timeline.",
                suggestPremium = false
            )
        }
        return CoachAnswer(
            text = "Premium is for people who want more than a one-off check: live signature refresh, " +
                "full scan history, Compliance JSON export, and my deeper next-step coaching. " +
                "Core scan + shield stay free — upgrade when you want the full operating system.",
            suggestPremium = true,
            premiumPitch = premiumValuePitch()
        )
    }

    private fun exportCoach(ctx: DeviceContext): CoachAnswer {
        if (ctx.isPremium) {
            return CoachAnswer(
                text = "Open Compliance and tap Export JSON — Premium unlocks that report for your notes or review.",
                suggestPremium = false
            )
        }
        return CoachAnswer(
            text = "You can view Compliance scores for free. Exporting the JSON report is a Premium unlock — " +
                "useful when you need a shareable artifact, not just a glance at the score.",
            suggestPremium = true,
            premiumPitch = "Go Premium to export Compliance JSON reports."
        )
    }

    private fun signatureCoach(ctx: DeviceContext): CoachAnswer {
        if (ctx.isPremium) {
            return CoachAnswer(
                text = "Open Scanner and tap Refresh threat signatures — Premium pulls the latest open-source IOC feed before you rescan.",
                suggestPremium = false
            )
        }
        return CoachAnswer(
            text = "Free scans use the bundled indicator set. Premium unlocks live signature refresh so you can " +
                "pull newer IOCs, then rescan with fresher intel.",
            suggestPremium = true,
            premiumPitch = "Go Premium to refresh threat signatures on demand."
        )
    }

    private fun timelineCoach(ctx: DeviceContext): CoachAnswer {
        if (ctx.isPremium) {
            return CoachAnswer(
                text = "Your Premium timeline keeps up to ${EntitlementPolicy.PREMIUM_TIMELINE_ENTRIES} scans. " +
                    "Open Timeline to compare cycles — trends beat one-off readings.",
                suggestPremium = false
            )
        }
        return CoachAnswer(
            text = "Free keeps the last ${EntitlementPolicy.FREE_TIMELINE_ENTRIES} scans " +
                "(you currently have ${ctx.timelineCount}). Premium expands history so you can spot patterns over time.",
            suggestPremium = true,
            premiumPitch = "Go Premium for a longer Scan Timeline."
        )
    }

    private fun shieldCoach(ctx: DeviceContext): CoachAnswer {
        val state = if (ctx.shieldActive) {
            "Shield is ON with ${ctx.shieldBlocked} blocks so far."
        } else {
            "Shield is OFF — enable it from the Shield tab when you want DNS filtering active."
        }
        return CoachAnswer(
            text = "$state The basic shield stays free. Premium focuses on exports, signature refresh, deeper history, and coaching.",
            suggestPremium = !ctx.isPremium,
            premiumPitch = if (ctx.isPremium) null else premiumValuePitch()
        )
    }

    private fun statusCoach(ctx: DeviceContext, originalPrompt: String): CoachAnswer {
        val scanLine = when (ctx.lastScan?.verdict) {
            null -> "I don't have a recent scan yet — run Nemesis first so we talk evidence, not guesses."
            ScanVerdict.CLEAN -> "Last scan looked clean (${ctx.lastScan.detections.size} detections). Encouraging — not a forever guarantee."
            ScanVerdict.SUSPICIOUS -> "Last scan was SUSPICIOUS (${ctx.lastScan.detections.size} findings). Review Scanner results, then consider a fresh signature refresh."
            ScanVerdict.INFECTED -> "Last scan found PRIVACY THREAT indicators (${ctx.lastScan.detections.size}). Open Scanner now and act on the findings."
        }
        val upsell = !ctx.isPremium && (
            ctx.lastScan?.verdict == ScanVerdict.SUSPICIOUS ||
                ctx.lastScan?.verdict == ScanVerdict.INFECTED ||
                ctx.timelineCount >= EntitlementPolicy.FREE_TIMELINE_ENTRIES
            )
        return CoachAnswer(
            text = "You asked: \"${originalPrompt.trim()}\".\n$scanLine\n" +
                if (upsell) {
                    "If you want fresher intel and a longer ledger while you harden this device, Premium helps."
                } else {
                    "Next free move: run or re-run Scanner, then toggle Shield if you want DNS filtering."
                },
            suggestPremium = upsell,
            premiumPitch = if (upsell) premiumValuePitch() else null
        )
    }

    private fun generalCoach(ctx: DeviceContext, originalPrompt: String): CoachAnswer {
        return CoachAnswer(
            text = "Quilla hears you: \"${originalPrompt.trim()}\". " +
                "I coach from local CoreGuard evidence — scans, shield, compliance, timeline. " +
                if (ctx.isPremium) {
                    "Premium is active: ask me about exports, signature refresh, or history depth."
                } else {
                    "Ask about scan, shield, export, signatures, or Premium if you want the full toolkit."
                },
            suggestPremium = !ctx.isPremium && (
                originalPrompt.contains("more", ignoreCase = true) ||
                    originalPrompt.contains("best", ignoreCase = true)
                ),
            premiumPitch = if (ctx.isPremium) null else premiumValuePitch()
        )
    }

    private fun premiumValuePitch(): String =
        "Premium unlocks: live signature refresh · Compliance JSON export · " +
            "${EntitlementPolicy.PREMIUM_TIMELINE_ENTRIES}-scan timeline · Premium next-step coaching tips. " +
            "Quilla Q&A stays free. Core scan + shield stay free."
}
