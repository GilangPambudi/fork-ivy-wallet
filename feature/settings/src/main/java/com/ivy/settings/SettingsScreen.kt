package com.ivy.settings

import androidx.annotation.DrawableRes
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.BoxWithConstraintsScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ivy.base.legacy.Theme
import com.ivy.design.l0_system.UI
import com.ivy.design.l0_system.style
import com.ivy.design.l1_buildingBlocks.IconScale
import com.ivy.design.l1_buildingBlocks.IvyIconScaled
import com.ivy.design.utils.thenIf
import com.ivy.legacy.Constants
import com.ivy.legacy.IvyWalletPreview
import com.ivy.legacy.rootScreen
import com.ivy.legacy.utils.drawColoredShadow
import com.ivy.navigation.AttributionsScreen
import com.ivy.navigation.ContributorsScreen
import com.ivy.navigation.ExchangeRatesScreen
import com.ivy.navigation.FeaturesScreen
import com.ivy.navigation.ImportScreen
import com.ivy.navigation.Navigation
import com.ivy.navigation.ReleasesScreen
import com.ivy.navigation.navigation
import com.ivy.navigation.screenScopedViewModel
import com.ivy.ui.R
import com.ivy.wallet.domain.data.IvyCurrency
import com.ivy.wallet.ui.theme.Gradient
import com.ivy.wallet.ui.theme.GradientGreen
import com.ivy.wallet.ui.theme.Gray
import com.ivy.wallet.ui.theme.MediumBlack
import com.ivy.wallet.ui.theme.Red
import com.ivy.wallet.ui.theme.White
import com.ivy.wallet.ui.theme.components.IvySwitch
import com.ivy.wallet.ui.theme.components.IvyToolbar
import com.ivy.wallet.ui.theme.modal.ChooseStartDateOfMonthModal
import com.ivy.wallet.ui.theme.modal.CurrencyModal
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.zIndex
import com.ivy.legacy.data.model.MainTab
import com.ivy.legacy.rivyui.NavStyle
import com.ivy.legacy.rivyui.Palette
import com.ivy.legacy.rivyui.RivyUiState
import com.ivy.wallet.ui.theme.components.IvyIcon
import com.ivy.wallet.ui.theme.modal.DeleteModal
import com.ivy.wallet.ui.theme.modal.IvyModal
import com.ivy.wallet.ui.theme.modal.ModalSet
import com.ivy.wallet.ui.theme.modal.ModalSkip
import com.ivy.wallet.ui.theme.modal.ModalTitle
import com.ivy.wallet.ui.theme.modal.NameModal
import com.ivy.wallet.ui.theme.modal.ProgressModal
import java.util.Locale

@ExperimentalFoundationApi
@Composable
fun BoxWithConstraintsScope.SettingsScreen() {
    val viewModel: SettingsViewModel = screenScopedViewModel()
    val uiState = viewModel.uiState()
    val rootScreen = rootScreen()

    UI(
        currencyCode = uiState.currencyCode,
        theme = uiState.currentTheme,
        onSwitchTheme = {
            viewModel.onEvent(SettingsEvent.SwitchTheme)
        },
        lockApp = uiState.lockApp,
        showNotifications = uiState.showNotifications,
        hideCurrentBalance = uiState.hideCurrentBalance,
        hideIncome = uiState.hideIncome,
        progressState = uiState.progressState,
        treatTransfersAsIncomeExpense = uiState.treatTransfersAsIncomeExpense,
        nameLocalAccount = uiState.name,
        startDateOfMonth = uiState.startDateOfMonth.toInt(),
        languageOptionVisible = uiState.languageOptionVisible,
        driveExport = uiState.driveExport,
        driveShowStatus = uiState.driveShowStatus,
        driveRecentConnected = uiState.driveRecentConnected,
        driveSetupStep = uiState.driveSetupStep,
        rivyUi = uiState.rivyUi,
        onRivyUiEvent = viewModel::onEvent,
        onAddRecentDriveFile = {
            viewModel.onEvent(SettingsEvent.AddRecentDriveFile)
        },
        onPickDriveFile = {
            viewModel.onEvent(SettingsEvent.PickDriveFile(it))
        },
        onCloseDriveSetup = {
            viewModel.onEvent(SettingsEvent.CloseDriveSetup)
        },
        onSetDriveShowStatus = {
            viewModel.onEvent(SettingsEvent.SetDriveShowStatus(it))
        },
        onConnectDriveExport = {
            viewModel.onEvent(SettingsEvent.ConnectDriveExport)
        },
        onRunDriveExport = {
            viewModel.onEvent(SettingsEvent.RunDriveExport)
        },
        onDisconnectDriveExport = {
            viewModel.onEvent(SettingsEvent.DisconnectDriveExport)
        },
        onSetCurrency = {
            viewModel.onEvent(SettingsEvent.SetCurrency(it))
        },
        onSetName = {
            viewModel.onEvent(SettingsEvent.SetName(it))
        },
        onBackupData = {
            viewModel.onEvent(SettingsEvent.BackupData(rootScreen))
        },
        onExportToCSV = {
            viewModel.onEvent(SettingsEvent.ExportToCsv(rootScreen))
        },
        onSetLockApp = {
            viewModel.onEvent(SettingsEvent.SetLockApp(it))
        },
        onSetShowNotifications = {
            viewModel.onEvent(SettingsEvent.SetShowNotifications(it))
        },
        onSetHideCurrentBalance = {
            viewModel.onEvent(SettingsEvent.SetHideCurrentBalance(it))
        },
        onSetHideIncome = {
            viewModel.onEvent(SettingsEvent.SetHideIncome(it))
        },
        onSetStartDateOfMonth = {
            viewModel.onEvent(SettingsEvent.SetStartDateOfMonth(it))
        },
        onSetTreatTransfersAsIncExp = {
            viewModel.onEvent(SettingsEvent.SetTransfersAsIncomeExpense(it))
        },
        onDeleteAllUserData = {
            viewModel.onEvent(SettingsEvent.DeleteAllUserData)
        },
        onDeleteCloudUserData = {
            viewModel.onEvent(SettingsEvent.DeleteCloudUserData)
        },
        onSwitchLanguage = {
            viewModel.onEvent(SettingsEvent.SwitchLanguage)
        }
    )
}

