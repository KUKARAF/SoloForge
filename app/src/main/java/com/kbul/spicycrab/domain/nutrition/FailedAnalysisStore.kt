package com.kbul.spicycrab.domain.nutrition

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Serializable
data class FailedAnalysis(
    val id: String,
    val comment: String,
    val imagePath: String?,
    val error: String,
    val failedEpoch: Long,
) {
    val imageFile: File? get() = imagePath?.let(::File)
}

/**
 * Keeps the photo and text of analyses that failed while the user was away, so they can be
 * retried by hand. Lives in app-private storage and is not part of the JSON backup.
 */
@Singleton
class FailedAnalysisStore @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val dir = File(context.filesDir, "failed_analyses")
    private val indexFile = File(dir, "index.json")
    private val json = Json { ignoreUnknownKeys = true }
    private val serializer = ListSerializer(FailedAnalysis.serializer())
    private val mutex = Mutex()

    private val _items = MutableStateFlow<List<FailedAnalysis>>(emptyList())
    val items: StateFlow<List<FailedAnalysis>> = _items.asStateFlow()

    init {
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            mutex.withLock { _items.value = readIndex() }
        }
    }

    /** Records a failure; [retryOf] replaces the earlier record when a retry fails again. */
    suspend fun record(imageFile: File?, comment: String, error: String, retryOf: String?) = edit { list ->
        val id = retryOf ?: UUID.randomUUID().toString()
        val storedImage = imageFile?.let { src ->
            if (src.parentFile == dir) src else File(dir, "$id.jpg").also { src.copyTo(it, overwrite = true) }
        }
        list.filterNot { it.id == id } +
            FailedAnalysis(id, comment, storedImage?.absolutePath, error, System.currentTimeMillis())
    }

    suspend fun remove(id: String) = edit { list ->
        list.find { it.id == id }?.imageFile?.delete()
        list.filterNot { it.id == id }
    }

    private suspend fun edit(transform: (List<FailedAnalysis>) -> List<FailedAnalysis>) = withContext(Dispatchers.IO) {
        mutex.withLock {
            dir.mkdirs()
            val updated = transform(readIndex())
            indexFile.writeText(json.encodeToString(serializer, updated))
            _items.value = updated
        }
    }

    private fun readIndex(): List<FailedAnalysis> =
        if (indexFile.exists()) json.decodeFromString(serializer, indexFile.readText()) else emptyList()
}
