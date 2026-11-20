package dev.leafledger.core

const val LAST_MANUAL_CHANNEL = "manual.lastChannel"
internal const val RECENT_CHANNELS = "recent.channels"
fun recentCategoryKey(type: EntryType) = "recent.categories.${type.name}"

/** Retains every category ID so old entries and merchant-rule backups keep their references. */
fun flatCategories(categories: List<Category>): List<Category> {
    val names = mutableSetOf<Pair<EntryType,String>>()
    return categories.map { category ->
        var name = category.name
        var suffix = 2
        while (!names.add(category.type to name)) {
            val ending = "（${suffix++}）"
            name = category.name.take(40-ending.length) + ending
        }
        category.copy(name=name,parentId=null)
    }
}

/** Manual defaults never leak into a screenshot's missing fields. */
fun manualChannel(snapshot: LedgerSnapshot): String? = snapshot.preferences
    .firstOrNull { it.key == LAST_MANUAL_CHANNEL }?.value
    ?.takeIf { id -> snapshot.channels.any { it.id == id && it.active } }

fun prepareQuickEntry(input: LedgerEntry, snapshot: LedgerSnapshot): LedgerEntry {
    val category = snapshot.categories.firstOrNull { it.id == input.categoryId } ?: error("请选择分类")
    return input.copy(subject = input.subject.trim().ifBlank { category.name }, merchant = input.merchant.trim())
        .also { LedgerValidation.entry(it, snapshot) }
}

fun frequentCategories(snapshot: LedgerSnapshot, type: EntryType): List<Category> {
    val available = snapshot.categories.filter { it.active && it.type == type }
    val history = snapshot.entries.sortedByDescending { it.createdAt }.mapNotNull { it.categoryId }
    val byId = available.associateBy { it.id }
    return ((recentIds(snapshot.preferences, recentCategoryKey(type)) + history).mapNotNull(byId::get) +
        available.sortedBy { it.name }).distinctBy { it.id }
}

fun frequentChannels(snapshot: LedgerSnapshot): List<Channel> {
    val available = snapshot.channels.filter { it.active }
    val history = snapshot.entries.sortedByDescending { it.createdAt }.mapNotNull { it.channelId }
    val byId = available.associateBy { it.id }
    return ((recentIds(snapshot.preferences, RECENT_CHANNELS) + history).mapNotNull(byId::get) +
        available.sortedBy { it.name }).distinctBy { it.id }
}

internal fun recentIds(preferences: List<Preference>, key: String): List<String> = runCatching {
    LedgerJson.decodeFromString<List<String>>(preferences.firstOrNull { it.key == key }?.value ?: "[]")
}.getOrDefault(emptyList())
