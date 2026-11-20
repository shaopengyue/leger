package dev.leafledger.ui

import dev.leafledger.core.*
import kotlinx.serialization.Serializable
import kotlinx.datetime.*

@Serializable
internal data class EntrySession(
    val base: LedgerEntry,
    val existing: Boolean = false,
    val amount: String = if(base.amount > 0) Money.format(base.amount) else "",
    val type: EntryType = base.type,
    val categoryId: String? = base.categoryId,
    val subject: String = base.subject,
    val merchant: String = base.merchant,
    val channelId: String? = base.channelId,
    val time: Long = base.occurredAt
) {
    fun dirty() = amount != (if(base.amount > 0) Money.format(base.amount) else "") || type != base.type ||
        categoryId != base.categoryId || subject != base.subject || merchant != base.merchant || channelId != base.channelId || time != base.occurredAt

    fun entry(snapshot: LedgerSnapshot) = prepareQuickEntry(base.copy(type=type,
        amount=Money.parseAddition(amount) ?: error("请填写完整金额，每项最多两位小数"), categoryId=categoryId,
        subject=subject, merchant=merchant, channelId=channelId, occurredAt=time),snapshot)
}

internal fun newManualSession(snapshot: LedgerSnapshot) = EntrySession(
    LedgerEntry(
        type = EntryType.EXPENSE,
        amount = 0,
        categoryId = frequentCategories(snapshot, EntryType.EXPENSE).firstOrNull()?.id,
        channelId = frequentChannels(snapshot).firstOrNull()?.id
    )
)

@Serializable
internal data class BillFilter(val type: String?=null,val categoryId:String?=null,val channelId:String?=null) {
    val count get() = listOf(type,categoryId,channelId).count { it!=null }
}
internal fun typeLabel(t:EntryType) = if(t==EntryType.INCOME || t==EntryType.REFUND) "收入" else "支出"
internal fun categoryName(id:String?,s:LedgerSnapshot):String {
    val c=s.categories.find { it.id==id } ?: return "未分类"
    return c.name
}
internal fun formatTime(time:Long,zone:String=kotlinx.datetime.TimeZone.currentSystemDefault().id):String {
    val t=Instant.fromEpochMilliseconds(time).toLocalDateTime(TimeZone.of(zone))
    return "${t.date} ${t.hour.toString().padStart(2,'0')}:${t.minute.toString().padStart(2,'0')}"
}
