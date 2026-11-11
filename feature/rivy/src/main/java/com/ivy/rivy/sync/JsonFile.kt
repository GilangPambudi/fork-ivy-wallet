package com.ivy.rivy.sync

import com.ivy.rivy.RivyJson
import kotlinx.serialization.json.JsonObject
import timber.log.Timber
import java.io.File

/** A small JSON document kept in the app's private storage. */
class JsonFile(private val file: File) {

    @Suppress("TooGenericExceptionCaught")
    fun read(): JsonObject? = try {
        if (file.exists()) RivyJson.parseToJsonElement(file.readText()) as? JsonObject else null
    } catch (e: Exception) {
        Timber.e(e, "Rivy: could not read ${file.name}")
        null
    }

    fun write(value: JsonObject) {
        file.parentFile?.mkdirs()
        // Write next to the file first, so a crash never leaves half a file.
        val temp = File(file.path + ".tmp")
        temp.writeText(value.toString())
        if (!temp.renameTo(file)) {
            file.writeText(value.toString())
            temp.delete()
        }
    }
}
