package dev.leafledger.core

import kotlinx.datetime.TimeZone

object LedgerValidation {
    fun category(category: Category, categories: List<Category>) {
        require(category.name.isNotBlank() && category.name.length <= 40) { "分类名称需为 1–40 个字符" }
        require(category.type == EntryType.EXPENSE || category.type == EntryType.INCOME) { "分类仅区分收入和支出" }
        category.parentId?.let { parentId ->
            val parent = categories.firstOrNull { it.id == parentId } ?: error("父分类不存在")
            require(parent.id != category.id && parent.parentId == null && parent.type == category.type) { "仅支持同类型的父子两级分类" }
            require(categories.none { it.parentId == category.id }) { "有子分类的父分类不能移动到其他分类下" }
        }
    }
    fun entry(entry: LedgerEntry, snapshot: LedgerSnapshot) {
        require(entry.amount in 1..Money.MAX) { "请输入有效金额，最多两位小数" }
        require(entry.occurredAt in 0..253402300799999L) { "交易时间无效" }
        require(runCatching { TimeZone.of(entry.zoneId) }.isSuccess) { "交易时区无效" }
        require(entry.note.length <= 2000 && entry.merchant.length <= 200) { "备注或商户名称过长" }
        entry.accountId?.let { id -> require(snapshot.accounts.any { it.id == id }) { "账户不存在" } }
        entry.channelId?.let { id -> require(snapshot.channels.any { it.id == id }) { "支付渠道不存在" } }
        if (entry.type == EntryType.TRANSFER) {
            require(entry.accountId != null && entry.targetAccountId != null && entry.accountId != entry.targetAccountId) { "转账需选择两个不同账户" }
            require(snapshot.accounts.any { it.id == entry.targetAccountId }) { "转入账户不存在" }
            require(entry.categoryId == null && entry.refundOf == null) { "转账不使用收支分类" }
        } else {
            require(entry.targetAccountId == null) { "只有转账可指定转入账户" }
            val cat = snapshot.categories.firstOrNull { it.id == entry.categoryId } ?: error("请选择分类")
            val expected = if (entry.type == EntryType.INCOME) EntryType.INCOME else EntryType.EXPENSE
            require(cat.type == expected) { "分类与流水类型不一致" }
        }
        require(entry.type == EntryType.REFUND || entry.refundOf == null) { "只有退款可关联原支出" }
        if (entry.type == EntryType.REFUND && entry.refundOf != null) {
            val original = snapshot.entries.firstOrNull { it.id == entry.refundOf } ?: error("原支出不存在")
            require(original.type == EntryType.EXPENSE && original.id != entry.id) { "退款仅能关联原支出" }
            require(entry.categoryId == original.categoryId) { "退款分类需与原支出一致" }
            require(entry.occurredAt >= original.occurredAt) { "退款时间不能早于原支出" }
            val refunded = snapshot.entries.filter { it.refundOf == original.id && it.id != entry.id }.sumOf { it.amount }
            require(entry.amount <= original.amount - refunded) { "累计退款不能超过原支出" }
        }
        val linked = snapshot.entries.filter { it.refundOf == entry.id }
        if (linked.isNotEmpty()) {
            require(entry.type == EntryType.EXPENSE && entry.amount >= linked.sumOf { it.amount }) { "原支出不能小于已退款金额或改为其他类型" }
            require(linked.all { it.occurredAt >= entry.occurredAt }) { "原支出不能晚于退款时间" }
        }
    }
    fun snapshot(s: LedgerSnapshot) {
        fun unique(ids: List<String>) { require(ids.all { it.isNotBlank() } && ids.distinct().size == ids.size) { "备份包含重复或空标识" } }
        unique(s.accounts.map { it.id }); unique(s.channels.map { it.id }); unique(s.categories.map { it.id })
        unique(s.entries.map { it.id }); unique(s.drafts.map { it.id }); unique(s.evidence.map { it.id })
        unique(s.evidence.map { it.fingerprint }); unique(s.rules.map { it.merchant }); unique(s.preferences.map { it.key })
        require(s.entries.mapNotNull { it.draftId }.let { it.distinct().size == it.size }) { "草稿重复入账" }
        require(s.accounts.all { it.name.isNotBlank() && it.name.length <= 40 } && s.channels.all { it.name.isNotBlank() && it.name.length <= 40 }) { "账户或渠道名称无效" }
        s.categories.forEach { category(it, s.categories) }
        s.entries.forEach { entry(it, s) }
        s.drafts.forEach { draft ->
            require(draft.status in listOf("PENDING", "CONFIRMED", "IGNORED", "MERGED")) { "草稿状态无效" }
            LedgerJson.decodeFromString<Candidate>(draft.candidateJson)
        }
        s.entries.mapNotNull { it.draftId }.forEach { id -> require(s.drafts.any { it.id == id && it.status == "CONFIRMED" }) { "流水草稿关联无效" } }
        s.evidence.forEach { e ->
            require(s.drafts.any { it.id == e.draftId }) { "来源草稿不存在" }
            require(e.entryId == null || s.entries.any { it.id == e.entryId }) { "来源流水不存在" }
            require(e.attachmentName == null || Regex("[a-zA-Z0-9_-]+\\.img").matches(e.attachmentName)) { "附件路径无效" }
        }
        s.rules.forEach { r -> require(s.categories.any { it.id == r.categoryId }) { "商户规则分类不存在" } }
    }
}
