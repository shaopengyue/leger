package dev.leafledger.core

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
abstract class LedgerDao {
    @Query("SELECT * FROM accounts ORDER BY sort, name") abstract suspend fun accounts(): List<Account>
    @Query("SELECT * FROM channels ORDER BY sort, name") abstract suspend fun channels(): List<Channel>
    @Query("SELECT * FROM categories ORDER BY sort, name") abstract suspend fun categories(): List<Category>
    @Query("SELECT * FROM entries ORDER BY occurredAt DESC") abstract suspend fun entries(): List<LedgerEntry>
    @Query("SELECT * FROM drafts ORDER BY receivedAt DESC") abstract suspend fun drafts(): List<Draft>
    @Query("SELECT * FROM evidence") abstract suspend fun evidence(): List<Evidence>
    @Query("SELECT * FROM merchant_rules") abstract suspend fun rules(): List<MerchantRule>
    @Query("SELECT * FROM preferences") abstract suspend fun preferences(): List<Preference>
    @Query("SELECT id FROM accounts UNION ALL SELECT id FROM channels UNION ALL SELECT id FROM categories UNION ALL SELECT id FROM entries UNION ALL SELECT id FROM drafts UNION ALL SELECT id FROM evidence UNION ALL SELECT merchant FROM merchant_rules UNION ALL SELECT `key` FROM preferences")
    abstract fun observeChanges(): Flow<List<String>>
    @Upsert abstract suspend fun account(row: Account)
    @Upsert abstract suspend fun channel(row: Channel)
    @Upsert abstract suspend fun category(row: Category)
    @Upsert abstract suspend fun entry(row: LedgerEntry)
    @Upsert abstract suspend fun draft(row: Draft)
    @Upsert abstract suspend fun evidence(row: Evidence)
    @Upsert abstract suspend fun rule(row: MerchantRule)
    @Upsert abstract suspend fun preference(row: Preference)
    @Query("DELETE FROM entries WHERE id = :id") abstract suspend fun deleteEntry(id: String)
    @Query("DELETE FROM categories WHERE id = :id") abstract suspend fun deleteCategory(id: String)
    @Query("DELETE FROM channels WHERE id = :id") abstract suspend fun deleteChannel(id: String)
    @Query("UPDATE entries SET categoryId = :targetId WHERE categoryId = :sourceId")
    abstract suspend fun moveEntryCategories(sourceId: String, targetId: String)
    @Query("UPDATE entries SET channelId = :targetId WHERE channelId = :sourceId")
    abstract suspend fun moveEntryChannels(sourceId: String, targetId: String)
    @Query("UPDATE merchant_rules SET categoryId = :targetId WHERE categoryId = :sourceId")
    abstract suspend fun moveRuleCategories(sourceId: String, targetId: String)
    @Query("DELETE FROM merchant_rules WHERE merchant = :merchant") abstract suspend fun deleteRule(merchant: String)
    @Query("UPDATE evidence SET entryId = NULL WHERE entryId = :id") abstract suspend fun unlinkEntry(id: String)
    @Query("DELETE FROM accounts") abstract suspend fun clearAccounts()
    @Query("DELETE FROM channels") abstract suspend fun clearChannels()
    @Query("DELETE FROM categories") abstract suspend fun clearCategories()
    @Query("DELETE FROM entries") abstract suspend fun clearEntries()
    @Query("DELETE FROM drafts") abstract suspend fun clearDrafts()
    @Query("DELETE FROM evidence") abstract suspend fun clearEvidence()
    @Query("DELETE FROM merchant_rules") abstract suspend fun clearRules()
    @Query("DELETE FROM preferences") abstract suspend fun clearPreferences()

    @Transaction
    open suspend fun snapshot() = LedgerSnapshot(accounts(), channels(), categories(), entries(), drafts(), evidence(), rules(), preferences())
}

@Database(entities = [Account::class, Channel::class, Category::class, LedgerEntry::class, Draft::class,
    Evidence::class, MerchantRule::class, Preference::class], version = 2, exportSchema = true)
@ConstructedBy(LedgerDatabaseConstructor::class)
abstract class LedgerDatabase : RoomDatabase() { abstract fun dao(): LedgerDao }

@Suppress("NO_ACTUAL_FOR_EXPECT")
expect object LedgerDatabaseConstructor : RoomDatabaseConstructor<LedgerDatabase> {
    override fun initialize(): LedgerDatabase
}

val Migration1To2 = object : androidx.room.migration.Migration(1, 2) {
    override fun migrate(connection: androidx.sqlite.SQLiteConnection) {
        connection.prepare("ALTER TABLE entries ADD COLUMN subject TEXT NOT NULL DEFAULT ''").use { it.step() }
        connection.prepare("UPDATE entries SET subject = CASE WHEN note != '' THEN note WHEN merchant != '' THEN merchant ELSE '记账' END").use { it.step() }
    }
}
