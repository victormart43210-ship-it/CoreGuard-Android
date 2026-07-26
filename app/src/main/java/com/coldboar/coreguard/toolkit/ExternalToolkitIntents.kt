package com.coldboar.coreguard.toolkit

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import java.net.URI

/**
 * Opens an [ExternalSecurityToolkit.Tool] HTTPS URL in the system browser.
 */
object ExternalToolkitIntents {

    fun open(context: Context, tool: ExternalSecurityToolkit.Tool) {
        val intent = intentFor(tool) ?: run {
            Toast.makeText(context, "Unable to open ${tool.host}.", Toast.LENGTH_SHORT).show()
            return
        }
        try {
            context.startActivity(intent)
        } catch (_: Exception) {
            Toast.makeText(
                context,
                "No browser available to open ${tool.host}.",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    internal fun intentFor(tool: ExternalSecurityToolkit.Tool): Intent? {
        if (!isHttpsUrl(tool.url)) return null
        return Intent(Intent.ACTION_VIEW, Uri.parse(tool.url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    /** Pure JVM-safe check used by unit tests and before constructing Android intents. */
    internal fun isHttpsUrl(url: String): Boolean {
        return try {
            val uri = URI(url)
            uri.scheme.equals("https", ignoreCase = true) && !uri.host.isNullOrBlank()
        } catch (_: Exception) {
            false
        }
    }
}
