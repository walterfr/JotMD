package io.github.walterfr.jotmd

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
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import io.github.walterfr.jotmd.core.exportToHtml
import io.github.walterfr.jotmd.core.findInDocument
import io.github.walterfr.jotmd.editor.MdTokens
import io.github.walterfr.jotmd.editor.TAG_URI
import io.github.walterfr.jotmd.editor.WIKI_URI
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import io.github.walterfr.jotmd.editor.EditorScreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

private const val AUTOSAVE_DELAY_MS = 1000L
private const val ASSET_REF_KEY = "asset://typora-ref.md"

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

private const val PREF_ROOT = "root_uri"
private const val PREF_DARK = "dark_theme"

/** Seletor de pasta com leitura+escrita persistentes (mesmo motivo do OpenMarkdown). */
private class PickRoot : ActivityResultContracts.OpenDocumentTree() {
    override fun createIntent(context: Context, input: Uri?): Intent =
        super.createIntent(context, input).addFlags(
            Intent.FLAG_GRANT_READ_URI_PERMISSION or
                Intent.FLAG_GRANT_WRITE_URI_PERMISSION or
                Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION,
        )
}

class MainActivity: ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val context = LocalContext.current
            val prefs = remember { context.getSharedPreferences("jotmd", Context.MODE_PRIVATE) }
            var isDark by remember { mutableStateOf(prefs.getBoolean(PREF_DARK, false)) }
            MdTokens.isDark = isDark
            MaterialTheme(colorScheme = if (isDark) darkColorScheme() else lightColorScheme()) {
                EditorHostScreen(
                    prefs = prefs,
                    isDark = isDark,
                    onToggleTheme = {
                        val next = !isDark
                        isDark = next
                        MdTokens.isDark = next
                        prefs.edit().putBoolean(PREF_DARK, next).apply()
                    },
                )
            }
        }
    }
}

/**
 * Shell da tela de edição: abre um arquivo por SAF, toca um bloco para editar
 * o markdown cru dele, toolbar embaixo para negrito/itálico/riscado/destaque/
 * código na seleção. Enter divide bloco, Backspace no início funde, `↑`/`↓`
 * navegam, Ctrl+Z/Ctrl+Shift+Z desfazem — ver BlockEditor.kt.
 *
 * Autosave: 1s depois da última mudança, e de novo ao sair do app (ON_STOP).
 * Só grava arquivo aberto por SAF — o documento de referência embutido nos
 * assets não tem onde gravar. Falha de escrita aparece no cabeçalho.
 */
