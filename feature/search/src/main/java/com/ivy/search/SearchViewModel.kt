package com.ivy.search

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.viewModelScope
import com.ivy.base.legacy.TransactionHistoryItem
import com.ivy.data.model.Category
import com.ivy.data.repository.CategoryRepository
import com.ivy.data.repository.TagRepository
import com.ivy.domain.features.Features
import com.ivy.legacy.datamodel.Account
import com.ivy.legacy.utils.getDefaultFIATCurrency
import com.ivy.legacy.utils.ioThread
import com.ivy.ui.ComposeViewModel
import com.ivy.wallet.domain.action.account.AccountsAct
import com.ivy.wallet.domain.action.settings.BaseCurrencyAct
import com.ivy.wallet.domain.action.transaction.AllTrnsAct
import com.ivy.wallet.domain.action.transaction.TrnsWithDateDivsAct
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.launch
import javax.inject.Inject

@Stable
@HiltViewModel
class SearchViewModel @Inject constructor(
    private val trnsWithDateDivsAct: TrnsWithDateDivsAct,
    private val accountsAct: AccountsAct,
    private val categoryRepository: CategoryRepository,
    private val tagRepository: TagRepository,
    private val baseCurrencyAct: BaseCurrencyAct,
    private val allTrnsAct: AllTrnsAct,
    private val features: Features
) : ComposeViewModel<SearchState, SearchEvent>() {

    private val transactions =
        mutableStateOf<ImmutableList<TransactionHistoryItem>>(persistentListOf())
    private val baseCurrency = mutableStateOf<String>(getDefaultFIATCurrency().currencyCode)
    private val accounts = mutableStateOf<ImmutableList<Account>>(persistentListOf())
    private val categories = mutableStateOf<ImmutableList<Category>>(persistentListOf())
    private val searchQuery = mutableStateOf("")

    @Composable
    fun getShouldShowAccountSpecificColorInTransactions(): Boolean {
        return features.showAccountColorsInTransactions.asEnabledState()
    }

    @Composable
    override fun uiState(): SearchState {
        LaunchedEffect(Unit) {
            search(searchQuery.value)
        }

        return SearchState(
            searchQuery = searchQuery.value,
            transactions = transactions.value,
            baseCurrency = baseCurrency.value,
            accounts = accounts.value,
            categories = categories.value,
            shouldShowAccountSpecificColorInTransactions = getShouldShowAccountSpecificColorInTransactions()
        )
    }

    override fun onEvent(event: SearchEvent) {
        when (event) {
            is SearchEvent.Search -> search(event.query)
        }
    }

    private fun search(query: String) {
        searchQuery.value = query
        val searchQuery = TransactionSearchQuery.parse(query)

        viewModelScope.launch {
            val queryResult = ioThread {
                val baseCurrency = baseCurrencyAct(Unit)
                val allTransactions = allTrnsAct(Unit)
                val categories = categoryRepository.findAll()
                val tags = tagRepository.findByIds(allTransactions.flatMap { it.tags }.distinct())
                val metadata = SearchMetadata(
                    categoryNames = categories.associate { it.id to it.name.value },
                    tagNames = tags.associate { it.id to it.name.value },
                )
                val filteredTransactions = allTransactions
                    .filter { transaction ->
                        transaction.matchesSearchQuery(searchQuery, metadata)
                    }
                SearchResult(
                    transactions = trnsWithDateDivsAct(
                        TrnsWithDateDivsAct.Input(
                            baseCurrency = baseCurrency,
                            transactions = filteredTransactions
                        )
                    ).toImmutableList(),
                    baseCurrency = baseCurrency,
                    accounts = accountsAct(Unit).toImmutableList(),
                    categories = categories.toImmutableList()
                )
            }

            transactions.value = queryResult.transactions
            baseCurrency.value = queryResult.baseCurrency
            accounts.value = queryResult.accounts
            categories.value = queryResult.categories
        }
    }

    private data class SearchResult(
        val transactions: ImmutableList<TransactionHistoryItem>,
        val baseCurrency: String,
        val accounts: ImmutableList<Account>,
        val categories: ImmutableList<Category>,
    )
}
