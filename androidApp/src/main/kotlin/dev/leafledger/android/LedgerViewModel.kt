package dev.leafledger.android

import android.app.Application
import android.net.Uri
import android.util.Base64
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.leafledger.core.*
import dev.leafledger.ui.RestorePreview
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import java.io.File

class LedgerViewModel(application: Application) : AndroidViewModel(application) {
    val app = application as LedgerApplication
    val busy = MutableStateFlow(false)
    val restorePreview = MutableStateFlow<RestorePreview?>(null)
    private var restoreDocument: BackupDocument? = null
    private var transferPassword: CharArray? = null
    private var transferMode: String? = null
    private val fileTaskMutex = kotlinx.coroutines.sync.Mutex()

    fun prepareDocument(mode: String, password: String?) {
        transferPassword?.fill('\u0000')
        transferPassword = password?.toCharArray(); transferMode = mode
    }
    fun cancelDocument() { transferPassword?.fill('\u0000'); transferPassword = null; transferMode = null }
    fun documentSelected(uri: Uri?) {
        if(uri == null) { cancelDocument(); return }
        val password = transferPassword; val mode = transferMode
        transferPassword = null; transferMode = null
        if(mode == null) { app.messages.value = "操作已过期，请重新选择"; return }
        task {
            app.ready.await()
            when(mode) {
                "export" -> {
                    requireNotNull(password) { "操作已过期，请重新输入密码" }
                    val snapshot = app.repository.snapshot()
                    val images = snapshot.evidence.mapNotNull { it.attachmentName }.distinct().associateWith { name ->
                        Base64.encodeToString(File(app.attachmentDirectory, name).readBytes(), Base64.NO_WRAP)
                    }
                    val bytes = EncryptedBackup.encrypt(BackupCodec.encode(BackupDocument(snapshot = snapshot, attachments = images)), password)
                    app.contentResolver.openOutputStream(uri, "wt")?.use { it.write(bytes) } ?: error("无法写入备份文件")
                    app.messages.value = "加密备份已导出，请妥善保存密码"
                }
                "restore" -> {
                    requireNotNull(password) { "操作已过期，请重新输入密码" }
                    val bytes = app.contentResolver.openInputStream(uri)?.use { it.readLimited(EncryptedBackup.MAX_BYTES + 52) } ?: error("无法读取备份")
                    val document = BackupCodec.decode(EncryptedBackup.decrypt(bytes, password))
                    document.attachments.forEach { (_, value) -> require(Base64.decode(value, Base64.NO_WRAP).size <= 20 * 1024 * 1024) { "备份中的图片过大" } }
                    restoreDocument = document
                    restorePreview.value = RestorePreview(document.snapshot.entries.size)
                }
                "csv" -> {
                    val text = BackupCodec.csv(app.repository.snapshot())
                    app.contentResolver.openOutputStream(uri, "wt")?.use { it.write(text.encodeToByteArray()) } ?: error("无法写入 CSV")
                    app.messages.value = "CSV 已导出（明文文件）"
                }
            }
        }.invokeOnCompletion { password?.fill('\u0000') }
    }
    fun cancelRestore() { restorePreview.value = null; restoreDocument = null }
    fun confirmRestore() {
        val document = restoreDocument ?: return
        cancelRestore()
        task {
            val staged = mutableListOf<File>()
            var committed = false
            try {
                val names = document.attachments.mapValues { (_, encoded) ->
                    val name = "${newId()}.img"
                    val file = File(app.attachmentDirectory, name)
                    staged += file
                    file.writeBytes(Base64.decode(encoded, Base64.NO_WRAP))
                    name
                }
                val snapshot = document.snapshot.copy(evidence = document.snapshot.evidence.map { evidence ->
                    evidence.copy(attachmentName = evidence.attachmentName?.let(names::getValue))
                })
                withContext(NonCancellable) {
                    app.repository.replace(snapshot)
                    committed = true
                    app.cleanupAttachments(snapshot)
                }
            } catch(e: Exception) {
                if (!committed) staged.forEach { it.delete() }
                throw e
            }
            app.messages.value = "账本已恢复"
        }
    }
    private fun task(block: suspend () -> Unit): Job = viewModelScope.launch(Dispatchers.IO) {
        if(!fileTaskMutex.tryLock()) { app.messages.value = "正在处理文件，请稍候"; return@launch }
        busy.value = true
        try { block() } catch(e: CancellationException) { throw e } catch(e: Exception) { app.messages.value = e.message ?: "文件操作失败" }
        finally { busy.value = false; fileTaskMutex.unlock() }
    }
    override fun onCleared() { cancelDocument(); cancelRestore(); super.onCleared() }
}
