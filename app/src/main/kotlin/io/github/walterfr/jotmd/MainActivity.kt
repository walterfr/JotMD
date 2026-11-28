package io.github.walterfr.jotmd

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import io.github.walterfr.jotmd.core.DraftRepository
import io.github.walterfr.jotmd.core.docRefsOf
import io.github.walterfr.jotmd.core.docStatsOf
import io.github.walterfr.jotmd.core.exportToHtml
import io.github.walterfr.jotmd.core.exportToPdf
import io.github.walterfr.jotmd.core.findInDocument
import io.github.walterfr.jotmd.core.importDocumentBytes
import io.github.walterfr.jotmd.core.isValidSearchRegex
import io.github.walterfr.jotmd.core.outlineOf
import io.github.walterfr.jotmd.core.selectionStatsOf
import io.github.walterfr.jotmd.core.suggestedMarkdownTitle
import io.github.walterfr.jotmd.editor.AppLanguage
import io.github.walterfr.jotmd.editor.DarkMdPalette
import io.github.walterfr.jotmd.editor.EditorPrefs
import io.github.walterfr.jotmd.editor.FOOTNOTE_URI
import io.github.walterfr.jotmd.editor.JotAboutInfo
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
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import io.github.walterfr.jotmd.editor.EditorScreen
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

private const val AUTOSAVE_DELAY_MS = 1000L

/**
 * OpenDocument pede leitura E escrita persistentes na própria intent. Sem os
 * flags, o seletor devolve um URI só de leitura e `openOutputStream` falha.
 */
private class OpenMarkdown : ActivityResultContracts.OpenDocument() {
    override fun createIntent(context: android.content.Context, input: Array<String>): Intent =
        super.createIntent(context, input).addFlags(
            Intent.FLAG_GRANT_READ_URI_PERMISSION or
                Intent.FLAG_GRANT_WRITE_URI_PERMISSION or
                Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION,
        )
}

private enum class DrawerTab { FILES, OUTLINE }

private sealed interface FileDialogState {
    data class CreateNote(val parentDocId: String, val parentLabel: String?) : FileDialogState
    data class CreateFolder(val parentDocId: String) : FileDialogState
    data class Rename(val node: DocNode) : FileDialogState
    data class Delete(val node: DocNode) : FileDialogState
}

private fun tabKey(tab: EditorTab): String? = tab.session?.uri?.toString() ?: tab.key

private fun descendantFilesWithRelativePath(nodes: List<DocNode>, dir: DocNode): List<Pair<DocNode, String>> {
    val startIndex = nodes.indexOfFirst { it.docId == dir.docId }
    if (startIndex < 0) return emptyList()
    val out = ArrayList<Pair<DocNode, String>>()
    val dirStack = ArrayList<Pair<Int, String>>()
    for (i in (startIndex + 1) until nodes.size) {
        val child = nodes[i]
        if (child.depth <= dir.depth) break
        while (dirStack.isNotEmpty() && dirStack.last().first >= child.depth) {
            dirStack.removeAt(dirStack.lastIndex)
        }
        if (child.isDir) {
            dirStack.add(child.depth to child.name)
        } else {
            val prefix = dirStack.joinToString("/") { it.second }
            val rel = if (prefix.isEmpty()) child.name else "$prefix/${child.name}"
            out += child to rel
        }
    }
    return out
}

/** Seletor de pasta com leitura+escrita persistentes (mesmo motivo do OpenMarkdown). */
private class PickRoot : ActivityResultContracts.OpenDocumentTree() {
    override fun createIntent(context: Context, input: Uri?): Intent =
        super.createIntent(context, input).addFlags(
            Intent.FLAG_GRANT_READ_URI_PERMISSION or
                Intent.FLAG_GRANT_WRITE_URI_PERMISSION or
                Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION,
        )
}

