package io.github.walterfr.jotmd.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import io.github.walterfr.jotmd.core.exportToHtml
import io.github.walterfr.jotmd.core.exportToPdf
import io.github.walterfr.jotmd.core.findInDocument
import io.github.walterfr.jotmd.editor.EditorScreen
import io.github.walterfr.jotmd.editor.MdTokens
import io.github.walterfr.jotmd.editor.TAG_URI
import io.github.walterfr.jotmd.editor.WIKI_URI
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.awt.Desktop
import java.awt.FileDialog
import java.awt.Frame
import java.io.FilenameFilter
import java.net.URI
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import javax.swing.JFileChooser
import javax.swing.SwingUtilities
import javax.swing.UIManager
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

private const val AUTOSAVE_DELAY_MS = 1000L

fun main() = application {
    val tabManager = remember {
        DesktopTabManager().apply {
            openInitial("typora-ref.md", loadBundledReferenceDoc())
        }
    }
    val scope = rememberCoroutineScope()
    var saveAction by remember { mutableStateOf<() -> Unit>({}) }
    var findAction by remember { mutableStateOf<() -> Unit>({}) }
    var escapeAction by remember { mutableStateOf<() -> Boolean>({ false }) }
    var isDark by remember { mutableStateOf(MdTokens.isDark) }
    val active = tabManager.activeTab
    val dirtyMark = if (active?.isDirty == true) "*" else ""

    Window(
        onCloseRequest = {
            tabManager.saveAll()
            exitApplication()
        },
        title = "JotMD — $dirtyMark${active?.title ?: "Sem título"}",
        state = rememberWindowState(width = 1120.dp, height = 760.dp),
        onPreviewKeyEvent = { event ->
            handleWindowShortcut(
                event = event,
                onSave = saveAction,
                onFind = findAction,
                onToggleSource = { active?.editorState?.toggleSourceMode() },
                onEscape = escapeAction,
            )
        },
    ) {
        MaterialTheme(colorScheme = if (isDark) darkColorScheme() else lightColorScheme()) {
            DesktopShell(
                ownerWindow = window,
                tabManager = tabManager,
                isDark = isDark,
                onToggleTheme = {
                    isDark = !isDark
                    MdTokens.isDark = isDark
                },
                onRegisterSave = { saveAction = it },
                onRegisterFind = { findAction = it },
                onRegisterEscape = { escapeAction = it },
                onLaunch = { block -> scope.launch { block() } },
            )
        }
    }
}

private fun handleWindowShortcut(
    event: KeyEvent,
    onSave: () -> Unit,
    onFind: () -> Unit,
    onToggleSource: () -> Unit,
    onEscape: () -> Boolean,
): Boolean {
    if (event.type != KeyEventType.KeyDown) return false
    return when {
        event.isCtrlPressed && event.key == Key.S -> {
            onSave()
            true
        }
        event.isCtrlPressed && event.key == Key.F -> {
            onFind()
            true
        }
        event.isCtrlPressed && event.key == Key.Slash -> {
            onToggleSource()
            true
        }
        event.key == Key.Escape -> onEscape()
        else -> false
    }
}