@ExperimentalFoundationApi
@Composable
@Suppress("LongMethod")
private fun BoxWithConstraintsScope.UI(
    currencyCode: String,
    theme: Theme,
    onSwitchTheme: () -> Unit,
    lockApp: Boolean,
    nameLocalAccount: String?,
    languageOptionVisible: Boolean,
    onSetCurrency: (String) -> Unit,
    startDateOfMonth: Int = 1,
    showNotifications: Boolean = true,
    hideCurrentBalance: Boolean = false,
    hideIncome: Boolean = false,
    progressState: Boolean = false,
    treatTransfersAsIncomeExpense: Boolean = false,
    driveExport: DriveExportStatus = DriveExportStatus.NotConnected,
    driveShowStatus: Boolean = true,
    driveRecentConnected: Boolean = false,
    driveSetupStep: Int = 0,
    onAddRecentDriveFile: () -> Unit = {},
    onPickDriveFile: (Int) -> Unit = {},
    onCloseDriveSetup: () -> Unit = {},
    rivyUi: RivyUiState? = null,
    onRivyUiEvent: (SettingsEvent) -> Unit = {},
    onSetDriveShowStatus: (Boolean) -> Unit = {},
    onConnectDriveExport: () -> Unit = {},
    onRunDriveExport: () -> Unit = {},
    onDisconnectDriveExport: () -> Unit = {},
    onSetName: (String) -> Unit = {},
    onBackupData: () -> Unit = {},
    onExportToCSV: () -> Unit = {},
    onSetLockApp: (Boolean) -> Unit = {},
    onSetShowNotifications: (Boolean) -> Unit = {},
    onSetTreatTransfersAsIncExp: (Boolean) -> Unit = {},
    onSetHideCurrentBalance: (Boolean) -> Unit = {},
    onSetHideIncome: (Boolean) -> Unit = {},
    onSetStartDateOfMonth: (Int) -> Unit = {},
    onDeleteAllUserData: () -> Unit = {},
    onDeleteCloudUserData: () -> Unit = {},
    onSwitchLanguage: () -> Unit = {}
) {
    var currencyModalVisible by remember { mutableStateOf(false) }
    var nameModalVisible by remember { mutableStateOf(false) }
    var chooseStartDateOfMonthVisible by remember { mutableStateOf(false) }
    var deleteCloudDataModalVisible by remember { mutableStateOf(false) }
    var deleteAllDataModalVisible by remember { mutableStateOf(false) }
    var deleteAllDataModalFinalVisible by remember { mutableStateOf(false) }
    var appearanceModal by remember { mutableStateOf(AppearanceModal.NONE) }
    val nav = navigation()

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .testTag("settings_lazy_column")
    ) {
        stickyHeader {
            IvyToolbar(
                onBack = { nav.onBackPressed() },
            ) {
                Spacer(Modifier.weight(1f))

                val rootScreen = rootScreen()
                Text(
                    modifier = Modifier.clickable {
                        rootScreen.openUrlInBrowser(Constants.URL_RIVY_REPO)
                    },
                    text = "${rootScreen.buildVersionName} (${rootScreen.buildVersionCode})",
                    style = UI.typo.nC.style(
                        color = UI.colors.gray,
                        fontWeight = FontWeight.Bold
                    )
                )

                Spacer(Modifier.width(32.dp))
            }
            // onboarding toolbar include paddingBottom 16.dp
        }

        item {
            Spacer(Modifier.height(8.dp))

            Text(
                modifier = Modifier.padding(start = 32.dp),
                text = stringResource(R.string.settings),
                style = UI.typo.h2.style(
                    fontWeight = FontWeight.Black
                )
            )

            Spacer(Modifier.height(24.dp))

            CurrencyButton(currency = currencyCode) {
                currencyModalVisible = true
            }

            Spacer(Modifier.height(12.dp))

            AccountCard(
                nameLocalAccount = nameLocalAccount,
            ) {
                nameModalVisible = true
            }

//            Spacer(Modifier.height(20.dp))
//            Premium()
        }

        item {
            SettingsSectionDivider(text = stringResource(R.string.import_export))

            Spacer(Modifier.height(16.dp))

            val nav = navigation()
            ExportCSV {
                onExportToCSV()
            }

            Spacer(Modifier.height(12.dp))

            DriveExport(
                status = driveExport,
                showStatus = driveShowStatus,
                recentConnected = driveRecentConnected,
                onAddRecent = onAddRecentDriveFile,
                onSetShowStatus = onSetDriveShowStatus,
                onConnect = onConnectDriveExport,
                onRun = onRunDriveExport,
                onDisconnect = onDisconnectDriveExport,
            )

            Spacer(Modifier.height(12.dp))

            SettingsDefaultButton(
                icon = R.drawable.ic_vue_security_shield,
                text = stringResource(R.string.backup_data),
                iconPadding = 8.dp
            ) {
                onBackupData()
            }

            Spacer(Modifier.height(12.dp))

            SettingsPrimaryButton(
                icon = R.drawable.ic_export_csv,
                text = stringResource(R.string.import_data),
                backgroundGradient = GradientGreen
            ) {
                nav.navigateTo(
                    ImportScreen(
                        launchedFromOnboarding = false
                    )
                )
            }
        }

        item {
            SettingsSectionDivider(text = stringResource(R.string.app_settings))

            Spacer(Modifier.height(16.dp))

            AppThemeButton(
                icon = when (theme) {
                    Theme.LIGHT -> R.drawable.home_more_menu_light_mode
                    Theme.DARK -> R.drawable.home_more_menu_dark_mode
                    Theme.AMOLED_DARK -> R.drawable.home_more_menu_amoled_dark_mode
                    Theme.AUTO -> R.drawable.home_more_menu_auto_mode
                },
                label = when (theme) {
                    Theme.LIGHT -> stringResource(R.string.light_mode)
                    Theme.DARK -> stringResource(R.string.dark_mode)
                    Theme.AMOLED_DARK -> stringResource(R.string.amoled_mode)
                    Theme.AUTO -> stringResource(R.string.auto_mode)
                }
            ) {
                onSwitchTheme()
            }

            Spacer(Modifier.height(12.dp))

            if (rivyUi != null) {
                RivyAppearance(
                    state = rivyUi,
                    onOpen = { appearanceModal = it },
                    onSetAutoHide = { onRivyUiEvent(SettingsEvent.SetNavAutoHide(it)) },
                )

                Spacer(Modifier.height(12.dp))
            }

            val nav = navigation()
//            SettingsDefaultButton(
//                icon = R.drawable.ic_custom_atom_m,
//                text = "Features"
//            ) {
//                nav.navigateTo(FeaturesScreen)
//            }
//
//            Spacer(Modifier.height(12.dp))

            if (languageOptionVisible) {
                SettingsDefaultButton(
                    icon = R.drawable.ic_vue_location_global,
                    iconPadding = 6.dp,
                    text = stringResource(R.string.language),
                    description = Locale.getDefault().displayName
                ) {
                    onSwitchLanguage()
                }

                Spacer(Modifier.height(12.dp))
            }

            SettingsDefaultButton(
                icon = R.drawable.ic_currency,
                text = stringResource(R.string.exchange_rates),
            ) {
                nav.navigateTo(ExchangeRatesScreen)
            }

            Spacer(Modifier.height(12.dp))

            AppSwitch(
                lockApp = lockApp,
                onSetLockApp = onSetLockApp,
                text = stringResource(R.string.lock_app),
                icon = R.drawable.ic_custom_fingerprint_m
            )

            Spacer(Modifier.height(12.dp))

            AppSwitch(
                lockApp = showNotifications,
                onSetLockApp = onSetShowNotifications,
                text = stringResource(R.string.show_notifications),
                icon = R.drawable.ic_notification_m
            )

            Spacer(Modifier.height(12.dp))

            AppSwitch(
                lockApp = hideCurrentBalance,
                onSetLockApp = onSetHideCurrentBalance,
                text = stringResource(R.string.hide_balance),
                description = stringResource(R.string.hide_balance_description),
                icon = R.drawable.ic_hide_m
            )

            Spacer(Modifier.height(12.dp))

            AppSwitch(
                lockApp = hideIncome,
                onSetLockApp = onSetHideIncome,
                text = stringResource(R.string.hide_income),
                description = stringResource(R.string.hide_income_description),
                icon = R.drawable.ic_hide_m
            )

            Spacer(Modifier.height(12.dp))

            AppSwitch(
                lockApp = treatTransfersAsIncomeExpense,
                onSetLockApp = onSetTreatTransfersAsIncExp,
                text = stringResource(R.string.transfers_as_income_expense),
                description = stringResource(R.string.transfers_as_income_expense_description),
                icon = R.drawable.ic_custom_transfer_m
            )

            Spacer(Modifier.height(12.dp))

            StartDateOfMonth(
                startDateOfMonth = startDateOfMonth
            ) {
                chooseStartDateOfMonthVisible = true
            }

            Spacer(Modifier.height(12.dp))

            CustomFeatures(
                onClick = { nav.navigateTo(FeaturesScreen) }
            )
        }

//        item {
//            SettingsSectionDivider(text = stringResource(R.string.experimental))
//
//            Spacer(Modifier.height(16.dp))
//
//            val nav = navigation()
//            SettingsDefaultButton(
//                icon = R.drawable.ic_custom_atom_m,
//                text = stringResource(R.string.experimental_settings)
//            ) {
//                nav.navigateTo(ExperimentalScreen)
//            }
//        }

        item {
            SettingsSectionDivider(text = stringResource(R.string.other))

            Spacer(Modifier.height(16.dp))

            val rootScreen = rootScreen()
            SettingsPrimaryButton(
                icon = R.drawable.github_logo,
                iconPadding = 10.dp,
                text = stringResource(R.string.ivy_wallet_is_opensource),
                backgroundGradient = Gradient.solid(MediumBlack)
            ) {
                rootScreen.openUrlInBrowser(url = Constants.URL_IVY_WALLET_REPO)
            }
        }

        item {
            SettingsSectionDivider(
                text = stringResource(R.string.danger_zone),
                color = Red
            )

            Spacer(Modifier.height(16.dp))

            SettingsPrimaryButton(
                icon = R.drawable.ic_delete,
                text = stringResource(R.string.delete_all_user_data),
                backgroundGradient = Gradient.solid(Red)
            ) {
                deleteAllDataModalVisible = true
            }
        }

        item {
            Spacer(modifier = Modifier.height(120.dp)) // last item spacer
        }
    }

    CurrencyModal(
        title = stringResource(R.string.set_currency),
        initialCurrency = IvyCurrency.fromCode(currencyCode),
        visible = currencyModalVisible,
        dismiss = { currencyModalVisible = false }
    ) {
        onSetCurrency(it)
    }

    NameModal(
        visible = nameModalVisible,
        name = nameLocalAccount ?: "",
        dismiss = { nameModalVisible = false }
    ) {
        onSetName(it)
    }

    ChooseStartDateOfMonthModal(
        visible = chooseStartDateOfMonthVisible,
        selectedStartDateOfMonth = startDateOfMonth,
        dismiss = { chooseStartDateOfMonthVisible = false }
    ) {
        onSetStartDateOfMonth(it)
    }

    DeleteModal(
        title = stringResource(R.string.delete_all_user_data_question),
        description = stringResource(
            R.string.delete_all_user_data_warning,
            stringResource(R.string.your_account)
        ),
        visible = deleteAllDataModalVisible,
        dismiss = { deleteAllDataModalVisible = false },
        onDelete = {
            deleteAllDataModalVisible = false
            deleteAllDataModalFinalVisible = true
        }
    )

    DeleteModal(
        title = stringResource(
            R.string.confirm_all_userd_data_deletion,
            stringResource(R.string.all_of_your_data)
        ),
        description = stringResource(R.string.final_deletion_warning),
        visible = deleteAllDataModalFinalVisible,
        dismiss = { deleteAllDataModalFinalVisible = false },
        onDelete = {
            onDeleteAllUserData()
        }
    )

    DeleteModal(
        title = stringResource(R.string.delete_all_cloud_data_question),
        description = stringResource(
            R.string.delete_all_user_cloud_data_warning,
            stringResource(R.string.your_account)
        ),
        visible = deleteCloudDataModalVisible,
        dismiss = { deleteCloudDataModalVisible = false },
        onDelete = {
            onDeleteCloudUserData()
            deleteCloudDataModalVisible = false
        }
    )

    ProgressModal(
        title = stringResource(R.string.exporting_data),
        description = stringResource(R.string.exporting_data_description),
        visible = progressState
    )

    DriveSetupModal(
        step = driveSetupStep,
        onPick = onPickDriveFile,
        onClose = onCloseDriveSetup,
    )

    if (rivyUi != null) {
        AppearanceModals(
            open = appearanceModal,
            state = rivyUi,
            onEvent = onRivyUiEvent,
            onClose = { appearanceModal = AppearanceModal.NONE },
        )
    }
}

