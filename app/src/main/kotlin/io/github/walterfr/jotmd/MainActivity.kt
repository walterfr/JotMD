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
import io.github.walterfr.jotmd.core.DocState
import io.github.walterfr.jotmd.core.parse
import io.github.walterfr.jotmd.editor.DocumentView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { MaterialTheme { ReaderScreen() } }
    }
}

/**
 * Tela do leitor. F1: abre um arquivo por SAF e renderiza, sem edição.
 *
 * Gravar fica para quando houver o que gravar — o editor é F2/F3. Abrir já
 * pede permissão persistente para que o arquivo continue acessível depois.
 */
@Composable
private fun ReaderScreen() {
    val context = LocalContext.current
    var doc by remember { mutableStateOf(DocState(leading = "", blocks = emptyList())) }
    var title by remember { mutableStateOf("typora-ref.md") }

    LaunchedEffect(Unit) {
        val text = withContext(Dispatchers.IO) {
            context.assets.open("typora-ref.md").bufferedReader().use { it.readText() }
        }
        doc = parse(text)
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
        doc = parse(text)
    }

    Column(Modifier.fillMaxSize().safeDrawingPadding()) {
        androidx.compose.foundation.layout.Row(
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
        DocumentView(doc)
    }
}
