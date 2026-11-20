package dev.leafledger.core

import androidx.room.Transactor
import androidx.room.useWriterConnection
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString

class LedgerRepository(private val database: LedgerDatabase) {
    private val dao = database.dao()
    val snapshots: Flow<LedgerSnapshot> = dao.observeChanges().map { dao.snapshot() }
    suspend fun snapshot() = dao.snapshot()
    private suspend fun <T> write(block: suspend () -> T): T = database.useWriterConnection {
        it.withTransaction(Transactor.SQLiteTransactionType.IMMEDIATE) { block() }
    }
    suspend fun initialize() = write {
        if (dao.preferences().none { it.key == "initialized" }) {
            dao.preference(Preference("initialized", "true"))
        }
        val previous=dao.categories()
        flatCategories(previous).forEach { row -> if(previous.find { it.id==row.id } != row) dao.category(row) }
    }
    suspend fun saveAccount(row: Account) = write {
        require(row.name.isNotBlank() && row.name.length <= 40) { "账户名称需为 1–40 个字符" }
        require(dao.accounts().none { it.id != row.id && it.name == row.name }) { "账户名称已存在" }; dao.account(row)
    }
    suspend fun saveChannel(row: Channel) = write {
        require(row.name.isNotBlank() && row.name.length <= 40) { "渠道名称需为 1–40 个字符" }
        require(dao.channels().none { it.id != row.id && it.name == row.name }) { "渠道名称已存在" }; dao.channel(row)
    }
    suspend fun saveCategory(input: Category) = write {
        val row=input.copy(parentId=null)
        val all = dao.categories()
        LedgerValidation.category(row, all)
        require(all.none { it.id != row.id && it.name == row.name && it.type == row.type }) { "该收支类型下已存在同名分类" }
        val updated = all.filterNot { it.id == row.id } + row
        updated.forEach { LedgerValidation.category(it, updated) }
        dao.entries().filter { it.categoryId == row.id }.forEach {
            require(row.type == if (it.type == EntryType.INCOME) EntryType.INCOME else EntryType.EXPENSE) { "已有流水的分类不能改变收支类型" }
        }
        dao.category(row)
    }
    suspend fun setPreference(key: String, value: String) = dao.preference(Preference(key, value))
    suspend fun deleteMerchantRule(merchant: String) = dao.deleteRule(merchant)

    suspend fun removeCategory(id: String, replacementId: String? = null) = write {
        val snapshot = dao.snapshot()
        val source = snapshot.categories.firstOrNull { it.id == id } ?: error("分类已不存在")
        require(snapshot.categories.none { it.parentId == id }) { "请先处理此分类的旧子分类" }
        val used = snapshot.entries.any { it.categoryId == id } || snapshot.rules.any { it.categoryId == id }
        if (replacementId == null) {
            require(!used) { "此分类正在使用，请选择迁移目标" }
        } else {
            require(snapshot.categories.any {
                it.id == replacementId && it.id != id && it.active && it.type == source.type
            }) { "请选择另一项已启用的同类型分类" }
            dao.moveEntryCategories(id, replacementId)
            dao.moveRuleCategories(id, replacementId)
        }
        EntryType.entries.forEach { type ->
            updateRecentAfterRemoval(snapshot.preferences, recentCategoryKey(type), id, replacementId)
        }
        dao.deleteCategory(id)
    }

    suspend fun removeChannel(id: String, replacementId: String? = null) = write {
        val snapshot = dao.snapshot()
        require(snapshot.channels.any { it.id == id }) { "支付渠道已不存在" }
        if (replacementId == null) {
            require(snapshot.entries.none { it.channelId == id }) { "此渠道正在使用，请选择迁移目标" }
        } else {
            require(snapshot.channels.any { it.id == replacementId && it.id != id && it.active }) {
                "请选择另一项已启用的支付渠道"
            }
            dao.moveEntryChannels(id, replacementId)
        }
        updateRecentAfterRemoval(snapshot.preferences, RECENT_CHANNELS, id, replacementId)
        if (snapshot.preferences.any { it.key == LAST_MANUAL_CHANNEL && it.value == id }) {
            dao.preference(Preference(LAST_MANUAL_CHANNEL, replacementId.orEmpty()))
        }
        dao.deleteChannel(id)
    }

