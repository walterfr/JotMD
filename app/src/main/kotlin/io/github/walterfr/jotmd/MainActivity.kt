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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import io.github.walterfr.jotmd.editor.EditorScreen
import io.github.walterfr.jotmd.editor.EditorState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

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
 * Ainda sem gravar de volta no arquivo: falta decidir onde entra a escrita via
 * SAF (a cada edição? só ao sair? um botão "Salvar"?) — questão de produto,
 * não téorica, não travo nisso.
 */
@Composable
private fun EditorHostScreen() {
    val context = LocalContext.current
    val editorState = remember { EditorState() }
    var title by remember { mutableStateOf("typora-ref.md") }

    LaunchedEffect(Unit) {
        val text = withContext(Dispatchers.IO) {
            context.assets.open("typora-ref.md").bufferedReader().use { it.readText() }
        }
        editorState.load(text)
    }

    val open = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        runCatching {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )
        }
        val text = context.contentResolver.openInputStream(uri)
            ?.bufferedReader()
            ?.use { it.readText() }
            ?: return@rememberLauncherForActivityResult
        title = uri.lastPathSegment?.substringAfterLast('/') ?: "documento"
        editorState.load(text)
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
            TextButton(onClick = { open.launch(arrayOf("text/markdown", "text/plain", "*/*")) }) {
                Text("Abrir")
            }
        }
        HorizontalDivider()
        EditorScreen(editorState, Modifier.weight(1f))
    }
}
