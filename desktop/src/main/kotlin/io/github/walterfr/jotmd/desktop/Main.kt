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
import androidx.compose.foundation.layout.IntrinsicSize
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
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
import io.github.walterfr.jotmd.core.SelectionStats
import io.github.walterfr.jotmd.core.docRefsOf
import io.github.walterfr.jotmd.core.docStatsOf
import io.github.walterfr.jotmd.core.exportToHtml
import io.github.walterfr.jotmd.core.exportToPdf
import io.github.walterfr.jotmd.core.findInDocument
import io.github.walterfr.jotmd.core.isValidSearchRegex
import io.github.walterfr.jotmd.core.outlineOf
import io.github.walterfr.jotmd.core.selectionStatsOf
import io.github.walterfr.jotmd.core.writeTextAtomic
import io.github.walterfr.jotmd.editor.AppLanguage
import io.github.walterfr.jotmd.editor.DarkMdPalette
import io.github.walterfr.jotmd.editor.EditorPrefs
import io.github.walterfr.jotmd.editor.EditorScreen
import io.github.walterfr.jotmd.editor.EditorState
import io.github.walterfr.jotmd.editor.FOOTNOTE_URI
import io.github.walterfr.jotmd.editor.JotAboutInfo
import io.github.walterfr.jotmd.editor.JotStrings
import io.github.walterfr.jotmd.editor.LightMdPalette
import io.github.walterfr.jotmd.editor.LocalJotStrings
import io.github.walterfr.jotmd.editor.MdPalette
import io.github.walterfr.jotmd.editor.MdThemePreset
import io.github.walterfr.jotmd.editor.MdTokens
import io.github.walterfr.jotmd.editor.NewsprintMdPalette
import io.github.walterfr.jotmd.editor.PixyllMdPalette
import io.github.walterfr.jotmd.editor.TAG_URI
import io.github.walterfr.jotmd.editor.WIKI_URI
import io.github.walterfr.jotmd.editor.WhiteyMdPalette
import io.github.walterfr.jotmd.editor.stringsFor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.awt.Desktop
import java.awt.FileDialog
import java.awt.Frame
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import java.io.FilenameFilter
import java.net.URI
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import javax.swing.JFileChooser
import javax.swing.SwingUtilities
import javax.swing.UIManager
import javax.swing.filechooser.FileNameExtensionFilter
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine
import kotlin.math.roundToInt
import org.jetbrains.skia.Image as SkiaImage

private const val AUTOSAVE_DELAY_MS = 1000L

private enum class SidebarTab { FILES, OUTLINE }

private sealed interface TreeDialogState {
    data class NewNote(val parentDir: Path) : TreeDialogState
    data class NewFolder(val parentDir: Path) : TreeDialogState
    data class Rename(val entry: DesktopDocEntry) : TreeDialogState
    data class Delete(val entry: DesktopDocEntry) : TreeDialogState
}

private fun isMarkdownOrTextArg(path: Path): Boolean {
    val ext = path.fileName?.toString()?.substringAfterLast('.', "")?.lowercase().orEmpty()
    return ext in setOf("md", "markdown", "mmd", "txt")
}

private fun isImportableDocArg(path: Path): Boolean {
    val ext = path.fileName?.toString()?.substringAfterLast('.', "")?.lowercase().orEmpty()
    return ext in setOf("html", "htm", "xhtml", "docx", "epub")
}