    private suspend fun updateRecentAfterRemoval(
        preferences: List<Preference>, key: String, sourceId: String, targetId: String?
    ) {
        val recent = recentIds(preferences, key)
        if (sourceId in recent) {
            val updated = recent.mapNotNull { if (it == sourceId) targetId else it }.distinct()
            dao.preference(Preference(key, LedgerJson.encodeToString(updated)))
        }
    }

    suspend fun saveEntry(input: LedgerEntry, rememberMerchant: Boolean = false): String = write {
        require(input.draftId == null || dao.entries().any { it.id == input.id && it.draftId == input.draftId }) { "请通过确认入口入账" }
        saveValidated(input, rememberMerchant)
    }
    suspend fun saveManualEntry(input: LedgerEntry): String = write {
        require(input.draftId == null || dao.entries().any { it.id == input.id && it.draftId == input.draftId }) { "请通过确认入口入账" }
        val row = prepareQuickEntry(input, dao.snapshot())
        val id = saveValidated(row, false)
        // Only genuinely manual records update the next manual record's default.
        if (row.method == CaptureMethod.MANUAL) dao.preference(Preference(LAST_MANUAL_CHANNEL, row.channelId.orEmpty()))
        id
    }
    private suspend fun saveValidated(input: LedgerEntry, rememberMerchant: Boolean): String {
        val s = dao.snapshot()
        val row = if (input.type == EntryType.REFUND && input.refundOf != null) {
            input.copy(categoryId = s.entries.firstOrNull { it.id == input.refundOf }?.categoryId)
        } else input
        LedgerValidation.entry(row, s)
        val previous = s.entries.firstOrNull { it.id == row.id }
        row.accountId?.let { id -> require(s.accounts.any { it.id == id && (it.active || previous?.accountId == id) }) { "账户已停用" } }
        row.channelId?.let { id -> require(s.channels.any { it.id == id && (it.active || previous?.channelId == id) }) { "渠道已停用" } }
        row.categoryId?.let { id -> require(s.categories.any { it.id == id && (it.active || previous?.categoryId == id || row.refundOf != null) }) { "分类已停用" } }
        dao.entry(row)
        row.categoryId?.let { rememberUse(recentCategoryKey(row.type), it, s.preferences) }
        if (row.channelId != null) {
            rememberUse(RECENT_CHANNELS, row.channelId, s.preferences)
        } else if (previous?.channelId != null) {
            // Clearing a historical channel can otherwise change the order inferred from old entries.
            val ordered = (frequentChannels(s).map { it.id } + recentIds(s.preferences, RECENT_CHANNELS)).distinct()
            dao.preference(Preference(RECENT_CHANNELS, LedgerJson.encodeToString(ordered)))
        }
        s.entries.filter { it.refundOf == row.id }.forEach { dao.entry(it.copy(categoryId = row.categoryId)) }
        if (rememberMerchant && row.merchant.isNotBlank() && row.categoryId != null) dao.rule(MerchantRule(row.merchant.trim(), row.categoryId))
        return row.id
    }
    private suspend fun rememberUse(key: String, id: String, preferences: List<Preference>) {
        val ids = (listOf(id) + recentIds(preferences, key)).distinct().take(64)
        dao.preference(Preference(key, LedgerJson.encodeToString(ids)))
    }
    suspend fun deleteEntry(id: String) = write {
        require(dao.entries().none { it.refundOf == id }) { "请先删除该支出关联的退款" }
        dao.unlinkEntry(id)
        dao.deleteEntry(id)
    }

    suspend fun replace(snapshot: LedgerSnapshot) = write {
        LedgerValidation.snapshot(snapshot)
        dao.clearRules(); dao.clearEvidence(); dao.clearEntries(); dao.clearDrafts()
        dao.clearCategories(); dao.clearAccounts(); dao.clearChannels(); dao.clearPreferences()
        snapshot.accounts.forEach { dao.account(it) }; snapshot.channels.forEach { dao.channel(it) }
        flatCategories(snapshot.categories).forEach { dao.category(it) }; snapshot.drafts.forEach { dao.draft(it) }
        snapshot.entries.forEach { dao.entry(it) }; snapshot.evidence.forEach { dao.evidence(it) }
        snapshot.rules.forEach { dao.rule(it) }; snapshot.preferences.forEach { dao.preference(it) }
        dao.preference(Preference("initialized", "true"))
    }
}
