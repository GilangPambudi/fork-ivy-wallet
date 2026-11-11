package com.ivy.rivy.sync

import com.ivy.data.db.dao.read.AccountDao
import com.ivy.data.db.dao.read.BudgetDao
import com.ivy.data.db.dao.read.CategoryDao
import com.ivy.data.db.dao.read.LoanDao
import com.ivy.data.db.dao.read.LoanRecordDao
import com.ivy.data.db.dao.read.PlannedPaymentRuleDao
import com.ivy.data.db.dao.read.TagAssociationDao
import com.ivy.data.db.dao.read.TagDao
import com.ivy.data.db.dao.read.TransactionDao
import com.ivy.data.db.dao.write.WriteBudgetDao
import com.ivy.data.db.dao.write.WriteLoanDao
import com.ivy.data.db.dao.write.WriteLoanRecordDao
import com.ivy.data.db.dao.write.WritePlannedPaymentRuleDao
import com.ivy.data.db.dao.write.WriteTagAssociationDao
import com.ivy.data.db.dao.write.WriteTransactionDao
import com.ivy.data.db.entity.AccountEntity
import com.ivy.data.db.entity.BudgetEntity
import com.ivy.data.db.entity.CategoryEntity
import com.ivy.data.db.entity.LoanEntity
import com.ivy.data.db.entity.LoanRecordEntity
import com.ivy.data.db.entity.PlannedPaymentRuleEntity
import com.ivy.data.db.entity.TagAssociationEntity
import com.ivy.data.db.entity.TagEntity
import com.ivy.data.db.entity.TransactionEntity
import com.ivy.data.model.AccountId
import com.ivy.data.model.CategoryId
import com.ivy.data.model.TagId
import com.ivy.data.repository.AccountRepository
import com.ivy.data.repository.CategoryRepository
import com.ivy.data.repository.TagRepository
import com.ivy.data.repository.mapper.AccountMapper
import com.ivy.data.repository.mapper.CategoryMapper
import com.ivy.data.repository.mapper.TagMapper
import com.ivy.rivy.inbox.RivyStore
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Everything that is synced. Accounts, categories and tags go through their
 * repositories so the copies the app keeps in memory stay correct.
 */