fun main(args: Array<String>) = application {
    val configStore = remember { DesktopConfigStore() }
    val initialSession = remember { configStore.load() }
    var prefsState by remember { mutableStateOf(initialSession.prefs) }
    remember(prefsState) {
        MdTokens.applyPreferences(prefsState)
        true
    }

    val startupState = remember {
        val mgr = DesktopTabManager()
        var initialRootDir: Path? = null
        val startupPaths = resolveStartupPaths(args)

        if (startupPaths.isNotEmpty()) {
            for (p in startupPaths) {
                when {
                    Files.isDirectory(p) -> if (initialRootDir == null) initialRootDir = p
                    isMarkdownOrTextArg(p) -> mgr.openOrFocus(p)
                    isImportableDocArg(p) -> {
                        importDesktopFile(p)?.let { (title, md) ->
                            val draftId = "import-${System.currentTimeMillis()}-${mgr.tabs.size}"
                            mgr.openInitial(title, md, draftId = draftId)
                        }
                    }
                }
            }
        } else if (initialSession.prefs.restoreSession) {
            initialRootDir = initialSession.lastRootDir?.takeIf { Files.isDirectory(it) }
            for (p in initialSession.openFiles) {
                if (Files.isRegularFile(p)) {
                    mgr.openOrFocus(p)
                }
            }
            for (draft in configStore.drafts.loadDrafts()) {
                mgr.openDraft(draft)
            }
        }

        if (mgr.tabs.isEmpty()) {
            mgr.newEmptyTab(title = stringsFor(initialSession.prefs.language).untitled)
        }
        mgr.tabs.forEach { it.editorState.applyPreferences(initialSession.prefs) }
        mgr to initialRootDir
    }

    val tabManager = startupState.first
    var rootDir by remember { mutableStateOf(startupState.second) }

    val scope = rememberCoroutineScope()
    var saveAction by remember { mutableStateOf<() -> Unit>({}) }
    var findAction by remember { mutableStateOf<() -> Unit>({}) }
    var quickOpenAction by remember { mutableStateOf<() -> Unit>({}) }
    var insertTableAction by remember { mutableStateOf<() -> Unit>({}) }
    var richPasteAction by remember { mutableStateOf<() -> Unit>({}) }
    var escapeAction by remember { mutableStateOf<() -> Boolean>({ false }) }

    val strings = remember(prefsState.language) { stringsFor(prefsState.language) }
    val isDark = prefsState.themePreset.isDark
    val active = tabManager.activeTab
    val dirtyMark = if (active?.isDirty == true) "*" else ""

    fun persistTabDraft(tab: DesktopTab) {
        if (tab.file != null) return
        val content = tab.editorState.text()
        if (content.isBlank()) {
            tab.draftId?.let { id ->
                configStore.drafts.deleteDraft(id)
                tab.draftId = null
            }
            return
        }
        val id = tab.draftId ?: "draft-${System.currentTimeMillis()}-${tab.id}".also { tab.draftId = it }
        configStore.drafts.saveDraft(id, tab.title, content)
    }

    fun persistSessionSnapshot() {
        configStore.save(
            DesktopSessionState(
                prefs = prefsState,
                lastRootDir = rootDir,
                openFiles = tabManager.tabs.mapNotNull { it.file },
            ),
        )
    }

    LaunchedEffect(prefsState, tabManager.tabs) {
        tabManager.tabs.forEach { it.editorState.applyPreferences(prefsState) }
    }

    var sessionInitialized by remember { mutableStateOf(false) }
    LaunchedEffect(prefsState, rootDir) {
        if (!sessionInitialized) {
            sessionInitialized = true
            return@LaunchedEffect
        }
        withContext(Dispatchers.IO) { persistSessionSnapshot() }
    }

    Window(
        onCloseRequest = {
            tabManager.saveAll()
            if (prefsState.restoreSession) {
                for (tab in tabManager.tabs) {
                    persistTabDraft(tab)
                }
                configStore.drafts.pruneOldDrafts()
            }
            persistSessionSnapshot()
            exitApplication()
        },
        title = "JotMD — $dirtyMark${active?.title ?: strings.untitled}",
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
        CompositionLocalProvider(LocalJotStrings provides strings) {
            MaterialTheme(colorScheme = if (isDark) darkColorScheme() else lightColorScheme()) {
                DesktopShell(
                    ownerWindow = window,
                    tabManager = tabManager,
                    configStore = configStore,
                    prefs = prefsState,
                    onPrefsChange = { updated ->
                        val normalized = updated.normalized()
                        prefsState = normalized
                        MdTokens.applyPreferences(normalized)
                        tabManager.tabs.forEach { it.editorState.applyPreferences(normalized) }
                    },
                    rootDir = rootDir,
                    onRootDirChange = { rootDir = it },
                    onPersistDraft = ::persistTabDraft,
                    onPersistSession = ::persistSessionSnapshot,
                    onCycleTheme = {
                        val next = MdTokens.cycleThemePreset()
                        prefsState = prefsState.copy(themePreset = next)
                    },
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
    configStore: DesktopConfigStore,
    prefs: EditorPrefs,
    onPrefsChange: (EditorPrefs) -> Unit,
    rootDir: Path?,
    onRootDirChange: (Path?) -> Unit,
    onPersistDraft: (DesktopTab) -> Unit,
    onPersistSession: () -> Unit,
    onCycleTheme: () -> Unit,
    onRegisterSave: (() -> Unit) -> Unit,
    onRegisterFind: (() -> Unit) -> Unit,
    onRegisterQuickOpen: (() -> Unit) -> Unit,
    onRegisterInsertTable: (() -> Unit) -> Unit,
    onRegisterRichPaste: (() -> Unit) -> Unit,
    onRegisterEscape: (() -> Boolean) -> Unit,
    onLaunch: (suspend () -> Unit) -> Unit,
) {
    val s = LocalJotStrings.current
    val themePreset = prefs.themePreset
    val isDark = themePreset.isDark
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
    var showWordCountModal by remember { mutableStateOf(false) }
    var showPreferencesModal by remember { mutableStateOf(false) }
    var showAboutModal by remember { mutableStateOf(false) }
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
            showNotice("${s.open}: ${path.fileName}", isError = true)
        } else {
            opened.editorState.applyPreferences(prefs)
            showNotice(null)
            onLaunch { withContext(Dispatchers.IO) { onPersistSession() } }
        }
    }

    fun triggerSave() {
        val tab = tabManager.activeTab ?: return
        if (tab.file != null) {
            onLaunch {
                val wrote = withContext(Dispatchers.IO) { saveTabIfChanged(tab) }
                if (wrote) refreshNonce++
                if (tab.saveFailed) showNotice(s.saveError, isError = true) else showNotice(null)
            }
        } else {
            onLaunch {
                val chosen = pickMarkdownFile(ownerWindow, FileDialog.SAVE, rootDir, s, tab.title)
                    ?: return@onLaunch
                val oldDraftId = tab.draftId
                val ok = withContext(Dispatchers.IO) {
                    val saved = tabManager.saveTabAs(tab, chosen)
                    if (saved && oldDraftId != null) {
                        configStore.drafts.deleteDraft(oldDraftId)
                        tab.draftId = null
                    }
                    saved
                }
                if (ok) {
                    refreshNonce++
                    showNotice(null)
                    withContext(Dispatchers.IO) { onPersistSession() }
                } else {
                    showNotice(s.saveError, isError = true)
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
            showNotice(s.tapBlockForTable, isError = true)
        }
    }

    fun triggerRichPaste() {
        val tab = tabManager.activeTab ?: return
        when (val payload = readClipboardPayload()) {
            is ClipboardPayload.Image -> {
                if (tab.file == null && rootDir == null) {
                    showNotice(s.openFolderHint, isError = true)
                    return
                }
                onLaunch {
                    val dest = withContext(Dispatchers.IO) {
                        saveClipboardImagePng(payload.image, tab.file, rootDir)
                    }
                    if (dest == null) {
                        showNotice(s.saveError, isError = true)
                        return@onLaunch
                    }
                    if (applyToEditor(tab) { it.insertImageMarkdown("image", dest) }) {
                        showNotice(null)
                    } else {
                        showNotice(s.tapBlockToPaste, isError = true)
                    }
                }
            }
            is ClipboardPayload.Html -> {
                if (applyToEditor(tab) { it.pasteHtmlAtCursor(payload.html) }) {
                    showNotice(null)
                } else {
                    showNotice(s.tapBlockToPaste, isError = true)
                }
            }
            is ClipboardPayload.Text -> {
                if (applyToEditor(tab) { it.pasteMarkdownAtCursor(payload.text) }) {
                    showNotice(null)
                } else {
                    showNotice(s.tapBlockToPaste, isError = true)
                }
            }
            ClipboardPayload.Empty -> {
                showNotice(s.clipboardEmpty, isError = true)
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
            showAboutModal -> {
                showAboutModal = false
                true
            }
            showPreferencesModal -> {
                showPreferencesModal = false
                true
            }
            showWordCountModal -> {
                showWordCountModal = false
                true
            }
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

    // Autosave de 1s após a última edição na aba ativa (arquivo ou rascunho).
    LaunchedEffect(activeTab?.id, activeTab?.editorState?.doc) {
        val tab = activeTab ?: return@LaunchedEffect
        delay(AUTOSAVE_DELAY_MS)
        if (tab.file != null) {
            val wrote = withContext(Dispatchers.IO) { saveTabIfChanged(tab) }
            if (wrote) refreshNonce++
        } else if (prefs.restoreSession) {
            withContext(Dispatchers.IO) { onPersistDraft(tab) }
        }
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
                    rootDir == null || match == null -> showNotice(s.noteMissing(target), isError = true)
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
                    showNotice("${s.footnoteNotFound}: $id", isError = true)
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
                            val chosen = pickDirectory(ownerWindow, rootDir, s.chooseFolder) ?: return@onLaunch
                            onRootDirChange(chosen)
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
                            val chosen = pickMarkdownFile(ownerWindow, FileDialog.LOAD, rootDir, s)
                                ?: return@onLaunch
                            openFile(chosen)
                        }
                    },
                    onImportFile = {
                        onLaunch {
                            val chosen = pickImportFile(ownerWindow, rootDir, s.importAction)
                                ?: return@onLaunch
                            val imported = withContext(Dispatchers.IO) { importDesktopFile(chosen) }
                            if (imported == null) {
                                showNotice(s.importError, isError = true)
                            } else {
                                val (title, md) = imported
                                val draftId = "import-${System.currentTimeMillis()}-${tabManager.tabs.size}"
                                val newTab = tabManager.openInitial(title, md, draftId = draftId)
                                newTab.editorState.applyPreferences(prefs)
                                if (prefs.restoreSession) {
                                    withContext(Dispatchers.IO) { onPersistDraft(newTab) }
                                }
                                showNotice("${s.importedNotice}: $title")
                            }
                        }
                    },
                    onNewTab = {
                        val newTab = tabManager.newEmptyTab(s.untitled)
                        newTab.editorState.applyPreferences(prefs)
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
                                dialogTitle = "${s.html} — ${s.saveAs}",
                                initialDir = rootDir,
                                defaultTitle = tab.title,
                                extensions = listOf(".html", ".htm"),
                            ) ?: return@onLaunch
                            val html = exportToHtml(tab.editorState.text(), tab.title, isDark)
                            val ok = runCatching {
                                withContext(Dispatchers.IO) {
                                    writeTextAtomic(chosen, html)
                                }
                            }.isSuccess
                            showNotice(
                                if (ok) "${s.htmlExported}: ${chosen.fileName}" else s.exportError,
                                isError = !ok,
                            )
                        }
                    },
                    onExportPdf = {
                        val tab = activeTab ?: return@HeaderBar
                        onLaunch {
                            val chosen = pickExportSaveFile(
                                owner = ownerWindow,
                                dialogTitle = "${s.pdf} — ${s.saveAs}",
                                initialDir = rootDir,
                                defaultTitle = tab.title,
                                extensions = listOf(".pdf"),
                            ) ?: return@onLaunch
                            val pdf = exportToPdf(tab.editorState.text(), tab.title)
                            val ok = runCatching {
                                withContext(Dispatchers.IO) { Files.write(chosen, pdf) }
                            }.isSuccess
                            showNotice(
                                if (ok) "${s.pdfExported}: ${chosen.fileName}" else s.exportError,
                                isError = !ok,
                            )
                        }
                    },
                    onToggleSource = { activeTab?.editorState?.toggleSourceMode() },
                    onCycleTheme = onCycleTheme,
                    onOpenPreferences = { showPreferencesModal = true },
                    onOpenAbout = { showAboutModal = true },
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
                                showNotice(s.replacedCount(1), isError = false)
                            }
                        },
                        onReplaceAll = {
                            if (invalidRegex) {
                                showNotice(s.invalidRegex, isError = true)
                            } else {
                                val replaced = activeTab.editorState.replaceAll(
                                    query = findQuery,
                                    replacement = replaceQuery,
                                    caseSensitive = caseSensitive,
                                    wholeWord = wholeWord,
                                    regex = findRegex,
                                )
                                showNotice(s.replacedCount(replaced), isError = false)
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
                        val closingDraftId = tabManager.tabs.firstOrNull { it.id == id }?.draftId
                        val closed = tabManager.close(id, defaultEmptyTitle = s.untitled)
                        if (closed) {
                            listStates.remove(id)
                            if (closingDraftId != null) {
                                onLaunch {
                                    withContext(Dispatchers.IO) {
                                        configStore.drafts.deleteDraft(closingDraftId)
                                    }
                                }
                            }
                            onLaunch { withContext(Dispatchers.IO) { onPersistSession() } }
                        } else {
                            showNotice(s.saveError, isError = true)
                        }
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
                    onShowWordCount = {
                        if (activeTab != null) showWordCountModal = true
                    },
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
        if (showWordCountModal && activeTab != null) {
            val stats = remember(activeTab.id, activeTab.editorState.doc) {
                docStatsOf(activeTab.editorState.doc)
            }
            val selectionStats = remember(
                activeTab.id,
                activeTab.editorState.editValue.selection,
                activeTab.editorState.editValue.text,
            ) {
                activeTab.editorState.selectedText().takeIf { it.isNotEmpty() }?.let(::selectionStatsOf)
            }
            WordCountModal(
                stats = stats,
                selectionStats = selectionStats,
                onDismiss = { showWordCountModal = false },
            )
        }
        if (showPreferencesModal) {
            PreferencesModal(
                prefs = prefs,
                onPrefsChange = onPrefsChange,
                onDismiss = { showPreferencesModal = false },
            )
        }
        if (showAboutModal) {
            AboutModal(onDismiss = { showAboutModal = false })
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
                                        dialogError = s.saveError
                                    }
                                }
                                is TreeDialogState.NewFolder -> {
                                    val created = withContext(Dispatchers.IO) {
                                        createDesktopFolder(dialog.parentDir, inputName)
                                    }
                                    if (created != null) {
                                        refreshNonce++
                                        treeDialog = null
                                        showNotice("${s.newFolder}: ${created.fileName}")
                                    } else {
                                        dialogError = s.saveError
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
                                        withContext(Dispatchers.IO) { onPersistSession() }
                                    } else {
                                        dialogError = s.renameFailed(dialog.entry.name)
                                    }
                                }
                                is TreeDialogState.Delete -> {
                                    val deleted = withContext(Dispatchers.IO) {
                                        deleteDesktopEntry(dialog.entry.path)
                                    }
                                    if (deleted) {
                                        tabManager.closeRemovedPath(dialog.entry.path, defaultEmptyTitle = s.untitled)
                                        listStates.keys.retainAll(tabManager.tabs.map { it.id }.toSet())
                                        refreshNonce++
                                        treeDialog = null
                                        showNotice("${s.delete}: ${dialog.entry.name}")
                                        withContext(Dispatchers.IO) { onPersistSession() }
                                    } else {
                                        dialogError = s.saveError
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
    val s = LocalJotStrings.current
    Column(
        Modifier
            .width(270.dp)
            .fillMaxHeight()
            .background(MdTokens.sidebarBackground),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            SidebarTabChip(
                label = s.files,
                selected = sidebarTab == SidebarTab.FILES,
                onClick = { onSelectTab(SidebarTab.FILES) },
                modifier = Modifier.weight(1f),
            )
            SidebarTabChip(
                label = s.outline,
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
                    text = rootDir?.fileName?.toString() ?: s.folder,
                    style = MaterialTheme.typography.titleSmall,
                    color = MdTokens.text,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f).padding(start = 8.dp),
                )
                TextButton(onClick = onPickFolder) {
                    Text(if (rootDir == null) s.chooseFolder else s.changeFolder)
                }
            }

            if (rootDir != null) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 2.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    SidebarActionChip(
                        label = s.addNote,
                        onClick = { onCreateNoteIn(rootDir) },
                        modifier = Modifier.weight(1f),
                    )
                    SidebarActionChip(
                        label = s.addFolder,
                        onClick = onCreateFolderInRoot,
                        modifier = Modifier.weight(1f),
                    )
                }
                OutlinedTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    placeholder = { Text(s.find) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp),
                )
            }
            HorizontalDivider(color = MdTokens.tableBorder)

            if (rootDir == null) {
                Text(
                    text = s.openFolderHint,
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
                        text = s.nothingFound,
                        style = MaterialTheme.typography.bodySmall,
                        color = MdTokens.muted,
                        modifier = Modifier.padding(14.dp),
                    )
                }
            } else if (entries.isEmpty()) {
                Text(
                    text = s.noFiles,
                    style = MaterialTheme.typography.bodySmall,
                    color = MdTokens.muted,
                    modifier = Modifier.padding(14.dp),
                )
            } else {
                LazyColumn(Modifier.fillMaxSize()) {
                    items(entries, key = { it.path.toString() }) { entry ->
                        val activeNorm = activeTab?.file?.toAbsolutePath()?.normalize()
                        val isOpenFile = !entry.isDir && activeNorm == entry.path.toAbsolutePath().normalize()
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(IntrinsicSize.Min)
                                .background(if (isOpenFile) MdTokens.activeItemBackground else Color.Transparent)
                                .clickable(enabled = !entry.isDir) { onOpenPath(entry.path) },
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(
                                Modifier
                                    .width(3.dp)
                                    .fillMaxHeight()
                                    .background(if (isOpenFile) MdTokens.activeItemBorder else Color.Transparent),
                            )
                            Row(
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(
                                        start = (9 + minOf(entry.depth, 6) * 12).dp,
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
            }
        } else {
            val outline = remember(activeTab?.id, activeTab?.editorState?.doc) {
                activeTab?.let { outlineOf(it.editorState.doc) }.orEmpty()
            }
            if (outline.isEmpty()) {
                Text(
                    text = s.noHeadings,
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
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                color = if (selected) MdTokens.text else MdTokens.muted,
                maxLines = 1,
            )
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(2.dp)
                .background(if (selected) MdTokens.activeItemBorder else Color.Transparent),
        )
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
    onImportFile: () -> Unit,
    onNewTab: () -> Unit,
    onSave: () -> Unit,
    onToggleFind: () -> Unit,
    onInsertTable: () -> Unit,
    onRichPaste: () -> Unit,
    onExportHtml: () -> Unit,
    onExportPdf: () -> Unit,
    onToggleSource: () -> Unit,
    onCycleTheme: () -> Unit,
    onOpenPreferences: () -> Unit,
    onOpenAbout: () -> Unit,
) {
    val s = LocalJotStrings.current
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
                Text(if (sidebarOpen) "◧ ${s.files}" else s.files)
            }
            TextButton(onClick = onOpenFile) { Text(s.openFile) }
            TextButton(onClick = onImportFile) { Text(s.importAction) }
            TextButton(onClick = onNewTab) { Text(s.addNote) }
            TextButton(onClick = onSave) { Text(s.save) }
            TextButton(onClick = onToggleFind) { Text(if (findOpen) "${s.close} ${s.find}" else s.find) }
            HeaderTapButton(label = s.table, onClick = onInsertTable)
            HeaderTapButton(label = s.smartPaste, onClick = onRichPaste)
            TextButton(onClick = onExportHtml) { Text(s.html) }
            TextButton(onClick = onExportPdf) { Text(s.pdf) }
            TextButton(onClick = onToggleSource) {
                Text(if (activeTab?.editorState?.isSourceMode == true) "Visual" else s.source)
            }
            TextButton(onClick = onCycleTheme) {
                Text("${s.theme}: ${themePreset.displayName}")
            }
            TextButton(onClick = onOpenPreferences) {
                Text(s.preferences)
            }
            TextButton(onClick = onOpenAbout) {
                Text(s.about)
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
                activeTab.saveFailed -> s.saveError to MaterialTheme.colorScheme.error
                activeTab.file == null -> s.draft to MdTokens.muted
                activeTab.isDirty -> "•" to MdTokens.muted
                else -> "✓" to MdTokens.muted
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
    val s = LocalJotStrings.current
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
                placeholder = { Text(s.findInNote) },
                isError = regexError,
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
            DesktopFindOptionChip(label = "Aa", active = caseSensitive, onClick = onToggleCaseSensitive)
            DesktopFindOptionChip(label = "[ab]", active = wholeWord, onClick = onToggleWholeWord)
            DesktopFindOptionChip(label = ".*", active = regex, onClick = onToggleRegex)
            if (regexError) {
                Text(s.invalidRegex, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            } else {
                Text(counter, style = MaterialTheme.typography.bodySmall, color = MdTokens.muted)
            }
            TextButton(onClick = onPrev, enabled = matchCount > 0) { Text("↑") }
            TextButton(onClick = onNext, enabled = matchCount > 0) { Text("↓") }
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
                placeholder = { Text(s.replaceWith) },
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onReplaceCurrent, enabled = matchCount > 0) { Text(s.replace) }
            TextButton(onClick = onReplaceAll, enabled = findQuery.isNotEmpty() && !regexError) { Text(s.replaceAll) }
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
    val s = LocalJotStrings.current
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
        is TreeDialogState.NewNote -> s.newNote
        is TreeDialogState.NewFolder -> s.newFolder
        is TreeDialogState.Rename -> if (dialog.entry.isDir) s.renameFolder else s.renameNote
        is TreeDialogState.Delete -> s.deleteTitle(dialog.entry.name)
    }
    val placeholder = when (dialog) {
        is TreeDialogState.NewNote -> "${s.newNote} (.md)"
        is TreeDialogState.NewFolder -> s.newFolder
        is TreeDialogState.Rename -> s.rename
        is TreeDialogState.Delete -> ""
    }
    val confirmLabel = when (dialog) {
        is TreeDialogState.NewNote, is TreeDialogState.NewFolder -> s.create
        is TreeDialogState.Rename -> s.rename
        is TreeDialogState.Delete -> s.delete
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
                        text = "${s.folder}: ${dialog.parentDir.fileName ?: dialog.parentDir}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MdTokens.muted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (dialog is TreeDialogState.Delete) {
                    Text(
                        text = if (dialog.entry.isDir) s.deleteFolderConfirm else s.deleteFileConfirm,
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
                        Text(s.cancel)
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
    onShowWordCount: () -> Unit,
) {
    val s = LocalJotStrings.current
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
            label = s.outline,
            active = outlineOpen,
            onClick = onToggleOutline,
        )
        FooterToggleButton(
            label = s.source,
            active = activeTab?.editorState?.isSourceMode == true,
            onClick = onToggleSource,
        )
        FooterToggleButton(
            label = s.focus,
            active = activeTab?.editorState?.isFocusMode == true,
            onClick = onToggleFocus,
        )
        FooterToggleButton(
            label = s.typewriter,
            active = activeTab?.editorState?.isTypewriterMode == true,
            onClick = onToggleTypewriter,
        )
        Spacer(Modifier.weight(1f))
        if (activeTab != null) {
            val stats = remember(activeTab.id, activeTab.editorState.doc) {
                docStatsOf(activeTab.editorState.doc)
            }
            Text(
                text = s.wordCountSummary(stats.words, stats.chars, stats.readingTimeMinutes),
                style = MaterialTheme.typography.labelSmall,
                color = MdTokens.muted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .clickable(onClick = onShowWordCount)
                    .padding(horizontal = 6.dp, vertical = 2.dp),
            )
        }
    }
}

/** Popover/diálogo de contagem de palavras no estilo Typora (Minutos, Linhas, Palavras, Caracteres). */
@Composable
private fun WordCountModal(
    stats: io.github.walterfr.jotmd.core.DocStats,
    selectionStats: SelectionStats?,
    onDismiss: () -> Unit,
) {
    val s = LocalJotStrings.current
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.20f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onDismiss,
            ),
        contentAlignment = Alignment.BottomEnd,
    ) {
        Surface(
            modifier = Modifier
                .padding(end = 16.dp, bottom = 38.dp)
                .width(260.dp)
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
            Column(
                modifier = Modifier.fillMaxWidth().padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = s.wordCount,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MdTokens.text,
                )
                HorizontalDivider(color = MdTokens.tableBorder)
                WordCountRow(label = s.minutes, value = stats.readingTimeMinutes.toString())
                WordCountRow(label = s.lines, value = stats.lines.toString())
                WordCountRow(label = s.words, value = stats.words.toString())
                WordCountRow(label = s.characters, value = stats.chars.toString())
                if (selectionStats != null) {
                    HorizontalDivider(color = MdTokens.tableBorder)
                    Text(
                        text = s.inSelection,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MdTokens.text,
                    )
                    WordCountRow(label = s.words, value = selectionStats.words.toString())
                    WordCountRow(label = s.characters, value = selectionStats.chars.toString())
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text(s.close) }
                }
            }
        }
    }
}