class MainActivity : ComponentActivity() {
    private var incomingSeq = 0L
    private var incomingEvent by mutableStateOf<IncomingIntentEvent<Intent>?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        incomingEvent = intent?.let { IncomingIntentEvent(++incomingSeq, it) }
        enableEdgeToEdge()
        setContent {
            val context = LocalContext.current
            val prefs = remember { context.getSharedPreferences("jotmd", Context.MODE_PRIVATE) }
            var prefsState by remember { mutableStateOf(loadAndroidPrefs(prefs)) }
            MdTokens.applyPreferences(prefsState)
            val isDark = prefsState.themePreset.isDark
            val strings = remember(prefsState.language) { stringsFor(prefsState.language) }
            LaunchedEffect(isDark) {
                enableEdgeToEdge(
                    statusBarStyle = if (isDark) {
                        SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
                    } else {
                        SystemBarStyle.light(
                            android.graphics.Color.TRANSPARENT,
                            android.graphics.Color.TRANSPARENT,
                        )
                    },
                    navigationBarStyle = if (isDark) {
                        SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
                    } else {
                        SystemBarStyle.light(
                            android.graphics.Color.TRANSPARENT,
                            android.graphics.Color.TRANSPARENT,
                        )
                    },
                )
            }
            MaterialTheme(colorScheme = if (isDark) darkColorScheme() else lightColorScheme()) {
                CompositionLocalProvider(LocalJotStrings provides strings) {
                    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                        EditorHostScreen(
                            prefs = prefs,
                            prefsState = prefsState,
                            onUpdatePrefs = { next ->
                                val normalized = next.normalized()
                                prefsState = normalized
                                saveAndroidPrefs(prefs, normalized)
                                MdTokens.applyPreferences(normalized)
                            },
                            incomingEvent = incomingEvent,
                            onConsumeIntent = { seq -> incomingEvent = consumeIncomingEvent(incomingEvent, seq) },
                        )
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        incomingEvent = IncomingIntentEvent(++incomingSeq, intent)
    }
}

/**
 * Shell da tela de edição: abre um arquivo por SAF, toca um bloco para editar
 * o markdown cru dele, toolbar embaixo para negrito/itálico/riscado/destaque/
 * código na seleção. Enter divide bloco, Backspace no início funde, `↑`/`↓`
 * navegam, Ctrl+Z/Ctrl+Shift+Z desfazem — ver BlockEditor.kt.
 *
 * Autosave: 1s depois da última mudança, e de novo ao sair do app (ON_STOP).
 * Grava arquivos abertos por SAF e rascunhos locais das abas sem arquivo.
 * Falha de escrita aparece no cabeçalho.
 */
@Composable
private fun EditorHostScreen(
    prefs: android.content.SharedPreferences,
    prefsState: EditorPrefs,
    onUpdatePrefs: (EditorPrefs) -> Unit,
    incomingEvent: IncomingIntentEvent<Intent>?,
    onConsumeIntent: (Long) -> Unit,
) {
    val s = LocalJotStrings.current
    val isDark = prefsState.themePreset.isDark
    val context = LocalContext.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val tabManager = remember { TabManager(initialTitle = s.untitled, initialText = "", initialKey = null) }
    val draftRepo = remember(context) { DraftRepository(context.filesDir.toPath().resolve("drafts")) }
    val activeTab = tabManager.activeTab
    val listStates = remember { mutableMapOf<Long, LazyListState>() }
    val tabRowState = rememberLazyListState()
    // Aviso transitório no cabeçalho (ex.: wikilink sem arquivo). Some ao abrir outro.
    var notice by remember { mutableStateOf<String?>(null) }
    var showWordCountDialog by remember { mutableStateOf(false) }
    var showPrefsDialog by remember { mutableStateOf(false) }
    var showAboutDialog by remember { mutableStateOf(false) }
    var showMoreMenu by remember { mutableStateOf(false) }
    var sessionRestored by remember { mutableStateOf(false) }
    var openedExternally by remember { mutableStateOf(false) }

    fun sessionTabKey(tab: EditorTab): String? = tab.key ?: tab.draftId?.let { "draft:$it" }

    fun persistSessionTabs() {
        val snapshot = if (prefsState.restoreSession) {
            AndroidSessionTabs(
                openUris = tabManager.tabs.mapNotNull { it.key },
                activeKey = sessionTabKey(tabManager.activeTab),
            )
        } else {
            AndroidSessionTabs()
        }
        saveAndroidSessionTabs(prefs, snapshot)
    }

    LaunchedEffect(prefsState, activeTab.id, tabManager.tabs.size) {
        for (tab in tabManager.tabs) {
            tab.editorState.applyPreferences(prefsState)
        }
    }

    LaunchedEffect(Unit) {
        try {
            if (prefsState.restoreSession) {
                val saved = loadAndroidSessionTabs(prefs)
                for (uriStr in saved.openUris) {
                    val uri = runCatching { Uri.parse(uriStr) }.getOrNull() ?: continue
                    val loaded = withContext(Dispatchers.IO) {
                        val content = runCatching {
                            context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                        }.getOrNull() ?: return@withContext null
                        val name = displayName(context.contentResolver, uri) ?: s.untitled
                        name to content
                    } ?: continue
                    val newSession = FileSession(context.contentResolver, uri, lastSaved = loaded.second)
                    val created = tabManager.openOrFocus(
                        key = uriStr,
                        title = loaded.first,
                        text = loaded.second,
                        session = newSession,
                        replaceInitialBlank = true,
                    )
                    created.editorState.applyPreferences(prefsState)
                }
                val drafts = withContext(Dispatchers.IO) { draftRepo.loadDrafts() }
                for (d in drafts.reversed()) {
                    if (tabManager.tabs.none { it.draftId == d.id }) {
                        val created = tabManager.openOrFocus(
                            key = null,
                            title = d.title.ifBlank { s.untitled },
                            text = d.content,
                            draftId = d.id,
                            replaceInitialBlank = true,
                        )
                        created.editorState.applyPreferences(prefsState)
                    }
                }
                val targetKey = saved.activeKey
                if (!openedExternally && targetKey != null) {
                    val match = tabManager.tabs.firstOrNull { sessionTabKey(it) == targetKey }
                    if (match != null) {
                        tabManager.select(match.id)
                    }
                }
            }
        } finally {
            sessionRestored = true
        }
    }

    LaunchedEffect(
        sessionRestored,
        prefsState.restoreSession,
        tabManager.tabs.map { it.id to it.draftId },
        activeTab.id,
    ) {
        if (sessionRestored) {
            persistSessionTabs()
        }
    }

    fun flushTab(tab: EditorTab): Boolean {
        val session = tab.session
        if (session != null) {
            val failed = runCatching { session.saveIfChanged(tab.editorState.text()) }.isFailure
            tabManager.updateSaveState(tab.id, failed)
            return !failed
        }
        val text = tab.editorState.text()
        if (text.isNotBlank()) {
            val draftId = tab.draftId ?: "draft-${System.currentTimeMillis()}-${tab.id}".also {
                tabManager.updateDraftId(tab.id, it)
            }
            runCatching { draftRepo.saveDraft(draftId, tab.title, text) }
        } else if (tab.draftId != null) {
            val oldDraftId = tab.draftId
            runCatching { draftRepo.deleteDraft(oldDraftId) }
            tabManager.updateDraftId(tab.id, null)
        }
        return true
    }

    suspend fun flushTabAsync(tab: EditorTab, precomputedText: String? = null): Boolean {
        val session = tab.session
        val text = precomputedText ?: withContext(Dispatchers.Main.immediate) { tab.editorState.text() }
        if (session != null) {
            val failed = runCatching { withContext(Dispatchers.IO) { session.saveIfChanged(text) } }.isFailure
            withContext(Dispatchers.Main.immediate) { tabManager.updateSaveState(tab.id, failed) }
            return !failed
        }
        if (text.isNotBlank()) {
            val draftId = tab.draftId ?: "draft-${System.currentTimeMillis()}-${tab.id}".also { newId ->
                withContext(Dispatchers.Main.immediate) { tabManager.updateDraftId(tab.id, newId) }
            }
            withContext(Dispatchers.IO) { runCatching { draftRepo.saveDraft(draftId, tab.title, text) } }
        } else if (tab.draftId != null) {
            val oldDraftId = tab.draftId
            withContext(Dispatchers.IO) { runCatching { draftRepo.deleteDraft(oldDraftId) } }
            withContext(Dispatchers.Main.immediate) { tabManager.updateDraftId(tab.id, null) }
        }
        return true
    }

    // Reinicia a cada mudança em doc ou troca de aba: só chega ao save quem ficou 1s sem editar.
    LaunchedEffect(activeTab.id, activeTab.editorState.doc, activeTab.session) {
        val session = activeTab.session
        if (session != null) {
            delay(AUTOSAVE_DELAY_MS)
            val text = activeTab.editorState.text()
            val failed = runCatching { withContext(Dispatchers.IO) { session.saveIfChanged(text) } }.isFailure
            tabManager.updateSaveState(activeTab.id, failed)
        } else {
            delay(AUTOSAVE_DELAY_MS)
            val text = activeTab.editorState.text()
            if (text.isNotBlank()) {
                val draftId = activeTab.draftId ?: "draft-${System.currentTimeMillis()}-${activeTab.id}".also {
                    tabManager.updateDraftId(activeTab.id, it)
                }
                withContext(Dispatchers.IO) {
                    runCatching { draftRepo.saveDraft(draftId, activeTab.title, text) }
                }
            } else if (activeTab.draftId != null) {
                val oldDraftId = activeTab.draftId
                withContext(Dispatchers.IO) {
                    runCatching { draftRepo.deleteDraft(oldDraftId) }
                }
                tabManager.updateDraftId(activeTab.id, null)
            }
        }
    }

    LaunchedEffect(activeTab.id) {
        val index = tabManager.tabs.indexOfFirst { it.id == activeTab.id }
        if (index < 0) return@LaunchedEffect
        val layout = tabRowState.layoutInfo
        val item = layout.visibleItemsInfo.firstOrNull { it.index == index }
        val fullyVisible = item != null &&
            item.offset >= layout.viewportStartOffset &&
            item.offset + item.size <= layout.viewportEndOffset
        if (!fullyVisible) {
            tabRowState.animateScrollToItem(index)
        }
    }

    // Matar o app dentro da janela do debounce perderia a última edição.
    val stopFlushScope = remember { CoroutineScope(SupervisorJob() + Dispatchers.IO) }
    val lifecycle = (context as ComponentActivity).lifecycle
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) {
                val snapshots = tabManager.tabs.toList().map { it to it.editorState.text() }
                persistSessionTabs()
                stopFlushScope.launch {
                    for ((tab, text) in snapshots) {
                        flushTabAsync(tab, text)
                    }
                }
            }
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }

    val scope = rememberCoroutineScope()

    var showFindBar by remember { mutableStateOf(false) }
    var findQuery by remember { mutableStateOf("") }
    var replaceQuery by remember { mutableStateOf("") }
    var caseSensitive by remember { mutableStateOf(false) }
    var wholeWord by remember { mutableStateOf(false) }
    var findRegex by remember { mutableStateOf(false) }
    var matchCursor by remember { mutableStateOf(0) }
    val regexValid = remember(findQuery, findRegex) { !findRegex || isValidSearchRegex(findQuery) }
    val docMatches = remember(
        activeTab.id,
        activeTab.editorState.doc,
        findQuery,
        caseSensitive,
        wholeWord,
        findRegex,
        regexValid,
    ) {
        if (!regexValid) {
            emptyList()
        } else {
            findInDocument(
                activeTab.editorState.doc,
                findQuery,
                caseSensitive = caseSensitive,
                wholeWord = wholeWord,
                regex = findRegex,
            )
        }
    }

    LaunchedEffect(docMatches) {
        if (docMatches.isEmpty()) {
            matchCursor = 0
        } else if (matchCursor >= docMatches.size) {
            matchCursor = 0
        }
    }

    LaunchedEffect(
        showFindBar,
        activeTab.id,
        findQuery,
        caseSensitive,
        wholeWord,
        findRegex,
        regexValid,
        matchCursor,
        docMatches,
    ) {
        if (showFindBar && findQuery.isNotEmpty() && regexValid) {
            activeTab.editorState.setSearchHighlight(
                findQuery,
                caseSensitive,
                wholeWord,
                findRegex,
                docMatches.getOrNull(matchCursor),
            )
        } else {
            activeTab.editorState.clearSearchHighlight()
        }
    }

