package com.ivy.legacy.utils

import com.google.firebase.crashlytics.FirebaseCrashlytics

fun sendToCrashlytics(
    msg: String
) {
    DeveloperException(msg).sendToCrashlytics(msg)
}

fun Exception.sendToCrashlytics(
    clarification: String? = null
) {
    clarification?.let {
        logToCrashlytics("Log: $it")
    }
    // Rivy ships without a Firebase project, so Crashlytics may not be initialized.
    runCatching { FirebaseCrashlytics.getInstance().recordException(this) }
}

fun logToCrashlytics(msg: String) {
    runCatching { FirebaseCrashlytics.getInstance().log(msg) }
}

class DeveloperException(msg: String) : Exception(msg)
