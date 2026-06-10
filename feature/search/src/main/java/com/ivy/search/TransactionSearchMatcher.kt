package com.ivy.search

import com.ivy.data.model.CategoryId
import com.ivy.data.model.TagId
import com.ivy.data.model.Transaction
import com.ivy.data.model.Transfer
import com.ivy.data.model.getFromValue
import com.ivy.data.model.primitive.NotBlankTrimmedString
import java.math.BigDecimal

internal data class SearchMetadata(
    val categoryNames: Map<CategoryId, String>,
    val tagNames: Map<TagId, String>,
)

internal fun Transaction.matchesSearchQuery(
    query: TransactionSearchQuery,
    metadata: SearchMetadata,
): Boolean {
    return when {
        query.isBlank -> true
        query.invalidAmountQuery -> false
        else -> {
            val matchesText = query.text?.let { text ->
                title.matchesText(text) ||
                        description.matchesText(text) ||
                        category?.let { metadata.categoryNames[it].matchesText(text) } == true ||
                        tags.any { metadata.tagNames[it].matchesText(text) }
            } == true

            val matchesAmount = query.amountFilter?.let { amountFilter ->
                searchableAmounts().any(amountFilter::matches)
            } == true

            matchesText || matchesAmount
        }
    }
}

internal data class TransactionSearchQuery(
    val text: String?,
    val amountFilter: AmountFilter?,
    val invalidAmountQuery: Boolean,
) {
    val isBlank: Boolean = text == null && amountFilter == null && !invalidAmountQuery

    companion object {
        fun parse(rawQuery: String): TransactionSearchQuery {
            val normalizedQuery = rawQuery.trim().lowercase()
            return when {
                normalizedQuery.isBlank() -> TransactionSearchQuery(
                    text = null,
                    amountFilter = null,
                    invalidAmountQuery = false
                )

                normalizedQuery.startsWith(AMOUNT_PREFIX) -> {
                    val amountFilter = normalizedQuery.removePrefix(AMOUNT_PREFIX)
                        .parseAmountFilter()
                    TransactionSearchQuery(
                        text = null,
                        amountFilter = amountFilter,
                        invalidAmountQuery = amountFilter == null
                    )
                }

                else -> {
                    val amountFilter = normalizedQuery.parseFlexibleAmount()
                        ?.let(AmountFilter::Exact)
                    TransactionSearchQuery(
                        text = normalizedQuery.removePrefix("#").takeIf { amountFilter == null },
                        amountFilter = amountFilter,
                        invalidAmountQuery = false
                    )
                }
            }
        }

        private const val AMOUNT_PREFIX = "amount:"
    }
}

internal sealed interface AmountFilter {
    fun matches(amount: BigDecimal): Boolean

    data class Exact(val amount: BigDecimal) : AmountFilter {
        override fun matches(amount: BigDecimal): Boolean = amount.compareTo(this.amount) == 0
    }

    data class Range(
        val minAmount: BigDecimal,
        val maxAmount: BigDecimal,
    ) : AmountFilter {
        override fun matches(amount: BigDecimal): Boolean {
            return amount >= minAmount && amount <= maxAmount
        }
    }
}

private fun String?.matchesText(query: String): Boolean {
    return this?.lowercase()?.contains(query) == true
}

private fun NotBlankTrimmedString?.matchesText(query: String): Boolean {
    return this?.value.matchesText(query)
}

private fun Transaction.searchableAmounts(): List<BigDecimal> {
    val fromAmount = getFromValue().amount.value.toBigDecimal()
    return when (this) {
        is Transfer -> listOf(fromAmount, toValue.amount.value.toBigDecimal())
        else -> listOf(fromAmount)
    }
}

private fun String.parseAmountFilter(): AmountFilter? {
    val rangeParts = split("-")
    return when (rangeParts.size) {
        1 -> parseFlexibleAmount()?.let(AmountFilter::Exact)
        2 -> parseAmountRange(rangeParts)

        else -> null
    }
}

private fun parseAmountRange(rangeParts: List<String>): AmountFilter.Range? {
    val min = rangeParts[0].parseFlexibleAmount()
    val max = rangeParts[1].parseFlexibleAmount()
    return if (min != null && max != null && min <= max) {
        AmountFilter.Range(min, max)
    } else {
        null
    }
}

private fun String.parseFlexibleAmount(): BigDecimal? {
    val amount = trim().replace(" ", "")
    if (amount.isBlank() || !amount.matches(Regex("""\d[\d,.]*"""))) return null

    val commaIndex = amount.lastIndexOf(',')
    val dotIndex = amount.lastIndexOf('.')
    val normalized = when {
        commaIndex >= 0 && dotIndex >= 0 -> {
            val decimalSeparator = if (commaIndex > dotIndex) ',' else '.'
            val groupingSeparator = if (decimalSeparator == ',') "." else ","
            amount
                .replace(groupingSeparator, "")
                .replace(decimalSeparator, '.')
        }

        commaIndex >= 0 -> amount.normalizeSingleSeparator(',')
        dotIndex >= 0 -> amount.normalizeSingleSeparator('.')
        else -> amount
    }

    return normalized.toBigDecimalOrNull()?.takeIf { it > BigDecimal.ZERO }
}

private fun String.normalizeSingleSeparator(separator: Char): String {
    val separatorCount = count { it == separator }
    val digitsAfterSeparator = length - lastIndexOf(separator) - 1
    val isDecimalSeparator = separatorCount == 1 && digitsAfterSeparator in 1..2
    return if (isDecimalSeparator) {
        replace(separator, '.')
    } else {
        replace(separator.toString(), "")
    }
}