    DisposableEffect(activeTab.id) {
        val state = activeTab.editorState
        onDispose { state.clearSearchHighlight() }
    }

    fun jumpToMatch(targetIndex: Int) {
        if (docMatches.isEmpty()) return
        val wrapped = ((targetIndex % docMatches.size) + docMatches.size) % docMatches.size
        matchCursor = wrapped
        val match = docMatches[wrapped]
        activeTab.editorState.focusMatch(match.blockIndex, match.rangeInBlock)
        scope.launch { listStates[activeTab.id]?.animateScrollToItem(match.blockIndex) }
    }

    fun saveExportedUri(uri: Uri, successNotice: String, write: (java.io.OutputStream) -> Unit) {
        scope.launch {
            val ok = runCatching {
                withContext(Dispatchers.IO) {
                    context.contentResolver.openOutputStream(uri)?.use(write)
                        ?: error("openOutputStream retornou null")
                }
            }.isSuccess
            notice = if (ok) successNotice else s.exportError
        }
    }

    val exportHtmlLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/html"),
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        val tab = tabManager.activeTab
        val html = exportToHtml(tab.editorState.text(), tab.title, isDark)
        saveExportedUri(uri, s.htmlExported) { it.write(html.toByteArray(Charsets.UTF_8)) }
    }

    val exportPdfLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/pdf"),
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        val tab = tabManager.activeTab
        val pdf = exportToPdf(tab.editorState.text(), tab.title)
        saveExportedUri(uri, s.pdfExported) { it.write(pdf) }
    }

    val importDocLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val name = withContext(Dispatchers.IO) {
                displayName(context.contentResolver, uri) ?: uri.lastPathSegment ?: "documento.html"
            }
            val imported = withContext(Dispatchers.IO) {
                val bytes = runCatching {
                    context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                }.getOrNull() ?: return@withContext null
                importDocumentBytes(name, bytes)
            }
            if (imported == null) {
                notice = s.importError
            } else if (flushTab(tabManager.activeTab)) {
                val mdTitle = suggestedMarkdownTitle(name)
                val created = tabManager.openOrFocus(
                    key = null,
                    title = mdTitle,
                    text = imported,
                    replaceInitialBlank = true,
                )
                created.editorState.applyPreferences(prefsState)
                notice = "${s.importedNotice}: $mdTitle"
            }
        }
    }

    // Carrega [uri] numa aba (ou foca se já aberta). Grava antes o autosave
    // pendente da aba atual: o debounce de 1s seria cancelado pela troca.
    fun openUri(uri: Uri) {
        val key = uri.toString()
        val existing = tabManager.tabs.firstOrNull { tabKey(it) == key }
        if (existing != null) {
            val current = tabManager.activeTab
            if (existing.id != current.id) {
                flushTab(current)
            }
            tabManager.select(existing.id)
            notice = null
            return
        }
        scope.launch {
            val loaded = withContext(Dispatchers.IO) {
                val content = runCatching {
                    context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                }.getOrNull() ?: return@withContext null
                val name = displayName(context.contentResolver, uri) ?: s.untitled
                name to content
            }
            if (loaded == null) {
                notice = s.importError
                return@launch
            }
            val (name, text) = loaded
            // Não troca de arquivo se o anterior não gravou — senão o erro some da vista.
            if (!flushTab(tabManager.activeTab)) return@launch
            val newSession = FileSession(context.contentResolver, uri, lastSaved = text)
            val created = tabManager.openOrFocus(key, name, text, newSession, replaceInitialBlank = true)
            created.editorState.applyPreferences(prefsState)
            notice = null
        }
    }

    LaunchedEffect(incomingEvent) {
        val event = incomingEvent ?: return@LaunchedEffect
        try {
            val action = withContext(Dispatchers.IO) {
                extractIncomingAction(event.payload, context.contentResolver)
            } ?: return@LaunchedEffect
            openedExternally = true
            when (action) {
                is IncomingAction.OpenMarkdownUri -> {
                    runCatching {
                        context.contentResolver.takePersistableUriPermission(
                            action.uri,
                            Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                        )
                    }
                    openUri(action.uri)
                }
                is IncomingAction.ImportUri -> {
                    val imported = withContext(Dispatchers.IO) {
                        val bytes = runCatching {
                            context.contentResolver.openInputStream(action.uri)?.use { it.readBytes() }
                        }.getOrNull() ?: return@withContext null
                        importDocumentBytes(action.suggestedName, bytes)
                    }
                    if (imported == null) {
                        notice = s.importError
                    } else if (flushTab(tabManager.activeTab)) {
                        val mdTitle = suggestedMarkdownTitle(action.suggestedName)
                        val created = tabManager.openOrFocus(
                            key = null,
                            title = mdTitle,
                            text = imported,
                            replaceInitialBlank = true,
                        )
                        created.editorState.applyPreferences(prefsState)
                        notice = "${s.importedNotice}: $mdTitle"
                    }
                }
                is IncomingAction.SharedText -> {
                    if (flushTab(tabManager.activeTab)) {
                        val created = tabManager.openOrFocus(
                            key = null,
                            title = action.title,
                            text = action.text,
                            replaceInitialBlank = true,
                        )
                        created.editorState.applyPreferences(prefsState)
                        notice = null
                    }
                }
            }
        } finally {
            onConsumeIntent(event.seq)
        }
    }

    fun smartPaste() {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        val clip = clipboard?.primaryClip
        if (clip == null || clip.itemCount == 0) {
            notice = s.clipboardEmpty
            return
        }
        val items = (0 until clip.itemCount).map { clip.getItemAt(it) }
        val html = items.firstNotNullOfOrNull { it.htmlText?.takeIf(String::isNotEmpty) }
        val imageUri = items.mapNotNull { it.uri }.firstOrNull { uri ->
            val uriMime = runCatching { context.contentResolver.getType(uri) }.getOrNull()
            if (uriMime != null) {
                uriMime.startsWith("image/", ignoreCase = true)
            } else {
                clip.description?.hasMimeType("image/*") == true
            }
        }
        val tab = tabManager.activeTab
        val pasted = when {
            html != null -> {
                if (tab.editorState.pasteHtmlAtCursor(html)) {
                    true
                } else {
                    val fallbackText = items
                        .mapNotNull { it.coerceToText(context)?.toString()?.takeIf(String::isNotEmpty) }
                        .joinToString("\n")
                    fallbackText.isNotEmpty() && tab.editorState.pasteMarkdownAtCursor(fallbackText)
                }
            }
            imageUri != null -> tab.editorState.insertImageMarkdown("imagem", imageUri.toString())
            else -> {
                val text = items
                    .mapNotNull { it.coerceToText(context)?.toString()?.takeIf(String::isNotEmpty) }
                    .joinToString("\n")
                if (text.isEmpty()) {
                    notice = s.clipboardEmpty
                    return
                }
                tab.editorState.pasteMarkdownAtCursor(text)
            }
        }
        if (pasted) {
            notice = null
        } else if (tab.editorState.focusedId == null) {
            notice = s.tapBlockToPaste
        }
    }

    val open = rememberLauncherForActivityResult(OpenMarkdown()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        runCatching {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
            )
        }
        openUri(uri)
    }

    var root by remember { mutableStateOf(prefs.getString(PREF_ROOT, null)?.let(Uri::parse)) }
    var nodes by remember { mutableStateOf(emptyList<DocNode>()) }
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    var drawerTab by remember { mutableStateOf(DrawerTab.FILES) }
    var fileDialog by remember { mutableStateOf<FileDialogState?>(null) }

    val pickRoot = rememberLauncherForActivityResult(PickRoot()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        runCatching {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
            )
        }
        prefs.edit().putString(PREF_ROOT, uri.toString()).apply()
        nodes = emptyList()
        root = uri
    }

    val index = remember { SearchIndex(context.applicationContext) }
    // Sobe a cada reindexação concluída: refaz a busca, senão o resultado
    // mostrado seria o de antes de o índice ver os arquivos novos/alterados.
    var indexVersion by remember { mutableStateOf(0) }
    var query by remember { mutableStateOf("") }
    var hits by remember { mutableStateOf(emptyList<SearchHit>()) }

    suspend fun refreshTree(r: Uri) {
        val listed = runCatching {
            withContext(Dispatchers.IO) { listMarkdownTree(context.contentResolver, r, includeEmptyDirs = true) }
        }.getOrDefault(emptyList())
        nodes = listed
        runCatching { withContext(Dispatchers.IO) { index.reindex(context.contentResolver, r, listed) } }
        indexVersion++
    }

    val saveAsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/markdown"),
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        val targetTab = tabManager.activeTab
        val text = targetTab.editorState.text()
        val oldDraftId = targetTab.draftId
        runCatching {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
            )
        }
        scope.launch {
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    val sentinel = if (text.isEmpty()) "\u0000" else ""
                    val session = FileSession(context.contentResolver, uri, lastSaved = sentinel)
                    session.saveIfChanged(text)
                    val name = displayName(context.contentResolver, uri) ?: targetTab.title
                    if (oldDraftId != null) {
                        runCatching { draftRepo.deleteDraft(oldDraftId) }
                    }
                    session to name
                }
            }
            result.onFailure {
                notice = s.saveError
            }.onSuccess { (session, name) ->
                tabManager.bindFile(targetTab.id, uri.toString(), name, session)
                persistSessionTabs()
                root?.let { refreshTree(it) }
                notice = "${s.savedNotice}: $name"
            }
        }
    }

    // Relista ao trocar a raiz e a cada abertura do painel (arquivos mudam fora
    // do app) e sincroniza o índice — que só relê arquivo com data alterada,
    // então isso também pega o que o próprio autosave gravou.
    LaunchedEffect(root, drawerState.isOpen) {
        val r = root ?: return@LaunchedEffect
        if (!drawerState.isOpen && nodes.isNotEmpty()) return@LaunchedEffect
        refreshTree(r)
    }

    LaunchedEffect(query, indexVersion) {
        delay(200)
        hits = runCatching { withContext(Dispatchers.IO) { index.search(query) } }.getOrDefault(emptyList())
    }

    // Links do texto renderizado. `[[nota]]`, `#tag` e `[^nota]` chegam como URIs internas
    // (WIKI_URI/TAG_URI/FOOTNOTE_URI); interceptar no LocalUriHandler evita passar callback
    // por BlockRenderer/ListBlock/etc. Qualquer outro link vai pro handler padrão.
    val defaultUriHandler = LocalUriHandler.current
    val onLink: (String) -> Unit = { uri ->
        when {
            uri.startsWith(WIKI_URI) -> {
                val target = uri.removePrefix(WIKI_URI)
                val r = root
                val node = resolveWikilink(target, nodes)
                when {
                    target.isEmpty() -> Unit // [[#seção]]: a própria nota, nada a abrir
                    r == null || node == null -> notice = s.noteMissing(target)
                    else -> openUri(DocumentsContract.buildDocumentUriUsingTree(r, node.docId))
                }
            }
            uri.startsWith(TAG_URI) -> {
                query = "#" + uri.removePrefix(TAG_URI)
                drawerTab = DrawerTab.FILES
                keyboardController?.hide()
                scope.launch { drawerState.open() }
            }
            uri.startsWith(FOOTNOTE_URI) -> {
                val id = uri.removePrefix(FOOTNOTE_URI)
                val tab = tabManager.activeTab
                val def = docRefsOf(tab.editorState.doc).resolveFootnote(id)
                if (def == null) {
                    notice = s.footnoteNotFound
                } else {
                    tab.editorState.focusBlockIndex(def.blockIndex)
                    if (def.blockIndex in tab.editorState.doc.blocks.indices) {
                        scope.launch { listStates[tab.id]?.animateScrollToItem(def.blockIndex) }
                    }
                    notice = null
                }
            }
            else -> runCatching { defaultUriHandler.openUri(uri) }
        }
    }
    val onLinkRef = rememberUpdatedState(onLink)
    val uriHandler = remember { object : UriHandler { override fun openUri(uri: String) = onLinkRef.value(uri) } }

    ModalNavigationDrawer(
        drawerState = drawerState,
        // Arrastar na borda brigaria com seleção de texto; o painel abre pelo botão.
        gesturesEnabled = drawerState.isOpen,
        drawerContent = {
            ModalDrawerSheet(
                modifier = Modifier.imePadding(),
                drawerContainerColor = MdTokens.sidebarBackground,
                drawerContentColor = MdTokens.text,
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    DrawerTabButton(
                        label = s.files,
                        selected = drawerTab == DrawerTab.FILES,
                        onClick = { drawerTab = DrawerTab.FILES },
                        modifier = Modifier.weight(1f),
                    )
                    DrawerTabButton(
                        label = s.outline,
                        selected = drawerTab == DrawerTab.OUTLINE,
                        onClick = { drawerTab = DrawerTab.OUTLINE },
                        modifier = Modifier.weight(1f),
                    )
                }
                HorizontalDivider(color = MdTokens.ruleColor)
                if (drawerTab == DrawerTab.FILES) {
                    TextButton(onClick = { pickRoot.launch(null) }) {
                        Text(if (root == null) s.chooseFolder else s.changeFolder)
                    }
                    if (root != null) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            TextButton(
                                onClick = {
                                    val r = root ?: return@TextButton
                                    val rootDocId = DocumentsContract.getTreeDocumentId(r)
                                    fileDialog = FileDialogState.CreateNote(rootDocId, parentLabel = null)
                                },
                                contentPadding = PaddingValues(horizontal = 8.dp),
                            ) { Text(s.addNote) }
                            TextButton(
                                onClick = {
                                    val r = root ?: return@TextButton
                                    val rootDocId = DocumentsContract.getTreeDocumentId(r)
                                    fileDialog = FileDialogState.CreateFolder(rootDocId)
                                },
                                contentPadding = PaddingValues(horizontal = 8.dp),
                            ) { Text(s.addFolder) }
                        }
                        OutlinedTextField(
                            value = query,
                            onValueChange = { query = it },
                            placeholder = { Text(s.find) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
                        )
                    }
                    HorizontalDivider(color = MdTokens.ruleColor)
                    if (query.isNotBlank()) {
                        if (hits.isEmpty()) {
                            Box(Modifier.weight(1f).fillMaxWidth()) {
                                Text(
                                    s.nothingFound,
                                    Modifier.padding(16.dp),
                                    color = MdTokens.muted,
                                )
                            }
                        } else {
                            LazyColumn(Modifier.weight(1f).fillMaxWidth()) {
                                items(hits, key = { it.docId }) { hit ->
                                    Column(
                                        Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                val r = root ?: return@clickable
                                                openUri(DocumentsContract.buildDocumentUriUsingTree(r, hit.docId))
                                                scope.launch { drawerState.close() }
                                            }
                                            .padding(horizontal = 16.dp, vertical = 10.dp),
                                    ) {
                                        Text(hit.name, style = MaterialTheme.typography.titleSmall, color = MdTokens.text)
                                        val (text, marks) = snippetMarks(hit.snippet)
                                        Text(
                                            buildAnnotatedString {
                                                append(text)
                                                for (m in marks) {
                                                    addStyle(SpanStyle(fontWeight = FontWeight.Bold), m.first, m.last + 1)
                                                }
                                            },
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MdTokens.muted,
                                            maxLines = 2,
                                        )
                                    }
                                }
                            }
                        }
                    } else {
                        LazyColumn(Modifier.weight(1f).fillMaxWidth()) {
                            items(nodes, key = { it.docId }) { node ->
                                val nodeUriStr = root?.let { r ->
                                    DocumentsContract.buildDocumentUriUsingTree(r, node.docId).toString()
                                }
                                val isOpenActive = !node.isDir && nodeUriStr != null && tabKey(activeTab) == nodeUriStr
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(IntrinsicSize.Min)
                                        .background(
                                            if (isOpenActive) MdTokens.activeItemBackground else Color.Transparent,
                                        )
                                        .clickable(enabled = !node.isDir) {
                                            val r = root ?: return@clickable
                                            openUri(
                                                DocumentsContract.buildDocumentUriUsingTree(r, node.docId),
                                            )
                                            scope.launch { drawerState.close() }
                                        },
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .width(3.dp)
                                            .fillMaxHeight()
                                            .background(
                                                if (isOpenActive) MdTokens.activeItemBorder else Color.Transparent,
                                            ),
                                    )
                                    Row(
                                        modifier = Modifier
                                            .weight(1f)
                                            .padding(
                                                start = (13 + 16 * node.depth).dp,
                                                top = 4.dp,
                                                end = 8.dp,
                                                bottom = 4.dp,
                                            ),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Text(
                                            text = if (node.isDir) node.name + "/" else node.name,
                                            style = MaterialTheme.typography.bodyLarge,
                                            fontWeight = if (isOpenActive) FontWeight.SemiBold else FontWeight.Normal,
                                            color = if (node.isDir) MdTokens.muted else MdTokens.text,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f).padding(vertical = 6.dp),
                                        )
                                        if (node.isDir) {
                                            DrawerNodeActionButton(label = "+") {
                                                fileDialog = FileDialogState.CreateNote(
                                                    parentDocId = node.docId,
                                                    parentLabel = node.name,
                                                )
                                            }
                                        }
                                        DrawerNodeActionButton(label = "✎") {
                                            fileDialog = FileDialogState.Rename(node)
                                        }
                                        DrawerNodeActionButton(label = "×") {
                                            fileDialog = FileDialogState.Delete(node)
                                        }
                                    }
                                }
                            }
                        }
                    }
                } else {
                    val outline = remember(activeTab.id, activeTab.editorState.doc) {
                        outlineOf(activeTab.editorState.doc)
                    }
                    if (outline.isEmpty()) {
                        Box(Modifier.weight(1f).fillMaxWidth()) {
                            Text(
                                s.noHeadings,
                                Modifier.padding(16.dp),
                                color = MdTokens.muted,
                            )
                        }
                    } else {
                        LazyColumn(Modifier.weight(1f).fillMaxWidth()) {
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
                                        .clickable {
                                            activeTab.editorState.focusBlockIndex(entry.blockIndex)
                                            scope.launch {
                                                listStates[activeTab.id]?.animateScrollToItem(entry.blockIndex)
                                                drawerState.close()
                                            }
                                        }
                                        .padding(
                                            start = (16 + (entry.level - 1) * 14).dp,
                                            top = 10.dp,
                                            end = 16.dp,
                                            bottom = 10.dp,
                                        ),
                                )
                            }
                        }
                    }
                }
                val stats = remember(activeTab.id, activeTab.editorState.doc) {
                    docStatsOf(activeTab.editorState.doc)
                }
                HorizontalDivider(color = MdTokens.ruleColor)
                Text(
                    text = s.wordCountSummary(stats.words, stats.chars, stats.readingTimeMinutes),
                    style = MaterialTheme.typography.bodySmall,
                    color = MdTokens.muted,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showWordCountDialog = true }
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                )
            }
        },
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .safeDrawingPadding(),
        ) {
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                val maxControlsWidth = (maxWidth - 200.dp).coerceAtLeast(130.dp)
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(
                        onClick = {
                            keyboardController?.hide()
                            scope.launch { drawerState.open() }
                        },
                        contentPadding = PaddingValues(horizontal = 8.dp),
                    ) { Text(s.folder) }
                    Row(
                        Modifier.weight(1f).padding(start = 4.dp, end = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = activeTab.title,
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onBackground,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (activeTab.saveFailed) {
                            Text(
                                s.saveError,
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(start = 6.dp),
                            )
                        }
                        notice?.let {
                            Text(
                                it,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(start = 6.dp).widthIn(max = 120.dp),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                    Row(
                        Modifier
                            .widthIn(max = maxControlsWidth)
                            .horizontalScroll(rememberScrollState()),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        ModeToggleChip(
                            label = s.source,
                            active = activeTab.editorState.isSourceMode,
                            onClick = { activeTab.editorState.toggleSourceMode() },
                        )
                        ModeToggleChip(
                            label = s.focus,
                            active = activeTab.editorState.isFocusMode,
                            onClick = { activeTab.editorState.toggleFocusMode() },
                        )
                        ModeToggleChip(
                            label = s.smartPaste,
                            active = false,
                            onClick = { smartPaste() },
                        )
                        ModeToggleChip(
                            label = s.table,
                            active = false,
                            onClick = {
                                val tab = tabManager.activeTab
                                if (tab.editorState.insertTable()) {
                                    notice = null
                                } else if (tab.editorState.focusedId == null) {
                                    notice = s.tapBlockForTable
                                }
                            },
                        )
                        TextButton(
                            onClick = { showFindBar = !showFindBar },
                            contentPadding = PaddingValues(horizontal = 6.dp),
                        ) { Text(s.find) }
                    }
                    Box {
                        TextButton(
                            onClick = { showMoreMenu = true },
                            contentPadding = PaddingValues(horizontal = 6.dp),
                        ) { Text("⋮") }
                        DropdownMenu(
                            expanded = showMoreMenu,
                            onDismissRequest = { showMoreMenu = false },
                        ) {
                            DropdownMenuItem(
                                text = { Text(s.open) },
                                onClick = {
                                    showMoreMenu = false
                                    open.launch(arrayOf("text/markdown", "text/plain", "*/*"))
                                },
                            )
                            DropdownMenuItem(
                                text = { Text(s.saveAs) },
                                onClick = {
                                    showMoreMenu = false
                                    val base = activeTab.title.substringBeforeLast('.').ifBlank { s.untitled }
                                    saveAsLauncher.launch("$base.md")
                                },
                            )
                            DropdownMenuItem(
                                text = { Text(s.importAction) },
                                onClick = {
                                    showMoreMenu = false
                                    importDocLauncher.launch(
                                        arrayOf(
                                            "text/html",
                                            "application/xhtml+xml",
                                            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                                            "application/epub+zip",
                                            "*/*",
                                        ),
                                    )
                                },
                            )
                            DropdownMenuItem(
                                text = { Text(s.html) },
                                onClick = {
                                    showMoreMenu = false
                                    val base = activeTab.title.substringBeforeLast('.').ifBlank { "documento" }
                                    exportHtmlLauncher.launch("$base.html")
                                },
                            )
                            DropdownMenuItem(
                                text = { Text(s.pdf) },
                                onClick = {
                                    showMoreMenu = false
                                    val base = activeTab.title.substringBeforeLast('.').ifBlank { "documento" }
                                    exportPdfLauncher.launch("$base.pdf")
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("${s.theme}: ${prefsState.themePreset.displayName}") },
                                onClick = {
                                    showMoreMenu = false
                                    val entries = MdThemePreset.entries
                                    val next = entries[(prefsState.themePreset.ordinal + 1) % entries.size]
                                    onUpdatePrefs(prefsState.copy(themePreset = next))
                                },
                            )
                            DropdownMenuItem(
                                text = { Text(s.preferences) },
                                onClick = {
                                    showMoreMenu = false
                                    showPrefsDialog = true
                                },
                            )
                            DropdownMenuItem(
                                text = { Text(s.recoverDrafts) },
                                onClick = {
                                    showMoreMenu = false
                                    scope.launch {
                                        val drafts = withContext(Dispatchers.IO) { draftRepo.loadDrafts() }
                                        var recoveredCount = 0
                                        for (d in drafts.reversed()) {
                                            if (tabManager.tabs.none { it.draftId == d.id }) {
                                                val created = tabManager.openOrFocus(
                                                    key = null,
                                                    title = d.title.ifBlank { s.untitled },
                                                    text = d.content,
                                                    draftId = d.id,
                                                    replaceInitialBlank = true,
                                                )
                                                created.editorState.applyPreferences(prefsState)
                                                recoveredCount++
                                            }
                                        }
                                        if (recoveredCount == 0) {
                                            notice = s.nothingFound
                                        }
                                    }
                                },
                            )
                            DropdownMenuItem(
                                text = { Text(s.about) },
                                onClick = {
                                    showMoreMenu = false
                                    showAboutDialog = true
                                },
                            )
                        }
                    }
                }
            }
            if (showFindBar) {
                HorizontalDivider()
                AndroidNoteFindBar(
                    findQuery = findQuery,
                    replaceQuery = replaceQuery,
                    caseSensitive = caseSensitive,
                    wholeWord = wholeWord,
                    regex = findRegex,
                    regexValid = regexValid,
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
                            notice = s.replacedCount(1)
                        }
                    },
                    onReplaceAll = {
                        if (regexValid) {
                            val replaced = activeTab.editorState.replaceAll(
                                query = findQuery,
                                replacement = replaceQuery,
                                caseSensitive = caseSensitive,
                                wholeWord = wholeWord,
                                regex = findRegex,
                            )
                            notice = s.replacedCount(replaced)
                        }
                    },
                    onClose = { showFindBar = false },
                )
            }
            HorizontalDivider()
            TabBar(
                tabs = tabManager.tabs,
                activeTabId = activeTab.id,
                listState = tabRowState,
                onSelect = { tab ->
                    val current = tabManager.activeTab
                    if (tab.id != current.id) {
                        flushTab(current)
                        tabManager.select(tab.id)
                        notice = null
                    }
                },
                onClose = { tab ->
                    val wasActive = tab.id == tabManager.activeTab.id
                    val draftToDelete = tab.draftId
                    val closed = tabManager.close(tab.id, emptyTitle = s.untitled) { t ->
                        if (t.session != null) flushTab(t) else true
                    }
                    if (closed) {
                        listStates.remove(tab.id)
                        if (draftToDelete != null) {
                            scope.launch(Dispatchers.IO) {
                                draftRepo.deleteDraft(draftToDelete)
                                draftRepo.pruneOldDrafts(20)
                            }
                        }
                        if (wasActive) notice = null
                    }
                },
                onNewTab = {
                    flushTab(tabManager.activeTab)
                    val created = tabManager.newEmptyTab(title = s.untitled)
                    created.editorState.applyPreferences(prefsState)
                    notice = null
                },
            )
            HorizontalDivider()
            CompositionLocalProvider(LocalUriHandler provides uriHandler) {
                key(activeTab.id) {
                    val listState = remember(activeTab.id) {
                        listStates.getOrPut(activeTab.id) { LazyListState() }
                    }
                    EditorScreen(
                        activeTab.editorState,
                        Modifier.weight(1f),
                        listState = listState,
                        autoFocusIfEmpty = sessionRestored,
                    )
                }
            }
        }
    }

    fileDialog?.let { dialog ->
        FileActionDialog(
            dialog = dialog,
            onDismiss = { fileDialog = null },
            onConfirmText = { rawName ->
                fileDialog = null
                val r = root ?: return@FileActionDialog
                scope.launch {
                    when (dialog) {
                        is FileDialogState.CreateNote -> {
                            val created = withContext(Dispatchers.IO) {
                                createMarkdownFile(context.contentResolver, r, dialog.parentDocId, rawName)
                            }
                            if (created == null) {
                                notice = s.saveError
                            } else {
                                refreshTree(r)
                                openUri(DocumentsContract.buildDocumentUriUsingTree(r, created.docId))
                                drawerState.close()
                            }
                        }
                        is FileDialogState.CreateFolder -> {
                            val created = withContext(Dispatchers.IO) {
                                createSubFolder(context.contentResolver, r, dialog.parentDocId, rawName)
                            }
                            if (created == null) {
                                notice = s.saveError
                            } else {
                                query = ""
                                refreshTree(r)
                            }
                        }
                        is FileDialogState.Rename -> {
                            val cleanTarget = sanitizeFileName(rawName, ensureMarkdownExtension = !dialog.node.isDir)
                            if (cleanTarget == dialog.node.name) return@launch
                            if (dialog.node.isDir) {
                                val oldDescendants = descendantFilesWithRelativePath(nodes, dialog.node)
                                val oldTabByRel = oldDescendants.mapNotNull { (child, rel) ->
                                    val oldKey = DocumentsContract.buildDocumentUriUsingTree(r, child.docId).toString()
                                    val tab = tabManager.tabs.firstOrNull { tabKey(it) == oldKey }
                                        ?: return@mapNotNull null
                                    rel to tab
                                }
                                for ((_, tab) in oldTabByRel) {
                                    if (!flushTabAsync(tab)) {
                                        notice = s.saveError
                                        return@launch
                                    }
                                }
                                val renamed = withContext(Dispatchers.IO) {
                                    renameDocument(context.contentResolver, r, dialog.node.docId, rawName, isDir = true)
                                }
                                if (renamed == null) {
                                    notice = s.renameFailed(dialog.node.name)
                                } else {
                                    refreshTree(r)
                                    val newByRel = descendantFilesWithRelativePath(nodes, renamed)
                                        .associate { (child, rel) -> rel to child }
                                    for ((rel, tab) in oldTabByRel) {
                                        val newNode = newByRel[rel] ?: continue
                                        val newUri = DocumentsContract.buildDocumentUriUsingTree(r, newNode.docId)
                                        tabManager.replaceSession(
                                            id = tab.id,
                                            session = FileSession(
                                                context.contentResolver,
                                                newUri,
                                                lastSaved = tab.editorState.text(),
                                            ),
                                            title = newNode.name,
                                        )
                                    }
                                }
                            } else {
                                val oldKey = DocumentsContract.buildDocumentUriUsingTree(r, dialog.node.docId).toString()
                                val matchingTab = tabManager.tabs.firstOrNull { tabKey(it) == oldKey }
                                if (matchingTab != null && !flushTabAsync(matchingTab)) {
                                    notice = s.saveError
                                    return@launch
                                }
                                val renamed = withContext(Dispatchers.IO) {
                                    renameDocument(context.contentResolver, r, dialog.node.docId, rawName, isDir = false)
                                }
                                if (renamed == null) {
                                    notice = s.renameFailed(dialog.node.name)
                                } else {
                                    if (matchingTab != null) {
                                        val newUri = DocumentsContract.buildDocumentUriUsingTree(r, renamed.docId)
                                        tabManager.replaceSession(
                                            id = matchingTab.id,
                                            session = FileSession(
                                                context.contentResolver,
                                                newUri,
                                                lastSaved = matchingTab.editorState.text(),
                                            ),
                                            title = renamed.name,
                                        )
                                    }
                                    refreshTree(r)
                                }
                            }
                        }
                        is FileDialogState.Delete -> {
                            val deletedKeys = if (dialog.node.isDir) {
                                val childKeys = descendantFilesWithRelativePath(nodes, dialog.node)
                                    .map { (child, _) ->
                                        DocumentsContract.buildDocumentUriUsingTree(r, child.docId).toString()
                                    }
                                    .toSet()
                                childKeys + DocumentsContract.buildDocumentUriUsingTree(r, dialog.node.docId).toString()
                            } else {
                                setOf(DocumentsContract.buildDocumentUriUsingTree(r, dialog.node.docId).toString())
                            }
                            val ok = withContext(Dispatchers.IO) {
                                deleteDocument(context.contentResolver, r, dialog.node.docId)
                            }
                            if (!ok) {
                                notice = s.saveError
                            } else {
                                val wasActiveDeleted = tabKey(tabManager.activeTab) in deletedKeys
                                for (tab in tabManager.tabs.toList()) {
                                    if (tabKey(tab) in deletedKeys) {
                                        tabManager.close(tab.id, emptyTitle = s.untitled) { true }
                                        listStates.remove(tab.id)
                                    }
                                }
                                if (wasActiveDeleted) notice = null
                                refreshTree(r)
                            }
                        }
                    }
                }
            },
        )
    }

    if (showWordCountDialog) {
        val stats = remember(activeTab.id, activeTab.editorState.doc) {
            docStatsOf(activeTab.editorState.doc)
        }
        val selectedStats = remember(
            activeTab.id,
            activeTab.editorState.focusedId,
            activeTab.editorState.editValue.selection,
            activeTab.editorState.editValue.text,
        ) {
            activeTab.editorState.selectedText().takeIf { it.isNotEmpty() }?.let(::selectionStatsOf)
        }
        AlertDialog(
            onDismissRequest = { showWordCountDialog = false },
            title = { Text(s.wordCount) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    WordCountRow(s.minutes, stats.readingTimeMinutes.toString())
                    WordCountRow(s.lines, stats.lines.toString())
                    WordCountRow(s.words, stats.words.toString())
                    WordCountRow(s.characters, stats.chars.toString())
                    if (selectedStats != null) {
                        HorizontalDivider()
                        Text(
                            text = s.inSelection,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        WordCountRow(s.words, selectedStats.words.toString())
                        WordCountRow(s.characters, selectedStats.chars.toString())
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showWordCountDialog = false }) { Text(s.close) }
            },
        )
    }

    if (showPrefsDialog) {
        PreferencesDialog(
            prefsState = prefsState,
            onUpdatePrefs = onUpdatePrefs,
            onDismiss = { showPrefsDialog = false },
        )
    }

    if (showAboutDialog) {
        AboutDialog(onDismiss = { showAboutDialog = false })
    }
}

