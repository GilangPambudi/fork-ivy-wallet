package com.ivy.settings

import com.ivy.base.legacy.Theme
import com.ivy.legacy.rivyui.RivyUiState

data class SettingsState(
    val currencyCode: String,
    val name: String,
    val currentTheme: Theme,
    val lockApp: Boolean,
    val showNotifications: Boolean,
    val hideCurrentBalance: Boolean,
    val hideIncome: Boolean,
    val treatTransfersAsIncomeExpense: Boolean,
    val startDateOfMonth: String,
    val progressState: Boolean,
    val languageOptionVisible: Boolean,
    val driveExport: DriveExportStatus,
    val driveShowStatus: Boolean,
    val driveRecentConnected: Boolean,
    val driveSetupStep: Int,
    val rivyUi: RivyUiState,
)

sealed interface DriveExportStatus {
    data object NotConnected : DriveExportStatus
    data object Pending : DriveExportStatus
    data class Success(val at: String) : DriveExportStatus
    data class Failed(val reason: String) : DriveExportStatus
}