@Composable
private fun DesktopShell(
    ownerWindow: Frame,
    tabManager: DesktopTabManager,
    isDark: Boolean,
    onToggleTheme: () -> Unit,
    onRegisterSave: (() -> Unit) -> Unit,
    onRegisterFind: (() -> Unit) -> Unit,
    onRegisterEscape: (() -> Boolean) -> Unit,
    onLaunch: (suspend () -> Unit) -> Unit,
) {
    var rootDir by remember { mutableStateOf<Path?>(null) }
    var entries by remember { mutableStateOf<List<DesktopDocEntry>>(emptyList()) }
    var sidebarOpen by remember { mutableStateOf(true) }
    var query by remember { mutableStateOf("") }
    var hits by remember { mutableStateOf<List<DesktopSearchHit>>(emptyList()) }
    var notice by remember { mutableStateOf<String?>(null) }
    var noticeIsError by remember { mutableStateOf(false) }
    var refreshNonce by remember { mutableStateOf(0) }
    var showFindBar by remember { mutableStateOf(false) }
    var findQuery by remember { mutableStateOf("") }
    var replaceQuery by remember { mutableStateOf("") }
    var matchCursor by remember { mutableStateOf(0) }
    val listStates = remember { mutableMapOf<Long, LazyListState>() }

    val activeTab = tabManager.activeTab
    val docMatches = remember(activeTab?.id, activeTab?.editorState?.doc, findQuery) {
        val doc = activeTab?.editorState?.doc ?: return@remember emptyList()
        findInDocument(doc, findQuery)
    }

    LaunchedEffect(docMatches) {
        if (docMatches.isEmpty() || matchCursor >= docMatches.size) {
            matchCursor = 0
        }
    }

    fun showNotice(message: String?, isError: Boolean = false) {
        notice = message
        noticeIsError = message != null && isError
    }

    fun openFile(path: Path) {
        val opened = tabManager.openOrFocus(path)
        if (opened == null) {
            showNotice("Não foi possível abrir: ${path.fileName}", isError = true)
        } else {
            showNotice(null)
        }
    }

    fun triggerSave() {
        val tab = tabManager.activeTab ?: return
        if (tab.file != null) {
            onLaunch {
                val wrote = withContext(Dispatchers.IO) { saveTabIfChanged(tab) }
                if (wrote) refreshNonce++
                if (tab.saveFailed) showNotice("Erro ao salvar", isError = true) else showNotice(null)
            }
        } else {
            onLaunch {
                val chosen = pickMarkdownFile(ownerWindow, FileDialog.SAVE, rootDir, tab.title)
                    ?: return@onLaunch
                val ok = withContext(Dispatchers.IO) { tabManager.saveTabAs(tab, chosen) }
                if (ok) {
                    refreshNonce++
                    showNotice(null)
                } else {
                    showNotice("Erro ao salvar", isError = true)
                }
            }
        }
    }

    fun jumpToMatch(targetIndex: Int) {
        val tab = tabManager.activeTab ?: return
        if (docMatches.isEmpty()) return
        val wrapped = ((targetIndex % docMatches.size) + docMatches.size) % docMatches.size
        matchCursor = wrapped
        val match = docMatches[wrapped]
        tab.editorState.focusMatch(match.blockIndex, match.rangeInBlock)
        onLaunch { listStates[tab.id]?.animateScrollToItem(match.blockIndex) }
    }

    onRegisterSave(::triggerSave)
    onRegisterFind { showFindBar = true }
    onRegisterEscape {
        if (showFindBar) {
            showFindBar = false
            true
        } else {
            false
        }
    }

    // Autosave de 1s após a última edição na aba ativa com arquivo associado.
    LaunchedEffect(activeTab?.id, activeTab?.editorState?.doc) {
        val tab = activeTab ?: return@LaunchedEffect
        if (tab.file == null) return@LaunchedEffect
        delay(AUTOSAVE_DELAY_MS)
        val wrote = withContext(Dispatchers.IO) { saveTabIfChanged(tab) }
        if (wrote) refreshNonce++
    }

    // Lista a pasta raiz ao trocar de diretório, abrir o painel ou salvar alterações.
    LaunchedEffect(rootDir, sidebarOpen, refreshNonce) {
        val dir = rootDir
        if (dir == null) {
            entries = emptyList()
            return@LaunchedEffect
        }
        entries = withContext(Dispatchers.IO) { listDesktopTree(dir) }
    }

    // Atualiza resultados de busca sem bloquear a thread de UI.
    LaunchedEffect(rootDir, query, refreshNonce) {
        val dir = rootDir
        if (dir == null || query.isBlank()) {
            hits = emptyList()
            return@LaunchedEffect
        }
        delay(150)
        hits = withContext(Dispatchers.IO) { searchDesktopFiles(dir, query) }
    }

    val onLink: (String) -> Unit = { uri ->
        when {
            uri.startsWith(WIKI_URI) -> {
                val target = uri.removePrefix(WIKI_URI)
                val match = resolveDesktopWikilink(target, entries)
                when {
                    target.isEmpty() -> Unit
                    rootDir == null || match == null -> showNotice("Nota não encontrada: $target", isError = true)
                    else -> openFile(match.path)
                }
            }
            uri.startsWith(TAG_URI) -> {
                query = "#" + uri.removePrefix(TAG_URI)
                sidebarOpen = true
            }
            else -> openExternalUri(uri)
        }
    }
    val onLinkRef = rememberUpdatedState(onLink)
    val uriHandler = remember {
        object : UriHandler {
            override fun openUri(uri: String) = onLinkRef.value(uri)
        }
    }

    Row(Modifier.fillMaxSize().background(MdTokens.background)) {
        if (sidebarOpen) {
            SidebarPanel(
                rootDir = rootDir,
                entries = entries,
                query = query,
                onQueryChange = { query = it },
                hits = hits,
                onPickFolder = {
                    onLaunch {
                        val chosen = pickDirectory(ownerWindow, rootDir) ?: return@onLaunch
                        rootDir = chosen
                        query = ""
                        showNotice(null)
                    }
                },
                onOpenPath = ::openFile,
            )
            Box(Modifier.fillMaxHeight().width(1.dp).background(MdTokens.tableBorder))
        }

        Column(Modifier.weight(1f).fillMaxHeight()) {
            HeaderBar(
                sidebarOpen = sidebarOpen,
                activeTab = activeTab,
                notice = notice,
                noticeIsError = noticeIsError,
                isDark = isDark,
                findOpen = showFindBar,
                onToggleSidebar = { sidebarOpen = !sidebarOpen },
                onOpenFile = {
                    onLaunch {
                        val chosen = pickMarkdownFile(ownerWindow, FileDialog.LOAD, rootDir) ?: return@onLaunch
                        openFile(chosen)
                    }
                },
                onNewTab = {
                    tabManager.newEmptyTab()
                    showNotice(null)
                },
                onSave = ::triggerSave,
                onToggleFind = { showFindBar = !showFindBar },
                onExportHtml = {
                    val tab = activeTab ?: return@HeaderBar
                    onLaunch {
                        val chosen = pickExportSaveFile(
                            owner = ownerWindow,
                            dialogTitle = "Exportar HTML",
                            initialDir = rootDir,
                            defaultTitle = tab.title,
                            extensions = listOf(".html", ".htm"),
                        ) ?: return@onLaunch
                        val html = exportToHtml(tab.editorState.text(), tab.title, isDark)
                        val ok = runCatching {
                            withContext(Dispatchers.IO) {
                                Files.writeString(chosen, html, StandardCharsets.UTF_8)
                            }
                        }.isSuccess
                        showNotice(
                            if (ok) "HTML exportado: ${chosen.fileName}" else "Erro ao exportar HTML",
                            isError = !ok,
                        )
                    }
                },
                onExportPdf = {
                    val tab = activeTab ?: return@HeaderBar
                    onLaunch {
                        val chosen = pickExportSaveFile(
                            owner = ownerWindow,
                            dialogTitle = "Exportar PDF",
                            initialDir = rootDir,
                            defaultTitle = tab.title,
                            extensions = listOf(".pdf"),
                        ) ?: return@onLaunch
                        val pdf = exportToPdf(tab.editorState.text(), tab.title)
                        val ok = runCatching {
                            withContext(Dispatchers.IO) { Files.write(chosen, pdf) }
                        }.isSuccess
                        showNotice(
                            if (ok) "PDF exportado: ${chosen.fileName}" else "Erro ao exportar PDF",
                            isError = !ok,
                        )
                    }
                },
                onToggleSource = { activeTab?.editorState?.toggleSourceMode() },
                onToggleTheme = onToggleTheme,
            )
            if (showFindBar && activeTab != null) {
                HorizontalDivider(color = MdTokens.tableBorder)
                DesktopNoteFindBar(
                    findQuery = findQuery,
                    replaceQuery = replaceQuery,
                    matchIndex = matchCursor,
                    matchCount = docMatches.size,
                    onFindChange = {
                        findQuery = it
                        matchCursor = 0
                    },
                    onReplaceChange = { replaceQuery = it },
                    onPrev = { jumpToMatch(matchCursor - 1) },
                    onNext = { jumpToMatch(matchCursor + 1) },
                    onReplaceAll = {
                        val replaced = activeTab.editorState.replaceAll(findQuery, replaceQuery)
                        showNotice("$replaced substituído(s)", isError = false)
                    },
                    onClose = { showFindBar = false },
                )
            }
            HorizontalDivider(color = MdTokens.tableBorder)

            TabStrip(
                tabs = tabManager.tabs,
                activeTabId = activeTab?.id,
                onSelect = { tabManager.select(it) },
                onClose = { id ->
                    val closed = tabManager.close(id)
                    if (closed) listStates.remove(id) else showNotice("Erro ao salvar antes de fechar", isError = true)
                },
            )
            HorizontalDivider(color = MdTokens.tableBorder)

            if (activeTab != null) {
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    CompositionLocalProvider(LocalUriHandler provides uriHandler) {
                        key(activeTab.id) {
                            val listState = remember(activeTab.id) {
                                listStates.getOrPut(activeTab.id) { LazyListState() }
                            }
                            EditorScreen(
                                state = activeTab.editorState,
                                modifier = Modifier.fillMaxSize(),
                                listState = listState,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SidebarPanel(
    rootDir: Path?,
    entries: List<DesktopDocEntry>,
    query: String,
    onQueryChange: (String) -> Unit,
    hits: List<DesktopSearchHit>,
    onPickFolder: () -> Unit,
    onOpenPath: (Path) -> Unit,
) {
    Column(
        Modifier
            .width(270.dp)
            .fillMaxHeight()
            .background(MdTokens.fenceBackground),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = rootDir?.fileName?.toString() ?: "Sem pasta",
                style = MaterialTheme.typography.titleSmall,
                color = MdTokens.text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f).padding(start = 8.dp),
            )
            TextButton(onClick = onPickFolder) {
                Text(if (rootDir == null) "Escolher pasta" else "Trocar pasta")
            }
        }

        if (rootDir != null) {
            OutlinedTextField(
                value = query,
                onValueChange = onQueryChange,
                placeholder = { Text("Buscar") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp),
            )
        }
        HorizontalDivider(color = MdTokens.tableBorder)

        if (rootDir == null) {
            Text(
                text = "Escolha uma pasta para navegar notas, [[wikilinks]] e #tags.",
                style = MaterialTheme.typography.bodySmall,
                color = MdTokens.muted,
                modifier = Modifier.padding(14.dp),
            )
        } else if (query.isNotBlank()) {
            LazyColumn(Modifier.fillMaxSize()) {
                items(hits, key = { it.path.toString() }) { hit ->
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .clickable { onOpenPath(hit.path) }
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                    ) {
                        Text(
                            text = hit.name,
                            style = MaterialTheme.typography.titleSmall,
                            color = MdTokens.text,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (hit.snippet.isNotEmpty()) {
                            Text(
                                text = buildAnnotatedString {
                                    append(hit.snippet)
                                    for (m in hit.marks) {
                                        addStyle(SpanStyle(fontWeight = FontWeight.Bold), m.first, m.last + 1)
                                    }
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MdTokens.muted,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
            if (hits.isEmpty()) {
                Text(
                    text = "Nada encontrado",
                    style = MaterialTheme.typography.bodySmall,
                    color = MdTokens.muted,
                    modifier = Modifier.padding(14.dp),
                )
            }
        } else {
            LazyColumn(Modifier.fillMaxSize()) {
                items(entries, key = { it.path.toString() }) { entry ->
                    Text(
                        text = if (entry.isDir) "${entry.name}/" else entry.name,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (entry.isDir) MdTokens.muted else MdTokens.text,
                        fontWeight = if (entry.isDir) FontWeight.Medium else FontWeight.Normal,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(enabled = !entry.isDir) { onOpenPath(entry.path) }
                            .padding(
                                start = (12 + 14 * entry.depth).dp,
                                top = 7.dp,
                                end = 12.dp,
                                bottom = 7.dp,
                            ),
                    )
                }
            }
        }
    }
}

@Composable
private fun HeaderBar(
    sidebarOpen: Boolean,
    activeTab: DesktopTab?,
    notice: String?,
    noticeIsError: Boolean,
    isDark: Boolean,
    findOpen: Boolean,
    onToggleSidebar: () -> Unit,
    onOpenFile: () -> Unit,
    onNewTab: () -> Unit,
    onSave: () -> Unit,
    onToggleFind: () -> Unit,
    onExportHtml: () -> Unit,
    onExportPdf: () -> Unit,
    onToggleSource: () -> Unit,
    onToggleTheme: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(MdTokens.background)
            .padding(horizontal = 8.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TextButton(onClick = onToggleSidebar) {
            Text(if (sidebarOpen) "Ocultar painel" else "Painel")
        }
        TextButton(onClick = onOpenFile) { Text("Abrir arquivo") }
        TextButton(onClick = onNewTab) { Text("Nova aba") }
        TextButton(onClick = onSave) { Text("Salvar") }
        TextButton(onClick = onToggleFind) { Text(if (findOpen) "Fechar busca" else "Buscar") }
        TextButton(onClick = onExportHtml) { Text("Exportar HTML") }
        TextButton(onClick = onExportPdf) { Text("Exportar PDF") }
        TextButton(onClick = onToggleSource) {
            Text(if (activeTab?.editorState?.isSourceMode == true) "Visual" else "</>")
        }
        TextButton(onClick = onToggleTheme) {
            Text(if (isDark) "Claro" else "Escuro")
        }

        Spacer(Modifier.weight(1f))

        notice?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodySmall,
                color = if (noticeIsError) MaterialTheme.colorScheme.error else MdTokens.link,
                modifier = Modifier.padding(end = 12.dp),
            )
        }

        if (activeTab != null) {
            val (statusText, statusColor) = when {
                activeTab.saveFailed -> "Erro ao salvar" to MaterialTheme.colorScheme.error
                activeTab.file == null -> "Rascunho" to MdTokens.muted
                activeTab.isDirty -> "Não salvo" to MdTokens.muted
                else -> "Salvo" to MdTokens.muted
            }
            Text(
                text = statusText,
                style = MaterialTheme.typography.bodySmall,
                color = statusColor,
                modifier = Modifier.padding(end = 12.dp),
            )
        }
    }
}

@Composable
private fun DesktopNoteFindBar(
    findQuery: String,
    replaceQuery: String,
    matchIndex: Int,
    matchCount: Int,
    onFindChange: (String) -> Unit,
    onReplaceChange: (String) -> Unit,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onReplaceAll: () -> Unit,
    onClose: () -> Unit,
) {
    val counter = if (matchCount == 0) "0/0" else "${matchIndex.coerceIn(0, matchCount - 1) + 1}/$matchCount"
    Row(
        Modifier
            .fillMaxWidth()
            .background(MdTokens.fenceBackground)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OutlinedTextField(
            value = findQuery,
            onValueChange = onFindChange,
            placeholder = { Text("Buscar na nota") },
            singleLine = true,
            modifier = Modifier.weight(1f),
        )
        Text(counter, style = MaterialTheme.typography.bodySmall, color = MdTokens.muted)
        TextButton(onClick = onPrev, enabled = matchCount > 0) { Text("Anterior") }
        TextButton(onClick = onNext, enabled = matchCount > 0) { Text("Próximo") }
        OutlinedTextField(
            value = replaceQuery,
            onValueChange = onReplaceChange,
            placeholder = { Text("Substituir por") },
            singleLine = true,
            modifier = Modifier.weight(1f),
        )
        TextButton(onClick = onReplaceAll, enabled = findQuery.isNotEmpty()) { Text("Substituir tudo") }
        TextButton(onClick = onClose) { Text("×") }
    }
}

@Composable
private fun TabStrip(
    tabs: List<DesktopTab>,
    activeTabId: Long?,
    onSelect: (Long) -> Unit,
    onClose: (Long) -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(MdTokens.metaBackground)
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 6.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        for (tab in tabs) {
            val isActive = tab.id == activeTabId
            val bg = if (isActive) MdTokens.background else Color.Transparent
            val label = if (tab.isDirty) "*${tab.title}" else tab.title

            Surface(
                color = bg,
                shape = RoundedCornerShape(4.dp),
                tonalElevation = if (isActive) 1.dp else 0.dp,
            ) {
                Row(
                    Modifier
                        .clickable { onSelect(tab.id) }
                        .padding(start = 10.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Normal,
                        color = if (isActive) MdTokens.text else MdTokens.muted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = "×",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MdTokens.muted,
                        modifier = Modifier
                            .clickable { onClose(tab.id) }
                            .padding(horizontal = 6.dp, vertical = 1.dp),
                    )
                }
            }
        }
    }
}

private fun loadBundledReferenceDoc(): String {
    val stream = Thread.currentThread().contextClassLoader?.getResourceAsStream("typora-ref.md")
        ?: return ""
    return stream.bufferedReader(StandardCharsets.UTF_8).use { it.readText() }
}

private fun openExternalUri(uri: String) {
    val allowed = uri.startsWith("https://", ignoreCase = true) ||
        uri.startsWith("http://", ignoreCase = true) ||
        uri.startsWith("mailto:", ignoreCase = true)
    if (!allowed) return
    runCatching {
        if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
            Desktop.getDesktop().browse(URI(uri))
        }
    }
}

private suspend fun pickDirectory(owner: Frame, initial: Path?): Path? =
    suspendCoroutine { cont ->
        SwingUtilities.invokeLater {
            runCatching { UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName()) }
            val chooser = JFileChooser(initial?.toFile()).apply {
                dialogTitle = "Escolher pasta de notas"
                fileSelectionMode = JFileChooser.DIRECTORIES_ONLY
                isAcceptAllFileFilterUsed = false
            }
            val result = chooser.showOpenDialog(owner)
            val selected = if (result == JFileChooser.APPROVE_OPTION) chooser.selectedFile?.toPath() else null
            cont.resume(selected)
        }
    }

private suspend fun pickMarkdownFile(
    owner: Frame,
    mode: Int,
    initialDir: Path?,
    defaultName: String? = null,
): Path? = suspendCoroutine { cont ->
    SwingUtilities.invokeLater {
        val dialog = FileDialog(
            owner,
            if (mode == FileDialog.SAVE) "Salvar arquivo Markdown" else "Abrir arquivo Markdown",
            mode,
        ).apply {
            directory = initialDir?.toString()
            if (mode == FileDialog.SAVE && !defaultName.isNullOrBlank()) {
                file = if (isMarkdownName(defaultName)) defaultName else "$defaultName.md"
            } else if (mode == FileDialog.LOAD) {
                file = "*.md;*.markdown"
            }
            filenameFilter = FilenameFilter { _, name -> isMarkdownName(name) }
            isVisible = true
        }
        val dir = dialog.directory
        val file = dialog.file
        val chosen = if (dir != null && file != null) {
            val raw = Path.of(dir, file)
            if (mode == FileDialog.SAVE && !isMarkdownName(raw.fileName.toString())) {
                raw.resolveSibling("${raw.fileName}.md")
            } else {
                raw
            }
        } else {
            null
        }
        cont.resume(chosen)
    }
}

private suspend fun pickExportSaveFile(
    owner: Frame,
    dialogTitle: String,
    initialDir: Path?,
    defaultTitle: String,
    extensions: List<String>,
): Path? = suspendCoroutine { cont ->
    val primaryExt = extensions.first()
    SwingUtilities.invokeLater {
        val base = defaultTitle.substringBeforeLast('.').ifBlank { "documento" }
        val dialog = FileDialog(owner, dialogTitle, FileDialog.SAVE).apply {
            directory = initialDir?.toString()
            file = "$base$primaryExt"
            filenameFilter = FilenameFilter { _, name ->
                extensions.any { name.endsWith(it, ignoreCase = true) }
            }
            isVisible = true
        }
        val dir = dialog.directory
        val file = dialog.file
        val chosen = if (dir != null && file != null) {
            val raw = Path.of(dir, file)
            val name = raw.fileName.toString()
            if (extensions.any { name.endsWith(it, ignoreCase = true) }) {
                raw
            } else {
                raw.resolveSibling("$name$primaryExt")
            }
        } else {
            null
        }
        cont.resume(chosen)
    }
}