@Composable
private fun WordCountRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = label, style = MaterialTheme.typography.bodySmall, color = MdTokens.muted)
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = MdTokens.text,
        )
    }
}

private fun paletteForPreset(preset: MdThemePreset): MdPalette = when (preset) {
    MdThemePreset.GITHUB -> LightMdPalette
    MdThemePreset.NEWSPRINT -> NewsprintMdPalette
    MdThemePreset.PIXYLL -> PixyllMdPalette
    MdThemePreset.WHITEY -> WhiteyMdPalette
    MdThemePreset.NIGHT -> DarkMdPalette
}

/** Modal Sobre com links de apoio, payload Pix e assinatura local. */
@Composable
private fun AboutModal(onDismiss: () -> Unit) {
    val s = LocalJotStrings.current
    var pixCopied by remember { mutableStateOf(false) }

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
                .width(460.dp)
                .heightIn(max = 580.dp)
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
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(18.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            text = "JotMD",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MdTokens.text,
                        )
                        Text(
                            text = JotAboutInfo.MADE_IN_FORTALEZA,
                            style = MaterialTheme.typography.bodySmall,
                            color = MdTokens.muted,
                        )
                    }
                    TextButton(onClick = onDismiss) { Text(s.close) }
                }
                HorizontalDivider(color = MdTokens.tableBorder)
                Text(
                    text = s.aboutDescription,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MdTokens.text,
                )
                HorizontalDivider(color = MdTokens.tableBorder)
                Text(
                    text = s.supportProject,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MdTokens.link,
                )
                Text(
                    text = s.supportPrompt,
                    style = MaterialTheme.typography.bodySmall,
                    color = MdTokens.text,
                )
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    for ((label, url) in listOf(
                        "GitHub Sponsors" to JotAboutInfo.GITHUB_SPONSORS_URL,
                        "Ko-fi" to JotAboutInfo.KOFI_URL,
                        "Buy Me a Coffee" to JotAboutInfo.BUY_ME_A_COFFEE_URL,
                        s.sourceCode to JotAboutInfo.REPO_URL,
                    )) {
                        TextButton(onClick = { openExternalUri(url) }) { Text(label) }
                    }
                }
                HorizontalDivider(color = MdTokens.tableBorder)
                Text(
                    text = s.pixLabel,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MdTokens.text,
                )
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = MdTokens.background,
                    border = BorderStroke(1.dp, MdTokens.tableBorder),
                ) {
                    Text(
                        text = JotAboutInfo.PIX_PAYLOAD,
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                        ),
                        color = MdTokens.muted,
                        modifier = Modifier.fillMaxWidth().padding(10.dp),
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = JotAboutInfo.LICENSE_LABEL,
                        style = MaterialTheme.typography.bodySmall,
                        color = MdTokens.muted,
                    )
                    TextButton(
                        onClick = {
                            runCatching {
                                Toolkit.getDefaultToolkit().systemClipboard
                                    .setContents(StringSelection(JotAboutInfo.PIX_PAYLOAD), null)
                            }
                            pixCopied = true
                        },
                    ) {
                        Text(if (pixCopied) s.pixCopied else s.copyPix)
                    }
                }
            }
        }
    }
}

