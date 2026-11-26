package com.ivy.importdata.csvimport

sealed interface BackupLinkOffer {
    data object Ask : BackupLinkOffer
    data object Linked : BackupLinkOffer
    data class Failed(val reason: String) : BackupLinkOffer
}