private enum class AppearanceModal { NONE, NAV_STYLE, TAB_ORDER, PALETTE }

@Composable
private fun RivyAppearance(
    state: RivyUiState,
    onOpen: (AppearanceModal) -> Unit,
    onSetAutoHide: (Boolean) -> Unit,
) {
    SettingsDefaultButton(
        icon = R.drawable.ic_vue_design_color_swatch,
        text = stringResource(R.string.rivy_palette),
        iconPadding = 6.dp,
        description = stringResource(state.palette.label()),
    ) {
        onOpen(AppearanceModal.PALETTE)
    }

    Spacer(Modifier.height(12.dp))

    SettingsDefaultButton(
        icon = R.drawable.ic_home,
        text = stringResource(R.string.rivy_nav_style),
        iconPadding = 6.dp,
        description = stringResource(state.navStyle.label()),
    ) {
        onOpen(AppearanceModal.NAV_STYLE)
    }

    if (state.navStyle != NavStyle.CLASSIC) {
        Spacer(Modifier.height(12.dp))

        SettingsDefaultButton(
            icon = R.drawable.ic_accounts,
            text = stringResource(R.string.rivy_tab_order),
            iconPadding = 6.dp,
            description = state.tabOrder.map { stringResource(it.label()) }.joinToString(" · "),
        ) {
            onOpen(AppearanceModal.TAB_ORDER)
        }
    }

    Spacer(Modifier.height(12.dp))

    AppSwitch(
        lockApp = state.autoHide,
        onSetLockApp = onSetAutoHide,
        text = stringResource(R.string.rivy_nav_auto_hide),
        description = stringResource(R.string.rivy_nav_auto_hide_description),
        icon = R.drawable.ic_hide_m,
    )
}

