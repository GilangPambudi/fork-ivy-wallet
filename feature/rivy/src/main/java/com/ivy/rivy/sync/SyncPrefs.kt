package com.ivy.rivy.sync

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** Where the sync server is and how far this device has synced. */
@Singleton
class SyncPrefs @Inject constructor(
    @ApplicationContext context: Context
) {
    private val prefs = context.getSharedPreferences("rivy_sync", Context.MODE_PRIVATE)

    var serverUrl: String
        get() = prefs.getString(KEY_SERVER_URL, "").orEmpty()
        set(value) = prefs.edit().putString(KEY_SERVER_URL, value).apply()

    /** Session token from the server. Empty when this device is not signed in. */
    var token: String
        get() = prefs.getString(KEY_TOKEN, "").orEmpty()
        set(value) = prefs.edit().putString(KEY_TOKEN, value).apply()

    var email: String
        get() = prefs.getString(KEY_EMAIL, "").orEmpty()
        set(value) = prefs.edit().putString(KEY_EMAIL, value).apply()

    /** Number of the last server change this device has applied. */
    var lastSeq: Long
        get() = prefs.getLong(KEY_LAST_SEQ, 0)
        set(value) = prefs.edit().putLong(KEY_LAST_SEQ, value).apply()

    /** Time of the last successful sync in epoch milliseconds, 0 if never. */
    var lastSyncAt: Long
        get() = prefs.getLong(KEY_LAST_SYNC_AT, 0)
        set(value) = prefs.edit().putLong(KEY_LAST_SYNC_AT, value).apply()

    var lastError: String
        get() = prefs.getString(KEY_LAST_ERROR, "").orEmpty()
        set(value) = prefs.edit().putString(KEY_LAST_ERROR, value).apply()

    val connected: Boolean
        get() = serverUrl.isNotEmpty() && token.isNotEmpty()

    fun clearSession() {
        prefs.edit()
            .remove(KEY_TOKEN)
            .remove(KEY_EMAIL)
            .remove(KEY_LAST_SEQ)
            .remove(KEY_LAST_SYNC_AT)
            .remove(KEY_LAST_ERROR)
            .apply()
    }

    private companion object {
        const val KEY_SERVER_URL = "server_url"
        const val KEY_TOKEN = "token"
        const val KEY_EMAIL = "email"
        const val KEY_LAST_SEQ = "last_seq"
        const val KEY_LAST_SYNC_AT = "last_sync_at"
        const val KEY_LAST_ERROR = "last_error"
    }
}