@Composable
private fun EditorHostScreen(
    prefs: android.content.SharedPreferences,
    isDark: Boolean,
    onToggleTheme: () -> Unit,
) {
    val context = LocalContext.current
    val tabManager = remember { TabManager(initialTitle = "typora-ref.md", initialKey = ASSET_REF_KEY) }
    val activeTab = tabManager.activeTab
    val listStates = remember { mutableMapOf<Long, LazyListState>() }
    val tabRowState = rememberLazyListState()
    // Aviso transitório no cabeçalho (ex.: wikilink sem arquivo). Some ao abrir outro.
    var notice by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        val text = withContext(Dispatchers.IO) {
            context.assets.open("typora-ref.md").bufferedReader().use { it.readText() }
        }
        val initial = tabManager.tabs.firstOrNull { it.key == ASSET_REF_KEY }
        if (initial != null && initial.editorState.text().isEmpty()) {
            initial.editorState.load(text)
        }
    }

    fun flushTab(tab: EditorTab): Boolean {
        val s = tab.session ?: return true
        val failed = runCatching { s.saveIfChanged(tab.editorState.text()) }.isFailure
        tabManager.updateSaveState(tab.id, failed)
        return !failed
    }

    // Reinicia a cada mudança em doc ou troca de aba: só chega ao save quem ficou 1s sem editar.
    LaunchedEffect(activeTab.id, activeTab.editorState.doc, activeTab.session) {
        val s = activeTab.session ?: return@LaunchedEffect
        delay(AUTOSAVE_DELAY_MS)
        val text = activeTab.editorState.text()
        val failed = runCatching { withContext(Dispatchers.IO) { s.saveIfChanged(text) } }.isFailure
        tabManager.updateSaveState(activeTab.id, failed)
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
    val lifecycle = (context as ComponentActivity).lifecycle
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) {
                // ponytail: escrita síncrona na main — arquivo de texto pequeno.
                for (tab in tabManager.tabs.toList()) {
                    flushTab(tab)
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
    var matchCursor by remember { mutableStateOf(0) }
    val docMatches = remember(activeTab.id, activeTab.editorState.doc, findQuery) {
        findInDocument(activeTab.editorState.doc, findQuery)
    }

    LaunchedEffect(docMatches) {
        if (docMatches.isEmpty()) {
            matchCursor = 0
        } else if (matchCursor >= docMatches.size) {
            matchCursor = 0
        }
    }

    fun jumpToMatch(targetIndex: Int) {
        if (docMatches.isEmpty()) return
        val wrapped = ((targetIndex % docMatches.size) + docMatches.size) % docMatches.size
        matchCursor = wrapped
        val match = docMatches[wrapped]
        activeTab.editorState.focusMatch(match.blockIndex, match.rangeInBlock)
        scope.launch { listStates[activeTab.id]?.animateScrollToItem(match.blockIndex) }
    }

    val exportHtmlLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/html"),
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val tab = tabManager.activeTab
            val html = exportToHtml(tab.editorState.text(), tab.title, isDark)
            val ok = runCatching {
                withContext(Dispatchers.IO) {
                    context.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { it.write(html) }
                        ?: error("openOutputStream retornou null")
                }
            }.isSuccess
            notice = if (ok) "HTML exportado" else "Erro ao exportar"
        }
    }

    // Carrega [uri] numa aba (ou foca se já aberta). Grava antes o autosave
    // pendente da aba atual: o debounce de 1s seria cancelado pela troca.
    fun openUri(uri: Uri) {
        val key = uri.toString()
        val existing = tabManager.tabs.firstOrNull { it.key == key }
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
                val content = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                    ?: return@withContext null
                val name = displayName(context.contentResolver, uri) ?: "documento"
                name to content
            } ?: return@launch
            val (name, text) = loaded
            // Não troca de arquivo se o anterior não gravou — senão o erro some da vista.
            if (!flushTab(tabManager.activeTab)) return@launch
            val newSession = FileSession(context.contentResolver, uri, lastSaved = text)
            tabManager.openOrFocus(key, name, text, newSession)
            notice = null
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

    val pickRoot = rememberLauncherForActivityResult(PickRoot()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        runCatching {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
            )
        }
        prefs.edit().putString(PREF_ROOT, uri.toString()).apply()
        root = uri
    }

    val index = remember { SearchIndex(context.applicationContext) }
    // Sobe a cada reindexação concluída: refaz a busca, senão o resultado
    // mostrado seria o de antes de o índice ver os arquivos novos/alterados.
    var indexVersion by remember { mutableStateOf(0) }
    var query by remember { mutableStateOf("") }
    var hits by remember { mutableStateOf(emptyList<SearchHit>()) }

    // Relista ao trocar a raiz e a cada abertura do painel (arquivos mudam fora
    // do app) e sincroniza o índice — que só relê arquivo com data alterada,
    // então isso também pega o que o próprio autosave gravou.
    LaunchedEffect(root, drawerState.isOpen) {
        val r = root ?: return@LaunchedEffect
        if (!drawerState.isOpen && nodes.isNotEmpty()) return@LaunchedEffect
        val listed = runCatching {
            withContext(Dispatchers.IO) { listMarkdownTree(context.contentResolver, r) }
        }.getOrDefault(emptyList())
        nodes = listed
        runCatching { withContext(Dispatchers.IO) { index.reindex(context.contentResolver, r, listed) } }
        indexVersion++
    }

    LaunchedEffect(query, indexVersion) {
        delay(200)
        hits = runCatching { withContext(Dispatchers.IO) { index.search(query) } }.getOrDefault(emptyList())
    }

    // Links do texto renderizado. `[[nota]]` e `#tag` chegam como URIs internas
    // (WIKI_URI/TAG_URI); interceptar no LocalUriHandler evita passar callback
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
                    r == null || node == null -> notice = "Nota não encontrada: $target"
                    else -> openUri(DocumentsContract.buildDocumentUriUsingTree(r, node.docId))
                }
            }
            uri.startsWith(TAG_URI) -> {
                query = "#" + uri.removePrefix(TAG_URI)
                scope.launch { drawerState.open() }
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
            ModalDrawerSheet {
                TextButton(onClick = { pickRoot.launch(null) }) {
                    Text(if (root == null) "Escolher pasta" else "Trocar pasta")
                }
                if (root != null) {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        placeholder = { Text("Buscar") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
                    )
                }
                HorizontalDivider()
                if (query.isNotBlank()) {
                    LazyColumn {
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
                                Text(hit.name, style = MaterialTheme.typography.titleSmall)
                                val (text, marks) = snippetMarks(hit.snippet)
                                Text(
                                    buildAnnotatedString {
                                        append(text)
                                        for (m in marks) {
                                            addStyle(SpanStyle(fontWeight = FontWeight.Bold), m.first, m.last + 1)
                                        }
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 2,
                                )
                            }
                        }
                    }
                    if (hits.isEmpty()) {
                        Text(
                            "Nada encontrado",
                            Modifier.padding(16.dp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                } else LazyColumn {
                    items(nodes, key = { it.docId }) { node ->
                        Text(
                            text = if (node.isDir) node.name + "/" else node.name,
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(enabled = !node.isDir) {
                                    val r = root ?: return@clickable
                                    openUri(
                                        DocumentsContract.buildDocumentUriUsingTree(r, node.docId),
                                    )
                                    scope.launch { drawerState.close() }
                                }
                                .padding(start = (16 + 16 * node.depth).dp, top = 10.dp, end = 16.dp, bottom = 10.dp),
                        )
                    }
                }
            }
        },
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .safeDrawingPadding(),
        ) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(
                    onClick = { scope.launch { drawerState.open() } },
                    contentPadding = PaddingValues(horizontal = 8.dp),
                ) { Text("Pasta") }
                Text(
                    text = activeTab.title,
                    modifier = Modifier.weight(1f).padding(start = 4.dp),
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (activeTab.saveFailed) {
                    Text(
                        "Erro ao salvar",
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(end = 6.dp),
                    )
                }
                notice?.let {
                    Text(
                        it,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(end = 6.dp),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                TextButton(
                    onClick = { showFindBar = !showFindBar },
                    contentPadding = PaddingValues(horizontal = 6.dp),
                ) { Text("Buscar") }
                TextButton(
                    onClick = {
                        val base = activeTab.title.substringBeforeLast('.').ifBlank { "documento" }
                        exportHtmlLauncher.launch("$base.html")
                    },
                    contentPadding = PaddingValues(horizontal = 6.dp),
                ) { Text("HTML") }
                TextButton(
                    onClick = onToggleTheme,
                    contentPadding = PaddingValues(horizontal = 6.dp),
                ) { Text(if (isDark) "Claro" else "Escuro") }
                TextButton(
                    onClick = { open.launch(arrayOf("text/markdown", "text/plain", "*/*")) },
                    contentPadding = PaddingValues(horizontal = 8.dp),
                ) { Text("Abrir") }
            }
            if (showFindBar) {
                HorizontalDivider()
                AndroidNoteFindBar(
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
                        notice = "$replaced substituído(s)"
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
                    if (tabManager.close(tab.id) { flushTab(it) }) {
                        listStates.remove(tab.id)
                        if (wasActive) notice = null
                    }
                },
                onNewTab = {
                    flushTab(tabManager.activeTab)
                    tabManager.newEmptyTab()
                    notice = null
                },
            )
            HorizontalDivider()
            CompositionLocalProvider(LocalUriHandler provides uriHandler) {
                key(activeTab.id) {
                    val listState = remember(activeTab.id) {
                        listStates.getOrPut(activeTab.id) { LazyListState() }
                    }
                    EditorScreen(activeTab.editorState, Modifier.weight(1f), listState = listState)
                }
            }
        }
    }
}

@Composable
private fun AndroidNoteFindBar(
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
    Surface(color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = findQuery,
                    onValueChange = onFindChange,
                    placeholder = { Text("Buscar na nota") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    counter,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 6.dp),
                )
                TextButton(onClick = onPrev, enabled = matchCount > 0, contentPadding = PaddingValues(horizontal = 6.dp)) {
                    Text("↑")
                }
                TextButton(onClick = onNext, enabled = matchCount > 0, contentPadding = PaddingValues(horizontal = 6.dp)) {
                    Text("↓")
                }
                TextButton(onClick = onClose, contentPadding = PaddingValues(horizontal = 6.dp)) {
                    Text("×")
                }
            }
            Spacer(Modifier.height(4.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = replaceQuery,
                    onValueChange = onReplaceChange,
                    placeholder = { Text("Substituir por") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                TextButton(
                    onClick = onReplaceAll,
                    enabled = findQuery.isNotEmpty(),
                    contentPadding = PaddingValues(horizontal = 8.dp),
                ) { Text("Subst. tudo") }
            }
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
