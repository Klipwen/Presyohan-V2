package com.presyohan.app

import android.content.Context
import java.io.File
import java.util.UUID
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class ImportDraftStore(context: Context) {
    private val appContext = context.applicationContext
    private val json = Json {
        encodeDefaults = true
        ignoreUnknownKeys = true
        prettyPrint = false
    }

    fun createSession(
        storeId: String,
        storeName: String? = null,
        source: ImportSource = ImportSource.SIMPLE_MANUAL,
        currentMode: EntryMode = EntryMode.SIMPLE,
        categories: MutableList<DraftCategory> = mutableListOf(),
        metadata: ImportMetadata = ImportMetadata()
    ): DraftImportSession {
        val now = System.currentTimeMillis()
        val session = DraftImportSession(
            sessionId = UUID.randomUUID().toString(),
            storeId = storeId,
            storeName = storeName,
            source = source,
            currentMode = currentMode,
            categories = categories,
            metadata = metadata,
            isDirty = categories.any { it.items.isNotEmpty() },
            createdAtMillis = now,
            updatedAtMillis = now
        )
        return saveSession(session)
    }

    fun saveSession(session: DraftImportSession): DraftImportSession {
        ensureDraftDir()
        val stamped = session.copy(updatedAtMillis = System.currentTimeMillis())
        val file = fileFor(stamped.sessionId)
        val tempFile = File(file.parentFile, "${file.name}.tmp")
        tempFile.writeText(json.encodeToString(stamped))
        if (!tempFile.renameTo(file)) {
            file.writeText(tempFile.readText())
            tempFile.delete()
        }
        return stamped
    }

    fun loadSession(sessionId: String): DraftImportSession? {
        val file = fileFor(sessionId)
        if (!file.exists()) return null
        return runCatching {
            json.decodeFromString<DraftImportSession>(file.readText())
        }.getOrNull()
    }

    fun deleteSession(sessionId: String): Boolean {
        val file = fileFor(sessionId)
        return !file.exists() || file.delete()
    }

    fun clearAllSessions(): Int {
        val dir = draftsDir()
        val files = dir.listFiles { file -> file.extension == FILE_EXTENSION } ?: return 0
        var deleted = 0
        files.forEach { file ->
            if (file.delete()) deleted++
        }
        return deleted
    }

    private fun draftsDir(): File = File(appContext.cacheDir, DRAFT_DIR_NAME)

    private fun ensureDraftDir() {
        val dir = draftsDir()
        if (!dir.exists()) dir.mkdirs()
    }

    private fun fileFor(sessionId: String): File {
        val safeId = sessionId.replace(Regex("[^A-Za-z0-9_-]"), "_")
        return File(draftsDir(), "$safeId.$FILE_EXTENSION")
    }

    private companion object {
        const val DRAFT_DIR_NAME = "import_drafts"
        const val FILE_EXTENSION = "json"
    }
}