@Composable
private fun AboutDialog(onDismiss: () -> Unit) {
    val s = LocalJotStrings.current
    val context = LocalContext.current
    val defaultUriHandler = LocalUriHandler.current
    val versionName = remember(context) {
        runCatching {
            @Suppress("DEPRECATION")
            context.packageManager.getPackageInfo(context.packageName, 0).versionName.orEmpty()
        }.getOrDefault("")
    }
    var pixCopied by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text(if (versionName.isNotBlank()) "JotMD v$versionName" else "JotMD")
                Text(
                    text = JotAboutInfo.MADE_IN_FORTALEZA,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(s.aboutDescription, style = MaterialTheme.typography.bodyMedium)
                HorizontalDivider()
                Text(
                    text = s.supportProject,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(s.supportPrompt, style = MaterialTheme.typography.bodySmall)
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    for ((label, url) in listOf(
                        "GitHub Sponsors" to JotAboutInfo.GITHUB_SPONSORS_URL,
                        "Ko-fi" to JotAboutInfo.KOFI_URL,
                        "Buy Me a Coffee" to JotAboutInfo.BUY_ME_A_COFFEE_URL,
                        s.sourceCode to JotAboutInfo.REPO_URL,
                    )) {
                        TextButton(
                            onClick = { runCatching { defaultUriHandler.openUri(url) } },
                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp),
                        ) {
                            Text(label)
                        }
                    }
                }
                HorizontalDivider()
                Text(
                    text = s.pixLabel,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = JotAboutInfo.PIX_PAYLOAD,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = JotAboutInfo.LICENSE_LABEL,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    TextButton(
                        onClick = {
                            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                            cm?.setPrimaryClip(ClipData.newPlainText("Pix JotMD", JotAboutInfo.PIX_PAYLOAD))
                            pixCopied = true
                        },
                    ) {
                        Text(if (pixCopied) s.pixCopied else s.copyPix)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(s.close) }
        },
    )
}

