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
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import io.github.walterfr.jotmd.editor.EditorScreen
import io.github.walterfr.jotmd.editor.EditorState
import kotlinx.coroutines.Dispatchers
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

private const val PREF_ROOT = "root_uri"

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
        setContent { MaterialTheme { EditorHostScreen() } }
    }
}

/**
 * Shell da tela de edição: abre um arquivo por SAF, toca um bloco para editar
 * o markdown cru dele, toolbar embaixo para negrito/itálico/riscado/código na
 * seleção. Enter divide bloco, Backspace no início funde, `↑`/`↓` navegam,
 * Ctrl+Z/Ctrl+Shift+Z desfazem — ver BlockEditor.kt.
 *
 * Autosave: 1s depois da última mudança, e de novo ao sair do app (ON_STOP).
 * Só grava arquivo aberto por SAF — o documento de referência embutido nos
 * assets não tem onde gravar. Falha de escrita aparece no cabeçalho.
 */
@Composable
private fun EditorHostScreen() {
    val context = LocalContext.current
    val editorState = remember { EditorState() }
    var title by remember { mutableStateOf("typora-ref.md") }
    var session by remember { mutableStateOf<FileSession?>(null) }
    var saveFailed by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        val text = withContext(Dispatchers.IO) {
            context.assets.open("typora-ref.md").bufferedReader().use { it.readText() }
        }
        editorState.load(text)
    }

    // Reinicia a cada mudança em doc: só chega ao save quem ficou 1s sem editar.
    LaunchedEffect(editorState.doc, session) {
        val s = session ?: return@LaunchedEffect
        delay(AUTOSAVE_DELAY_MS)
        val text = editorState.text()
        saveFailed = runCatching { withContext(Dispatchers.IO) { s.saveIfChanged(text) } }.isFailure
    }

    // Matar o app dentro da janela do debounce perderia a última edição.
    val lifecycle = (context as ComponentActivity).lifecycle
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) {
                val s = session ?: return@LifecycleEventObserver
                // ponytail: escrita síncrona na main — arquivo de texto pequeno.
                saveFailed = runCatching { s.saveIfChanged(editorState.text()) }.isFailure
            }
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }

    val scope = rememberCoroutineScope()

    // Carrega [uri] no editor. Grava antes o autosave pendente do arquivo atual:
    // o debounce de 1s seria cancelado pela troca e a última edição se perderia.
    fun openUri(uri: Uri) {
        session?.let { s ->
            saveFailed = runCatching { s.saveIfChanged(editorState.text()) }.isFailure
            // Não troca de arquivo se o anterior não gravou — senão a edição some.
            if (saveFailed) return
        }
        scope.launch {
            val text = withContext(Dispatchers.IO) {
                context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
            } ?: return@launch
            title = displayName(context.contentResolver, uri) ?: "documento"
            editorState.load(text)
            saveFailed = false
            session = FileSession(context.contentResolver, uri, lastSaved = text)
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

    // ponytail: SharedPreferences basta para uma string; DataStore não é
    // dependência ainda. Trocar quando houver mais configurações.
    val prefs = remember { context.getSharedPreferences("jotmd", Context.MODE_PRIVATE) }
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
                                Text(
                                    hit.snippet,
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
        Column(Modifier.fillMaxSize().safeDrawingPadding()) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = { scope.launch { drawerState.open() } }) { Text("Pasta") }
                Text(
                    text = title,
                    modifier = Modifier.weight(1f).padding(start = 8.dp),
                    style = MaterialTheme.typography.titleMedium,
                )
                if (saveFailed) {
                    Text("Erro ao salvar", color = MaterialTheme.colorScheme.error)
                }
                TextButton(onClick = { open.launch(arrayOf("text/markdown", "text/plain", "*/*")) }) {
                    Text("Abrir")
                }
            }
            HorizontalDivider()
            EditorScreen(editorState, Modifier.weight(1f))
        }
    }
}
