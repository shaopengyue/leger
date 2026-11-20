package dev.leafledger.android

import android.app.Application
import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import dev.leafledger.core.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import java.io.File
import java.io.InputStream

class LedgerApplication : Application() {
    val messages = MutableStateFlow<String?>(null)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO + CoroutineExceptionHandler { _, error ->
        messages.value = error.message ?: "操作失败，请重试"
    })
    lateinit var repository: LedgerRepository; private set
    lateinit var ready: Deferred<Unit>; private set

    // Older encrypted backups may still contain attachments.
    val attachmentDirectory get() = File(noBackupFilesDir, "captures").apply { mkdirs() }

    override fun onCreate() {
        super.onCreate()
        val database = Room.databaseBuilder<LedgerDatabase>(this, File(noBackupFilesDir, "ledger.db").absolutePath)
            .addMigrations(Migration1To2).setDriver(BundledSQLiteDriver()).setQueryCoroutineContext(Dispatchers.IO).build()
        repository = LedgerRepository(database)
        ready = scope.async { repository.initialize() }
    }

    fun cleanupAttachments(snapshot: LedgerSnapshot) {
        val needed = snapshot.evidence.mapNotNull { it.attachmentName }.toSet()
        attachmentDirectory.listFiles()?.filter { it.isFile && it.name !in needed }?.forEach { it.delete() }
    }
}

fun InputStream.readLimited(max: Int): ByteArray {
    val output = java.io.ByteArrayOutputStream()
    val buffer = ByteArray(8192)
    while(true) {
        val n = read(buffer); if(n < 0) break
        require(output.size() + n <= max) { "文件过大，请选择较小的文件" }
        output.write(buffer, 0, n)
    }
    return output.toByteArray()
}