@Composable
private fun BoxWithConstraintsScope.AppearanceModals(
    open: AppearanceModal,
    state: RivyUiState,
    onEvent: (SettingsEvent) -> Unit,
    onClose: () -> Unit,
) {
    IvyModal(
        id = null,
        visible = open != AppearanceModal.NONE,
        dismiss = onClose,
        PrimaryAction = {
            ModalSet(label = stringResource(R.string.rivy_done), onClick = onClose)
        },
    ) {
        Spacer(Modifier.height(32.dp))

        ModalTitle(
            text = stringResource(
                when (open) {
                    AppearanceModal.TAB_ORDER -> R.string.rivy_tab_order
                    AppearanceModal.PALETTE -> R.string.rivy_palette
                    else -> R.string.rivy_nav_style
                }
            )
        )

        Spacer(Modifier.height(16.dp))

        when (open) {
            AppearanceModal.NAV_STYLE -> NavStyle.entries.forEach { style ->
                ChoiceRow(
                    text = stringResource(style.label()),
                    selected = style == state.navStyle,
                ) { onEvent(SettingsEvent.SetNavStyle(style)) }
            }

            AppearanceModal.PALETTE -> Palette.entries.forEach { palette ->
                ChoiceRow(
                    text = stringResource(palette.label()),
                    selected = palette == state.palette,
                ) { onEvent(SettingsEvent.SetPalette(palette)) }
            }

            AppearanceModal.TAB_ORDER -> {
                Text(
                    modifier = Modifier.padding(horizontal = 32.dp),
                    text = stringResource(R.string.rivy_tab_order_description),
                    style = UI.typo.c.style(color = UI.colors.gray, fontWeight = FontWeight.SemiBold),
                )
                Spacer(Modifier.height(8.dp))
                TabOrderList(
                    order = state.tabOrder,
                    onMove = { tab, by -> onEvent(SettingsEvent.MoveTab(tab, by)) },
                )
            }

            AppearanceModal.NONE -> Unit
        }

        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun ChoiceRow(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clip(UI.shapes.r4)
            .background(if (selected) UI.colors.primary.copy(alpha = 0.14f) else UI.colors.medium)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            modifier = Modifier.weight(1f),
            text = text,
            style = UI.typo.b2.style(
                color = if (selected) UI.colors.primary else UI.colors.pureInverse,
                fontWeight = FontWeight.Bold,
            ),
        )
        if (selected) {
            IvyIcon(icon = R.drawable.ic_check, tint = UI.colors.primary)
        }
    }
}