@Singleton
@Suppress("LongParameterList")
class SyncSources @Inject constructor(
    private val accountDao: AccountDao,
    private val accountRepository: AccountRepository,
    private val accountMapper: AccountMapper,
    private val categoryDao: CategoryDao,
    private val categoryRepository: CategoryRepository,
    private val categoryMapper: CategoryMapper,
    private val transactionDao: TransactionDao,
    private val writeTransactionDao: WriteTransactionDao,
    private val plannedPaymentRuleDao: PlannedPaymentRuleDao,
    private val writePlannedPaymentRuleDao: WritePlannedPaymentRuleDao,
    private val budgetDao: BudgetDao,
    private val writeBudgetDao: WriteBudgetDao,
    private val loanDao: LoanDao,
    private val writeLoanDao: WriteLoanDao,
    private val loanRecordDao: LoanRecordDao,
    private val writeLoanRecordDao: WriteLoanRecordDao,
    private val tagDao: TagDao,
    private val tagRepository: TagRepository,
    private val tagMapper: TagMapper,
    private val tagAssociationDao: TagAssociationDao,
    private val writeTagAssociationDao: WriteTagAssociationDao,
    private val store: RivyStore,
) {
    val all: List<RecordSource> by lazy {
        listOf(
            accounts(),
            categories(),
            transactions(),
            plannedPaymentRules(),
            budgets(),
            loans(),
            loanRecords(),
            tags(),
            tagAssociations(),
            store.source(TYPE_INBOX_ITEM),
            store.source(TYPE_MERCHANT_RULE),
            store.source(TYPE_ACCOUNT_LINK),
        )
    }

    private fun accounts(): RecordSource = EntitySource<AccountEntity>(
        type = "account",
        serializer = AccountEntity.serializer(),
        idOf = { it.id.toString() },
        load = { accountDao.findAll() },
        save = { entities ->
            val accounts = entities.mapNotNull { entity ->
                with(accountMapper) { entity.toDomain() }.getOrNull()
            }
            accountRepository.saveMany(accounts)
        },
        remove = { id -> accountRepository.deleteById(AccountId(UUID.fromString(id))) },
        removeAll = { accountRepository.deleteAll() },
    )

    private fun categories(): RecordSource = EntitySource<CategoryEntity>(
        type = "category",
        serializer = CategoryEntity.serializer(),
        idOf = { it.id.toString() },
        load = { categoryDao.findAll() },
        save = { entities ->
            val categories = entities.mapNotNull { entity ->
                with(categoryMapper) { entity.toDomain() }.getOrNull()
            }
            categoryRepository.saveMany(categories)
        },
        remove = { id -> categoryRepository.deleteById(CategoryId(UUID.fromString(id))) },
        removeAll = { categoryRepository.deleteAll() },
    )

    private fun transactions(): RecordSource = EntitySource<TransactionEntity>(
        type = TYPE_TRANSACTION,
        serializer = TransactionEntity.serializer(),
        idOf = { it.id.toString() },
        load = { transactionDao.findAll() },
        save = { entities -> writeTransactionDao.saveMany(entities) },
        remove = { id -> writeTransactionDao.deleteById(UUID.fromString(id)) },
        removeAll = { writeTransactionDao.deleteAll() },
    )

    private fun plannedPaymentRules(): RecordSource = EntitySource<PlannedPaymentRuleEntity>(
        type = "planned_payment_rule",
        serializer = PlannedPaymentRuleEntity.serializer(),
        idOf = { it.id.toString() },
        load = { plannedPaymentRuleDao.findAll() },
        save = { entities -> writePlannedPaymentRuleDao.saveMany(entities) },
        remove = { id -> writePlannedPaymentRuleDao.deleteById(UUID.fromString(id)) },
        removeAll = { writePlannedPaymentRuleDao.deleteAll() },
    )

    private fun budgets(): RecordSource = EntitySource<BudgetEntity>(
        type = "budget",
        serializer = BudgetEntity.serializer(),
        idOf = { it.id.toString() },
        load = { budgetDao.findAll() },
        save = { entities -> writeBudgetDao.saveMany(entities) },
        remove = { id -> writeBudgetDao.deleteById(UUID.fromString(id)) },
        removeAll = { writeBudgetDao.deleteAll() },
    )

    private fun loans(): RecordSource = EntitySource<LoanEntity>(
        type = "loan",
        serializer = LoanEntity.serializer(),
        idOf = { it.id.toString() },
        load = { loanDao.findAll() },
        save = { entities -> writeLoanDao.saveMany(entities) },
        remove = { id -> writeLoanDao.deleteById(UUID.fromString(id)) },
        removeAll = { writeLoanDao.deleteAll() },
    )

    private fun loanRecords(): RecordSource = EntitySource<LoanRecordEntity>(
        type = "loan_record",
        serializer = LoanRecordEntity.serializer(),
        idOf = { it.id.toString() },
        load = { loanRecordDao.findAll() },
        save = { entities -> writeLoanRecordDao.saveMany(entities) },
        remove = { id -> writeLoanRecordDao.deleteById(UUID.fromString(id)) },
        removeAll = { writeLoanRecordDao.deleteAll() },
    )

    private fun tags(): RecordSource = EntitySource<TagEntity>(
        type = "tag",
        serializer = TagEntity.serializer(),
        idOf = { it.id.toString() },
        load = { tagDao.findAll() },
        save = { entities ->
            for (entity in entities) {
                val tag = with(tagMapper) { entity.toDomain() }.getOrNull()
                if (tag != null) tagRepository.save(tag)
            }
        },
        remove = { id -> tagRepository.deleteById(TagId(UUID.fromString(id))) },
        removeAll = { tagRepository.deleteAll() },
    )

    private fun tagAssociations(): RecordSource = EntitySource<TagAssociationEntity>(
        type = "tag_association",
        serializer = TagAssociationEntity.serializer(),
        idOf = { "${it.tagId}_${it.associatedId}" },
        load = { tagAssociationDao.findAll() },
        save = { entities -> writeTagAssociationDao.save(entities) },
        remove = { id ->
            val parts = id.split("_")
            if (parts.size == 2) {
                writeTagAssociationDao.deleteId(UUID.fromString(parts[0]), UUID.fromString(parts[1]))
            }
        },
        removeAll = { writeTagAssociationDao.deleteAll() },
    )
}
