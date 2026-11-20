package dev.leafledger.core

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

val LedgerJson = Json { ignoreUnknownKeys = false; encodeDefaults = true }
@Serializable data class BackupDocument(val format: String = "leaf-ledger", val version: Int = 1,
    val exportedAt: Long = nowMillis(), val snapshot: LedgerSnapshot, val attachments: Map<String, String> = emptyMap())
object BackupCodec {
    fun encode(document: BackupDocument): ByteArray { validate(document); return LedgerJson.encodeToString(document).encodeToByteArray() }
    fun decode(bytes: ByteArray): BackupDocument {
        require(bytes.size <= 100 * 1024 * 1024) { "备份文件过大" }
        return LedgerJson.decodeFromString<BackupDocument>(bytes.decodeToString()).also(::validate)
    }
    fun validate(document: BackupDocument) {
        require(document.format == "leaf-ledger" && document.version == 1) { "不支持的备份格式或版本" }
        LedgerValidation.snapshot(document.snapshot)
        val expected = document.snapshot.evidence.mapNotNull { it.attachmentName }.toSet()
        require(document.attachments.keys == expected) { "备份附件不完整" }
    }
    fun csv(snapshot: LedgerSnapshot): String {
        fun cell(value: String) = "\"" + (if (value.trimStart().firstOrNull() in listOf('=', '+', '-', '@')) "'" else "") + value.replace("\"", "\"\"") + "\""
        val accounts = snapshot.accounts.associate { it.id to it.name }; val cats = snapshot.categories.associate { it.id to it.name }
        val channels = snapshot.channels.associate { it.id to it.name }
        return "\uFEFF" + listOf("类型,金额(元),时间戳,时区,分类,账户,转入账户,支付渠道,业务来源,商户,备注").plus(snapshot.entries.map { e ->
            listOf(when(e.type) { EntryType.EXPENSE -> "支出"; EntryType.INCOME -> "收入"; EntryType.TRANSFER -> "转账"; EntryType.REFUND -> "退款" },
                Money.format(e.amount), e.occurredAt.toString(), e.zoneId, cats[e.categoryId].orEmpty(), accounts[e.accountId].orEmpty(),
                accounts[e.targetAccountId].orEmpty(), channels[e.channelId].orEmpty(), e.source.label, e.merchant, e.note).joinToString(",", transform = ::cell)
        }).joinToString("\r\n")
    }
}
