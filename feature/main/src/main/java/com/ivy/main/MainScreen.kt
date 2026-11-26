package com.ivy.main

import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.BoxWithConstraintsScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ivy.accounts.AccountsTab
import com.ivy.base.model.TransactionType
import com.ivy.home.HomeTab
import com.ivy.legacy.IvyWalletPreview
import com.ivy.legacy.data.model.MainTab
import com.ivy.legacy.ivyWalletCtx
import com.ivy.legacy.rivyui.NavStyle
import com.ivy.legacy.rivyui.RivyUiState
import com.ivy.legacy.utils.onScreenStart
import com.ivy.navigation.EditPlannedScreen
import com.ivy.navigation.EditTransactionScreen
import com.ivy.navigation.MainScreen
import com.ivy.navigation.navigation
import com.ivy.reports.ReportScreen
import com.ivy.settings.SettingsScreen
import com.ivy.wallet.domain.deprecated.logic.model.CreateAccountData
import com.ivy.wallet.ui.theme.modal.edit.AccountModal
import com.ivy.wallet.ui.theme.modal.edit.AccountModalData
import com.ivy.navigation.ReportScreen as ReportDestination

@ExperimentalAnimationApi
@ExperimentalFoundationApi
@Composable
fun BoxWithConstraintsScope.MainScreen(screen: MainScreen) {
    val viewModel: MainViewModel = viewModel()

    val currency by viewModel.currency.observeAsState("")
    val rivyUi by viewModel.rivyUi.collectAsState()

    onScreenStart {
        viewModel.start(screen)
    }

    val ivyContext = ivyWalletCtx()
    UI(
        screen = screen,
        tab = ivyContext.mainTab,
        baseCurrency = currency,
        rivyUi = rivyUi,
        selectTab = viewModel::selectTab,
        onCreateAccount = viewModel::createAccount
    )
}

@ExperimentalAnimationApi
@ExperimentalFoundationApi
@Composable
private fun BoxWithConstraintsScope.UI(
    screen: MainScreen,
    tab: MainTab,

    baseCurrency: String,
    rivyUi: RivyUiState,

    selectTab: (MainTab) -> Unit,
    onCreateAccount: (CreateAccountData) -> Unit,
) {
    val tabs = rivyUi.tabs
    val pagerState = rememberPagerState(
        initialPage = tabs.indexOf(tab).coerceAtLeast(0)
    ) { tabs.size }

    LaunchedEffect(tab, tabs) {
        val page = tabs.indexOf(tab)
        if (page < 0) {
            selectTab(tabs.first())
        } else if (page != pagerState.currentPage) {
            pagerState.animateScrollToPage(page)
        }
    }
    LaunchedEffect(pagerState, tabs) {
        snapshotFlow { pagerState.settledPage }.collect { page ->
            tabs.getOrNull(page)?.let(selectTab)
        }
    }

    var navHidden by remember { mutableStateOf(false) }
    val autoHide by rememberUpdatedState(rivyUi.autoHide)
    val hideOnScroll = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (autoHide) {
                    if (available.y < -HIDE_SCROLL_THRESHOLD) navHidden = true
                    if (available.y > HIDE_SCROLL_THRESHOLD) navHidden = false
                }
                return Offset.Zero
            }
        }
    }
    LaunchedEffect(tab) { navHidden = false }
    val hideProgress by animateFloatAsState(
        targetValue = if (rivyUi.autoHide && navHidden) 1f else 0f,
        label = "nav_hide",
    )

    HorizontalPager(
        state = pagerState,
        modifier = Modifier
            .fillMaxSize()
            .nestedScroll(hideOnScroll),
        key = { tabs[it] },
    ) { page ->
        BoxWithConstraints(Modifier.fillMaxSize()) {
            when (tabs[page]) {
                MainTab.HOME -> HomeTab()
                MainTab.ACCOUNTS -> AccountsTab()
                MainTab.REPORTS -> ReportScreen(screen = ReportDestination)
                MainTab.SETTINGS -> SettingsScreen()
            }
        }
    }

    var accountModalData: AccountModalData? by remember { mutableStateOf(null) }

    val nav = navigation()
    BottomBar(
        tab = tab,
        selectTab = selectTab,

        onAddIncome = {
            nav.navigateTo(
                EditTransactionScreen(
                    initialTransactionId = null,
                    type = TransactionType.INCOME
                )
            )
        },
        onAddExpense = {
            nav.navigateTo(
                EditTransactionScreen(
                    initialTransactionId = null,
                    type = TransactionType.EXPENSE
                )
            )
        },
        onAddTransfer = {
            nav.navigateTo(
                EditTransactionScreen(
                    initialTransactionId = null,
                    type = TransactionType.TRANSFER
                )
            )
        },
        onAddPlannedPayment = {
            nav.navigateTo(
                EditPlannedScreen(
                    type = TransactionType.EXPENSE,
                    plannedPaymentRuleId = null
                )
            )
        },

        showAddAccountModal = {
            accountModalData = AccountModalData(
                account = null,
                balance = 0.0,
                baseCurrency = baseCurrency
            )
        },

        style = rivyUi.navStyle,
        tabs = tabs,
        hideProgress = hideProgress,
        pagePosition = pagerState.currentPage + pagerState.currentPageOffsetFraction,
    )

    AccountModal(
        modal = accountModalData,
        onCreateAccount = onCreateAccount,
        onEditAccount = { _, _ -> },
        dismiss = {
            accountModalData = null
        }
    )
}

private const val HIDE_SCROLL_THRESHOLD = 6f

@ExperimentalAnimationApi
@ExperimentalFoundationApi
@Preview
@Composable
private fun PreviewMainScreen() {
    IvyWalletPreview {
        UI(
            screen = MainScreen,
            tab = MainTab.HOME,
            baseCurrency = "BGN",
            rivyUi = RivyUiState(
                navStyle = NavStyle.CLASSIC,
                tabOrder = RivyUiState.DEFAULT_ORDER,
                autoHide = false,
                palette = com.ivy.legacy.rivyui.Palette.ORIGINAL,
            ),
            selectTab = {},
            onCreateAccount = { }
        )
    }
}
