package com.ivy.settings

import com.ivy.domain.RootScreen
import com.ivy.legacy.data.model.MainTab
import com.ivy.legacy.rivyui.NavStyle
import com.ivy.legacy.rivyui.Palette

sealed interface SettingsEvent {
    data class SetCurrency(val newCurrency: String) : SettingsEvent
    data class SetName(val newName: String) : SettingsEvent
    data class ExportToCsv(val rootScreen: RootScreen) : SettingsEvent
    data class BackupData(val rootScreen: RootScreen) : SettingsEvent
    data object SwitchTheme : SettingsEvent
    data class SetLockApp(val lockApp: Boolean) : SettingsEvent
    data class SetShowNotifications(val showNotifications: Boolean) : SettingsEvent
    data class SetHideCurrentBalance(val hideCurrentBalance: Boolean) : SettingsEvent
    data class SetHideIncome(val hideIncome: Boolean) : SettingsEvent
    data class SetTransfersAsIncomeExpense(val treatTransfersAsIncomeExpense: Boolean) :
        SettingsEvent

    data class SetStartDateOfMonth(val startDate: Int) : SettingsEvent
    data object DeleteCloudUserData : SettingsEvent
    data object DeleteAllUserData : SettingsEvent
    data object SwitchLanguage : SettingsEvent
    data object ConnectDriveExport : SettingsEvent
    data object AddRecentDriveFile : SettingsEvent
    data class PickDriveFile(val step: Int) : SettingsEvent
    data object CloseDriveSetup : SettingsEvent
    data object ConnectDriveBackup : SettingsEvent
    data object DisconnectDriveBackup : SettingsEvent
    data class SetNavStyle(val style: NavStyle) : SettingsEvent
    data class SetPalette(val palette: Palette) : SettingsEvent
    data class SetNavAutoHide(val autoHide: Boolean) : SettingsEvent
    data class MoveTab(val tab: MainTab, val by: Int) : SettingsEvent
    data object RunDriveExport : SettingsEvent
    data object DisconnectDriveExport : SettingsEvent
    data class SetDriveShowStatus(val show: Boolean) : SettingsEvent
}