/** Modal de Preferências (Aparência, Editor e Geral) inspirado no Typora. */
@Composable
private fun PreferencesModal(
    prefs: EditorPrefs,
    onPrefsChange: (EditorPrefs) -> Unit,
    onDismiss: () -> Unit,
) {
    val s = LocalJotStrings.current
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
                .width(500.dp)
                .heightIn(max = 620.dp)
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
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(18.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = s.preferences,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MdTokens.text,
                    )
                    TextButton(onClick = onDismiss) { Text(s.close) }
                }
                HorizontalDivider(color = MdTokens.tableBorder)

                // Seção 1: Aparência
                Text(
                    text = s.appearance,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MdTokens.link,
                )
                Text(text = s.theme, style = MaterialTheme.typography.bodySmall, color = MdTokens.muted)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    for (preset in MdThemePreset.entries) {
                        val p = paletteForPreset(preset)
                        val selected = prefs.themePreset == preset
                        Surface(
                            onClick = { onPrefsChange(prefs.copy(themePreset = preset)) },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(6.dp),
                            color = p.background,
                            border = BorderStroke(
                                width = if (selected) 2.dp else 1.dp,
                                color = if (selected) MdTokens.activeItemBorder else MdTokens.tableBorder,
                            ),
                        ) {
                            Column(
                                modifier = Modifier.padding(vertical = 8.dp, horizontal = 6.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                Box(
                                    Modifier
                                        .size(14.dp)
                                        .clip(CircleShape)
                                        .background(p.link),
                                )
                                Text(
                                    text = preset.displayName,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                                    color = p.text,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(s.fontSize, style = MaterialTheme.typography.bodySmall, color = MdTokens.text)
                        Text(
                            "${prefs.fontSizeSp.roundToInt()} sp",
                            style = MaterialTheme.typography.bodySmall,
                            color = MdTokens.muted,
                        )
                    }
                    Slider(
                        value = prefs.fontSizeSp.coerceIn(14f, 22f),
                        onValueChange = { onPrefsChange(prefs.copy(fontSizeSp = it.roundToInt().toFloat())) },
                        valueRange = 14f..22f,
                        steps = 7,
                    )
                }

                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(s.columnWidth, style = MaterialTheme.typography.bodySmall, color = MdTokens.text)
                        Text(
                            "${prefs.maxColumnWidthDp} dp",
                            style = MaterialTheme.typography.bodySmall,
                            color = MdTokens.muted,
                        )
                    }
                    Slider(
                        value = prefs.maxColumnWidthDp.toFloat().coerceIn(680f, 1100f),
                        onValueChange = {
                            val snapped = ((it / 20f).roundToInt() * 20).coerceIn(680, 1100)
                            onPrefsChange(prefs.copy(maxColumnWidthDp = snapped))
                        },
                        valueRange = 680f..1100f,
                    )
                }

                HorizontalDivider(color = MdTokens.tableBorder)

                // Seção 2: Editor
                Text(
                    text = s.editor,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MdTokens.link,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(s.autoPair, style = MaterialTheme.typography.bodyMedium, color = MdTokens.text)
                    Switch(
                        checked = prefs.autoPairEnabled,
                        onCheckedChange = { onPrefsChange(prefs.copy(autoPairEnabled = it)) },
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(s.indentSize, style = MaterialTheme.typography.bodyMedium, color = MdTokens.text)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        for (size in listOf(2, 4)) {
                            DesktopFindOptionChip(
                                label = "$size ${s.spaces}",
                                active = prefs.indentSize == size,
                                onClick = { onPrefsChange(prefs.copy(indentSize = size)) },
                            )
                        }
                    }
                }

                HorizontalDivider(color = MdTokens.tableBorder)

                // Seção 3: Geral
                Text(
                    text = s.general,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MdTokens.link,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(s.language, style = MaterialTheme.typography.bodyMedium, color = MdTokens.text)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        val langOptions = listOf(
                            AppLanguage.SYSTEM to s.system,
                            AppLanguage.PT_BR to "PT-BR",
                            AppLanguage.EN to "EN",
                        )
                        for ((lang, label) in langOptions) {
                            DesktopFindOptionChip(
                                label = label,
                                active = prefs.language == lang,
                                onClick = { onPrefsChange(prefs.copy(language = lang)) },
                            )
                        }
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(s.restoreSession, style = MaterialTheme.typography.bodyMedium, color = MdTokens.text)
                    Switch(
                        checked = prefs.restoreSession,
                        onCheckedChange = { onPrefsChange(prefs.copy(restoreSession = it)) },
                    )
                }
            }
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
    val s = LocalJotStrings.current
    val focusRequester = remember { FocusRequester() }
    val items = remember(query, entries, openTabs, s.draft) {
        filterQuickOpen(query = query, entries = entries, openTabs = openTabs, draftLabel = s.draft)
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
                    placeholder = { Text(s.quickOpenPlaceholder) },
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
                        text = s.quickOpenNothingFound,
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

private suspend fun pickDirectory(owner: Frame, initial: Path?, dialogTitleText: String): Path? =
    suspendCoroutine { cont ->
        SwingUtilities.invokeLater {
            runCatching { UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName()) }
            val chooser = JFileChooser(initial?.toFile()).apply {
                dialogTitle = dialogTitleText
                fileSelectionMode = JFileChooser.DIRECTORIES_ONLY
                isAcceptAllFileFilterUsed = false
            }
            val result = chooser.showOpenDialog(owner)
            val selected = if (result == JFileChooser.APPROVE_OPTION) chooser.selectedFile?.toPath() else null
            cont.resume(selected)
        }
    }

private suspend fun pickImportFile(
    owner: Frame,
    initialDir: Path?,
    dialogTitleText: String,
): Path? = suspendCoroutine { cont ->
    SwingUtilities.invokeLater {
        runCatching { UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName()) }
        val chooser = JFileChooser(initialDir?.toFile()).apply {
            dialogTitle = dialogTitleText
            fileSelectionMode = JFileChooser.FILES_ONLY
            fileFilter = FileNameExtensionFilter(
                "HTML, DOCX, EPUB (*.html, *.htm, *.docx, *.epub)",
                "html",
                "htm",
                "xhtml",
                "docx",
                "epub",
            )
            isAcceptAllFileFilterUsed = true
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
    strings: JotStrings,
    defaultName: String? = null,
): Path? = suspendCoroutine { cont ->
    SwingUtilities.invokeLater {
        val dialog = FileDialog(
            owner,
            if (mode == FileDialog.SAVE) strings.saveAs else strings.openFile,
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