@Composable
private fun TabOrderList(
    order: List<MainTab>,
    onMove: (MainTab, Int) -> Unit,
) {
    val currentOrder by rememberUpdatedState(order)
    val stepPx = with(LocalDensity.current) { TAB_ROW_STEP.toPx() }
    var dragging by remember { mutableStateOf<MainTab?>(null) }
    var dragOffset by remember { mutableFloatStateOf(0f) }

    order.forEach { tab ->
        key(tab) {
            val active = tab == dragging
            Row(
                modifier = Modifier
                    .zIndex(if (active) 1f else 0f)
                    .graphicsLayer { translationY = if (active) dragOffset else 0f }
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp)
                    .height(TAB_ROW_HEIGHT)
                    .shadow(if (active) 8.dp else 0.dp, UI.shapes.r4)
                    .clip(UI.shapes.r4)
                    .background(if (active) UI.colors.primary.copy(alpha = 0.14f) else UI.colors.medium)
                    .pointerInput(tab) {
                        detectDragGesturesAfterLongPress(
                            onDragStart = {
                                dragging = tab
                                dragOffset = 0f
                            },
                            onDragEnd = { dragging = null },
                            onDragCancel = { dragging = null },
                            onDrag = { change, amount ->
                                change.consume()
                                dragOffset += amount.y
                                val index = currentOrder.indexOf(tab)
                                if (dragOffset > stepPx / 2 && index < currentOrder.lastIndex) {
                                    onMove(tab, 1)
                                    dragOffset -= stepPx
                                } else if (dragOffset < -stepPx / 2 && index > 0) {
                                    onMove(tab, -1)
                                    dragOffset += stepPx
                                }
                            },
                        )
                    }
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    modifier = Modifier.weight(1f),
                    text = stringResource(tab.label()),
                    style = UI.typo.b2.style(color = UI.colors.pureInverse, fontWeight = FontWeight.Bold),
                )
                IvyIcon(
                    icon = R.drawable.ic_hamburger,
                    tint = UI.colors.gray,
                )
            }
        }
    }
}

