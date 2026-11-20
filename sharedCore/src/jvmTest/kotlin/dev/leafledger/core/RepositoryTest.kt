package dev.leafledger.core

import androidx.room.Room
import androidx.room.useWriterConnection
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import kotlinx.coroutines.*
import kotlinx.coroutines.test.runTest
import java.nio.file.Files
import kotlin.test.*

class RepositoryTest {
    private suspend fun withRepository(configure: Boolean = true, test: suspend (LedgerRepository) -> Unit) =
        withDatabase(configure) { repository, _ -> test(repository) }

    private suspend fun withDatabase(configure: Boolean, test: suspend (LedgerRepository, LedgerDatabase) -> Unit) {
        val directory = Files.createTempDirectory("leaf-ledger-test").toFile()
        val database = Room.databaseBuilder<LedgerDatabase>(directory.resolve("ledger.db").absolutePath)
            .setDriver(BundledSQLiteDriver()).setQueryCoroutineContext(Dispatchers.IO).build()
        try {
            val repository = LedgerRepository(database)
            repository.initialize()
            if (configure) {
                listOf("餐饮", "购物", "交通").forEachIndexed { i, name ->
                    repository.saveCategory(Category("expense-$i", name, EntryType.EXPENSE))
                }
                repository.saveCategory(Category("income-0", "工资", EntryType.INCOME))
                repository.saveChannel(Channel("channel-0", "微信支付"))
                repository.saveChannel(Channel("channel-1", "支付宝"))
            }
            test(repository, database)
        }
        finally { database.close(); directory.deleteRecursively() }
    }
    @Test fun initializeLeavesCategoriesAndChannelsEmpty() = runTest { withRepository(configure = false) { r ->
        val before = r.snapshot()
        assertTrue(before.categories.isEmpty())
        assertTrue(before.channels.isEmpty())
        r.initialize()
        assertEquals(before, r.snapshot())
    } }

    @Test fun legacyBackupRetainsConfirmedEntriesAndDraftReferences() = runTest { withRepository { r ->
        val candidate = LedgerJson.encodeToString(Candidate(amount = 900))
        val confirmed = Draft(candidateJson = candidate, method = CaptureMethod.SHARE, status = "CONFIRMED")
        val pending = Draft(candidateJson = candidate, method = CaptureMethod.SHARE)
        val row = LedgerEntry(
            type = EntryType.EXPENSE, amount = 900, categoryId = "expense-0", channelId = "channel-1",
            method = CaptureMethod.SHARE, draftId = confirmed.id
        )
        val evidence = Evidence(fingerprint = "legacy-sample", draftId = confirmed.id, entryId = row.id,
            source = Source.UNKNOWN, summary = "合成测试数据")
        val old = r.snapshot().copy(entries = listOf(row), drafts = listOf(confirmed, pending), evidence = listOf(evidence))
        val decoded = BackupCodec.decode(BackupCodec.encode(BackupDocument(snapshot = old)))
        r.replace(decoded.snapshot)
        r.initialize()
        assertEquals(row, r.snapshot().entries.single())
        assertEquals(old.drafts.toSet(), r.snapshot().drafts.toSet())
        assertEquals(evidence, r.snapshot().evidence.single())

        r.saveManualEntry(row.copy(subject = "已编辑"))
        assertEquals("已编辑", r.snapshot().entries.single().subject)
        assertEquals("channel-1", frequentChannels(r.snapshot()).first().id)
        assertNull(manualChannel(r.snapshot()))
        assertFailsWith<IllegalArgumentException> { r.saveManualEntry(row.copy(id = newId(), draftId = pending.id)) }
        assertEquals(1, r.snapshot().entries.size)
    } }
    @Test fun restoreValidationFailurePreservesCurrentData() = runTest { withRepository { r ->
        val before = r.snapshot()
        assertFailsWith<IllegalArgumentException> { r.replace(before.copy(channels = before.channels + before.channels.first())) }
        assertEquals(before, r.snapshot())
    } }
    @Test fun refundsTrackReclassificationAndProtectOriginalDeletion() = runTest { withRepository { r ->
        val original = LedgerEntry(type = EntryType.EXPENSE, amount = 1000, categoryId = "expense-0", occurredAt = 1000)
        r.saveEntry(original)
        r.saveEntry(LedgerEntry(type = EntryType.REFUND, amount = 300, refundOf = original.id, categoryId = "expense-0", occurredAt = 2000))
        r.saveEntry(original.copy(categoryId = "expense-1"))
        assertEquals("expense-1", r.snapshot().entries.first { it.type == EntryType.REFUND }.categoryId)
        assertFailsWith<IllegalArgumentException> { r.deleteEntry(original.id) }
        assertEquals(700L, totals(r.snapshot().entries).expense)
    } }
    @Test fun restoreRoundTripPreservesData() = runTest { withRepository { r ->
        r.saveEntry(LedgerEntry(type = EntryType.INCOME, amount = 600000, categoryId = "income-0"))
        val before = r.snapshot(); val decoded = BackupCodec.decode(BackupCodec.encode(BackupDocument(snapshot = before)))
        r.replace(decoded.snapshot); assertEquals(before, r.snapshot())
    } }
    @Test fun migrationRetainsLinkedRefundsRulesAmountsAndBackup() = runTest { withRepository { r ->
        val expense = LedgerEntry(type = EntryType.EXPENSE, amount = 1000, occurredAt = 1000,
            categoryId = "expense-0", channelId = "channel-0", subject = "午饭", merchant = "测试商户")
        r.saveManualEntry(expense)
        r.saveEntry(expense, rememberMerchant = true)
        val refund = LedgerEntry(type = EntryType.REFUND, amount = 300, occurredAt = 2000,
            categoryId = "expense-0", channelId = "channel-0", refundOf = expense.id)
        r.saveEntry(refund)
        val before = r.snapshot()
        r.removeCategory("expense-0", "expense-1")
        r.removeChannel("channel-0", "channel-1")
        val after = r.snapshot()
        assertEquals(before.entries.map { it.copy(categoryId = "expense-1", channelId = "channel-1") }, after.entries)
        assertEquals(before.rules.map { it.copy(categoryId = "expense-1") }, after.rules)
        assertEquals(totals(before.entries), totals(after.entries))
        assertFalse(after.categories.any { it.id == "expense-0" })
        assertFalse(after.channels.any { it.id == "channel-0" })
        assertEquals("expense-1", frequentCategories(after, EntryType.EXPENSE).first().id)
        assertEquals("channel-1", frequentChannels(after).first().id)
        assertEquals("channel-1", manualChannel(after))
        r.replace(BackupCodec.decode(BackupCodec.encode(BackupDocument(snapshot = after))).snapshot)
        assertEquals(after, r.snapshot())
    } }

