package dev.leafledger.core

import androidx.room.*
import kotlinx.datetime.*
import kotlinx.serialization.Serializable
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
fun newId(): String = Uuid.random().toString()
fun nowMillis(): Long = Clock.System.now().toEpochMilliseconds()

@Serializable enum class EntryType { EXPENSE, INCOME, TRANSFER, REFUND }
@Serializable enum class CaptureMethod { MANUAL, NOTIFICATION, SHARE, PICKER }
@Serializable enum class Source(val label: String) {
    WECHAT("微信"),
    ALIPAY("支付宝"),
    MEITUAN("美团"),
    DOUYIN("抖音"),
    GRID("国家电网"),
    MOBILE("中国移动"),
    UNIONPAY("云闪付"),
    UNKNOWN("未指定来源")
}

@Serializable @Entity(tableName = "accounts")
data class Account(@PrimaryKey val id: String = newId(), val name: String, val active: Boolean = true, val sort: Int = 0)
@Serializable @Entity(tableName = "channels")
data class Channel(@PrimaryKey val id: String = newId(), val name: String, val active: Boolean = true, val sort: Int = 0)
@Serializable @Entity(tableName = "categories", indices = [Index("parentId")])
data class Category(@PrimaryKey val id: String = newId(), val name: String, val type: EntryType,
    val parentId: String? = null, val active: Boolean = true, val sort: Int = 0)

@Serializable @Entity(tableName = "entries", indices = [Index(value = ["draftId"], unique = true), Index("occurredAt"), Index("refundOf")])
data class LedgerEntry(
    @PrimaryKey val id: String = newId(), val type: EntryType, val amount: Long,
    val occurredAt: Long = nowMillis(), val zoneId: String = TimeZone.currentSystemDefault().id,
    val categoryId: String? = null, val accountId: String? = null, val targetAccountId: String? = null,
    val channelId: String? = null, val source: Source = Source.UNKNOWN, val merchant: String = "",
    val note: String = "", val refundOf: String? = null, val draftId: String? = null,
    val method: CaptureMethod = CaptureMethod.MANUAL, val createdAt: Long = nowMillis(),
    @ColumnInfo(defaultValue = "''") val subject: String = ""
)

// Retain the older draft payload for encrypted backup validation.
@Serializable data class Candidate(
    val type: EntryType = EntryType.EXPENSE, val amount: Long? = null, val occurredAt: Long? = null,
    val source: Source = Source.UNKNOWN, val merchant: String = "", val channelName: String? = null,
    val orderId: String? = null, val categoryId: String? = null,
    val warnings: List<String> = emptyList(), val parserVersion: Int = 1, val subject: String = ""
)
@Serializable @Entity(tableName = "drafts", indices = [Index("status")])
data class Draft(@PrimaryKey val id: String = newId(), val candidateJson: String,
    val method: CaptureMethod, val receivedAt: Long = nowMillis(), val status: String = "PENDING")
@Serializable @Entity(tableName = "evidence", indices = [Index(value = ["fingerprint"], unique = true), Index("draftId"), Index("entryId")])
data class Evidence(@PrimaryKey val id: String = newId(), val fingerprint: String, val draftId: String,
    val entryId: String? = null, val source: Source, val summary: String,
    val orderId: String? = null, val attachmentName: String? = null, val receivedAt: Long = nowMillis())
@Serializable @Entity(tableName = "merchant_rules")
data class MerchantRule(@PrimaryKey val merchant: String, val categoryId: String)
@Serializable @Entity(tableName = "preferences")
data class Preference(@PrimaryKey val key: String, val value: String)

@Serializable data class LedgerSnapshot(
    val accounts: List<Account> = emptyList(), val channels: List<Channel> = emptyList(),
    val categories: List<Category> = emptyList(), val entries: List<LedgerEntry> = emptyList(),
    val drafts: List<Draft> = emptyList(), val evidence: List<Evidence> = emptyList(),
    val rules: List<MerchantRule> = emptyList(), val preferences: List<Preference> = emptyList()
)

object Money {
    const val MAX = 999_999_999_99L
    fun parse(value: String): Long? {
        val original = value.trim().removePrefix("¥").removePrefix("￥").trim()
        if (',' in original && !Regex("\\d{1,3}(,\\d{3})+(\\.\\d{1,2})?").matches(original)) return null
        val cleaned = original.replace(",", "")
        if (!Regex("\\d{1,9}(\\.\\d{1,2})?").matches(cleaned)) return null
        val parts = cleaned.split('.')
        return ((parts[0].toLongOrNull() ?: return null) * 100 +
            parts.getOrNull(1).orEmpty().padEnd(2, '0').toLong()).takeIf { it in 1..MAX }
    }
    fun parseAddition(expression: String): Long? {
        var total = 0L
        for (term in expression.split('+', '＋')) {
            val cents = parse(term) ?: return null
            if (cents > MAX - total) return null
            total += cents
        }
        return total
    }
    fun format(value: Long): String {
        val n = if (value < 0) -value else value
        return (if (value < 0) "-" else "") + "${n / 100}.${(n % 100).toString().padStart(2, '0')}"
    }
}

fun LedgerEntry.localDate(): LocalDate = Instant.fromEpochMilliseconds(occurredAt).toLocalDateTime(TimeZone.of(zoneId)).date
data class MonthTotals(val income: Long, val expense: Long) { val balance: Long get() = income - expense }
fun totals(entries: List<LedgerEntry>): MonthTotals = MonthTotals(
    entries.filter { it.type == EntryType.INCOME }.sumOf { it.amount },
    entries.sumOf { when (it.type) { EntryType.EXPENSE -> it.amount; EntryType.REFUND -> -it.amount; else -> 0L } }
)
fun categoryTotals(snapshot: LedgerSnapshot, entries: List<LedgerEntry>): Map<String, Long> {
    val categories = snapshot.categories.associateBy { it.id }
    return entries.filter { it.type == EntryType.EXPENSE || it.type == EntryType.REFUND }
        .groupBy { categories[it.categoryId]?.parentId ?: it.categoryId.orEmpty() }
        .mapValues { (_, rows) -> rows.sumOf { if (it.type == EntryType.REFUND) -it.amount else it.amount } }
}
