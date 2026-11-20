@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package dev.leafledger.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.leafledger.core.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonObject

private val UiStateJson = Json(LedgerJson) { ignoreUnknownKeys = true }
private val RouteSaver = Saver<String, String>(
    save = { it },
    restore = { if (it == "识别结果") "明细" else it }
)

@Composable fun LedgerApp(repository:LedgerRepository,platform:PlatformActions) = LedgerTheme {
    val snapshot by repository.snapshots.collectAsState(initial=null)
    val keyboard=LocalSoftwareKeyboardController.current
    val scope=rememberCoroutineScope()
    val snackbar=remember { SnackbarHostState() }
    val billScroll=rememberLazyListState()
    var route by rememberSaveable(stateSaver=RouteSaver) {mutableStateOf("明细")}
    var editorJson by rememberSaveable {mutableStateOf<String?>(null)}
    var editorReturnRoute by rememberSaveable { mutableStateOf<String?>(null) }
    val session=remember(editorJson) {
        editorJson?.let { encoded ->
            runCatching {
                val state=UiStateJson.parseToJsonElement(encoded).jsonObject
                if (state["draftId"]?.let { it != JsonNull } == true) null
                else UiStateJson.decodeFromJsonElement<EntrySession>(state)
            }.getOrNull()
        }
    }
    val editor=session.takeIf { editorReturnRoute==null }
    var filterJson by rememberSaveable {mutableStateOf(LedgerJson.encodeToString(BillFilter()))}
    val filter=remember(filterJson) { UiStateJson.decodeFromString<BillFilter>(filterJson) }
    var searchOpen by rememberSaveable {mutableStateOf(false)}
    var focusSearch by rememberSaveable {mutableStateOf(false)}
    var query by rememberSaveable {mutableStateOf("")}
    val entriesByMonth=remember(snapshot?.entries) { snapshot?.entries.orEmpty().groupBy { it.monthKey() } }
    val billGroups=remember(entriesByMonth,filter,query) {
        entriesByMonth.keys.sortedDescending().mapNotNull { month ->
            val entries=entriesByMonth.getValue(month)
            val rows=filteredBills(entries,filter,query).sortedByDescending { it.occurredAt }
            if(rows.isEmpty()) null else BillMonth(
                month, rows,
                income = rows.filter {typeLabel(it.type)=="收入"}.sumOf {it.amount},
                expense = rows.filter {typeLabel(it.type)=="支出"}.sumOf {it.amount}
            )
        }
    }
    var choosingMonth by remember {mutableStateOf<String?>(null)}
    var filtersOpen by remember {mutableStateOf(false)}
    var moreOpen by remember {mutableStateOf(false)}
    var manageId by rememberSaveable {mutableStateOf<String?>(null)}
    var manageNew by rememberSaveable {mutableStateOf(false)}
    var manageDirty by rememberSaveable {mutableStateOf(false)}
    var removalOpen by rememberSaveable { mutableStateOf(false) }
    var removalError by remember { mutableStateOf<String?>(null) }
    var discard by rememberSaveable {mutableStateOf<String?>(null)}
    var password by remember {mutableStateOf<String?>(null)}
    var saving by remember {mutableStateOf(false)}
    val message by platform.messages.collectAsState()
    val fileBusy by platform.busy.collectAsState()
    val restore by platform.restorePreview.collectAsState()

    fun setEditor(value:EntrySession?) {editorJson=value?.let {LedgerJson.encodeToString(it)}}
    fun closeEditor() = setEditor(null)
    fun closeRemoval() { removalOpen = false; removalError = null }
    fun closeManage() {
        closeRemoval()
        manageNew=false;manageId=null;manageDirty=false
        editorReturnRoute?.let { route=it;editorReturnRoute=null }
    }
    fun selectMonth(value:String) {
        val index=billGroups.takeWhile { it.month!=value }.sumOf { 1+it.rows.size }
        billScroll.requestScrollToItem(index)
        choosingMonth=null
        keyboard?.hide()
    }
    fun closeSearch() {
        searchOpen=false
        focusSearch=false
        if(query.isNotEmpty()) billScroll.requestScrollToItem(0)
        query=""
        keyboard?.hide()
    }
    fun goBack():Boolean {
        if(saving) return true
        when {
            discard!=null -> discard=null
            filtersOpen -> filtersOpen=false
            choosingMonth!=null -> choosingMonth=null
            removalOpen -> closeRemoval()
            editor!=null -> if(editor.dirty()) discard="editor" else closeEditor()
            manageNew || manageId!=null -> if(manageDirty) discard="manage" else closeManage()
            route in listOf("分类","支付渠道") -> route="设置"
            route!="明细" -> route="明细"
            searchOpen -> closeSearch()
            else -> return false
        }
        return true
    }
    val back by rememberUpdatedState<()->Boolean>({goBack()})
    DisposableEffect(platform) {platform.setBackHandler {back()};onDispose {platform.setBackHandler(null)}}
    fun notify(text:String) {scope.launch {snackbar.showSnackbar(text)}}
    fun run(block:suspend ()->Unit) {
        if(saving) return
        saving=true
        scope.launch {
            try {block()} catch(e:CancellationException) {throw e} catch(e:Exception) {notify(e.message ?: "操作失败，请重试")}
            finally {saving=false}
        }
    }
    LaunchedEffect(message) {message?.let {snackbar.showSnackbar(it);platform.consumeMessage()}}
    LaunchedEffect(snapshot?.categories, snapshot?.channels, filter) {
        snapshot?.let { current ->
            val valid = filter.copy(
                categoryId = filter.categoryId?.takeIf { id -> current.categories.any { it.id == id } },
                channelId = filter.channelId?.takeIf { id -> current.channels.any { it.id == id } }
            )
            if (valid != filter) {
                filterJson = LedgerJson.encodeToString(valid)
                billScroll.requestScrollToItem(0)
            }
        }
    }
    val title=when {
        editor!=null -> if(editor.existing) "编辑流水" else "记一笔"
        manageNew -> "新增$route"
        manageId!=null -> "编辑$route"
        else -> route
    }
    val isHome=editor==null && route=="明细"
    val pageColor=if(isHome) MaterialTheme.colorScheme.background else MaterialTheme.colorScheme.surface
    Scaffold(modifier=Modifier.imePadding(),containerColor=pageColor,snackbarHost={SnackbarHost(snackbar,
        modifier=Modifier.padding(bottom=if(editor!=null) 80.dp else 0.dp))},
        topBar={
            val colors = TopAppBarDefaults.topAppBarColors(containerColor=pageColor)
            if (isHome) CenterAlignedTopAppBar(
                title = { Text("账单", fontWeight = FontWeight.SemiBold) },
                colors = colors,
                actions = {
                    IconButton(onClick = {
                        if (searchOpen) closeSearch() else { searchOpen = true; focusSearch = true }
                    }) { Icon(Icons.Outlined.Search, "搜索") }
                    IconButton(onClick = { keyboard?.hide(); filtersOpen = true }) {
                        BadgedBox(badge = { if (filter.count > 0) Badge { Text(filter.count.toString()) } }) {
                            Icon(Icons.Outlined.FilterList, "筛选")
                        }
                    }
                    Box {
                        IconButton(onClick = { moreOpen=true }) { Icon(Icons.Outlined.MoreVert,"更多") }
                        DropdownMenu(moreOpen, { moreOpen=false }) {
                            DropdownMenuItem(text = { Text("设置") }, onClick = { moreOpen=false;route="设置" })
                        }
                    }
                }
            ) else TopAppBar(
                title = { Text(title, fontWeight=FontWeight.SemiBold) },
                colors = colors,
                navigationIcon = { IconButton(onClick = { goBack() }) { Icon(Icons.AutoMirrored.Outlined.ArrowBack,"返回") } }
            )
        },
        floatingActionButton={if(editor==null && route=="明细" && snapshot!=null) ExtendedFloatingActionButton(
            onClick={keyboard?.hide();run {setEditor(newManualSession(repository.snapshot()))}},icon={Icon(Icons.Outlined.Add,null)},text={Text("记一笔")},
            containerColor=MaterialTheme.colorScheme.primary,contentColor=MaterialTheme.colorScheme.onPrimary,
            modifier=Modifier.heightIn(min=56.dp))}) {padding->
        val s=snapshot
        Column(Modifier.fillMaxSize().padding(padding)) {
            if(fileBusy) LinearProgressIndicator(Modifier.fillMaxWidth())
            if(s==null) {LinearProgressIndicator(Modifier.fillMaxWidth());return@Column}
            val icons = remember(s.categories) { s.categories.associate { it.id to iconForCategoryName(it.name) } }
            when {
                editor!=null -> key(editor.base.id) {
                    QuickEntryPage(editor,s,platform,saving,
                        onChange={setEditor(it)},onSave={run {
                            val row=editor.entry(repository.snapshot())
                            repository.saveManualEntry(row)
                            closeEditor()
                            notify(if(filteredBills(listOf(row),filter,query).isNotEmpty()) "已保存" else "已保存，当前筛选未显示这笔记录")
                        }},
                        onDelete=if(editor.existing) {{run {repository.deleteEntry(editor.base.id);setEditor(null);notify("已删除")}}} else null,
                        onManage={ kind ->
                            editorReturnRoute=route
                            route=kind
                            manageNew=true
                            manageDirty=false
                        })
                }
                route=="明细" -> BillList(
                    snapshot = s,
                    icons = icons,
                    groups = billGroups,
                    onChooseMonth = { choosingMonth=it },
                    state = billScroll,
                    filter = filter,
                    query = query,
                    searchOpen = searchOpen,
                    focusSearch = focusSearch,
                    onSearchFocused = { focusSearch=false },
                    onQuery = { query=it;billScroll.requestScrollToItem(0) },
                    onCloseSearch = ::closeSearch,
                    onClearFilter = { filterJson=LedgerJson.encodeToString(BillFilter());billScroll.requestScrollToItem(0) },
                    onEdit = { setEditor(EntrySession(it,existing=true)) }
                )
                route=="设置" -> Column(Modifier.padding(20.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                    Text("记账管理",style=MaterialTheme.typography.titleMedium)
                    listOf("分类","支付渠道").forEach {name->OutlinedButton(onClick={route=name},modifier=Modifier.fillMaxWidth()){Text(name,Modifier.weight(1f));Icon(Icons.Outlined.ChevronRight,null)}}
                    HorizontalDivider();Text("本地数据",style=MaterialTheme.typography.titleMedium)
                    OutlinedButton(onClick={password="export"},modifier=Modifier.fillMaxWidth()){Text("导出加密备份")}
                    OutlinedButton(onClick={password="restore"},modifier=Modifier.fillMaxWidth()){Text("恢复备份")}
                    Text("数据保存在本机，卸载前请导出备份。",style=MaterialTheme.typography.bodySmall)
                }
                else -> if(manageNew || manageId!=null) key(route,manageId,manageNew) {
                    ManageForm(route,manageId,s,session?.type ?: EntryType.EXPENSE,
                        onCancel={goBack()},onDirtyChanged={manageDirty=it},
                        onRemove=if (manageId != null) {{ if (!saving) { keyboard?.hide(); removalOpen = true } }} else null,
                        onSave={name,type,active->run {
                        val id = manageId ?: newId()
                        if(route=="分类") {
                            val existing=s.categories.find { it.id==manageId }
                            repository.saveCategory(Category(id,name,type,active=active,sort=existing?.sort ?: 0))
                            if (editorReturnRoute != null && session != null && !session.existing &&
                                active && session.type == type && session.categoryId == null) {
                                setEditor(session.copy(categoryId = id))
                            }
                        } else {
                            val existing=s.channels.find { it.id==manageId }
                            repository.saveChannel(Channel(id,name,active,sort=existing?.sort ?: 0))
                            if (editorReturnRoute != null && session != null && !session.existing && active && session.channelId == null) {
                                setEditor(session.copy(channelId = id))
                            }
                        }
                        keyboard?.hide()
                        closeManage()
                    }})
                } else LazyColumn(contentPadding=PaddingValues(20.dp)) {
                    item {Button(onClick={manageNew=true;manageDirty=false},modifier=Modifier.fillMaxWidth()){Text("新增$route")};Spacer(Modifier.height(16.dp))}
                    if(route=="分类") items(s.categories.sortedWith(compareBy<Category> {it.type}.thenBy {it.name}),key={it.id}) {c->ListItem(headlineContent={Text(categoryName(c.id,s))},supportingContent={Text(typeLabel(c.type)+if(c.active) "" else " · 已停用")},
                        leadingContent={CategoryBadge(icons[c.id] ?: CategoryIcon.RECEIPT, 36.dp)},
                        trailingContent={Icon(Icons.Outlined.ChevronRight,null)},modifier=Modifier.clickable {manageId=c.id;manageDirty=false});HorizontalDivider()}
                    else items(s.channels.sortedBy {it.name},key={it.id}) {c->ListItem(headlineContent={Text(c.name)},supportingContent={Text(if(c.active) "启用" else "已停用")},
                        trailingContent={Icon(Icons.Outlined.ChevronRight,null)},modifier=Modifier.clickable {manageId=c.id;manageDirty=false});HorizontalDivider()}
                }
            }
        }
    }
    snapshot?.let {s->if(filtersOpen) BillFilterSheet(s,filter,{filtersOpen=false}){
        filterJson=LedgerJson.encodeToString(it);filtersOpen=false;billScroll.requestScrollToItem(0)
    }}
    choosingMonth?.let { BillMonthSheet(billGroups.map { group -> group.month },it,{choosingMonth=null},::selectMonth) }
    if (removalOpen) {
        val id = manageId
        val current = snapshot
        if (id != null && current != null) {
            CatalogRemovalDialog(route, id, current, saving, removalError, ::closeRemoval) { target ->
                run {
                    removalError = null
                    try {
                        if (route == "分类") repository.removeCategory(id, target)
                        else repository.removeChannel(id, target)
                        closeManage()
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        removalError = e.message ?: "移除失败，请重试"
                    }
                }
            }
        }
    }
    if(discard!=null) AlertDialog(onDismissRequest={discard=null},title={Text("放弃未保存的修改？")},
        text={Text("已填写的内容尚未保存。")},confirmButton={TextButton(onClick={if(discard=="editor") closeEditor() else closeManage();discard=null}){Text("放弃修改")}},
        dismissButton={TextButton(onClick={discard=null}){Text("继续编辑")}})
    password?.let {mode->PasswordDialog(mode,{password=null}){p->password=null;if(mode=="export") platform.exportBackup(p) else platform.chooseRestore(p)}}
    restore?.let {p->AlertDialog(onDismissRequest=platform::cancelRestore,title={Text("覆盖恢复账本？")},
        text={Text("已校验 ${p.entries} 笔流水。恢复将覆盖当前账本。")},
        confirmButton={TextButton(onClick=platform::confirmRestore){Text("确认覆盖")}},dismissButton={TextButton(onClick=platform::cancelRestore){Text("取消")}})}
}