private val TAB_ROW_HEIGHT = 52.dp
private val TAB_ROW_STEP = 60.dp

private fun NavStyle.label(): Int = when (this) {
    NavStyle.CLASSIC -> R.string.rivy_nav_classic
    NavStyle.PILL_FLOAT -> R.string.rivy_nav_pill_float
    NavStyle.PILL_LABEL -> R.string.rivy_nav_pill_label
    NavStyle.PILL_CENTER -> R.string.rivy_nav_pill_center
}

private fun Palette.label(): Int = when (this) {
    Palette.ORIGINAL -> R.string.rivy_palette_original
    Palette.RIVY -> R.string.rivy_palette_rivy
    Palette.WALLPAPER -> R.string.rivy_palette_wallpaper
}

private fun MainTab.label(): Int = when (this) {
    MainTab.HOME -> R.string.home
    MainTab.ACCOUNTS -> R.string.accounts
    MainTab.REPORTS -> R.string.reports
    MainTab.SETTINGS -> R.string.settings
}

@Composable
private fun BoxWithConstraintsScope.DriveSetupModal(
    step: Int,
    onPick: (Int) -> Unit,
    onClose: () -> Unit,
) {
    val first = step != 2
    IvyModal(
        id = null,
        visible = step != 0,
        dismiss = onClose,
        SecondaryActions = {
            ModalSkip(
                text = stringResource(if (first) R.string.drive_setup_cancel else R.string.skip),
                onClick = onClose,
            )
        },
        PrimaryAction = {
            ModalSet(label = stringResource(R.string.drive_setup_pick)) {
                onPick(if (first) 1 else 2)
            }
        },
    ) {
        Spacer(Modifier.height(32.dp))

        Text(
            modifier = Modifier.padding(horizontal = 32.dp),
            text = stringResource(if (first) R.string.drive_setup_step1 else R.string.drive_setup_step2),
            style = UI.typo.c.style(color = UI.colors.primary, fontWeight = FontWeight.ExtraBold),
        )

        Spacer(Modifier.height(8.dp))

        ModalTitle(
            text = stringResource(if (first) R.string.drive_setup_title1 else R.string.drive_setup_title2)
        )

        Spacer(Modifier.height(16.dp))

        Text(
            modifier = Modifier.padding(horizontal = 32.dp),
            text = stringResource(if (first) R.string.drive_setup_body1 else R.string.drive_setup_body2),
            style = UI.typo.b2.style(color = UI.colors.pureInverse, fontWeight = FontWeight.Medium),
        )

        Spacer(Modifier.height(32.dp))
    }
}

@Composable
private fun StartDateOfMonth(
    startDateOfMonth: Int,
    onClick: () -> Unit
) {
    SettingsButtonRow(
        onClick = onClick
    ) {
        Spacer(Modifier.width(12.dp))

        IvyIconScaled(
            icon = R.drawable.ic_custom_calendar_m,
            tint = UI.colors.pureInverse,
            iconScale = IconScale.M,
            padding = 2.dp
        )

        Spacer(Modifier.width(8.dp))

        Text(
            modifier = Modifier.padding(vertical = 20.dp),
            text = stringResource(R.string.start_date_of_month),
            style = UI.typo.b2.style(
                color = UI.colors.pureInverse,
                fontWeight = FontWeight.Bold
            )
        )

        Spacer(Modifier.weight(1f))

        Text(
            text = startDateOfMonth.toString(),
            style = UI.typo.nB2.style(
                fontWeight = FontWeight.ExtraBold,
                color = UI.colors.pureInverse
            )
        )

        Spacer(Modifier.width(32.dp))
    }
}

@Composable
private fun CustomFeatures(
    onClick: () -> Unit
) {
    SettingsButtonRow(
        onClick = onClick
    ) {
        Spacer(Modifier.width(12.dp))

        IvyIconScaled(
            icon = R.drawable.ic_custom_programming_m,
            tint = UI.colors.pureInverse,
            iconScale = IconScale.M,
            padding = 0.dp
        )

        Spacer(modifier = Modifier.width(8.dp))

        Text(
            modifier = Modifier.padding(vertical = 20.dp),
            text = stringResource(R.string.advanced_features),
            style = UI.typo.b2.style(
                color = UI.colors.pureInverse,
                fontWeight = FontWeight.Bold
            )
        )
    }
}

@Composable
private fun AppThemeButton(
    @DrawableRes icon: Int,
    label: String,
    onClick: () -> Unit
) {
    SettingsPrimaryButton(
        icon = icon,
        text = label,
        backgroundGradient = Gradient.solid(UI.colors.medium),
        textColor = UI.colors.pureInverse,
        iconPadding = 6.dp,
        description = stringResource(R.string.tap_to_switch_theme),
        onClick = onClick
    )
}

