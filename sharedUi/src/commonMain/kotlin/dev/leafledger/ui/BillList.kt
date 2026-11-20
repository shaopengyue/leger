@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
package dev.leafledger.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import dev.leafledger.core.*

internal fun LedgerEntry.monthKey() = localDate().toString().substringBeforeLast('-')

internal fun monthLabel(month: String): String =
    "${month.substringBeforeLast('-')}年${month.substringAfterLast('-').toInt()}月"

internal fun filteredBills(entries:List<LedgerEntry>,filter:BillFilter,query:String) = entries.filter { e ->
    (query.isBlank() || (e.subject+" "+e.merchant+" "+e.note).contains(query,true)) &&
        (filter.type==null || typeLabel(e.type)==filter.type) &&
        (filter.channelId==null || e.channelId==filter.channelId) &&
        (filter.categoryId==null || e.categoryId==filter.categoryId)
}

internal data class BillMonth(val month: String, val rows: List<LedgerEntry>, val income: Long, val expense: Long)

@Composable internal fun BillList(snapshot:LedgerSnapshot,icons:Map<String,CategoryIcon>,groups:List<BillMonth>,onChooseMonth:(String)->Unit,
    state:LazyListState,filter:BillFilter,query:String,searchOpen:Boolean,focusSearch:Boolean,onSearchFocused:()->Unit,
    onQuery:(String)->Unit,onCloseSearch:()->Unit,onClearFilter:()->Unit,onEdit:(LedgerEntry)->Unit) {
    val focus=remember { FocusRequester() }
    val keyboard=LocalSoftwareKeyboardController.current
    val rowInset = 16.dp
    val badgeSize = 44.dp
    val rowGap = 14.dp
    val incomeColor = if (isSystemInDarkTheme()) Color(0xFFFFD35C) else Color(0xFF8A5A00)
    LaunchedEffect(searchOpen,focusSearch) {
        if(searchOpen && focusSearch) {
            withFrameNanos { }
            focus.requestFocus()
            keyboard?.show()
            onSearchFocused()
        }
    }
    Column(Modifier.fillMaxSize()) {
        if(searchOpen) {
            OutlinedTextField(query,onQuery,placeholder={Text("查找事项或商户")},singleLine=true,
                trailingIcon={IconButton(onClick={keyboard?.hide();onCloseSearch()}){Icon(Icons.Outlined.Close,"关闭搜索")}},
                modifier=Modifier.fillMaxWidth().padding(horizontal=20.dp,vertical=8.dp).focusRequester(focus))
        }
        if(filter.count>0) {
            InputChip(selected=true,onClick=onClearFilter,label={Text("已筛选 ${filter.count} 项 · 清除")},
                trailingIcon={Icon(Icons.Outlined.Close,null,Modifier.size(16.dp))},modifier=Modifier.padding(horizontal=20.dp))
        }
        LazyColumn(state=state,contentPadding=PaddingValues(bottom=96.dp),modifier=Modifier.fillMaxWidth().weight(1f)) {
            groups.forEach { group ->
                stickyHeader(key="month-${group.month}") {
                    Column(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.background)) {
                        FlowRow(
                            Modifier.fillMaxWidth().padding(horizontal=16.dp,vertical=6.dp),
                            horizontalArrangement=Arrangement.SpaceBetween,
                            verticalArrangement=Arrangement.spacedBy(4.dp),
                            itemVerticalAlignment=Alignment.CenterVertically
                        ) {
                            TextButton(onClick={keyboard?.hide();onChooseMonth(group.month)},
                                contentPadding=PaddingValues(horizontal=4.dp)) {
                                Text(monthLabel(group.month),style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.Medium)
                                Icon(Icons.Outlined.ExpandMore,null,Modifier.size(20.dp))
                            }
                            Text(
                                buildAnnotatedString {
                                    append("支出 ¥${Money.format(group.expense)}  收入 ")
                                    withStyle(SpanStyle(color = incomeColor)) { append("¥${Money.format(group.income)}") }
                                },
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        HorizontalDivider()
                    }
                }
                items(group.rows, key = { it.id }) { entry ->
                    val category = categoryName(entry.categoryId, snapshot)
                    val subject = entry.subject.ifBlank { entry.merchant }.trim()
                    val supportingColor = MaterialTheme.colorScheme.onSurfaceVariant
                    val supportingStyle = MaterialTheme.typography.bodySmall
                    Row(
                        Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface)
                            .clickable { keyboard?.hide(); onEdit(entry) }
                            .padding(horizontal = rowInset, vertical = 15.dp),
                        horizontalArrangement = Arrangement.spacedBy(rowGap)
                    ) {
                        Box(Modifier.align(Alignment.CenterVertically)) {
                            CategoryBadge(entry.categoryId?.let(icons::get) ?: CategoryIcon.RECEIPT, badgeSize)
                        }
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                            Text(category, maxLines = 2, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Medium)
                            if (subject.isNotEmpty() && subject != category) {
                                Text(subject, style = supportingStyle, color = supportingColor, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            Text(formatTime(entry.occurredAt, entry.zoneId).drop(5), style = supportingStyle, color = supportingColor)
                        }
                        Column(Modifier.widthIn(max = 150.dp), horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(5.dp)) {
                            val income = typeLabel(entry.type) == "收入"
                            Text(
                                (if (income) "+" else "−") + Money.format(entry.amount),
                                fontWeight = FontWeight.SemiBold,
                                color = if (income) incomeColor else MaterialTheme.colorScheme.onSurface
                            )
                            Text(snapshot.channels.find { it.id == entry.channelId }?.name ?: "未指定渠道",
                                style = supportingStyle, color = supportingColor, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                    }
                    HorizontalDivider(Modifier.padding(start = rowInset + badgeSize + rowGap))
                }
            }
            if(groups.isEmpty()) item(key="empty") {
                Column(Modifier.fillMaxWidth().padding(horizontal=24.dp,vertical=44.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(8.dp)) {
                    Text(if(filter.count>0 || query.isNotEmpty()) "没有匹配的流水" else "暂无流水")
                    Text(if(filter.count>0 || query.isNotEmpty()) "试试修改搜索或筛选条件" else "点击右下角“记一笔”开始",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable internal fun BillMonthSheet(months:List<String>,selected:String?,onDismiss:()->Unit,onSelect:(String)->Unit) {
    ModalBottomSheet(onDismissRequest=onDismiss,sheetState=rememberModalBottomSheetState(skipPartiallyExpanded=true)) {
        Text("选择月份",style=MaterialTheme.typography.titleLarge,modifier=Modifier.padding(horizontal=20.dp,vertical=12.dp))
        LazyColumn(Modifier.fillMaxWidth().heightIn(max=420.dp)) {
            items(months,key={it}) { month ->
                ListItem(
                    headlineContent={Text(monthLabel(month))},
                    trailingContent={if(month==selected) Icon(Icons.Outlined.Check,"当前月份")},
                    modifier=Modifier.clickable {onSelect(month)}
                )
            }
        }
    }
}

@Composable internal fun BillFilterSheet(snapshot:LedgerSnapshot,initial:BillFilter,onDismiss:()->Unit,onApply:(BillFilter)->Unit) {
    var draft by remember(initial) { mutableStateOf(initial) }
    ModalBottomSheet(onDismissRequest=onDismiss,sheetState=rememberModalBottomSheetState(skipPartiallyExpanded=true)) {
        Column(
            Modifier.fillMaxWidth().heightIn(max = 620.dp).padding(start = 20.dp, end = 20.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text("选择筛选项", style = MaterialTheme.typography.titleLarge)
            Column(
                Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                FilterOptionGroup("收支类型", draft.type, listOf("支出" to "支出", "收入" to "收入")) {
                    draft = draft.copy(type = it)
                }
                FilterOptionGroup("分类", draft.categoryId, snapshot.categories.map { it.id to it.name }) {
                    draft = draft.copy(categoryId = it)
                }
                FilterOptionGroup("支付渠道", draft.channelId, snapshot.channels.map { it.id to it.name }) {
                    draft = draft.copy(channelId = it)
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                FilledTonalButton(onClick = onDismiss, modifier = Modifier.weight(1f)) { Text("取消") }
                Button(onClick = { onApply(draft) }, modifier = Modifier.weight(1f)) { Text("确定") }
            }
        }
    }
}

@Composable
private fun FilterOptionGroup(
    label: String, selected: String?, options: List<Pair<String, String>>, onSelect: (String?) -> Unit
) {
    val spacing = 8.dp
    val minWidth = 100.dp * LocalDensity.current.fontScale.coerceAtLeast(1f)
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val columns = ((maxWidth + spacing) / (minWidth + spacing)).toInt().coerceIn(1, 3)
        Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(spacing)) {
            Text(label, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            (listOf(null to "全部") + options).chunked(columns).forEach { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(spacing)) {
                    row.forEach { (id, name) ->
                        val checked = id == selected
                        val shape = RoundedCornerShape(8.dp)
                        Surface(
                            modifier = Modifier.weight(1f),
                            shape = shape,
                            color = if (checked) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer,
                            contentColor = if (checked) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                        ) {
                            Box(
                                Modifier.selectable(checked, role = Role.RadioButton, onClick = { onSelect(id) })
                                    .heightIn(min = 48.dp).padding(8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(name, textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis,
                                    fontWeight = if (checked) FontWeight.SemiBold else FontWeight.Normal)
                            }
                        }
                    }
                    repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}
