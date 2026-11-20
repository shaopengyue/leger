package dev.leafledger.core

import kotlinx.datetime.*
import kotlin.test.*

class LedgerRulesTest {
    private val account = Account("cash", "现金")
    private val food = Category("food", "餐饮", EntryType.EXPENSE)
    private val takeout = Category("takeout", "外卖", EntryType.EXPENSE, "food")
    private val income = Category("salary", "工资", EntryType.INCOME)
    private fun snapshot(entries: List<LedgerEntry> = emptyList()) = LedgerSnapshot(accounts = listOf(account), categories = listOf(food, takeout, income), entries = entries)
    private fun expense(amount: Long = 10000) = LedgerEntry(id = "original", type = EntryType.EXPENSE, amount = amount, categoryId = food.id, accountId = account.id, occurredAt = 1000)

    @Test fun moneyUsesExactCents() { assertEquals(10L, Money.parse("0.10")); assertEquals(123456L, Money.parse("1,234.56")); assertEquals("-0.01", Money.format(-1)); assertEquals("100.00", Money.format(10000)) }
    @Test fun invalidAmountsAreRejected() { listOf("0", "-1", "1.001", "NaN", "1e3", "1000000000", "").forEach { assertNull(Money.parse(it), it) } }
    @Test fun malformedSeparatorsNeverSilentlyChangeAmounts() { listOf("1,2", "12,34.56", "1,,000", "1,0000").forEach { assertNull(Money.parse(it), it) } }
    @Test fun transferDoesNotInflateTotals() {
        val rows = listOf(expense(), LedgerEntry(type = EntryType.INCOME, amount = 30000, categoryId = income.id), LedgerEntry(type = EntryType.TRANSFER, amount = 90000), LedgerEntry(type = EntryType.REFUND, amount = 2000, categoryId = food.id))
        assertEquals(MonthTotals(30000, 8000), totals(rows)); assertEquals(22000L, totals(rows).balance)
    }
    @Test fun parentAggregatesDirectAndChildOnce() {
        val rows = listOf(expense(), expense(2000).copy(id = "child", categoryId = takeout.id), expense(500).copy(id = "refund", type = EntryType.REFUND, categoryId = takeout.id))
        assertEquals(mapOf("food" to 11500L), categoryTotals(snapshot(rows), rows))
    }
    @Test fun crossMonthRefundBelongsToRefundMonth() {
        val first = expense().copy(occurredAt = Instant.parse("2026-10-31T10:00:00Z").toEpochMilliseconds(), zoneId = "Asia/Shanghai")
        val refund = first.copy(id = "refund", type = EntryType.REFUND, amount = 1000, refundOf = first.id, occurredAt = Instant.parse("2026-11-02T10:00:00Z").toEpochMilliseconds())
        assertEquals(10, first.localDate().monthNumber); assertEquals(11, refund.localDate().monthNumber)
        assertEquals(-1000L, totals(listOf(refund)).expense)
    }
    @Test fun refundsCannotExceedOriginal() {
        val original = expense(); val first = original.copy(id = "r1", type = EntryType.REFUND, amount = 7000, refundOf = original.id, occurredAt = 2000)
        assertFailsWith<IllegalArgumentException> { LedgerValidation.entry(first.copy(id = "r2", amount = 4000), snapshot(listOf(original, first))) }
    }
    @Test fun refundRequiresOriginalCategoryAndChronology() {
        val original = expense()
        assertFailsWith<IllegalArgumentException> { LedgerValidation.entry(original.copy(id = "r", type = EntryType.REFUND, refundOf = original.id, occurredAt = 999), snapshot(listOf(original))) }
        assertFailsWith<IllegalArgumentException> { LedgerValidation.entry(original.copy(id = "r", type = EntryType.REFUND, categoryId = takeout.id, refundOf = original.id, occurredAt = 2000), snapshot(listOf(original))) }
    }
    @Test fun cannotReduceOriginalBelowRefunds() {
        val original = expense(); val refund = original.copy(id = "r", type = EntryType.REFUND, amount = 5000, refundOf = original.id, occurredAt = 2000)
        assertFailsWith<IllegalArgumentException> { LedgerValidation.entry(original.copy(amount = 4000), snapshot(listOf(original, refund))) }
    }
    @Test fun sameAccountTransferIsRejected() {
        assertFailsWith<IllegalArgumentException> { LedgerValidation.entry(LedgerEntry(type = EntryType.TRANSFER, amount = 100, accountId = "cash", targetAccountId = "cash"), snapshot()) }
    }
    @Test fun categoryCannotCycleOrGainThirdLevel() {
        assertFailsWith<IllegalArgumentException> { LedgerValidation.category(food.copy(parentId = takeout.id), listOf(food, takeout)) }
        assertFailsWith<IllegalArgumentException> { LedgerValidation.category(Category("third", "早餐", EntryType.EXPENSE, takeout.id), listOf(food, takeout)) }
    }
    @Test fun categoryCannotCrossIncomeAndExpense() {
        assertFailsWith<IllegalArgumentException> { LedgerValidation.category(food.copy(parentId = income.id), listOf(food, income)) }
    }
    @Test fun backupRoundTripPreservesIdsAndCents() {
        val s = snapshot(listOf(expense()))
        assertEquals(s, BackupCodec.decode(BackupCodec.encode(BackupDocument(snapshot = s))).snapshot)
    }
    @Test fun backupRejectsFutureVersionsAndDanglingReferences() {
        assertFailsWith<IllegalArgumentException> { BackupCodec.encode(BackupDocument(version = 2, snapshot = snapshot())) }
        assertFailsWith<IllegalArgumentException> { BackupCodec.encode(BackupDocument(snapshot = snapshot(listOf(expense().copy(accountId = "missing"))))) }
    }
    @Test fun backupRejectsMissingAndUnsafeAttachments() {
        val draft = Draft(id = "d", candidateJson = LedgerJson.encodeToString(Candidate.serializer(), Candidate(amount = 100)), method = CaptureMethod.SHARE)
        val evidence = Evidence(fingerprint = "image", draftId = "d", source = Source.ALIPAY, summary = "", attachmentName = "safe.img")
        assertFailsWith<IllegalArgumentException> { BackupCodec.encode(BackupDocument(snapshot = snapshot().copy(drafts = listOf(draft), evidence = listOf(evidence)))) }
        assertFailsWith<IllegalArgumentException> { BackupCodec.encode(BackupDocument(snapshot = snapshot().copy(drafts = listOf(draft), evidence = listOf(evidence.copy(attachmentName = "../unsafe.img"))), attachments = mapOf("../unsafe.img" to "AA=="))) }
    }
    @Test fun csvEscapesQuotesAndSpreadsheetFormulas() {
        val csv = BackupCodec.csv(snapshot(listOf(expense().copy(merchant = "=HYPERLINK(\"x\")", note = "a,\nb"))))
        assertTrue(csv.startsWith("\uFEFF")); assertTrue(csv.contains("'=HYPERLINK(\"\"x\"\")")); assertTrue(csv.contains("\"a,\nb\""))
    }
}
