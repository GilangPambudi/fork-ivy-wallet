package com.ivy.legacy.rivyui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue

object OpenModals {
    var count by mutableIntStateOf(0)
        private set

    fun opened() {
        count += 1
    }

    fun closed() {
        count = (count - 1).coerceAtLeast(0)
    }
}
