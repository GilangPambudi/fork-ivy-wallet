package com.ivy.domain.usecase.csv

import arrow.core.Some
import com.ivy.base.TestDispatchersProvider
import com.ivy.base.time.impl.TestTimeConverter
import com.ivy.data.file.FileSystem
import com.ivy.data.model.Transaction
import com.ivy.data.model.getFromAccount
import com.ivy.data.model.getToAccount
import com.ivy.data.model.primitive.AssetCode
import com.ivy.data.model.primitive.NotBlankTrimmedString
import com.ivy.data.model.testing.account
import com.ivy.data.model.testing.category
import com.ivy.data.model.testing.transaction
import com.ivy.data.repository.AccountRepository
import com.ivy.data.repository.CategoryRepository
import com.ivy.data.repository.TransactionRepository
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.list
import io.kotest.property.arbitrary.next
import io.kotest.property.checkAll
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import java.time.Instant

class ExportCsvUseCasePropertyTest {

    private val accountRepository = mockk<AccountRepository>()
    private val categoryRepository = mockk<CategoryRepository>(relaxed = true)
    private val transactionRepository = mockk<TransactionRepository>()
    private val fileSystem = mockk<FileSystem>()
    private val timeConverter = TestTimeConverter()

    private lateinit var useCase: ExportCsvUseCase

    @Before
    fun setup() {
        useCase = ExportCsvUseCase(
            accountRepository = accountRepository,
            categoryRepository = categoryRepository,
            transactionRepository = transactionRepository,
            dispatchers = TestDispatchersProvider,
            fileSystem = fileSystem,
            timeConverter = timeConverter
        )
    }

    @Test
    fun `property - num of row and columns matches the format`() = runTest {
        checkAll(Arb.list(Arb.transaction())) { trns ->
            // given
            val accounts = trns.flatMap {
                listOfNotNull(it.getFromAccount(), it.getToAccount())
            }.map {
                Arb.account(accountId = Some(it)).next()
            }
            coEvery { accountRepository.findAll() } returns accounts
            val categories = trns
                .mapNotNull(Transaction::category)
                .map {
                    Arb.category(categoryId = Some(it)).next()
                }.run {
                    if (isNotEmpty()) {
                        drop(Arb.int(indices).bind()).shuffled()
                    } else {
                        this
                    }
                }
            coEvery { categoryRepository.findAll() } returns categories

            // when
            val csv = useCase.exportCsv { trns }

            // then
            val rows = ReadCsvUseCase().readCsv(csv)
            rows.size shouldBe trns.size + 1 // +1 for the header
            rows.forEach { row ->
                // Matches the expected # of columns
                val hasExpectedNumOfColumns = row.size == IvyCsvRow.Columns.size
                if (!hasExpectedNumOfColumns) {
                    println("(${row.size} cols) $row")
                }
                hasExpectedNumOfColumns shouldBe true
            }
        }
    }

    @Test
    fun `balance rows come first with plain numbers`() = runTest {
        val account = Arb.account().next().copy(
            name = NotBlankTrimmedString.unsafe("BCA, utama"),
            asset = AssetCode.unsafe("IDR"),
        )
        coEvery { accountRepository.findAll() } returns listOf(account)

        val csv = useCase.exportCsv(
            plainNumbers = true,
            balances = listOf(account to 1234567.5, account to -2500.0),
            balancesTime = Instant.parse("2026-11-26T03:32:00Z"),
        ) { emptyList() }

        val rows = ReadCsvUseCase().readCsv(csv)
        rows.size shouldBe 3
        rows[1] shouldBe listOf(
            "2026-11-26T03:32:00", "Saldo", "", "BCA, utama", "1234567.50", "IDR", "BALANCE"
        ) + List(IvyCsvRow.Columns.size - 7) { "" }
        rows[2][4] shouldBe "-2500.00"
    }
}