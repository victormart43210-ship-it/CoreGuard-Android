package com.coldboar.coreguard.quilla.knowledge

import android.content.Context
import android.util.Log
import org.json.JSONObject

/**
 * Loads Quilla's bundled cyber knowledge corpora from `assets/knowledge/`.
 */
object CyberKnowledgeAssets {

    private const val TAG = "QuillaKnowledge"
    private const val MANIFEST = "knowledge/manifest.json"

    @Volatile
    private var loadingAttempted: Boolean = false

    fun ensureLoaded(context: Context) {
        if (CyberKnowledgeBase.isLoaded()) return
        synchronized(this) {
            if (CyberKnowledgeBase.isLoaded()) return
            try {
                val assetManager = context.applicationContext.assets
                val manifestText = assetManager.open(MANIFEST).bufferedReader().use { it.readText() }
                val files = JSONObject(manifestText).optJSONArray("files")
                    ?: error("knowledge manifest missing files[]")
                val docs = ArrayList<String>(files.length())
                for (i in 0 until files.length()) {
                    val name = files.optString(i).trim()
                    if (name.isEmpty()) continue
                    val path = "knowledge/$name"
                    docs += assetManager.open(path).bufferedReader().use { it.readText() }
                }
                CyberKnowledgeBase.loadDocuments(docs)
                loadingAttempted = true
                Log.i(TAG, "Loaded ${CyberKnowledgeBase.size()} cyber knowledge entries")
            } catch (t: Throwable) {
                loadingAttempted = true
                Log.w(TAG, "Failed to load cyber knowledge assets: ${t.message}")
            }
        }
    }
}
