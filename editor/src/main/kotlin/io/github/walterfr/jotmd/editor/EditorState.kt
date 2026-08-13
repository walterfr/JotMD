package io.github.walterfr.jotmd.editor

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import io.github.walterfr.jotmd.core.Block
import io.github.walterfr.jotmd.core.BlockId
import io.github.walterfr.jotmd.core.BlockIdSource
import io.github.walterfr.jotmd.core.BlockType
import io.github.walterfr.jotmd.core.DocState
import io.github.walterfr.jotmd.core.parse
import io.github.walterfr.jotmd.editor.ops.UndoFrame
import io.github.walterfr.jotmd.editor.ops.UndoStack
import io.github.walterfr.jotmd.editor.ops.mergeWithPrevious
import io.github.walterfr.jotmd.editor.ops.reparse
import io.github.walterfr.jotmd.editor.ops.splitAt

/**
 * Estado de edição por bloco. F3: Enter divide o bloco (`splitAt`), Backspace
 * no offset 0 funde com o anterior (`mergeWithPrevious`), `↑`/`↓` nas bordas
 * pulam de bloco, Ctrl+Z/Ctrl+Shift+Z desfazem com coalescência.
 *
 * [ids] é uma ÚNICA fonte de IDs para a vida inteira do documento carregado —
 * toda operação estrutural (split, merge, reparse) usa a mesma instância.
 * Chamar `parse()` de novo com uma fonte nova no meio da edição recomeçaria os
 * IDs do zero e colidiria com os que já existem em [doc], quebrando a unicidade
 * que o `LazyColumn(key = ...)` depende.
 *
 * [doc] é sincronizado a cada tecla, não só ao perder o foco — ver histórico
 * de por quê em EditorStateTest e no commit do F2 (toolbar disputando foco com
 * o BasicTextField).
 */
class EditorState(initialText: String = "") {
    private var ids = BlockIdSource()
    private val undo = UndoStack()

    var doc: DocState by mutableStateOf(parse(initialText, ids))
        private set
    var focusedId: BlockId? by mutableStateOf(null)
        private set
    var editValue: TextFieldValue by mutableStateOf(TextFieldValue(""))
        private set

    fun load(text: String) {
        ids = BlockIdSource()
        doc = parse(text, ids)
        focusedId = null
        editValue = TextFieldValue("")
        undo.clear()
    }

    fun focus(block: Block) {
        if (focusedId == block.id) return
        focusedId = block.id
        editValue = TextFieldValue(block.source, TextRange(block.source.length))
    }

    fun edit(value: TextFieldValue) {
        recordUndoCheckpoint()
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
        recordUndoCheckpoint()
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

    /** Enter no cursor: divide o bloco focado em dois e reparseia a cauda (Armadilha 1). */
    fun splitAtCursor() {
        val id = focusedId ?: return
        val index = doc.blocks.indexOfFirst { it.id == id }
        if (index < 0) return
        recordUndoCheckpoint()

        val offset = editValue.selection.start.coerceIn(0, editValue.text.length)
        val (before, after) = splitAt(doc.blocks[index], offset, ids)
        val spliced = doc.blocks.toMutableList().apply {
            set(index, before)
            add(index + 1, after)
        }

        // Enter no fim do texto — o caso mais comum. `after` fica vazio, e
        // reparse o absorveria: parse() nunca cria bloco pra texto vazio, então
        // o foco cairia direto no PRÓXIMO bloco real, pulando o parágrafo em
        // branco que o usuário queria abrir pra digitar. Pula o reparse aqui.
        //
        // Exceto CODE_FENCE: uma cerca sem fechar precisa continuar cascateando
        // (Armadilha 1) — a linha em branco que o Enter criou pode muito bem
        // ainda estar DENTRO da cerca, não ser um bloco novo separado. Escopo
        // conhecido: uma cerca já FECHADA também cai nesse ramo mais devagar e
        // pode reproduzir o mesmo pulo — caso raro (Enter logo depois de ``` de
        // fechamento), não tratado agora.
        if (after.source.isEmpty() && before.type != BlockType.CODE_FENCE) {
            doc = doc.copy(blocks = spliced)
            focusedId = after.id
            editValue = TextFieldValue("", TextRange(0))
            return
        }

        val result = reparse(spliced, index, ids)
        doc = doc.copy(blocks = result)
        focusAtOffset(result, index, before.source.length + before.trailing.length)
    }

    /** Backspace no offset 0: funde [id] com o bloco anterior e reparseia. No-op sem anterior. */
    fun mergeWithPreviousBlock(id: BlockId) {
        val index = doc.blocks.indexOfFirst { it.id == id }
        if (index <= 0) return
        recordUndoCheckpoint()

        val mergeOffset = doc.blocks[index - 1].source.length
        val merged = mergeWithPrevious(doc.blocks, id, ids)
        val result = reparse(merged, index - 1, ids)
        doc = doc.copy(blocks = result)
        focusAtOffset(result, index - 1, mergeOffset)
    }

    /** `↑` na borda de cima: pula pro bloco anterior, cursor no fim. No-op no primeiro bloco. */
    fun focusPrevious(id: BlockId) {
        val index = doc.blocks.indexOfFirst { it.id == id }
        if (index <= 0) return
        focus(doc.blocks[index - 1])
    }

    /** `↓` na borda de baixo: pula pro bloco seguinte, cursor no início. No-op no último bloco. */
    fun focusNext(id: BlockId) {
        val index = doc.blocks.indexOfFirst { it.id == id }
        if (index < 0 || index == doc.blocks.size - 1) return
        val next = doc.blocks[index + 1]
        focusedId = next.id
        editValue = TextFieldValue(next.source, TextRange(0))
    }

    fun undo() {
        val restored = undo.undo(currentFrame()) ?: return
        applyFrame(restored)
    }

    fun redo() {
        val restored = undo.redo(currentFrame()) ?: return
        applyFrame(restored)
    }

    private fun currentFrame() = UndoFrame(doc, focusedId, System.currentTimeMillis())

    private fun applyFrame(frame: UndoFrame) {
        doc = frame.doc
        focusedId = frame.focusedId
        val block = frame.focusedId?.let { fid -> doc.blocks.find { it.id == fid } }
        editValue = if (block != null) TextFieldValue(block.source, TextRange(block.source.length)) else TextFieldValue("")
    }

    private fun recordUndoCheckpoint() {
        undo.recordBeforeChange(currentFrame())
    }

    /** Acha, a partir de [fromIndex], o bloco que contém o [targetOffset]-ésimo caractere da cauda, e foca ali. */
    private fun focusAtOffset(blocks: List<Block>, fromIndex: Int, targetOffset: Int) {
        var remaining = targetOffset
        for (i in fromIndex until blocks.size) {
            val block = blocks[i]
            if (remaining <= block.source.length) {
                focusedId = block.id
                editValue = TextFieldValue(block.source, TextRange(remaining.coerceIn(0, block.source.length)))
                return
            }
            remaining -= block.source.length + block.trailing.length
        }
        val last = blocks.lastOrNull() ?: return
        focusedId = last.id
        editValue = TextFieldValue(last.source, TextRange(last.source.length))
    }

    private fun syncDocFromEdit() {
        val id = focusedId ?: return
        doc = doc.copy(
            blocks = doc.blocks.map { if (it.id == id) it.copy(source = editValue.text) else it },
        )
    }
}
