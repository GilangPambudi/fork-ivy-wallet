package com.ivy.legacy.rivyui

import com.ivy.base.legacy.SharedPrefs
import com.ivy.legacy.data.model.MainTab
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

enum class NavStyle { CLASSIC, PILL_FLOAT, PILL_LABEL, PILL_CENTER }

enum class Palette { ORIGINAL, RIVY, WALLPAPER }

data class RivyUiState(
    val navStyle: NavStyle,
    val tabOrder: List<MainTab>,
    val autoHide: Boolean,
    val palette: Palette,
) {
    val tabs: List<MainTab>
        get() = if (navStyle == NavStyle.CLASSIC) CLASSIC_TABS else tabOrder

    companion object {
        val CLASSIC_TABS = listOf(MainTab.HOME, MainTab.ACCOUNTS)
        val DEFAULT_ORDER = listOf(MainTab.HOME, MainTab.ACCOUNTS, MainTab.REPORTS, MainTab.SETTINGS)
    }
}

@Singleton
class RivyUi @Inject constructor(
    private val sharedPrefs: SharedPrefs,
) {
    private val _state = MutableStateFlow(read())
    val state: StateFlow<RivyUiState> = _state.asStateFlow()

    fun setNavStyle(style: NavStyle) = save(KEY_NAV_STYLE, style.name)

    fun setPalette(palette: Palette) = save(KEY_PALETTE, palette.name)

    fun setAutoHide(autoHide: Boolean) {
        sharedPrefs.putBoolean(KEY_AUTO_HIDE, autoHide)
        _state.value = read()
    }

    fun moveTab(tab: MainTab, by: Int) {
        val order = _state.value.tabOrder.toMutableList()
        val from = order.indexOf(tab)
        val to = (from + by).coerceIn(0, order.lastIndex)
        if (from < 0 || from == to) return
        order.add(to, order.removeAt(from))
        save(KEY_TAB_ORDER, order.joinToString(",") { it.name })
    }

    private fun save(key: String, value: String) {
        sharedPrefs.putString(key, value)
        _state.value = read()
    }

    private fun read(): RivyUiState {
        val saved = sharedPrefs.getString(KEY_TAB_ORDER, null)
            ?.split(",")
            ?.mapNotNull { name -> MainTab.entries.firstOrNull { it.name == name } }
            .orEmpty()
            .distinct()
        return RivyUiState(
            navStyle = enumOr(KEY_NAV_STYLE, NavStyle.CLASSIC),
            tabOrder = saved + RivyUiState.DEFAULT_ORDER.filterNot { it in saved },
            autoHide = sharedPrefs.getBoolean(KEY_AUTO_HIDE, false),
            palette = enumOr(KEY_PALETTE, Palette.ORIGINAL),
        )
    }

    private inline fun <reified T : Enum<T>> enumOr(key: String, default: T): T =
        sharedPrefs.getString(key, null)
            ?.let { name -> enumValues<T>().firstOrNull { it.name == name } }
            ?: default

    companion object {
        private const val KEY_NAV_STYLE = "rivy_nav_style"
        private const val KEY_TAB_ORDER = "rivy_tab_order"
        private const val KEY_AUTO_HIDE = "rivy_nav_auto_hide"
        private const val KEY_PALETTE = "rivy_palette"
    }
}