@Composable
private fun WordCountRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
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

@Composable
private fun PreferencesDialog(
    prefsState: EditorPrefs,
    onUpdatePrefs: (EditorPrefs) -> Unit,
    onDismiss: () -> Unit,
) {
    val s = LocalJotStrings.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(s.preferences) },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = s.appearance,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    for (preset in MdThemePreset.entries) {
                        val palette = paletteForPreset(preset)
                        val selected = preset == prefsState.themePreset
                        val borderColor = if (selected) {
                            MdTokens.activeItemBorder
                        } else {
                            MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
                        }
                        Surface(
                            onClick = { onUpdatePrefs(prefsState.copy(themePreset = preset)) },
                            shape = RoundedCornerShape(8.dp),
                            color = palette.background,
                            modifier = Modifier.border(
                                width = if (selected) 2.dp else 1.dp,
                                color = borderColor,
                                shape = RoundedCornerShape(8.dp),
                            ),
                        ) {
                            Column(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                ) {
                                    Box(
                                        Modifier
                                            .size(8.dp)
                                            .clip(RoundedCornerShape(4.dp))
                                            .background(palette.text),
                                    )
                                    Box(
                                        Modifier
                                            .width(18.dp)
                                            .height(4.dp)
                                            .clip(RoundedCornerShape(2.dp))
                                            .background(palette.link),
                                    )
                                }
                                Text(
                                    text = preset.displayName,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                    color = palette.text,
                                )
                            }
                        }
                    }
                }
                StepperPrefRow(
                    label = s.fontSize,
                    valueText = "${prefsState.fontSizeSp.toInt()} sp",
                    canDecrease = prefsState.fontSizeSp > 14f,
                    canIncrease = prefsState.fontSizeSp < 22f,
                    onDecrease = {
                        onUpdatePrefs(prefsState.copy(fontSizeSp = (prefsState.fontSizeSp - 1f).coerceIn(14f, 22f)))
                    },
                    onIncrease = {
                        onUpdatePrefs(prefsState.copy(fontSizeSp = (prefsState.fontSizeSp + 1f).coerceIn(14f, 22f)))
                    },
                )
                StepperPrefRow(
                    label = s.columnWidth,
                    valueText = "${prefsState.maxColumnWidthDp} dp",
                    canDecrease = prefsState.maxColumnWidthDp > 680,
                    canIncrease = prefsState.maxColumnWidthDp < 1100,
                    onDecrease = {
                        onUpdatePrefs(
                            prefsState.copy(maxColumnWidthDp = (prefsState.maxColumnWidthDp - 40).coerceIn(680, 1100)),
                        )
                    },
                    onIncrease = {
                        onUpdatePrefs(
                            prefsState.copy(maxColumnWidthDp = (prefsState.maxColumnWidthDp + 40).coerceIn(680, 1100)),
                        )
                    },
                )

                HorizontalDivider()
                Text(
                    text = s.editor,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(s.autoPair, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    Switch(
                        checked = prefsState.autoPairEnabled,
                        onCheckedChange = { onUpdatePrefs(prefsState.copy(autoPairEnabled = it)) },
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(s.indentSize, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        for (size in listOf(2, 4)) {
                            FindOptionChip(
                                label = "$size ${s.spaces}",
                                active = prefsState.indentSize == size,
                                onClick = { onUpdatePrefs(prefsState.copy(indentSize = size)) },
                            )
                        }
                    }
                }

                HorizontalDivider()
                Text(
                    text = s.general,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(s.language, style = MaterialTheme.typography.bodyMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        FindOptionChip(
                            label = s.system,
                            active = prefsState.language == AppLanguage.SYSTEM,
                            onClick = { onUpdatePrefs(prefsState.copy(language = AppLanguage.SYSTEM)) },
                        )
                        FindOptionChip(
                            label = "PT-BR",
                            active = prefsState.language == AppLanguage.PT_BR,
                            onClick = { onUpdatePrefs(prefsState.copy(language = AppLanguage.PT_BR)) },
                        )
                        FindOptionChip(
                            label = "English",
                            active = prefsState.language == AppLanguage.EN,
                            onClick = { onUpdatePrefs(prefsState.copy(language = AppLanguage.EN)) },
                        )
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(s.restoreSession, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    Switch(
                        checked = prefsState.restoreSession,
                        onCheckedChange = { onUpdatePrefs(prefsState.copy(restoreSession = it)) },
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(s.close) }
        },
    )
}

@Composable
private fun StepperPrefRow(
    label: String,
    valueText: String,
    canDecrease: Boolean,
    canIncrease: Boolean,
    onDecrease: () -> Unit,
    onIncrease: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            CompactFindButton(label = "-", enabled = canDecrease, onClick = onDecrease)
            Text(valueText, style = MaterialTheme.typography.bodySmall)
            CompactFindButton(label = "+", enabled = canIncrease, onClick = onIncrease)
        }
    }
}

