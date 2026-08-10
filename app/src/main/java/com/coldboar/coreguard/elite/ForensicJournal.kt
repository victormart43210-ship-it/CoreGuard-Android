package com.coldboar.coreguard.elite

import android.content.Context
import android.util.Base64
import android.util.Log
import com.coldboar.coreguard.CoreGuardApplication
import com.coldboar.coreguard.HardwareKeyManager
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * Append-only **Cryptographic Forensic Journal**.
 *
 * Each entry chains `SHA-256(prevHash || canonicalPayload)`. The journal file
 * is encrypted at rest with [HardwareKeyManager] (StrongBox → TEE → software).
 *
 * Not a remote SIEM — on-device, exportable for IT / analysts.
 *
 * ## Module boundary
 *
 * Screens should prefer [EliteModule.appendJournal] / export helpers so journal
 * I/O stays behind the Elite façade. Engines may append directly when already
 * inside the elite package (e.g. DTS band transitions).
 *
 * ## Test seam
 *
 * [memoryStore] bypasses Keystore encryption for JVM unit tests. Never enable
 * in production UI paths.
 */
object ForensicJournal {

    private const val TAG = "ForensicJournal"
    private const val FILE_NAME = "forensic_journal.enc"
    private const val GENESIS = "CORE_GUARD_FORENSIC_GENESIS_v1"

    enum class EventKind {
        THREAT_SCORE,
        OVERLAY_ALERT,
        ACCESSIBILITY_ALERT,
        SCAM_URL,
        NETWORK_BLOCK,
        TAMPER,
        /** Nemesis Scanner completed with non-clean verdict or IOC hits. */
        NEMESIS_SCAN,
        MANUAL_NOTE
    }

    data class Entry(
        val id: String,
        val timestampMs: Long,
        val kind: EventKind,
        val packageName: String?,
        val details: String,
        val metadata: Map<String, String>,
        val prevHash: String,
        val entryHash: String
    )

    private val lock = ReentrantLock()

    /**
     * When non-null, journal I/O uses this in-memory list (unit tests) instead of
     * StrongBox-encrypted files.
     */
    @Volatile
    internal var memoryStore: MutableList<Entry>? = null

    fun append(
        context: Context,
        kind: EventKind,
        packageName: String?,
        details: String,
        metadata: Map<String, String> = emptyMap()
    ): Entry = lock.withLock {
        val entries = loadUnlocked(context).toMutableList()
        val prev = entries.lastOrNull()?.entryHash ?: sha256Hex(GENESIS.toByteArray())
        val id = UUID.randomUUID().toString()
        val ts = System.currentTimeMillis()
        val canonical = canonicalPayload(id, ts, kind, packageName, details, metadata, prev)
        val hash = sha256Hex(canonical.toByteArray(Charsets.UTF_8))
        val entry = Entry(id, ts, kind, packageName, details, metadata, prev, hash)
        entries += entry
        persistUnlocked(context, entries)
        Log.i(TAG, "Appended ${kind.name} hash=${hash.take(12)}…")
        entry
    }

    fun all(context: Context): List<Entry> = lock.withLock { loadUnlocked(context) }

    /** Deletes all journal entries (user retention control). Irreversible. */
    fun clear(context: Context) = lock.withLock {
        memoryStore?.clear()
        val f = file(context)
        if (f.exists()) {
            f.delete()
        }
        Log.i(TAG, "Forensic journal cleared")
    }

    fun verifyChain(context: Context): Boolean = lock.withLock {
        val entries = loadUnlocked(context)
        var prev = sha256Hex(GENESIS.toByteArray())
        for (e in entries) {
            if (e.prevHash != prev) return false
            val canonical = canonicalPayload(
                e.id, e.timestampMs, e.kind, e.packageName, e.details, e.metadata, e.prevHash
            )
            if (sha256Hex(canonical.toByteArray(Charsets.UTF_8)) != e.entryHash) return false
            prev = e.entryHash
        }
        true
    }

