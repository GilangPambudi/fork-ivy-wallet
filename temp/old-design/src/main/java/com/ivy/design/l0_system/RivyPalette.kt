package com.ivy.design.l0_system

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color

object RivyPalette {
    val IvyPrimary = Color(0xFF6B4DFF)
    val IvyPrimaryEnd = Color(0xFFAA99FF)
    val IvyAccent = Color(0xFF14CC9E)
    val IvyAccentEnd = Color(0xFF49F2C8)

    val RivyPrimary = Color(0xFF7C5CFF)
    val RivyPrimaryEnd = Color(0xFFB3A3FF)

    var primary by mutableStateOf(IvyPrimary)
        private set
    var primaryEnd by mutableStateOf(IvyPrimaryEnd)
        private set
    var accent by mutableStateOf(IvyAccent)
        private set
    var accentEnd by mutableStateOf(IvyAccentEnd)
        private set

    fun set(primary: Color, primaryEnd: Color, accent: Color, accentEnd: Color) {
        this.primary = primary
        this.primaryEnd = primaryEnd
        this.accent = accent
        this.accentEnd = accentEnd
    }
}
