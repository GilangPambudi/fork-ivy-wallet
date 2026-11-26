package com.ivy.legacy.rivyui

import android.os.Build
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalContext
import com.ivy.design.l0_system.RivyPalette

@Composable
fun ApplyRivyPalette(palette: Palette) {
    val context = LocalContext.current
    SideEffect {
        val wallpaper = if (palette == Palette.WALLPAPER && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            dynamicLightColorScheme(context)
        } else {
            null
        }
        when {
            wallpaper != null -> RivyPalette.set(
                primary = wallpaper.primary,
                primaryEnd = wallpaper.inversePrimary,
                accent = wallpaper.primary,
                accentEnd = wallpaper.inversePrimary,
            )
            palette == Palette.ORIGINAL -> RivyPalette.set(
                primary = RivyPalette.IvyPrimary,
                primaryEnd = RivyPalette.IvyPrimaryEnd,
                accent = RivyPalette.IvyAccent,
                accentEnd = RivyPalette.IvyAccentEnd,
            )
            else -> RivyPalette.set(
                primary = RivyPalette.RivyPrimary,
                primaryEnd = RivyPalette.RivyPrimaryEnd,
                accent = RivyPalette.RivyPrimary,
                accentEnd = RivyPalette.RivyPrimaryEnd,
            )
        }
    }
}