@Composable
private fun AppSwitch(
    lockApp: Boolean,
    onSetLockApp: (Boolean) -> Unit,
    text: String,
    icon: Int,
    description: String = "",
) {
    SettingsButtonRow(
        onClick = {
            onSetLockApp(!lockApp)
        }
    ) {
        Spacer(Modifier.width(12.dp))

        IvyIconScaled(
            icon = icon,
            tint = UI.colors.pureInverse,
            iconScale = IconScale.M,
            padding = 0.dp
        )

        Spacer(Modifier.width(8.dp))

        Column(
            Modifier
                .weight(1f)
                .padding(top = 20.dp, bottom = 20.dp, end = 8.dp)
        ) {
            Text(
                text = text,
                style = UI.typo.b2.style(
                    color = UI.colors.pureInverse,
                    fontWeight = FontWeight.Bold
                )
            )
            if (description.isNotEmpty()) {
                Text(
                    modifier = Modifier.padding(end = 8.dp),
                    text = description,
                    style = UI.typo.nB2.style(
                        color = Gray,
                        fontWeight = FontWeight.Normal
                    ).copy(fontSize = 14.sp)
                )
            }
        }

        // Spacer(Modifier.weight(1f))

        IvySwitch(enabled = lockApp) {
            onSetLockApp(it)
        }

        Spacer(Modifier.width(16.dp))
    }
}

@Composable
private fun AccountCard(
    nameLocalAccount: String?,
    onCardClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .fillMaxWidth()
            .clip(UI.shapes.r2)
            .background(UI.colors.medium, UI.shapes.r2)
            .clickable {
                onCardClick()
            }
    ) {
        Spacer(Modifier.height(16.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("settings_profile_card"),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Spacer(Modifier.width(24.dp))

            Text(
                text = stringResource(R.string.account_uppercase),
                style = UI.typo.c.style(
                    fontWeight = FontWeight.Black,
                    color = UI.colors.gray
                )
            )
        }

        Spacer(Modifier.height(4.dp))

        AccountCardLocalAccount(
            name = nameLocalAccount,
        )

        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun AccountCardLocalAccount(
    name: String?
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Spacer(Modifier.width(20.dp))
        IvyIconScaled(
            icon = R.drawable.ic_local_account,
            iconScale = IconScale.M
        )

        Spacer(Modifier.width(12.dp))

        Text(
            modifier = Modifier
                .weight(1f)
                .testTag("local_account_name"),
            text = if (!name.isNullOrBlank()) name else stringResource(R.string.anonymous),
            style = UI.typo.b2.style(
                fontWeight = FontWeight.Bold
            )
        )

        Spacer(Modifier.width(12.dp))
    }
}

@Composable
private fun DriveExport(
    status: DriveExportStatus,
    showStatus: Boolean,
    recentConnected: Boolean,
    onAddRecent: () -> Unit,
    onSetShowStatus: (Boolean) -> Unit,
    onConnect: () -> Unit,
    onRun: () -> Unit,
    onDisconnect: () -> Unit,
) {
    val files = if (recentConnected) "rivy.csv, rivy-terbaru.csv" else "rivy.csv"
    val description = when (status) {
        DriveExportStatus.NotConnected -> stringResource(R.string.drive_export_not_connected)
        DriveExportStatus.Pending -> stringResource(R.string.drive_export_pending)
        is DriveExportStatus.Success -> stringResource(R.string.drive_export_success_files, files, status.at)
        is DriveExportStatus.Failed -> stringResource(R.string.drive_export_failed, status.reason)
    }
    SettingsDefaultButton(
        icon = R.drawable.ic_export_csv,
        text = stringResource(R.string.drive_export),
        iconPadding = 6.dp,
        description = description,
    ) {
        if (status == DriveExportStatus.NotConnected) onConnect() else onRun()
    }

    if (status != DriveExportStatus.NotConnected) {
        if (!recentConnected) {
            Spacer(Modifier.height(12.dp))

            SettingsDefaultButton(
                icon = R.drawable.ic_export_csv,
                text = stringResource(R.string.drive_setup_add_recent),
                iconPadding = 6.dp,
            ) {
                onAddRecent()
            }
        }

        Spacer(Modifier.height(12.dp))

        AppSwitch(
            lockApp = showStatus,
            onSetLockApp = onSetShowStatus,
            text = stringResource(R.string.drive_show_status),
            description = stringResource(R.string.drive_show_status_description),
            icon = R.drawable.ic_vue_weather_cloud,
        )

        Spacer(Modifier.height(12.dp))

        SettingsDefaultButton(
            icon = R.drawable.ic_delete,
            text = stringResource(R.string.drive_export_disconnect),
            iconPadding = 6.dp,
        ) {
            onDisconnect()
        }
    }
}

@Composable
private fun ExportCSV(
    onExportToCSV: () -> Unit
) {
    SettingsDefaultButton(
        icon = R.drawable.ic_vue_pc_printer,
        text = stringResource(R.string.export_to_csv),
        iconPadding = 6.dp,
        description = stringResource(R.string.do_not_use_for_backup_purposes)
    ) {
        onExportToCSV()
    }
}


@Composable
private fun SettingsPrimaryButton(
    @DrawableRes icon: Int,
    text: String,
    hasShadow: Boolean = false,
    backgroundGradient: Gradient = Gradient.solid(UI.colors.medium),
    textColor: Color = White,
    iconPadding: Dp = 0.dp,
    description: String? = null,
    onClick: () -> Unit
) {
    SettingsButtonRow(
        hasShadow = hasShadow,
        backgroundGradient = backgroundGradient,
        onClick = onClick
    ) {
        Spacer(Modifier.width(12.dp))

        IvyIconScaled(
            icon = icon,
            tint = textColor,
            iconScale = IconScale.M,
            padding = iconPadding
        )

        Spacer(Modifier.width(8.dp))

        Column(
            Modifier
                .weight(1f)
                .padding(top = 20.dp, bottom = 20.dp, end = 8.dp)
        ) {
            Text(
                text = text,
                style = UI.typo.b2.style(
                    color = textColor,
                    fontWeight = FontWeight.Bold,
                )
            )
            if (!description.isNullOrEmpty()) {
                Text(
                    modifier = Modifier.padding(end = 8.dp),
                    text = description,
                    style = UI.typo.nB2.style(
                        color = Gray,
                        fontWeight = FontWeight.Normal
                    ).copy(fontSize = 14.sp)
                )
            }
        }
    }
}

@Composable
private fun SettingsButtonRow(
    onClick: (() -> Unit)?,
    hasShadow: Boolean = false,
    backgroundGradient: Gradient = Gradient.solid(UI.colors.medium),
    content: @Composable RowScope.() -> Unit
) {
    Row(
        modifier = Modifier
            .padding(horizontal = 16.dp)
            .thenIf(hasShadow) {
                drawColoredShadow(color = backgroundGradient.startColor)
            }
            .fillMaxWidth()
            .clip(UI.shapes.r4)
            .background(backgroundGradient.asHorizontalBrush(), UI.shapes.r4)
            .thenIf(onClick != null) {
                clickable {
                    onClick?.invoke()
                }
            },
        verticalAlignment = Alignment.CenterVertically
    ) {
        content()
    }
}

@Composable
private fun AccountCardButton(
    @DrawableRes icon: Int,
    text: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .clip(UI.shapes.rFull)
            .background(UI.colors.pure, UI.shapes.rFull)
            .clickable {
                onClick()
            },
        verticalAlignment = Alignment.CenterVertically
    ) {
        Spacer(Modifier.width(12.dp))

        IvyIconScaled(
            icon = icon,
            iconScale = IconScale.M
        )

        Spacer(Modifier.width(4.dp))

        Text(
            modifier = Modifier
                .padding(vertical = 10.dp),
            text = text,
            style = UI.typo.b2.style(
                fontWeight = FontWeight.Bold,
                color = UI.colors.pureInverse
            )
        )

        Spacer(Modifier.width(24.dp))
    }
}

