@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
package dev.leafledger.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.leafledger.core.*

@Composable internal fun QuickEntryPage(
    session:EntrySession,snapshot:LedgerSnapshot,platform:PlatformActions,saving:Boolean,
    onChange:(EntrySession)->Unit,onSave:()->Unit,onDelete:(()->Unit)?,
    onManage:(String)->Unit
) {
    val keyboard=LocalSoftwareKeyboardController.current
    val focus=remember { FocusRequester() }
    var amountField by rememberSaveable(stateSaver = TextFieldValue.Saver) {
        mutableStateOf(TextFieldValue(session.amount, TextRange(session.amount.length)))
    }
    val sum = remember(session.amount) { Money.parseAddition(session.amount) }
    val hasAddition = session.amount.any { it == '+' || it == '＋' }
    val recent = remember(snapshot, session.type) { frequentCategories(snapshot, session.type) }
    val recentChannels = remember(snapshot) { frequentChannels(snapshot) }
    var error by remember { mutableStateOf<String?>(null) }
    var delete by remember { mutableStateOf(false) }
    fun save() {
        if(saving) return
        try { session.entry(snapshot);error=null;keyboard?.hide();onSave() } catch(e:Exception) { error=e.message }
    }
    Column(Modifier.fillMaxSize().imePadding()) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal=20.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                listOf(EntryType.EXPENSE,EntryType.INCOME).forEachIndexed { i,t ->
                    SegmentedButton(selected=session.type==t,onClick={
                        if (t != session.type) {
                            val first = frequentCategories(snapshot, t).firstOrNull()?.id
                            onChange(session.copy(type = t, categoryId = first))
                            error = null
                        }
                    },
                        enabled=!saving,shape=SegmentedButtonDefaults.itemShape(i,2)) { Text(typeLabel(t)) }
                }
            }
            Text("分类 *", style = MaterialTheme.typography.labelLarge)
            val selected = snapshot.categories.find { it.id == session.categoryId }
            key(session.type) {
                QuickChoices((recent + listOfNotNull(selected)).distinctBy { it.id }.map { it.id to it.name },
                    session.categoryId, !saving) { id ->
                    if (id != session.categoryId) onChange(session.copy(categoryId = id))
                    error = null
                }
            }
            if (recent.isEmpty()) TextButton(onClick = { keyboard?.hide(); onManage("分类") }, enabled = !saving) {
                Text("去设置添加分类")
            }
            val channelLabel = if (session.channelId == null) "支付渠道（可选） · 未指定" else "支付渠道（可选）"
            Text(channelLabel, style = MaterialTheme.typography.labelLarge)
            val selectedChannel = snapshot.channels.find { it.id == session.channelId }
            QuickChoices((recentChannels + listOfNotNull(selectedChannel)).distinctBy { it.id }.map { it.id to it.name },
                session.channelId, !saving) { id ->
                onChange(session.copy(channelId = id.takeUnless { it == session.channelId }))
                error = null
            }
            if (recentChannels.isEmpty()) TextButton(onClick = { keyboard?.hide(); onManage("支付渠道") }, enabled = !saving) {
                Text("去设置添加渠道")
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), itemVerticalAlignment = Alignment.CenterVertically) {
                Text("时间", style = MaterialTheme.typography.labelLarge)
                AssistChip(
                    onClick = {
                        keyboard?.hide()
                        platform.selectDateTime(session.time, session.base.zoneId) { onChange(session.copy(time = it)) }
                    },
                    enabled = !saving,
                    leadingIcon = { Icon(Icons.Outlined.Schedule, null, Modifier.size(18.dp)) },
                    label = { Text(formatTime(session.time, session.base.zoneId)) }
                )
            }
            TextField(value=amountField,onValueChange={
                amountField=it
                if (session.amount != it.text) onChange(session.copy(amount=it.text))
                error=null
            },
                modifier=Modifier.fillMaxWidth().focusRequester(focus),singleLine=true,
                label={Text("实际金额")},prefix={Text("¥ ",style=MaterialTheme.typography.headlineLarge)},
                textStyle=MaterialTheme.typography.headlineLarge.copy(fontWeight=FontWeight.SemiBold),
                colors=TextFieldDefaults.colors(focusedContainerColor=MaterialTheme.colorScheme.surfaceContainer,
                    unfocusedContainerColor=MaterialTheme.colorScheme.surfaceContainer),
                trailingIcon={
                    IconButton(enabled=!saving && sum!=null,onClick={
                        val expression = session.amount.trimEnd() + "+"
                        amountField = TextFieldValue(expression, TextRange(expression.length))
                        onChange(session.copy(amount=expression))
                        error=null
                        focus.requestFocus()
                        keyboard?.show()
                    }) { Icon(Icons.Outlined.Add, "加一项金额") }
                },
                keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Decimal,imeAction=ImeAction.Done),
                keyboardActions=KeyboardActions(onDone={save()}),enabled=!saving)
            OutlinedTextField(
                session.subject, { onChange(session.copy(subject = it)) }, label = { Text("事项（可选，如午饭）") },
                supportingText = { Text("留空时使用分类名称") }, singleLine = true, enabled = !saving,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                session.merchant, { onChange(session.copy(merchant = it)) }, label = { Text("商户（可选）") },
                singleLine = true, enabled = !saving, modifier = Modifier.fillMaxWidth()
            )
            if(onDelete!=null) TextButton(onClick={delete=true},enabled=!saving){Text("删除流水",color=MaterialTheme.colorScheme.error)}
        }
        Surface(shadowElevation=3.dp) {
            Column(Modifier.fillMaxWidth().padding(horizontal=20.dp,vertical=10.dp)) {
                val feedback = error ?: if (hasAddition) {
                    sum?.let { "合计 ¥${Money.format(it)}" } ?: "请补全金额"
                } else null
                feedback?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (error != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 6.dp)
                    )
                }
                Button(onClick={save()},enabled=!saving,modifier=Modifier.fillMaxWidth().heightIn(min=52.dp)) {
                    Text(if(saving) "保存中…" else "保存")
                }
            }
        }
    }
    if(delete) AlertDialog(onDismissRequest={delete=false},title={Text("删除这笔流水？")},
        confirmButton={TextButton(onClick={delete=false;onDelete?.invoke()}){Text("删除")}},dismissButton={TextButton(onClick={delete=false}){Text("取消")}})
}

@Composable
private fun QuickChoices(
    options: List<Pair<String, String>>, selected: String?, enabled: Boolean, onSelect: (String) -> Unit
) {
    if (options.isEmpty()) return
    val state = rememberLazyListState(initialFirstVisibleItemIndex = options.indexOfFirst { it.first == selected }.coerceAtLeast(0))
    LazyRow(
        state = state,
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(options, key = { it.first }) { (id, name) ->
            FilterChip(
                modifier = Modifier.widthIn(max = 200.dp),
                selected = id == selected,
                onClick = { onSelect(id) },
                enabled = enabled,
                label = { Text(name, maxLines = 1, overflow = TextOverflow.Ellipsis) }
            )
        }
    }
}
