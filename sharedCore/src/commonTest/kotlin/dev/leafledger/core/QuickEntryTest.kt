package dev.leafledger.core

import kotlin.test.*

class QuickEntryTest {
    @Test fun additionUsesExactCentsAndRejectsIncompleteOrOverflowingAmounts() {
        assertEquals(2370L, Money.parseAddition("12.50＋8+3.20"))
        assertEquals(30L, Money.parseAddition("0.1+0.2"))
        assertEquals(Money.MAX, Money.parseAddition("999999999.98+0.01"))
        for (invalid in listOf("8+", "+8", "8++2", "1.001+2", "8-2", "999999999.99+0.01")) {
            assertNull(Money.parseAddition(invalid), invalid)
        }
    }
    @Test fun flatteningRetainsIdsAndResolvesDuplicateNames() {
        val source=listOf(Category("a","餐饮",EntryType.EXPENSE),Category("b","餐饮",EntryType.EXPENSE,"a"),Category("c","餐饮",EntryType.INCOME))
        val flat=flatCategories(source)
        assertEquals(source.map {it.id},flat.map {it.id})
        assertTrue(flat.all {it.parentId==null})
        assertEquals(listOf("餐饮","餐饮（2）","餐饮"),flat.map {it.name})
        assertEquals(flat,flatCategories(flat))
    }
    private val snapshot = LedgerSnapshot(categories = listOf(Category("food", "餐饮", EntryType.EXPENSE)),
        channels = listOf(Channel("wechat", "微信支付")))
    @Test fun optionalSubjectFallsBackToCategoryOnlyAtSave() {
        val input = LedgerEntry(type = EntryType.EXPENSE, amount = 1200, categoryId = "food")
        assertEquals("餐饮", prepareQuickEntry(input, snapshot).subject)
        assertEquals("", input.subject)
        assertEquals("午饭", prepareQuickEntry(input.copy(subject=" 午饭 "), snapshot).subject)
    }
    @Test fun categoryAndValidAmountAreStillRequired() {
        assertFails { prepareQuickEntry(LedgerEntry(type=EntryType.EXPENSE, amount=1200),snapshot) }
        assertFails { prepareQuickEntry(LedgerEntry(type=EntryType.EXPENSE,amount=0,categoryId="food"),snapshot) }
    }
    @Test fun onlyActiveRememberedChannelIsRestored() {
        assertNull(manualChannel(snapshot))
        val s = snapshot.copy(preferences=listOf(Preference(LAST_MANUAL_CHANNEL,"wechat")))
        assertEquals("wechat",manualChannel(s))
        assertNull(manualChannel(s.copy(channels=listOf(s.channels.single().copy(active=false)))))
    }
    @Test fun recentOrderIncludesEveryEnabledChoiceAndRespectsEntryType() {
        val categories = (0..11).map { Category("c$it", "分类$it", EntryType.EXPENSE, active = it != 11) }
        val channels = (0..11).map { Channel("ch$it", "渠道$it", active = it != 11) }
        val s = snapshot.copy(categories = categories, channels = channels,
            entries = listOf(LedgerEntry(type = EntryType.EXPENSE, amount = 100, categoryId = "c9", channelId = "ch9")))
        val sortedCategories = frequentCategories(s, EntryType.EXPENSE)
        val sortedChannels = frequentChannels(s)
        assertEquals("c9", sortedCategories.first().id)
        assertEquals("ch9", sortedChannels.first().id)
        assertEquals(categories.filter { it.active }.toSet(), sortedCategories.toSet())
        assertEquals(channels.filter { it.active }.toSet(), sortedChannels.toSet())
        assertTrue(frequentCategories(s, EntryType.INCOME).isEmpty())
    }
}