@Composable
private fun CurrencyButton(
    currency: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clip(UI.shapes.r4)
            .border(2.dp, UI.colors.medium, UI.shapes.r4)
            .clickable {
                onClick()
            },
        verticalAlignment = Alignment.CenterVertically
    ) {
        Spacer(Modifier.width(12.dp))

        IvyIconScaled(
            icon = R.drawable.ic_currency,
            iconScale = IconScale.M,
            padding = 0.dp
        )

        Spacer(Modifier.width(8.dp))

        Text(
            modifier = Modifier.padding(vertical = 20.dp),
            text = stringResource(R.string.set_currency),
            style = UI.typo.b2.style(
                color = UI.colors.pureInverse,
                fontWeight = FontWeight.Bold
            )
        )

        Spacer(Modifier.weight(1f))

        Text(
            text = currency,
            style = UI.typo.b1.style(
                color = UI.colors.pureInverse,
                fontWeight = FontWeight.ExtraBold
            )
        )

        Spacer(Modifier.height(4.dp))

        IvyIconScaled(
            icon = R.drawable.ic_arrow_right,
            iconScale = IconScale.M
        )

        Spacer(Modifier.width(24.dp))
    }
}

@Composable
private fun SettingsSectionDivider(
    text: String,
    color: Color = Gray
) {
    Column {
        Spacer(Modifier.height(32.dp))

        Text(
            modifier = Modifier.padding(start = 32.dp),
            text = text,
            style = UI.typo.b2.style(
                color = color,
                fontWeight = FontWeight.Bold
            )
        )
    }
}

@Composable
private fun SettingsDefaultButton(
    @DrawableRes icon: Int,
    text: String,
    iconPadding: Dp = 0.dp,
    description: String? = null,
    onClick: () -> Unit,
) {
    SettingsPrimaryButton(
        icon = icon,
        text = text,
        backgroundGradient = Gradient.solid(UI.colors.medium),
        textColor = UI.colors.pureInverse,
        iconPadding = iconPadding,
        description = description
    ) {
        onClick()
    }
}

@ExperimentalFoundationApi
@Preview
@Composable
private fun Preview(theme: Theme = Theme.LIGHT) {
    IvyWalletPreview(theme) {
        UI(
            nameLocalAccount = null,
            theme = Theme.AUTO,
            onSwitchTheme = {},
            lockApp = false,
            currencyCode = "BGN",
            onSetCurrency = {},
            languageOptionVisible = true
        )
    }
}

/** For screenshot testing */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SettingsUiTest(isDark: Boolean) {
    val theme = when (isDark) {
        true -> Theme.DARK
        false -> Theme.LIGHT
    }
    Preview(theme)
}