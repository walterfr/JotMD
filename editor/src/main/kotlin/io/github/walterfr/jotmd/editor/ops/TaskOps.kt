package io.github.walterfr.jotmd.editor.ops

import io.github.walterfr.jotmd.editor.listRows

/**
 * Alterna `[ ]` <-> `[x]` do item [itemIndex] (mesma ordem de `listRows`, que
 * conta também itens sem checkbox). Troca UM caractere no source, em vez de
 * reserializar, porque o invariante byte a byte do projeto não admite
 * normalizar CRLF/espaços do resto do bloco. Índice inválido ou item sem
 * checkbox: devolve [source] intacto.
 */
fun toggleTask(source: String, itemIndex: Int): String {
    val row = listRows(source).getOrNull(itemIndex) ?: return source
    val at = (row.checkOffset ?: return source) + 1
    val mark = if (row.checked == true) ' ' else 'x'
    return source.substring(0, at) + mark + source.substring(at + 1)
}
