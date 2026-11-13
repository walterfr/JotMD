package io.github.walterfr.jotmd

import android.content.Intent
import android.net.Uri
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

class MainActivity : ComponentActivity() {
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

    val open = rememberLauncherForActivityResult(OpenMarkdown()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        runCatching {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
            )
        }
        val text = context.contentResolver.openInputStream(uri)
            ?.bufferedReader()
            ?.use { it.readText() }
            ?: return@rememberLauncherForActivityResult
        title = displayName(context.contentResolver, uri) ?: "documento"
        editorState.load(text)
        saveFailed = false
        session = FileSession(context.contentResolver, uri, lastSaved = text)
    }

    Column(Modifier.fillMaxSize().safeDrawingPadding()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
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