@Composable
private fun DrawerNodeActionButton(
    label: String,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(6.dp),
        color = Color.Transparent,
    ) {
        Box(
            modifier = Modifier.defaultMinSize(minWidth = 30.dp, minHeight = 32.dp).padding(horizontal = 4.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun FileActionDialog(
    dialog: FileDialogState,
    onDismiss: () -> Unit,
    onConfirmText: (String) -> Unit,
) {
    val s = LocalJotStrings.current
    var input by remember(dialog) {
        mutableStateOf(if (dialog is FileDialogState.Rename) dialog.node.name else "")
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                when (dialog) {
                    is FileDialogState.CreateNote ->
                        if (dialog.parentLabel == null) s.newNote else s.newNoteInTitle(dialog.parentLabel)
                    is FileDialogState.CreateFolder -> s.newFolder
                    is FileDialogState.Rename ->
                        if (dialog.node.isDir) s.renameFolder else s.renameNote
                    is FileDialogState.Delete -> s.deleteTitle(dialog.node.name)
                },
            )
        },
        text = {
            when (dialog) {
                is FileDialogState.CreateNote -> OutlinedTextField(
                    value = input,
                    onValueChange = { input = it },
                    placeholder = { Text("${s.untitled}.md") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                is FileDialogState.CreateFolder -> OutlinedTextField(
                    value = input,
                    onValueChange = { input = it },
                    placeholder = { Text(s.untitled) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                is FileDialogState.Rename -> OutlinedTextField(
                    value = input,
                    onValueChange = { input = it },
                    placeholder = { Text(s.rename) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                is FileDialogState.Delete -> Text(
                    if (dialog.node.isDir) {
                        s.deleteFolderConfirm
                    } else {
                        s.deleteFileConfirm
                    },
                )
            }
        },
        confirmButton = {
            when (dialog) {
                is FileDialogState.CreateNote,
                is FileDialogState.CreateFolder,
                -> TextButton(onClick = { onConfirmText(input) }) { Text(s.create) }
                is FileDialogState.Rename -> TextButton(
                    onClick = { onConfirmText(input) },
                    enabled = input.isNotBlank(),
                ) { Text(s.rename) }
                is FileDialogState.Delete -> TextButton(
                    onClick = { onConfirmText("") },
                ) { Text(s.delete, color = MaterialTheme.colorScheme.error) }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(s.cancel) }
        },
    )
}

@Composable
private fun DrawerTabButton(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        modifier = modifier,
        color = Color.Transparent,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier = Modifier.padding(vertical = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (selected) MdTokens.text else MdTokens.muted,
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
}

/** Chip de modo na barra superior sem roubar foco do `BasicTextField` ativo (Armadilha 5). */
@Composable
private fun ModeToggleChip(
    label: String,
    active: Boolean,
    onClick: () -> Unit,
) {
    val currentOnClick = rememberUpdatedState(onClick)
    val shape = RoundedCornerShape(6.dp)
    val bg = if (active) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent
    val fg = if (active) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.primary
    Box(
        modifier = Modifier
            .clip(shape)
            .background(bg)
            .then(
                if (active) Modifier.border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f), shape)
                else Modifier,
            )
            .defaultMinSize(minHeight = 36.dp)
            .pointerInput(Unit) { detectTapGestures(onTap = { currentOnClick.value() }) }
            .padding(horizontal = 8.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
            color = fg,
            maxLines = 1,
        )
    }
}

@Composable
private fun AndroidNoteFindBar(
    findQuery: String,
    replaceQuery: String,
    caseSensitive: Boolean,
    wholeWord: Boolean,
    regex: Boolean,
    regexValid: Boolean,
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
    Surface(color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                OutlinedTextField(
                    value = findQuery,
                    onValueChange = onFindChange,
                    placeholder = { Text(s.findInNote) },
                    singleLine = true,
                    isError = !regexValid,
                    modifier = Modifier.weight(1f),
                )
                FindOptionChip(label = "Aa", active = caseSensitive, onClick = onToggleCaseSensitive)
                FindOptionChip(label = "[ab]", active = wholeWord, onClick = onToggleWholeWord)
                FindOptionChip(label = ".*", active = regex, onClick = onToggleRegex)
                Text(
                    counter,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 4.dp),
                )
                CompactFindButton(label = "↑", enabled = matchCount > 0, onClick = onPrev)
                CompactFindButton(label = "↓", enabled = matchCount > 0, onClick = onNext)
                CompactFindButton(label = "×", enabled = true, onClick = onClose)
            }
            if (!regexValid) {
                Text(
                    text = s.invalidRegex,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(start = 4.dp, top = 2.dp),
                )
            }
            Spacer(Modifier.height(4.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = replaceQuery,
                    onValueChange = onReplaceChange,
                    placeholder = { Text(s.replaceWith) },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                TextButton(
                    onClick = onReplaceCurrent,
                    enabled = matchCount > 0,
                    contentPadding = PaddingValues(horizontal = 8.dp),
                ) { Text(s.replace) }
                TextButton(
                    onClick = onReplaceAll,
                    enabled = findQuery.isNotEmpty() && regexValid,
                    contentPadding = PaddingValues(horizontal = 8.dp),
                ) { Text(s.replaceAll) }
            }
        }
    }
}

@Composable
private fun FindOptionChip(
    label: String,
    active: Boolean,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(6.dp)
    Surface(
        onClick = onClick,
        shape = shape,
        color = if (active) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent,
        modifier = if (active) {
            Modifier.border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f), shape)
        } else {
            Modifier
        },
    ) {
        Box(
            modifier = Modifier.defaultMinSize(minWidth = 32.dp, minHeight = 36.dp).padding(horizontal = 6.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
                color = if (active) {
                    MaterialTheme.colorScheme.onSecondaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        }
    }
}

@Composable
private fun CompactFindButton(
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(6.dp),
        color = Color.Transparent,
    ) {
        Box(
            modifier = Modifier.defaultMinSize(minWidth = 32.dp, minHeight = 36.dp).padding(horizontal = 4.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = if (enabled) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
                },
            )
        }
    }
}

/**
 * Barra horizontal de abas. Usa `pointerInput(Unit)` + `detectTapGestures` +
 * `rememberUpdatedState` (Armadilha 5 do AGENTS.md) para não roubar foco do
 * `BasicTextField` nem perder toques em recomposições.
 */
@Composable
private fun TabBar(
    tabs: List<EditorTab>,
    activeTabId: Long,
    listState: LazyListState,
    onSelect: (EditorTab) -> Unit,
    onClose: (EditorTab) -> Unit,
    onNewTab: () -> Unit,
) {
    val currentNewTab = rememberUpdatedState(onNewTab)
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LazyRow(
            modifier = Modifier.weight(1f),
            state = listState,
            verticalAlignment = Alignment.CenterVertically,
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            items(tabs, key = { it.id }) { tab ->
                TabChip(
                    tab = tab,
                    active = tab.id == activeTabId,
                    onSelect = { onSelect(tab) },
                    onClose = { onClose(tab) },
                )
            }
        }
        Box(
            modifier = Modifier
                .defaultMinSize(minWidth = 40.dp, minHeight = 36.dp)
                .pointerInput(Unit) { detectTapGestures(onTap = { currentNewTab.value() }) }
                .padding(horizontal = 8.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "+",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun TabChip(
    tab: EditorTab,
    active: Boolean,
    onSelect: () -> Unit,
    onClose: () -> Unit,
) {
    val currentSelect = rememberUpdatedState(onSelect)
    val currentClose = rememberUpdatedState(onClose)
    val shape = RoundedCornerShape(6.dp)
    val bg = if (active) {
        MaterialTheme.colorScheme.secondaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
    }
    val fg = if (active) {
        MaterialTheme.colorScheme.onSecondaryContainer
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    Row(
        modifier = Modifier
            .clip(shape)
            .background(bg)
            .then(
                if (active) {
                    Modifier.border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f), shape)
                } else {
                    Modifier
                },
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = Modifier
                .defaultMinSize(minHeight = 36.dp)
                .pointerInput(Unit) { detectTapGestures(onTap = { currentSelect.value() }) }
                .padding(start = 10.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            if (tab.saveFailed) {
                Text(
                    text = "!",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                )
            }
            Text(
                text = tab.title,
                color = fg,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = 160.dp),
            )
        }
        Box(
            modifier = Modifier
                .defaultMinSize(minWidth = 32.dp, minHeight = 36.dp)
                .pointerInput(Unit) { detectTapGestures(onTap = { currentClose.value() }) }
                .padding(end = 6.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "×",
                color = fg,
                style = MaterialTheme.typography.labelLarge,
            )
        }
    }
}
