package com.ivy.rivy.inbox

import android.content.Context
import com.ivy.rivy.sync.JsonFile
import com.ivy.rivy.sync.RecordSource
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Keeps the items Rivy adds on top of Ivy Wallet: transactions read from bank
 * emails and what the owner taught the app about them. They live in one small
 * file, so Ivy's database stays exactly as upstream defines it.
 */
@Singleton
class RivyStore @Inject constructor(
    @ApplicationContext context: Context
) {
    private val file = JsonFile(File(File(context.filesDir, "rivy"), "store.json"))
    private val mutex = Mutex()
    private var loaded: MutableMap<String, MutableMap<String, JsonObject>>? = null

    private val _version = MutableStateFlow(0L)

    /** Goes up every time something in the store changes. */
    val version: StateFlow<Long> = _version

    suspend fun all(type: String): Map<String, JsonObject> = locked {
        data()[type]?.toMap().orEmpty()
    }

    suspend fun put(type: String, items: Map<String, JsonObject>) = locked {
        if (items.isNotEmpty()) {
            data().getOrPut(type) { mutableMapOf() }.putAll(items)
            persist()
        }
    }

    suspend fun remove(type: String, id: String) = locked {
        if (data()[type]?.remove(id) != null) {
            persist()
        }
    }

    suspend fun clear(type: String) = locked {
        if (data().remove(type) != null) {
            persist()
        }
    }

    /** The items of one kind as something the sync engine can read and write. */
    fun source(kind: String): RecordSource = object : RecordSource {
        override val type: String = kind

        override suspend fun readAll(): Map<String, JsonObject> = all(kind)

        override fun normalize(id: String, data: JsonObject): JsonObject = data

        override suspend fun upsert(items: Map<String, JsonObject>) = put(kind, items)

        override suspend fun delete(id: String) = remove(kind, id)

        override suspend fun clear() = clear(kind)
    }

    private suspend fun <T> locked(block: () -> T): T = withContext(Dispatchers.IO) {
        mutex.withLock { block() }
    }

    private fun data(): MutableMap<String, MutableMap<String, JsonObject>> {
        loaded?.let { return it }
        val map = mutableMapOf<String, MutableMap<String, JsonObject>>()
        file.read()?.forEach { (type, items) ->
            if (items is JsonObject) {
                val byId = mutableMapOf<String, JsonObject>()
                items.forEach { (id, item) ->
                    if (item is JsonObject) byId[id] = item
                }
                map[type] = byId
            }
        }
        loaded = map
        return map
    }

    private fun persist() {
        file.write(JsonObject(data().mapValues { JsonObject(it.value) }))
        _version.value += 1
    }
}
