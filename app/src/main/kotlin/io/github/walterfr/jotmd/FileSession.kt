package io.github.walterfr.jotmd

import android.content.ContentResolver
import android.net.Uri
import android.provider.OpenableColumns
import java.io.OutputStreamWriter

/**
 * Nome de exibição do arquivo. `uri.lastPathSegment` num URI de DocumentsUI é um
 * ID opaco ("msf:1000008159"), não o nome — visto no tablet.
 */
fun displayName(resolver: ContentResolver, uri: Uri): String? =
    runCatching {
        resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
            if (it.moveToFirst()) it.getString(0) else null
        }
    }.getOrNull()

/**
 * Arquivo aberto por SAF e o último texto que sabemos estar nele.
 *
 * [saveIfChanged] só escreve quando o texto difere do último gravado — abrir um
 * arquivo e não mexer nunca reescreve nada, e o autosave não regrava a cada
 * pausa se nada mudou. Usa modo `"rwt"` (com fallback para `"wt"` em provedores
 * que não aceitam `"rwt"`), grava UTF-8 explicitamente e chama `flush()`.
 */
class FileSession(
    private val resolver: ContentResolver,
    val uri: Uri,
    private var lastSaved: String,
) {
    /** Grava [text] se mudou. Devolve true se escreveu. Lança em falha de E/S. */
    // Sincronizado: a troca de arquivo grava na main enquanto o autosave pode
    // estar gravando em IO a mesma sessão.
    @Synchronized
    fun saveIfChanged(text: String): Boolean {
        if (text == lastSaved) return false
        val out = runCatching { resolver.openOutputStream(uri, "rwt") }.getOrNull()
            ?: resolver.openOutputStream(uri, "wt")
            ?: error("sem stream de escrita: $uri")
        out.use { stream ->
            OutputStreamWriter(stream, Charsets.UTF_8).use { writer ->
                writer.write(text)
                writer.flush()
            }
        }
        lastSaved = text
        return true
    }
}
