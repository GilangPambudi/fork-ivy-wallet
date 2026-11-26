package com.ivy.settings

import com.ivy.base.legacy.Theme

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
)

sealed interface DriveExportStatus {
    data object NotConnected : DriveExportStatus
    data object Pending : DriveExportStatus
    data class Success(val at: String) : DriveExportStatus
    data class Failed(val reason: String) : DriveExportStatus
}
