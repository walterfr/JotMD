package io.github.walterfr.jotmd.desktop

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
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
import io.github.walterfr.jotmd.core.OutlineEntry
import io.github.walterfr.jotmd.core.docRefsOf
import io.github.walterfr.jotmd.core.docStatsOf
import io.github.walterfr.jotmd.core.exportToHtml
import io.github.walterfr.jotmd.core.exportToPdf
import io.github.walterfr.jotmd.core.findInDocument
import io.github.walterfr.jotmd.core.isValidSearchRegex
import io.github.walterfr.jotmd.core.outlineOf
import io.github.walterfr.jotmd.editor.EditorScreen
import io.github.walterfr.jotmd.editor.EditorState
import io.github.walterfr.jotmd.editor.FOOTNOTE_URI
import io.github.walterfr.jotmd.editor.MdThemePreset
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
import org.jetbrains.skia.Image as SkiaImage

private const val AUTOSAVE_DELAY_MS = 1000L

private enum class SidebarTab { FILES, OUTLINE }

private sealed interface TreeDialogState {
    data class NewNote(val parentDir: Path) : TreeDialogState
    data class NewFolder(val parentDir: Path) : TreeDialogState
    data class Rename(val entry: DesktopDocEntry) : TreeDialogState
    data class Delete(val entry: DesktopDocEntry) : TreeDialogState
}

