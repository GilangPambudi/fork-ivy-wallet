package com.ivy.rivy.sync

import com.ivy.rivy.RivyJson
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.jsonObject

/** One kind of item that is synced, for example accounts or transactions. */
interface RecordSource {
    /** Name of the kind on the server. */
    val type: String

    /** Every item on this device by id, in the form that is synced. */
    suspend fun readAll(): Map<String, JsonObject>

    /**
     * Brings an item received from the server into the form [readAll] returns.
     * Throws when the item cannot be used.
     */
    fun normalize(id: String, data: JsonObject): JsonObject

    /** Saves items that came out of [normalize], by id. */
    suspend fun upsert(items: Map<String, JsonObject>)

    suspend fun delete(id: String)

    suspend fun clear()
}

/**
 * Columns left over from Ivy's old cloud sync. They say nothing about the
 * item, so every device sends the same fixed value for them.
 */
private val legacyColumns: Map<String, JsonElement> = mapOf(
    "isSynced" to JsonPrimitive(true),
    "isDeleted" to JsonPrimitive(false),
    "lastSyncTime" to JsonPrimitive(0),
)

internal fun canonicalEntity(raw: JsonObject): JsonObject =
    JsonObject(raw.mapValues { (key, value) -> legacyColumns[key] ?: value })

internal fun isMarkedDeleted(raw: JsonObject): Boolean =
    (raw["isDeleted"] as? JsonPrimitive)?.booleanOrNull == true

/** A [RecordSource] backed by one table of the app's database. */
internal class EntitySource<E : Any>(
    override val type: String,
    private val serializer: KSerializer<E>,
    private val idOf: (E) -> String,
    private val load: suspend () -> List<E>,
    private val save: suspend (List<E>) -> Unit,
    private val remove: suspend (String) -> Unit,
    private val removeAll: suspend () -> Unit,
) : RecordSource {

    override suspend fun readAll(): Map<String, JsonObject> {
        val result = LinkedHashMap<String, JsonObject>()
        for (entity in load()) {
            val raw = RivyJson.encodeToJsonElement(serializer, entity).jsonObject
            if (!isMarkedDeleted(raw)) {
                result[idOf(entity)] = canonicalEntity(raw)
            }
        }
        return result
    }

    override fun normalize(id: String, data: JsonObject): JsonObject {
        val entity = RivyJson.decodeFromJsonElement(serializer, canonicalEntity(data))
        require(idOf(entity) == id) { "id of the item does not match the record" }
        return canonicalEntity(RivyJson.encodeToJsonElement(serializer, entity).jsonObject)
    }

    override suspend fun upsert(items: Map<String, JsonObject>) {
        save(items.values.map { RivyJson.decodeFromJsonElement(serializer, it) })
    }

    override suspend fun delete(id: String) = remove(id)

    override suspend fun clear() = removeAll()
}
