package dev.leafledger.ui

import kotlinx.coroutines.flow.StateFlow

data class RestorePreview(val entries: Int)
interface PlatformActions {
    val messages: StateFlow<String?>
    val restorePreview: StateFlow<RestorePreview?>
    val busy: StateFlow<Boolean>
    fun consumeMessage()
    fun setBackHandler(handler: (() -> Boolean)?)
    fun selectDateTime(current: Long, zone: String, onSelected: (Long) -> Unit)
    fun exportBackup(password: String)
    fun chooseRestore(password: String)
    fun confirmRestore()
    fun cancelRestore()
    fun exportCsv()
}
