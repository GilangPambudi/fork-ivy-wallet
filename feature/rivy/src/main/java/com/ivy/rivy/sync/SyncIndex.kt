package com.ivy.rivy.sync

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Remembers the fingerprint of every item as it was when it last matched the
 * server. Comparing it with the local data shows what changed on this device,
 * without touching the app's database tables.
 *
 * Only [SyncEngine] uses it, one sync at a time.
 */
@Singleton
class SyncIndex @Inject constructor(
    @ApplicationContext context: Context
) {
    private val file = JsonFile(File(File(context.filesDir, "rivy"), "sync_index.json"))
    private var loaded: MutableMap<String, String>? = null

    /** Fingerprints by [indexKey]. Changes are kept after [save]. */
    fun entries(): MutableMap<String, String> {
        loaded?.let { return it }
        val map = mutableMapOf<String, String>()
        file.read()?.forEach { (key, value) ->
            if (value is JsonPrimitive) map[key] = value.content
        }
        loaded = map
        return map
    }

    fun save() {
        file.write(JsonObject(entries().mapValues { JsonPrimitive(it.value) }))
    }

    fun clear() {
        entries().clear()
        save()
    }
}
