@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package dev.leafledger.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import dev.leafledger.core.*


@Composable
internal fun ManageForm(
    kind: String, id: String?, snapshot: LedgerSnapshot, defaultType: EntryType,
    onCancel: () -> Unit, onSave: (String, EntryType, Boolean) -> Unit, onDirtyChanged: (Boolean) -> Unit,
    onRemove: (() -> Unit)? = null
) {
    val category = snapshot.categories.find { it.id == id }
    val channel = snapshot.channels.find { it.id == id }
    val initialName = (category?.name ?: channel?.name).orEmpty()
    val initialType = category?.type ?: defaultType
    val initialActive = category?.active ?: channel?.active ?: true
    var name by rememberSaveable { mutableStateOf(initialName) }
    var type by rememberSaveable { mutableStateOf(initialType) }
    var active by rememberSaveable { mutableStateOf(initialActive) }
    LaunchedEffect(name, type, active) {
        onDirtyChanged(name != initialName || type != initialType || active != initialActive)
    }
    Column(Modifier.padding(20.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(16.dp)){
        OutlinedTextField(name,{name=it},label={Text("名称")},singleLine=true,modifier=Modifier.fillMaxWidth())
        if(kind=="分类") Choice("类型",type,listOf(EntryType.EXPENSE to "支出",EntryType.INCOME to "收入")){type=it}
        Row(verticalAlignment=Alignment.CenterVertically){Text("启用",Modifier.weight(1f));Switch(active,{active=it})}
        Button(onClick={onSave(name.trim(),type,active)},enabled=name.isNotBlank(),modifier=Modifier.fillMaxWidth()){Text("保存")}
        TextButton(onClick=onCancel){Text("取消")}
        if (onRemove != null) {
            TextButton(onClick = onRemove) { Text("移除$kind", color = MaterialTheme.colorScheme.error) }
        }
    }
}

@Composable
internal fun CatalogRemovalDialog(
    kind: String, id: String, snapshot: LedgerSnapshot, saving: Boolean, error: String?,
    onDismiss: () -> Unit, onConfirm: (String?) -> Unit
) {
    val category = snapshot.categories.find { it.id == id }
    val channel = snapshot.channels.find { it.id == id }
    val name = (if (kind == "分类") category?.name else channel?.name) ?: return
    val usedCount = if (kind == "分类") snapshot.entries.count { it.categoryId == id }
        else snapshot.entries.count { it.channelId == id }
    val hasRules = kind == "分类" && snapshot.rules.any { it.categoryId == id }
    val needsTarget = usedCount > 0 || hasRules
    val targets = if (kind == "分类") {
        snapshot.categories.filter { it.id != id && it.active && it.type == category?.type }.map { it.id to it.name }
    } else {
        snapshot.channels.filter { it.id != id && it.active }.map { it.id to it.name }
    }.sortedBy { it.second }
    var target by rememberSaveable(id) { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = { if (!saving) onDismiss() },
        title = { Text("移除$kind") },
        text = {
            Column(
                Modifier.heightIn(max = 300.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (needsTarget) {
                    val references = if (hasRules) "及相关旧商户规则" else ""
                    Text("「$name」关联 $usedCount 笔流水$references。请选择迁移目标，迁移后删除原$kind。")
                    if (targets.isEmpty()) {
                        Text(if (kind == "分类") "请先新增或启用另一项同收支类型的分类。" else "请先新增或启用另一项支付渠道。")
                    } else {
                        Choice("迁移目标", target, targets) {
                            if (!saving) target = it
                        }
                    }
                } else {
                    Text("确定删除「$name」？")
                }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !saving) { Text("取消") } },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(if (needsTarget) target else null) },
                enabled = !saving && (!needsTarget || targets.any { it.first == target }),
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
            ) { Text(if (needsTarget) "迁移并删除" else "删除") }
        }
    )
}
@Composable internal fun <T> Choice(label: String, selected: T, options: List<Pair<T, String>>, onSelect: (T) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box(Modifier.fillMaxWidth()) {
        OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(14.dp)) {
            Column(Modifier.weight(1f)) {
                Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(options.firstOrNull { it.first == selected }?.second ?: "请选择", modifier = Modifier.fillMaxWidth())
            }
            Icon(Icons.Outlined.ExpandMore, null)
        }
        DropdownMenu(expanded, { expanded = false }, modifier = Modifier.heightIn(max = 320.dp)) { options.forEach { (id, text) -> DropdownMenuItem(text = { Text(text) }, onClick = { onSelect(id); expanded = false }) } }
    }
}

@Composable internal fun PasswordDialog(mode: String, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var password by remember { mutableStateOf("") }
    var repeat by remember { mutableStateOf("") }
    val exporting = mode == "export"
    AlertDialog(onDismissRequest = onDismiss, title = { Text(if(exporting) "设置备份密码" else "输入备份密码") }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(if(exporting) "至少 8 个字符。密码不会保存，请妥善保管。" else "选择文件并校验后，再确认是否覆盖当前账本。")
            OutlinedTextField(password, { password = it }, label = { Text("密码") }, visualTransformation = PasswordVisualTransformation(), singleLine = true)
            if(exporting) OutlinedTextField(repeat, { repeat = it }, label = { Text("再次输入") }, visualTransformation = PasswordVisualTransformation(), singleLine = true)
        }
    }, confirmButton = { Button(onClick = { onConfirm(password) }, enabled = password.length >= 8 && (!exporting || password == repeat)) { Text(if(exporting) "选择保存位置" else "选择备份文件") } }, dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } })
}
