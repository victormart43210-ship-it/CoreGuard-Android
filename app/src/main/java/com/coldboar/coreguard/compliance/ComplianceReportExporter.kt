package com.coldboar.coreguard.compliance

import android.content.Context
import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Exports a [MasvsComplianceReport] to JSON and (optionally) writes it to a
 * shareable file in the app's external files directory.
 *
 * JSON schema (example):
 * ```json
 * {
 *   "reportVersion": "1",
 *   "standard": "OWASP MASVS v2",
 *   "generatedAt": "2025-01-15T10:30:00Z",
 *   "overallScore": 87,
 *   "categories": [
 *     {
 *       "id": "MASVS-RESILIENCE",
 *       "label": "MASVS-RESILIENCE",
 *       "score": 90,
 *       "checks": [
 *         { "id": "debugger", "displayName": "Debugger Attached", "state": "PASS", "explanation": "..." }
 *       ]
 *     }
 *   ]
 * }
 * ```
 */
class ComplianceReportExporter(private val context: Context) {

    /**
     * Serialises [report] to a JSON string.
     */
    fun toJson(report: MasvsComplianceReport): String {
        val root = JSONObject()
        root.put("reportVersion", "1")
        root.put("standard", "OWASP MASVS v2")
        root.put("generatedAt", isoTimestamp(report.generatedAtMs))
        root.put("overallScore", report.overallScore)

        val categories = JSONArray()
        for (cat in report.categoryScores) {
            val catObj = JSONObject()
            catObj.put("id", cat.category.name)
            catObj.put("label", cat.category.label)
            catObj.put("description", cat.category.description)
            catObj.put("score", cat.score)

            val checks = JSONArray()
            for (check in cat.checks) {
                val checkObj = JSONObject()
                checkObj.put("id", check.id)
                checkObj.put("displayName", check.displayName)
                checkObj.put("state", check.state.name)
                checkObj.put("explanation", check.explanation)
                checks.put(checkObj)
            }
            catObj.put("checks", checks)
            categories.put(catObj)
        }
        root.put("categories", categories)
        return root.toString(2)
    }

    /**
     * Writes the JSON representation of [report] to a timestamped file in the
     * app's external files directory and returns a [Uri] pointing to it.
     *
     * Returns `null` if external storage is unavailable.
     */
    fun exportToFile(report: MasvsComplianceReport): File? {
        val dir = context.getExternalFilesDir("compliance_reports") ?: return null
        dir.mkdirs()
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date(report.generatedAtMs))
        val file = File(dir, "masvs_report_$timestamp.json")
        file.writeText(toJson(report))
        return file
    }

    private fun isoTimestamp(epochMs: Long): String =
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }.format(Date(epochMs))
}