fun main() = application {
    val tabManager = remember {
        DesktopTabManager().apply {
            openInitial("typora-ref.md", loadBundledReferenceDoc())
        }
    }
    val scope = rememberCoroutineScope()
    var saveAction by remember { mutableStateOf<() -> Unit>({}) }
    var findAction by remember { mutableStateOf<() -> Unit>({}) }
    var quickOpenAction by remember { mutableStateOf<() -> Unit>({}) }
    var insertTableAction by remember { mutableStateOf<() -> Unit>({}) }
    var richPasteAction by remember { mutableStateOf<() -> Unit>({}) }
    var escapeAction by remember { mutableStateOf<() -> Boolean>({ false }) }
    var themePreset by remember { mutableStateOf(MdTokens.themePreset) }
    val isDark = themePreset.isDark
    val active = tabManager.activeTab
    val dirtyMark = if (active?.isDirty == true) "*" else ""

    Window(
        onCloseRequest = {
            tabManager.saveAll()
            exitApplication()
        },
        title = "JotMD — $dirtyMark${active?.title ?: "Sem Título"}",
        icon = remember { loadWindowIcon() },
        state = rememberWindowState(width = 1120.dp, height = 760.dp),
        onPreviewKeyEvent = { event ->
            handleWindowShortcut(
                event = event,
                onSave = saveAction,
                onFind = findAction,
                onQuickOpen = quickOpenAction,
                onToggleSource = { active?.editorState?.toggleSourceMode() },
                onInsertTable = insertTableAction,
                onRichPaste = richPasteAction,
                onEscape = escapeAction,
            )
        },
    ) {
        MaterialTheme(colorScheme = if (isDark) darkColorScheme() else lightColorScheme()) {
            DesktopShell(
                ownerWindow = window,
                tabManager = tabManager,
                themePreset = themePreset,
                onCycleTheme = { themePreset = MdTokens.cycleThemePreset() },
                onRegisterSave = { saveAction = it },
                onRegisterFind = { findAction = it },
                onRegisterQuickOpen = { quickOpenAction = it },
                onRegisterInsertTable = { insertTableAction = it },
                onRegisterRichPaste = { richPasteAction = it },
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
    onQuickOpen: () -> Unit,
    onToggleSource: () -> Unit,
    onInsertTable: () -> Unit,
    onRichPaste: () -> Unit,
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
        event.isCtrlPressed && event.key == Key.P -> {
            onQuickOpen()
            true
        }
        event.isCtrlPressed && event.key == Key.Slash -> {
            onToggleSource()
            true
        }
        event.isCtrlPressed && event.isShiftPressed && !event.isAltPressed && event.key == Key.V -> {
            onRichPaste()
            true
        }
        event.isCtrlPressed && !event.isShiftPressed && !event.isAltPressed && event.key == Key.T -> {
            onInsertTable()
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
    themePreset: MdThemePreset,
    onCycleTheme: () -> Unit,
    onRegisterSave: (() -> Unit) -> Unit,
    onRegisterFind: (() -> Unit) -> Unit,
    onRegisterQuickOpen: (() -> Unit) -> Unit,
    onRegisterInsertTable: (() -> Unit) -> Unit,
    onRegisterRichPaste: (() -> Unit) -> Unit,
    onRegisterEscape: (() -> Boolean) -> Unit,
    onLaunch: (suspend () -> Unit) -> Unit,
) {
    val isDark = themePreset.isDark
    var rootDir by remember { mutableStateOf<Path?>(null) }
    var entries by remember { mutableStateOf<List<DesktopDocEntry>>(emptyList()) }
    var sidebarOpen by remember { mutableStateOf(true) }
    var sidebarTab by remember { mutableStateOf(SidebarTab.FILES) }
    var query by remember { mutableStateOf("") }
    var hits by remember { mutableStateOf<List<DesktopSearchHit>>(emptyList()) }
    var notice by remember { mutableStateOf<String?>(null) }
    var noticeIsError by remember { mutableStateOf(false) }
    var refreshNonce by remember { mutableStateOf(0) }
    var showFindBar by remember { mutableStateOf(false) }
    var findQuery by remember { mutableStateOf("") }
    var replaceQuery by remember { mutableStateOf("") }
    var caseSensitive by remember { mutableStateOf(false) }
    var wholeWord by remember { mutableStateOf(false) }
    var findRegex by remember { mutableStateOf(false) }
    var matchCursor by remember { mutableStateOf(0) }
    var showQuickOpen by remember { mutableStateOf(false) }
    var quickOpenQuery by remember { mutableStateOf("") }
    var treeDialog by remember { mutableStateOf<TreeDialogState?>(null) }
    var dialogError by remember { mutableStateOf<String?>(null) }
    var dialogBusy by remember { mutableStateOf(false) }
    val listStates = remember { mutableMapOf<Long, LazyListState>() }

    val activeTab = tabManager.activeTab
    val invalidRegex = findRegex && findQuery.isNotEmpty() && !isValidSearchRegex(findQuery)
    val docMatches = remember(
        activeTab?.id,
        activeTab?.editorState?.doc,
        findQuery,
        caseSensitive,
        wholeWord,
        findRegex,
        invalidRegex,
    ) {
        val doc = activeTab?.editorState?.doc ?: return@remember emptyList()
        if (invalidRegex) return@remember emptyList()
        findInDocument(
            doc,
            findQuery,
            caseSensitive = caseSensitive,
            wholeWord = wholeWord,
            regex = findRegex,
        )
    }

    LaunchedEffect(docMatches) {
        if (docMatches.isEmpty() || matchCursor >= docMatches.size) {
            matchCursor = 0
        }
    }

    LaunchedEffect(
        showFindBar,
        activeTab?.id,
        findQuery,
        caseSensitive,
        wholeWord,
        findRegex,
        matchCursor,
        docMatches,
    ) {
        val tab = activeTab ?: return@LaunchedEffect
        if (!showFindBar || findQuery.isEmpty() || invalidRegex) {
            tab.editorState.clearSearchHighlight()
        } else {
            tab.editorState.setSearchHighlight(
                findQuery,
                caseSensitive,
                wholeWord,
                findRegex,
                docMatches.getOrNull(matchCursor),
            )
        }
    }

    DisposableEffect(activeTab?.id) {
        val tab = activeTab
        onDispose {
            tab?.editorState?.clearSearchHighlight()
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

    fun applyToEditor(tab: DesktopTab, action: (EditorState) -> Boolean): Boolean {
        if (action(tab.editorState)) return true
        if (tab.editorState.focusedId == null && tab.editorState.doc.blocks.isNotEmpty()) {
            tab.editorState.focus(tab.editorState.doc.blocks.last())
            return action(tab.editorState)
        }
        return false
    }

    fun triggerInsertTable() {
        val tab = tabManager.activeTab ?: return
        if (applyToEditor(tab) { it.insertTable() }) {
            showNotice(null)
        } else {
            showNotice("Não foi possível inserir tabela aqui", isError = true)
        }
    }

    fun triggerRichPaste() {
        val tab = tabManager.activeTab ?: return
        when (val payload = readClipboardPayload()) {
            is ClipboardPayload.Image -> {
                if (tab.file == null && rootDir == null) {
                    showNotice("Salve a nota ou abra uma pasta antes de colar imagens.", isError = true)
                    return
                }
                onLaunch {
                    val dest = withContext(Dispatchers.IO) {
                        saveClipboardImagePng(payload.image, tab.file, rootDir)
                    }
                    if (dest == null) {
                        showNotice("Erro ao salvar imagem da área de transferência", isError = true)
                        return@onLaunch
                    }
                    if (applyToEditor(tab) { it.insertImageMarkdown("imagem", dest) }) {
                        showNotice(null)
                    } else {
                        showNotice("Posicione o cursor em um bloco para colar a imagem", isError = true)
                    }
                }
            }
            is ClipboardPayload.Html -> {
                if (applyToEditor(tab) { it.pasteHtmlAtCursor(payload.html) }) {
                    showNotice(null)
                } else {
                    showNotice("Não foi possível colar HTML neste ponto", isError = true)
                }
            }
            is ClipboardPayload.Text -> {
                if (applyToEditor(tab) { it.pasteMarkdownAtCursor(payload.text) }) {
                    showNotice(null)
                } else {
                    showNotice("Não foi possível colar texto neste ponto", isError = true)
                }
            }
            ClipboardPayload.Empty -> {
                showNotice("Área de transferência vazia ou sem conteúdo compatível", isError = true)
            }
        }
    }

    onRegisterSave(::triggerSave)
    onRegisterFind {
        showQuickOpen = false
        showFindBar = true
    }
    onRegisterQuickOpen {
        showQuickOpen = !showQuickOpen
        if (showQuickOpen) quickOpenQuery = ""
    }
    onRegisterInsertTable(::triggerInsertTable)
    onRegisterRichPaste(::triggerRichPaste)
    onRegisterEscape {
        when {
            treeDialog != null -> {
                if (!dialogBusy) {
                    treeDialog = null
                    dialogError = null
                }
                true
            }
            showQuickOpen -> {
                showQuickOpen = false
                true
            }
            showFindBar -> {
                showFindBar = false
                true
            }
            else -> false
        }
    }

    DisposableEffect(rootDir) {
        val dir = rootDir ?: return@DisposableEffect onDispose {}
        val watcher = runCatching {
            DirectoryTreeWatcher(dir) {
                SwingUtilities.invokeLater { refreshNonce++ }
            }
        }.getOrNull()
        onDispose { watcher?.close() }
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
        entries = withContext(Dispatchers.IO) { listDesktopTree(dir, includeEmptyDirs = true) }
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
                sidebarTab = SidebarTab.FILES
                sidebarOpen = true
            }
            uri.startsWith(FOOTNOTE_URI) -> {
                val id = uri.removePrefix(FOOTNOTE_URI)
                val tab = activeTab
                val def = tab?.let { docRefsOf(it.editorState.doc).resolveFootnote(id) }
                if (tab == null || def == null) {
                    showNotice("Nota de rodapé não encontrada: $id", isError = true)
                } else {
                    tab.editorState.focusBlockIndex(def.blockIndex)
                    onLaunch { listStates[tab.id]?.animateScrollToItem(def.blockIndex) }
                }
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

    Box(Modifier.fillMaxSize().background(MdTokens.background)) {
        Row(Modifier.fillMaxSize()) {
            if (sidebarOpen) {
                SidebarPanel(
                    sidebarTab = sidebarTab,
                    onSelectTab = { sidebarTab = it },
                    rootDir = rootDir,
                    entries = entries,
                    query = query,
                    onQueryChange = { query = it },
                    hits = hits,
                    activeTab = activeTab,
                    onPickFolder = {
                        onLaunch {
                            val chosen = pickDirectory(ownerWindow, rootDir) ?: return@onLaunch
                            rootDir = chosen
                            query = ""
                            showNotice(null)
                        }
                    },
                    onOpenPath = ::openFile,
                    onCreateNoteIn = { parentDir ->
                        dialogError = null
                        treeDialog = TreeDialogState.NewNote(parentDir)
                    },
                    onCreateFolderInRoot = {
                        val dir = rootDir ?: return@SidebarPanel
                        dialogError = null
                        treeDialog = TreeDialogState.NewFolder(dir)
                    },
                    onRenameEntry = { entry ->
                        dialogError = null
                        treeDialog = TreeDialogState.Rename(entry)
                    },
                    onDeleteEntry = { entry ->
                        dialogError = null
                        treeDialog = TreeDialogState.Delete(entry)
                    },
                    onSelectOutlineEntry = { entry ->
                        activeTab?.let { tab ->
                            tab.editorState.focusBlockIndex(entry.blockIndex)
                            onLaunch { listStates[tab.id]?.animateScrollToItem(entry.blockIndex) }
                        }
                    },
                )
                Box(Modifier.fillMaxHeight().width(1.dp).background(MdTokens.tableBorder))
            }

            Column(Modifier.weight(1f).fillMaxHeight()) {
                HeaderBar(
                    sidebarOpen = sidebarOpen,
                    activeTab = activeTab,
                    notice = notice,
                    noticeIsError = noticeIsError,
                    themePreset = themePreset,
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
                    onInsertTable = ::triggerInsertTable,
                    onRichPaste = ::triggerRichPaste,
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
                    onCycleTheme = onCycleTheme,
                )
                if (showFindBar && activeTab != null) {
                    HorizontalDivider(color = MdTokens.tableBorder)
                    DesktopNoteFindBar(
                        findQuery = findQuery,
                        replaceQuery = replaceQuery,
                        caseSensitive = caseSensitive,
                        wholeWord = wholeWord,
                        regex = findRegex,
                        regexError = invalidRegex,
                        matchIndex = matchCursor,
                        matchCount = docMatches.size,
                        onFindChange = {
                            findQuery = it
                            matchCursor = 0
                        },
                        onReplaceChange = { replaceQuery = it },
                        onToggleCaseSensitive = {
                            caseSensitive = !caseSensitive
                            matchCursor = 0
                        },
                        onToggleWholeWord = {
                            wholeWord = !wholeWord
                            matchCursor = 0
                        },
                        onToggleRegex = {
                            findRegex = !findRegex
                            matchCursor = 0
                        },
                        onPrev = { jumpToMatch(matchCursor - 1) },
                        onNext = { jumpToMatch(matchCursor + 1) },
                        onReplaceCurrent = {
                            val match = docMatches.getOrNull(matchCursor)
                            if (match != null && activeTab.editorState.replaceMatch(match, replaceQuery)) {
                                showNotice("1 substituído", isError = false)
                            }
                        },
                        onReplaceAll = {
                            if (invalidRegex) {
                                showNotice("Expressão regular inválida", isError = true)
                            } else {
                                val replaced = activeTab.editorState.replaceAll(
                                    query = findQuery,
                                    replacement = replaceQuery,
                                    caseSensitive = caseSensitive,
                                    wholeWord = wholeWord,
                                    regex = findRegex,
                                )
                                showNotice("$replaced substituído(s)", isError = false)
                            }
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
                } else {
                    Spacer(Modifier.weight(1f))
                }
                HorizontalDivider(color = MdTokens.tableBorder)
                DesktopFooterBar(
                    outlineOpen = sidebarOpen && sidebarTab == SidebarTab.OUTLINE,
                    activeTab = activeTab,
                    onToggleOutline = {
                        if (sidebarOpen && sidebarTab == SidebarTab.OUTLINE) {
                            sidebarOpen = false
                        } else {
                            sidebarTab = SidebarTab.OUTLINE
                            sidebarOpen = true
                        }
                    },
                    onToggleSource = { activeTab?.editorState?.toggleSourceMode() },
                    onToggleFocus = { activeTab?.editorState?.toggleFocusMode() },
                    onToggleTypewriter = { activeTab?.editorState?.toggleTypewriterMode() },
                )
            }
        }
        if (showQuickOpen) {
            QuickOpenModal(
                query = quickOpenQuery,
                onQueryChange = { quickOpenQuery = it },
                entries = entries,
                openTabs = tabManager.tabs,
                onSelect = { item ->
                    when {
                        item.path != null -> openFile(item.path)
                        item.tabId != null -> {
                            tabManager.select(item.tabId)
                            showNotice(null)
                        }
                    }
                    showQuickOpen = false
                },
                onDismiss = { showQuickOpen = false },
            )
        }
        treeDialog?.let { dialog ->
            TreeCrudModal(
                dialog = dialog,
                errorMessage = dialogError,
                busy = dialogBusy,
                onDismiss = {
                    if (!dialogBusy) {
                        treeDialog = null
                        dialogError = null
                    }
                },
                onConfirm = { inputName ->
                    if (dialogBusy) return@TreeCrudModal
                    dialogBusy = true
                    dialogError = null
                    onLaunch {
                        try {
                            when (dialog) {
                                is TreeDialogState.NewNote -> {
                                    val created = withContext(Dispatchers.IO) {
                                        createDesktopMarkdownFile(dialog.parentDir, inputName)
                                    }
                                    if (created != null) {
                                        refreshNonce++
                                        treeDialog = null
                                        openFile(created)
                                    } else {
                                        dialogError = "Nome inválido ou nota já existe."
                                    }
                                }
                                is TreeDialogState.NewFolder -> {
                                    val created = withContext(Dispatchers.IO) {
                                        createDesktopFolder(dialog.parentDir, inputName)
                                    }
                                    if (created != null) {
                                        refreshNonce++
                                        treeDialog = null
                                        showNotice("Pasta criada: ${created.fileName}")
                                    } else {
                                        dialogError = "Nome inválido ou pasta já existe."
                                    }
                                }
                                is TreeDialogState.Rename -> {
                                    val renamed = withContext(Dispatchers.IO) {
                                        renameDesktopEntry(dialog.entry.path, inputName)
                                    }
                                    if (renamed != null) {
                                        tabManager.updateRenamedPath(dialog.entry.path, renamed)
                                        refreshNonce++
                                        treeDialog = null
                                        showNotice(null)
                                    } else {
                                        dialogError = "Não foi possível renomear (nome inválido ou já existe)."
                                    }
                                }
                                is TreeDialogState.Delete -> {
                                    val deleted = withContext(Dispatchers.IO) {
                                        deleteDesktopEntry(dialog.entry.path)
                                    }
                                    if (deleted) {
                                        tabManager.closeRemovedPath(dialog.entry.path)
                                        listStates.keys.retainAll(tabManager.tabs.map { it.id }.toSet())
                                        refreshNonce++
                                        treeDialog = null
                                        showNotice("Excluído: ${dialog.entry.name}")
                                    } else {
                                        dialogError = "Não foi possível excluir \"${dialog.entry.name}\"."
                                    }
                                }
                            }
                        } finally {
                            dialogBusy = false
                        }
                    }
                },
            )
        }
    }
}

@Composable
private fun SidebarPanel(
    sidebarTab: SidebarTab,
    onSelectTab: (SidebarTab) -> Unit,
    rootDir: Path?,
    entries: List<DesktopDocEntry>,
    query: String,
    onQueryChange: (String) -> Unit,
    hits: List<DesktopSearchHit>,
    activeTab: DesktopTab?,
    onPickFolder: () -> Unit,
    onOpenPath: (Path) -> Unit,
    onCreateNoteIn: (Path) -> Unit,
    onCreateFolderInRoot: () -> Unit,
    onRenameEntry: (DesktopDocEntry) -> Unit,
    onDeleteEntry: (DesktopDocEntry) -> Unit,
    onSelectOutlineEntry: (OutlineEntry) -> Unit,
) {
    Column(
        Modifier
            .width(270.dp)
            .fillMaxHeight()
            .background(MdTokens.fenceBackground),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            SidebarTabChip(
                label = "Arquivos",
                selected = sidebarTab == SidebarTab.FILES,
                onClick = { onSelectTab(SidebarTab.FILES) },
                modifier = Modifier.weight(1f),
            )
            SidebarTabChip(
                label = "Índices",
                selected = sidebarTab == SidebarTab.OUTLINE,
                onClick = { onSelectTab(SidebarTab.OUTLINE) },
                modifier = Modifier.weight(1f),
            )
        }
        HorizontalDivider(color = MdTokens.tableBorder)

        if (sidebarTab == SidebarTab.FILES) {
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
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 2.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    SidebarActionChip(
                        label = "+ Nota",
                        onClick = { onCreateNoteIn(rootDir) },
                        modifier = Modifier.weight(1f),
                    )
                    SidebarActionChip(
                        label = "+ Pasta",
                        onClick = onCreateFolderInRoot,
                        modifier = Modifier.weight(1f),
                    )
                }
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
                        val activeNorm = activeTab?.file?.toAbsolutePath()?.normalize()
                        val isOpenFile = !entry.isDir && activeNorm == entry.path.toAbsolutePath().normalize()
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(if (isOpenFile) MdTokens.background else Color.Transparent)
                                .clickable(enabled = !entry.isDir) { onOpenPath(entry.path) }
                                .padding(
                                    start = (12 + minOf(entry.depth, 6) * 12).dp,
                                    top = 4.dp,
                                    end = 6.dp,
                                    bottom = 4.dp,
                                ),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = if (entry.isDir) "${entry.name}/" else entry.name,
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (entry.isDir) MdTokens.muted else MdTokens.text,
                                fontWeight = when {
                                    isOpenFile -> FontWeight.SemiBold
                                    entry.isDir -> FontWeight.Medium
                                    else -> FontWeight.Normal
                                },
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f).padding(vertical = 3.dp),
                            )
                            if (entry.isDir) {
                                TreeEntryAction(label = "+", onClick = { onCreateNoteIn(entry.path) })
                            }
                            TreeEntryAction(label = "✎", onClick = { onRenameEntry(entry) })
                            TreeEntryAction(label = "×", onClick = { onDeleteEntry(entry) })
                        }
                    }
                }
            }
        } else {
            val outline = remember(activeTab?.id, activeTab?.editorState?.doc) {
                activeTab?.let { outlineOf(it.editorState.doc) }.orEmpty()
            }
            if (outline.isEmpty()) {
                Text(
                    text = "Sem títulos no documento",
                    style = MaterialTheme.typography.bodySmall,
                    color = MdTokens.muted,
                    modifier = Modifier.padding(14.dp),
                )
            } else {
                LazyColumn(Modifier.fillMaxSize()) {
                    items(outline, key = { "${it.blockId.v}:${it.blockIndex}" }) { entry ->
                        Text(
                            text = entry.title,
                            style = if (entry.level <= 2) {
                                MaterialTheme.typography.titleSmall
                            } else {
                                MaterialTheme.typography.bodyMedium
                            },
                            color = MdTokens.text,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSelectOutlineEntry(entry) }
                                .padding(
                                    start = (12 + (entry.level - 1).coerceAtLeast(0) * 14).dp,
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
}

@Composable
private fun SidebarActionChip(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(4.dp),
        color = MdTokens.background,
        border = BorderStroke(1.dp, MdTokens.tableBorder),
    ) {
        Box(
            modifier = Modifier.padding(vertical = 5.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MdTokens.text,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun TreeEntryAction(
    label: String,
    onClick: () -> Unit,
) {
    Text(
        text = label,
        style = MaterialTheme.typography.labelMedium,
        color = MdTokens.muted,
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 5.dp, vertical = 2.dp),
    )
}

@Composable
private fun SidebarTabChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(4.dp)
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = shape,
        color = if (selected) MdTokens.background else Color.Transparent,
        border = if (selected) BorderStroke(1.dp, MdTokens.tableBorder) else null,
    ) {
        Box(
            modifier = Modifier.padding(vertical = 6.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                color = if (selected) MdTokens.text else MdTokens.muted,
            )
        }
    }
}

@Composable
private fun HeaderBar(
    sidebarOpen: Boolean,
    activeTab: DesktopTab?,
    notice: String?,
    noticeIsError: Boolean,
    themePreset: MdThemePreset,
    findOpen: Boolean,
    onToggleSidebar: () -> Unit,
    onOpenFile: () -> Unit,
    onNewTab: () -> Unit,
    onSave: () -> Unit,
    onToggleFind: () -> Unit,
    onInsertTable: () -> Unit,
    onRichPaste: () -> Unit,
    onExportHtml: () -> Unit,
    onExportPdf: () -> Unit,
    onToggleSource: () -> Unit,
    onCycleTheme: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(MdTokens.background)
            .padding(horizontal = 8.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            Modifier.weight(1f).horizontalScroll(rememberScrollState()),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onToggleSidebar) {
                Text(if (sidebarOpen) "Ocultar painel" else "Painel")
            }
            TextButton(onClick = onOpenFile) { Text("Abrir arquivo") }
            TextButton(onClick = onNewTab) { Text("Nova aba") }
            TextButton(onClick = onSave) { Text("Salvar") }
            TextButton(onClick = onToggleFind) { Text(if (findOpen) "Fechar busca" else "Buscar") }
            HeaderTapButton(label = "Tabela", onClick = onInsertTable)
            HeaderTapButton(label = "Colar+", onClick = onRichPaste)
            TextButton(onClick = onExportHtml) { Text("Exportar HTML") }
            TextButton(onClick = onExportPdf) { Text("Exportar PDF") }
            TextButton(onClick = onToggleSource) {
                Text(if (activeTab?.editorState?.isSourceMode == true) "Visual" else "</>")
            }
            TextButton(onClick = onCycleTheme) {
                Text("Tema: ${themePreset.displayName}")
            }
        }

        notice?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodySmall,
                color = if (noticeIsError) MaterialTheme.colorScheme.error else MdTokens.link,
                modifier = Modifier.padding(horizontal = 12.dp),
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

/** Botão de cabeçalho sem foco próprio para preservar seleção e cursor do editor (Armadilha 5). */
@Composable
private fun HeaderTapButton(
    label: String,
    onClick: () -> Unit,
) {
    val currentOnClick = rememberUpdatedState(onClick)
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .pointerInput(Unit) { detectTapGestures(onTap = { currentOnClick.value() }) }
            .padding(horizontal = 12.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            maxLines = 1,
        )
    }
}

@Composable
private fun DesktopNoteFindBar(
    findQuery: String,
    replaceQuery: String,
    caseSensitive: Boolean,
    wholeWord: Boolean,
    regex: Boolean,
    regexError: Boolean,
    matchIndex: Int,
    matchCount: Int,
    onFindChange: (String) -> Unit,
    onReplaceChange: (String) -> Unit,
    onToggleCaseSensitive: () -> Unit,
    onToggleWholeWord: () -> Unit,
    onToggleRegex: () -> Unit,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onReplaceCurrent: () -> Unit,
    onReplaceAll: () -> Unit,
    onClose: () -> Unit,
) {
    val counter = if (matchCount == 0) "0/0" else "${matchIndex.coerceIn(0, matchCount - 1) + 1}/$matchCount"
    Column(
        Modifier
            .fillMaxWidth()
            .background(MdTokens.fenceBackground)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = findQuery,
                onValueChange = onFindChange,
                placeholder = { Text("Buscar na nota") },
                isError = regexError,
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
            DesktopFindOptionChip(label = "Aa", active = caseSensitive, onClick = onToggleCaseSensitive)
            DesktopFindOptionChip(label = "[ab]", active = wholeWord, onClick = onToggleWholeWord)
            DesktopFindOptionChip(label = ".*", active = regex, onClick = onToggleRegex)
            if (regexError) {
                Text("Regex inválida", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            } else {
                Text(counter, style = MaterialTheme.typography.bodySmall, color = MdTokens.muted)
            }
            TextButton(onClick = onPrev, enabled = matchCount > 0) { Text("Anterior") }
            TextButton(onClick = onNext, enabled = matchCount > 0) { Text("Próximo") }
            TextButton(onClick = onClose, contentPadding = PaddingValues(horizontal = 8.dp)) { Text("×") }
        }
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = replaceQuery,
                onValueChange = onReplaceChange,
                placeholder = { Text("Substituir por") },
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onReplaceCurrent, enabled = matchCount > 0) { Text("Substituir") }
            TextButton(onClick = onReplaceAll, enabled = findQuery.isNotEmpty() && !regexError) { Text("Substituir tudo") }
        }
    }
}

@Composable
private fun TreeCrudModal(
    dialog: TreeDialogState,
    errorMessage: String?,
    busy: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    val initialText = when (dialog) {
        is TreeDialogState.Rename -> dialog.entry.name
        else -> ""
    }
    var inputText by remember(dialog) { mutableStateOf(initialText) }
    val focusRequester = remember { FocusRequester() }
    val isDelete = dialog is TreeDialogState.Delete

    LaunchedEffect(dialog) {
        if (!isDelete) focusRequester.requestFocus()
    }

    val title = when (dialog) {
        is TreeDialogState.NewNote -> "Nova nota"
        is TreeDialogState.NewFolder -> "Nova pasta"
        is TreeDialogState.Rename -> "Renomear ${if (dialog.entry.isDir) "pasta" else "nota"}"
        is TreeDialogState.Delete -> "Excluir ${if (dialog.entry.isDir) "pasta" else "nota"}"
    }
    val placeholder = when (dialog) {
        is TreeDialogState.NewNote -> "Nome da nota (ex.: resumo.md)"
        is TreeDialogState.NewFolder -> "Nome da pasta"
        is TreeDialogState.Rename -> "Novo nome"
        is TreeDialogState.Delete -> ""
    }
    val confirmLabel = when (dialog) {
        is TreeDialogState.NewNote, is TreeDialogState.NewFolder -> "Criar"
        is TreeDialogState.Rename -> "Renomear"
        is TreeDialogState.Delete -> "Excluir"
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.25f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onDismiss,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            modifier = Modifier
                .width(380.dp)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {},
                )
                .onPreviewKeyEvent { event ->
                    if (event.type == KeyEventType.KeyDown && event.key == Key.Escape) {
                        onDismiss()
                        true
                    } else {
                        false
                    }
                },
            shape = RoundedCornerShape(8.dp),
            color = MdTokens.fenceBackground,
            border = BorderStroke(1.dp, MdTokens.tableBorder),
            shadowElevation = 12.dp,
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MdTokens.text,
                )
                if (dialog is TreeDialogState.NewNote) {
                    Text(
                        text = "Pasta: ${dialog.parentDir.fileName ?: dialog.parentDir}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MdTokens.muted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (dialog is TreeDialogState.Delete) {
                    val extra = if (dialog.entry.isDir) " e todo o seu conteúdo" else ""
                    Text(
                        text = "Excluir \"${dialog.entry.name}\"$extra? Esta ação não pode ser desfeita.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MdTokens.text,
                    )
                } else {
                    OutlinedTextField(
                        value = inputText,
                        onValueChange = { inputText = it },
                        placeholder = { Text(placeholder) },
                        singleLine = true,
                        enabled = !busy,
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(focusRequester)
                            .onPreviewKeyEvent { event ->
                                when {
                                    event.type == KeyEventType.KeyDown &&
                                        (event.key == Key.Enter || event.key == Key.NumPadEnter) -> {
                                        onConfirm(inputText)
                                        true
                                    }
                                    event.type == KeyEventType.KeyDown && event.key == Key.Escape -> {
                                        onDismiss()
                                        true
                                    }
                                    else -> false
                                }
                            },
                    )
                }
                if (!errorMessage.isNullOrEmpty()) {
                    Text(
                        text = errorMessage,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(onClick = onDismiss, enabled = !busy) {
                        Text("Cancelar")
                    }
                    Spacer(Modifier.width(8.dp))
                    TextButton(onClick = { onConfirm(inputText) }, enabled = !busy) {
                        Text(
                            text = confirmLabel,
                            color = if (isDelete) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DesktopFindOptionChip(
    label: String,
    active: Boolean,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(4.dp)
    Surface(
        onClick = onClick,
        shape = shape,
        color = if (active) MdTokens.background else Color.Transparent,
        border = if (active) BorderStroke(1.dp, MdTokens.link) else BorderStroke(1.dp, MdTokens.tableBorder),
    ) {
        Box(
            modifier = Modifier.defaultMinSize(minWidth = 34.dp, minHeight = 36.dp).padding(horizontal = 8.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
                color = if (active) MdTokens.link else MdTokens.muted,
            )
        }
    }
}

@Composable
private fun DesktopFooterBar(
    outlineOpen: Boolean,
    activeTab: DesktopTab?,
    onToggleOutline: () -> Unit,
    onToggleSource: () -> Unit,
    onToggleFocus: () -> Unit,
    onToggleTypewriter: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(30.dp)
            .background(MdTokens.metaBackground)
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        FooterToggleButton(
            label = "Índices",
            active = outlineOpen,
            onClick = onToggleOutline,
        )
        FooterToggleButton(
            label = "</>",
            active = activeTab?.editorState?.isSourceMode == true,
            onClick = onToggleSource,
        )
        FooterToggleButton(
            label = "Foco",
            active = activeTab?.editorState?.isFocusMode == true,
            onClick = onToggleFocus,
        )
        FooterToggleButton(
            label = "Máquina",
            active = activeTab?.editorState?.isTypewriterMode == true,
            onClick = onToggleTypewriter,
        )
        Spacer(Modifier.weight(1f))
        if (activeTab != null) {
            val stats = remember(activeTab.id, activeTab.editorState.doc) {
                docStatsOf(activeTab.editorState.doc)
            }
            Text(
                text = "Contagem de Palavras: ${stats.words} palavras · ${stats.chars} caracteres · ${stats.lines} linhas · ~${stats.readingTimeMinutes} min leitura",
                style = MaterialTheme.typography.labelSmall,
                color = MdTokens.muted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** Botão compacto de rodapé sem roubar foco do `BasicTextField` ativo (Armadilha 5). */
@Composable
private fun FooterToggleButton(
    label: String,
    active: Boolean,
    onClick: () -> Unit,
) {
    val currentOnClick = rememberUpdatedState(onClick)
    val shape = RoundedCornerShape(4.dp)
    val bg = if (active) MdTokens.background else Color.Transparent
    val fg = if (active) MdTokens.link else MdTokens.muted
    Box(
        modifier = Modifier
            .clip(shape)
            .background(bg)
            .then(if (active) Modifier.border(1.dp, MdTokens.tableBorder, shape) else Modifier)
            .pointerInput(Unit) { detectTapGestures(onTap = { currentOnClick.value() }) }
            .padding(horizontal = 8.dp, vertical = 3.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
            color = fg,
            maxLines = 1,
        )
    }
}

@Composable
private fun QuickOpenModal(
    query: String,
    onQueryChange: (String) -> Unit,
    entries: List<DesktopDocEntry>,
    openTabs: List<DesktopTab>,
    onSelect: (QuickOpenItem) -> Unit,
    onDismiss: () -> Unit,
) {
    val focusRequester = remember { FocusRequester() }
    val items = remember(query, entries, openTabs) {
        filterQuickOpen(query = query, entries = entries, openTabs = openTabs)
    }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.25f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onDismiss,
            ),
        contentAlignment = Alignment.TopCenter,
    ) {
        Surface(
            modifier = Modifier
                .padding(top = 48.dp)
                .width(420.dp)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {},
                ),
            shape = RoundedCornerShape(8.dp),
            color = MdTokens.fenceBackground,
            border = BorderStroke(1.dp, MdTokens.tableBorder),
            shadowElevation = 12.dp,
        ) {
            Column(Modifier.fillMaxWidth().padding(10.dp)) {
                OutlinedTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    placeholder = { Text("Abrir rápido (nome ou caminho)") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester)
                        .onPreviewKeyEvent { event ->
                            when {
                                event.type == KeyEventType.KeyDown &&
                                    (event.key == Key.Enter || event.key == Key.NumPadEnter) -> {
                                    items.firstOrNull()?.let(onSelect)
                                    true
                                }
                                event.type == KeyEventType.KeyDown && event.key == Key.Escape -> {
                                    onDismiss()
                                    true
                                }
                                else -> false
                            }
                        },
                )
                Spacer(Modifier.height(6.dp))
                HorizontalDivider(color = MdTokens.tableBorder)
                if (items.isEmpty()) {
                    Text(
                        text = "Nenhum arquivo encontrado",
                        style = MaterialTheme.typography.bodySmall,
                        color = MdTokens.muted,
                        modifier = Modifier.padding(12.dp),
                    )
                } else {
                    LazyColumn(Modifier.fillMaxWidth().heightIn(max = 360.dp)) {
                        items(items, key = { "${it.tabId ?: -1}:${it.path ?: it.title}" }) { item ->
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onSelect(item) }
                                    .padding(horizontal = 10.dp, vertical = 8.dp),
                            ) {
                                Text(
                                    text = item.title,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium,
                                    color = MdTokens.text,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                if (item.subtitle.isNotEmpty() && item.subtitle != item.title) {
                                    Text(
                                        text = item.subtitle,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MdTokens.muted,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
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

private fun loadWindowIcon(): Painter? = runCatching {
    val bytes = DesktopTabManager::class.java.classLoader
        ?.getResourceAsStream("jotmd-icon.png")
        ?.use { it.readBytes() }
        ?: return null
    BitmapPainter(SkiaImage.makeFromEncoded(bytes).toComposeImageBitmap())
}.getOrNull()
