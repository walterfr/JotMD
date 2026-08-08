package io.github.walterfr.jotmd.editor

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import io.github.walterfr.jotmd.core.Block
import io.github.walterfr.jotmd.core.BlockId
import io.github.walterfr.jotmd.core.DocState

/**
 * Estado de edição por bloco. F2: trocar o texto de um bloco não muda o tipo
 * dele nem cria/funde blocos — isso é `splitAt`/`mergeWithPrevious`/`reparse`,
 * de F3. [Block.trailing] nunca é tocado aqui, só `source`.
 *
 * [doc] é sincronizado a cada tecla, não só ao perder o foco. Um blur causado
 * por um toque no toolbar (que também disputa foco) não some com a edição:
 * não existe uma edição "pendente" que dependa do momento exato do blur.
 */
class EditorState(initial: DocState) {
    var doc: DocState by mutableStateOf(initial)
        private set
    var focusedId: BlockId? by mutableStateOf(null)
        private set
    var editValue: TextFieldValue by mutableStateOf(TextFieldValue(""))
        private set

    fun load(newDoc: DocState) {
        doc = newDoc
        focusedId = null
        editValue = TextFieldValue("")
    }

    fun focus(block: Block) {
        if (focusedId == block.id) return
        focusedId = block.id
        editValue = TextFieldValue(block.source, TextRange(block.source.length))
    }

    fun edit(value: TextFieldValue) {
        editValue = value
        syncDocFromEdit()
    }

    /** Limpa o foco só se [id] ainda for o bloco focado — evita apagar um foco novo. */
    fun release(id: BlockId) {
        if (focusedId == id) focusedId = null
    }

    /** Envolve a seleção atual do bloco focado com [prefix]/[suffix]. Sem bloco focado, não faz nada. */
    fun wrapSelection(prefix: String, suffix: String = prefix) {
        if (focusedId == null) return
        val value = editValue
        val range = value.selection
        val text = value.text
        val newText = text.substring(0, range.start) + prefix +
            text.substring(range.start, range.end) + suffix +
            text.substring(range.end)
        editValue = TextFieldValue(
            text = newText,
            selection = TextRange(range.start + prefix.length, range.end + prefix.length),
        )
        syncDocFromEdit()
    }

    private fun syncDocFromEdit() {
        val id = focusedId ?: return
        doc = doc.copy(
            blocks = doc.blocks.map { if (it.id == id) it.copy(source = editValue.text) else it },
        )
    }
}
