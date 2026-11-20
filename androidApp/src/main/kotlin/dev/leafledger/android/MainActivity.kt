package dev.leafledger.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import dev.leafledger.ui.*

class MainActivity : ComponentActivity() {
    private val model: LedgerViewModel by viewModels()
    private var backHandler: (() -> Boolean)? = null
    private val app get() = application as LedgerApplication
    private val createBackup = registerForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { model.documentSelected(it) }
    private val openBackup = registerForActivityResult(ActivityResultContracts.OpenDocument()) { model.documentSelected(it) }
    private val createCsv = registerForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { model.documentSelected(it) }
    private val actions by lazy { object : PlatformActions {
        override val messages = app.messages
        override val restorePreview = model.restorePreview
        override val busy = model.busy
        override fun consumeMessage() { app.messages.value = null }
        override fun setBackHandler(handler: (() -> Boolean)?) { backHandler = handler }
        override fun selectDateTime(current: Long, zone: String, onSelected: (Long) -> Unit) {
            val calendar = java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone(zone)).apply { timeInMillis = current }
            android.app.DatePickerDialog(this@MainActivity, { _, year, month, day ->
                android.app.TimePickerDialog(this@MainActivity, { _, hour, minute ->
                    calendar.set(year, month, day, hour, minute, 0)
                    calendar.set(java.util.Calendar.MILLISECOND, 0)
                    onSelected(calendar.timeInMillis)
                }, calendar.get(java.util.Calendar.HOUR_OF_DAY), calendar.get(java.util.Calendar.MINUTE), true).show()
            }, calendar.get(java.util.Calendar.YEAR), calendar.get(java.util.Calendar.MONTH), calendar.get(java.util.Calendar.DAY_OF_MONTH)).show()
        }
        override fun exportBackup(password: String) { model.prepareDocument("export", password); createBackup.launch("${getString(R.string.app_name)}-${System.currentTimeMillis()}.leafbackup") }
        override fun chooseRestore(password: String) { model.prepareDocument("restore", password); openBackup.launch(arrayOf("application/octet-stream", "*/*")) }
        override fun confirmRestore() { model.confirmRestore() }
        override fun cancelRestore() { model.cancelRestore() }
        override fun exportCsv() { model.prepareDocument("csv", null); createCsv.launch("${getString(R.string.app_name)}-${System.currentTimeMillis()}.csv") }
    } }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        onBackPressedDispatcher.addCallback(this, object : androidx.activity.OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (backHandler?.invoke() != true) {
                    isEnabled = false
                    try { onBackPressedDispatcher.onBackPressed() } finally { isEnabled = true }
                }
            }
        })
        setContent { LedgerApp(app.repository, actions) }
    }
}