    /** Export plaintext JSON (caller may share via share-sheet). */
    fun exportJson(context: Context): String {
        val arr = JSONArray()
        for (e in all(context)) {
            arr.put(
                JSONObject().apply {
                    put("id", e.id)
                    put("timestampMs", e.timestampMs)
                    put("kind", e.kind.name)
                    put("packageName", e.packageName)
                    put("details", e.details)
                    put("metadata", JSONObject(e.metadata))
                    put("prevHash", e.prevHash)
                    put("entryHash", e.entryHash)
                }
            )
        }
        return JSONObject()
            .put("product", "CoreGuard Elite Forensic Journal")
            .put("chainValid", verifyChain(context))
            .put("entries", arr)
            .toString(2)
    }

    fun exportCsv(context: Context): String = buildString {
        appendLine("timestampMs,kind,packageName,details,entryHash,prevHash")
        for (e in all(context)) {
            append(e.timestampMs).append(',')
            append(e.kind.name).append(',')
            append(csv(e.packageName)).append(',')
            append(csv(e.details)).append(',')
            append(e.entryHash).append(',')
            append(e.prevHash).append('\n')
        }
    }

    private fun csv(value: String?): String {
        val v = value.orEmpty().replace("\"", "\"\"")
        return "\"$v\""
    }

    private fun keyManager(context: Context): HardwareKeyManager =
        CoreGuardApplication.get()?.keyManager ?: HardwareKeyManager(context)

    private fun file(context: Context): File = File(context.filesDir, FILE_NAME)

    private fun loadUnlocked(context: Context): List<Entry> {
        memoryStore?.let { return it.toList() }
        val f = file(context)
        if (!f.exists() || f.length() == 0L) return emptyList()
        return runCatching {
            val enc = f.readBytes()
            val plain = keyManager(context).decrypt(enc)
            val arr = JSONArray(String(plain, Charsets.UTF_8))
            buildList {
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    val metaObj = o.optJSONObject("metadata") ?: JSONObject()
                    val meta = metaObj.keys().asSequence().associateWith { metaObj.getString(it) }
                    val pkg = if (o.isNull("packageName")) {
                        null
                    } else {
                        o.optString("packageName").ifBlank { null }
                    }
                    add(
                        Entry(
                            id = o.getString("id"),
                            timestampMs = o.getLong("timestampMs"),
                            kind = EventKind.valueOf(o.getString("kind")),
                            packageName = pkg,
                            details = o.getString("details"),
                            metadata = meta,
                            prevHash = o.getString("prevHash"),
                            entryHash = o.getString("entryHash")
                        )
                    )
                }
            }
        }.getOrElse {
            Log.w(TAG, "Journal load failed: ${it.message}")
            emptyList()
        }
    }

    private fun persistUnlocked(context: Context, entries: List<Entry>) {
        memoryStore?.let {
            it.clear()
            it.addAll(entries)
            return
        }
        val arr = JSONArray()
        for (e in entries) {
            arr.put(
                JSONObject().apply {
                    put("id", e.id)
                    put("timestampMs", e.timestampMs)
                    put("kind", e.kind.name)
                    if (e.packageName != null) put("packageName", e.packageName) else put("packageName", JSONObject.NULL)
                    put("details", e.details)
                    put("metadata", JSONObject(e.metadata))
                    put("prevHash", e.prevHash)
                    put("entryHash", e.entryHash)
                }
            )
        }
        val plain = arr.toString().toByteArray(Charsets.UTF_8)
        val enc = keyManager(context).encrypt(plain)
        file(context).writeBytes(enc)
    }

    private fun canonicalPayload(
        id: String,
        ts: Long,
        kind: EventKind,
        packageName: String?,
        details: String,
        metadata: Map<String, String>,
        prevHash: String
    ): String {
        val metaSorted = metadata.toSortedMap().entries.joinToString(";") { "${it.key}=${it.value}" }
        return listOf(id, ts.toString(), kind.name, packageName.orEmpty(), details, metaSorted, prevHash)
            .joinToString("|")
    }

    private fun sha256Hex(bytes: ByteArray): String {
        val dig = MessageDigest.getInstance("SHA-256").digest(bytes)
        return dig.joinToString("") { "%02x".format(it) }
    }

    /** Debug helper — not for secrets. */
    fun debugFingerprint(bytes: ByteArray): String =
        Base64.encodeToString(bytes, Base64.NO_WRAP).take(16)
}