    @Test fun invalidRemovalTargetsDoNotModifyLedgerAndUnusedItemsCanBeDeleted() = runTest { withRepository { r ->
        r.saveManualEntry(LedgerEntry(type = EntryType.EXPENSE, amount = 800, categoryId = "expense-0", channelId = "channel-0"))
        r.saveChannel(Channel("channel-1", "支付宝", active = false))
        val before = r.snapshot()
        assertFailsWith<IllegalArgumentException> { r.removeCategory("expense-0") }
        assertFailsWith<IllegalArgumentException> { r.removeCategory("expense-0", "income-0") }
        assertFailsWith<IllegalArgumentException> { r.removeCategory("expense-0", "expense-0") }
        assertFailsWith<IllegalArgumentException> { r.removeChannel("channel-0") }
        assertFailsWith<IllegalArgumentException> { r.removeChannel("channel-0", "channel-1") }
        assertFailsWith<IllegalArgumentException> { r.removeChannel("channel-0", "missing") }
        assertEquals(before, r.snapshot())
        r.removeCategory("expense-1")
        r.removeChannel("channel-1")
        val after = r.snapshot()
        assertEquals(before.copy(categories = before.categories.filterNot { it.id == "expense-1" },
            channels = before.channels.filterNot { it.id == "channel-1" }), after)
    } }

    @Test fun databaseFailureDuringDeletionRollsBackMigration() = runTest { withDatabase(true) { r, database ->
        r.saveManualEntry(LedgerEntry(type = EntryType.EXPENSE, amount = 800, categoryId = "expense-0", channelId = "channel-0"))
        val before = r.snapshot()
        database.useWriterConnection { connection ->
            connection.usePrepared("CREATE TRIGGER test_abort_channel_delete BEFORE DELETE ON channels BEGIN SELECT RAISE(ABORT, 'test failure'); END") { it.step() }
        }
        assertFails { r.removeChannel("channel-0", "channel-1") }
        assertEquals(before, r.snapshot())
    } }
    @Test fun tenThousandEntriesKeepExactTotalsAndRestore() = runTest { withRepository { r ->
        val seed = r.snapshot()
        val rows = (1..10_000).map { index -> LedgerEntry(id = "load-$index", type = EntryType.EXPENSE,
            amount = 123, occurredAt = 1_700_000_000_000L + index, categoryId = "expense-0", merchant = "交易 $index") }
        r.replace(seed.copy(entries = rows))
        val actual = r.snapshot()
        assertEquals(10_000, actual.entries.size)
        assertEquals(1_230_000L, totals(actual.entries).expense)
        assertEquals(actual, BackupCodec.decode(BackupCodec.encode(BackupDocument(snapshot = actual))).snapshot)
    } }
    @Test fun quickSaveDefaultsAndChannelPreferenceAreAtomic() = runTest { withRepository { r ->
        val row=LedgerEntry(type=EntryType.EXPENSE,amount=800,categoryId="expense-0",channelId="channel-0")
        r.saveManualEntry(row)
        assertEquals("餐饮",r.snapshot().entries.single().subject)
        assertEquals("channel-0",manualChannel(r.snapshot()))
        r.saveManualEntry(row.copy(id=newId(),categoryId="expense-2",occurredAt=1))
        assertEquals("expense-2",frequentCategories(r.snapshot(),EntryType.EXPENSE).first().id)
        assertFails { r.saveManualEntry(row.copy(id=newId(),amount=0,channelId="channel-1")) }
        assertEquals("channel-0",manualChannel(r.snapshot()))
        assertEquals("channel-0", frequentChannels(r.snapshot()).first().id)
        r.saveManualEntry(row.copy(id=newId(),channelId="channel-1",occurredAt=1))
        assertEquals("channel-1",manualChannel(r.snapshot()))
        assertEquals("channel-1", frequentChannels(r.snapshot()).first().id)
        val beforeUnset = frequentChannels(r.snapshot())
        r.saveManualEntry(row.copy(id=newId(),channelId=null))
        assertEquals(beforeUnset, frequentChannels(r.snapshot()))
    } }
}
