package io.github.walterfr.jotmd

import android.content.ContentResolver
import android.net.Uri
import android.provider.OpenableColumns

/**
 * Arquivo aberto por SAF e o último texto que sabemos estar nele.
 *
 * [saveIfChanged] só escreve quando o texto difere do último gravado — abrir um
 * arquivo e não mexer nunca reescreve nada, e o autosave não regrava a cada
 * pausa se nada mudou.
 *
 * ponytail: modo "wt" trunca antes de escrever. Se o processo morrer no meio de
 * uma escrita, o arquivo fica truncado. Arquivo de texto pequeno, uma escrita
 * só — janela mínima. Upgrade: gravar num temporário e renomear, se algum
 * provedor SAF ou documento grande mostrar problema real.
 */
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

class FileSession(
    private val resolver: ContentResolver,
    val uri: Uri,
    private var lastSaved: String,
) {
    /** Grava [text] se mudou. Devolve true se escreveu. Lança em falha de E/S. */
    fun saveIfChanged(text: String): Boolean {
        if (text == lastSaved) return false
        val out = resolver.openOutputStream(uri, "wt") ?: error("sem stream de escrita: $uri")
        out.bufferedWriter().use { it.write(text) }
        lastSaved = text
        return true
    }
}
