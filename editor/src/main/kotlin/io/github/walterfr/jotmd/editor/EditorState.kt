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
import io.github.walterfr.jotmd.core.replaceAllInText
import io.github.walterfr.jotmd.core.serialize
import io.github.walterfr.jotmd.editor.ops.SmartDelete
import io.github.walterfr.jotmd.editor.ops.SmartEnter
import io.github.walterfr.jotmd.editor.ops.UndoFrame
import io.github.walterfr.jotmd.editor.ops.UndoStack
import io.github.walterfr.jotmd.editor.ops.applySmartPair
import io.github.walterfr.jotmd.editor.ops.isFrontMatterTrigger
import io.github.walterfr.jotmd.editor.ops.mergeWithPrevious
import io.github.walterfr.jotmd.editor.ops.reparse
import io.github.walterfr.jotmd.editor.ops.smartBackspace
import io.github.walterfr.jotmd.editor.ops.smartDeleteAtEnd
import io.github.walterfr.jotmd.editor.ops.smartEnter
import io.github.walterfr.jotmd.editor.ops.smartSplitInline
import io.github.walterfr.jotmd.editor.ops.smartTab
import io.github.walterfr.jotmd.editor.ops.splitAt
import io.github.walterfr.jotmd.editor.ops.toggleTask

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

    var autoPairEnabled: Boolean = true
    var isSourceMode: Boolean by mutableStateOf(false)

    var doc: DocState by mutableStateOf(parse(initialText, ids))
        private set
    var focusedId: BlockId? by mutableStateOf(null)
        private set
    var editValue: TextFieldValue by mutableStateOf(TextFieldValue(""))
        private set

    fun toggleSourceMode() {
        isSourceMode = !isSourceMode
    }

    /** Documento inteiro como texto, byte a byte — o que vai pro arquivo ao gravar. */
    fun text(): String = serialize(doc)

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

    /**
     * [from] = bloco cujo campo emitiu o valor. O IME (SwiftKey) pode entregar o
     * commit da composição ao campo antigo DEPOIS que um toque já trocou o foco;
     * sem o filtro esse texto sobrescreveria o bloco recém-focado.
     */
    fun edit(value: TextFieldValue, from: BlockId? = focusedId) {
        if (from == null || from != focusedId) return
        val prev = editValue
        val paired = if (autoPairEnabled && value.text != prev.text) {
            applySmartPair(
                prev.text,
                prev.selection.start,
                prev.selection.end,
                value.text,
                value.selection.start,
                value.selection.end,
            )
        } else {
            null
        }
        recordUndoCheckpoint()
        editValue = if (paired != null) {
            TextFieldValue(paired.text, TextRange(paired.selectionStart, paired.selectionEnd))
        } else {
            value
        }
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
    /**
     * Enter em lista/numerada/tarefa/citação (ver [smartEnter]). Devolve false
     * quando a linha do cursor não tem marcador — o chamador segue o Enter padrão.
     */
    fun smartEnterAtCursor(): Boolean {
        val id = focusedId ?: return false
        val index = doc.blocks.indexOfFirst { it.id == id }
        if (index < 0 || !editValue.selection.collapsed) return false
        when (val r = smartEnter(editValue.text, editValue.selection.start) ?: return false) {
            is SmartEnter.Insert -> edit(TextFieldValue(r.text, TextRange(r.cursor)))
            is SmartEnter.Exit -> {
                recordUndoCheckpoint()
                val block = doc.blocks[index]
                val paragraph = Block(ids.next(), BlockType.PARAGRAPH, "", block.trailing)
                val replacement = if (r.text.isEmpty()) {
                    // Só havia o item vazio: a própria estrutura vira o parágrafo.
                    listOf(paragraph)
                } else {
                    listOf(block.copy(source = r.text, trailing = "\n\n"), paragraph)
                }
                doc = doc.copy(blocks = doc.blocks.subList(0, index) + replacement + doc.blocks.subList(index + 1, doc.blocks.size))
                focusedId = paragraph.id
                editValue = TextFieldValue("", TextRange(0))
            }
        }
        return true
    }

    fun splitAtCursor() {
        val id = focusedId ?: return
        val index = doc.blocks.indexOfFirst { it.id == id }
        if (index < 0) return
        val offset = editValue.selection.start.coerceIn(0, editValue.text.length)

        if (isFrontMatterTrigger(index, editValue.text, offset)) {
            recordUndoCheckpoint()
            val original = doc.blocks[index]
            val fm = Block(ids.next(), BlockType.FRONT_MATTER, "---\n\n---", "\n\n")
            val next = Block(ids.next(), BlockType.PARAGRAPH, "", original.trailing)
            doc = doc.copy(
                blocks = listOf(fm, next) + doc.blocks.subList(1, doc.blocks.size),
            )
            focusedId = fm.id
            editValue = TextFieldValue(fm.source, TextRange(4))
            return
        }

        recordUndoCheckpoint()

        val (rawBefore, rawAfter) = splitAt(doc.blocks[index], offset, ids)
        var before = rawBefore
        var after = rawAfter
        var splitCursor = 0
        if (before.type == BlockType.PARAGRAPH || before.type == BlockType.HEADING) {
            smartSplitInline(before.source, after.source)?.let { (closedBefore, reopenedAfter, cursorAfter) ->
                before = before.copy(
                    type = parse(closedBefore, frontMatter = false).blocks.firstOrNull()?.type ?: before.type,
                    source = closedBefore,
                )
                after = after.copy(
                    type = parse(reopenedAfter, frontMatter = false).blocks.firstOrNull()?.type ?: after.type,
                    source = reopenedAfter,
                )
                splitCursor = cursorAfter
            }
        }

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
        // (Armadilha 1).
        if (after.source.isEmpty() && before.type != BlockType.CODE_FENCE) {
            doc = doc.copy(blocks = spliced)
            focusedId = after.id
            editValue = TextFieldValue("", TextRange(0))
            return
        }

        // Otimização 5k+ linhas: split entre dois parágrafos puros sem sintaxe
        // de cerca não altera o parse dos milhares de blocos seguintes.
        if (before.type == BlockType.PARAGRAPH && after.type == BlockType.PARAGRAPH &&
            !hasFenceOpener(before.source) && !hasFenceOpener(after.source)
        ) {
            doc = doc.copy(blocks = spliced)
            focusedId = after.id
            editValue = TextFieldValue(after.source, TextRange(splitCursor.coerceIn(0, after.source.length)))
            return
        }

        val result = reparse(spliced, index, ids)
        doc = doc.copy(blocks = result)
        focusAtOffset(result, index, before.source.length + before.trailing.length + splitCursor)
    }

    /**
     * Sai do bloco YAML front matter para um parágrafo abaixo quando o cursor
     * está no final após linha vazia (`\n\n---`), como no Typora (`DESIGN.md`).
     */
    fun exitFrontMatterOnEnter(): Boolean {
        val id = focusedId ?: return false
        if (!editValue.selection.collapsed) return false
        val index = doc.blocks.indexOfFirst { it.id == id }
        if (index < 0) return false
        val block = doc.blocks[index]
        if (block.type != BlockType.FRONT_MATTER) return false

        val text = editValue.text
        val offset = editValue.selection.start
        if (!text.endsWith("\n\n---") || offset < text.length - 4) return false

        recordUndoCheckpoint()
        val cleaned = if (text.length > 8) text.removeSuffix("\n\n---") + "\n---" else text
        val updatedFm = block.copy(source = cleaned, trailing = "\n\n")
        val paragraph = Block(ids.next(), BlockType.PARAGRAPH, "", block.trailing)
        doc = doc.copy(
            blocks = doc.blocks.subList(0, index) + listOf(updatedFm, paragraph) + doc.blocks.subList(index + 1, doc.blocks.size),
        )
        focusedId = paragraph.id
        editValue = TextFieldValue("", TextRange(0))
        return true
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

    /**
     * Backspace no cursor: remove marcador/indentação em cascata ou par vazio
     * ([smartBackspace]); no offset 0 sem marcador, apaga HR/bloco em branco
     * anterior ou funde com o anterior. Devolve false para o Backspace padrão.
     */
    fun backspaceAtCursor(): Boolean {
        val id = focusedId ?: return false
        if (!editValue.selection.collapsed) return false
        val index = doc.blocks.indexOfFirst { it.id == id }
        if (index < 0) return false

        val r = smartBackspace(editValue.text, editValue.selection.start)
        if (r != null) {
            recordUndoCheckpoint()
            val block = doc.blocks[index]
            val newType = if (block.type == BlockType.FRONT_MATTER || block.type == BlockType.CODE_FENCE) {
                block.type
            } else {
                parse(r.text, frontMatter = false).blocks.firstOrNull()?.type ?: BlockType.PARAGRAPH
            }
            editValue = TextFieldValue(r.text, TextRange(r.cursor))
            doc = doc.copy(
                blocks = doc.blocks.map { if (it.id == id) it.copy(type = newType, source = r.text) else it },
            )
            return true
        }

        if (editValue.selection.start == 0) {
            if (index > 0) {
                val prev = doc.blocks[index - 1]
                if (prev.type == BlockType.HORIZONTAL_RULE || (prev.source.isBlank() && editValue.text.isNotBlank())) {
                    recordUndoCheckpoint()
                    val remaining = doc.blocks.toMutableList().apply { removeAt(index - 1) }
                    val current = remaining[index - 1]
                    val result = if (current.source.isBlank()) remaining else reparse(remaining, index - 1, ids)
                    doc = doc.copy(blocks = result)
                    val target = result.find { it.id == id } ?: result.getOrNull(index - 1)
                    if (target != null) {
                        focusedId = target.id
                        editValue = TextFieldValue(target.source, TextRange(0))
                    } else {
                        focusedId = null
                        editValue = TextFieldValue("")
                    }
                } else {
                    mergeWithPreviousBlock(id)
                }
            }
            return true
        }

        return false
    }

    /** Indentação/desindentação com `Tab` / `Shift+Tab` no bloco focado. */
    fun tabAtCursor(shift: Boolean = false): Boolean {
        val id = focusedId ?: return false
        val index = doc.blocks.indexOfFirst { it.id == id }
        if (index < 0) return false

        val r = smartTab(editValue.text, editValue.selection.start, editValue.selection.end, shift)
            ?: return false
        recordUndoCheckpoint()
        val block = doc.blocks[index]
        val newType = if (block.type == BlockType.FRONT_MATTER ||
            block.type == BlockType.CODE_FENCE ||
            block.type == BlockType.CODE_INDENTED
        ) {
            block.type
        } else {
            parse(r.text, frontMatter = false).blocks.firstOrNull()?.type ?: BlockType.PARAGRAPH
        }
        editValue = TextFieldValue(r.text, TextRange(r.selectionStart, r.selectionEnd))
        doc = doc.copy(
            blocks = doc.blocks.map { if (it.id == id) it.copy(type = newType, source = r.text) else it },
        )
        return true
    }

    /** `Delete` no fim do bloco focado ou em bloco vazio: apaga bloco seguinte ou funde. */
    fun deleteAtCursor(): Boolean {
        val id = focusedId ?: return false
        if (!editValue.selection.collapsed || editValue.selection.start != editValue.text.length) return false
        val index = doc.blocks.indexOfFirst { it.id == id }
        if (index < 0) return false

        return when (val result = smartDeleteAtEnd(doc.blocks, index)) {
            null -> false
            is SmartDelete.RemoveBlock -> {
                recordUndoCheckpoint()
                doc = doc.copy(blocks = result.blocks)
                val target = result.focusId?.let { fid -> result.blocks.find { it.id == fid } }
                if (target != null) {
                    focusedId = target.id
                    editValue = TextFieldValue(
                        target.source,
                        TextRange(result.cursor.coerceIn(0, target.source.length)),
                    )
                } else {
                    focusedId = null
                    editValue = TextFieldValue("")
                }
                true
            }
            is SmartDelete.MergeNext -> {
                mergeWithPreviousBlock(result.nextId)
                true
            }
        }
    }

    /** Foca [blockIndex] e seleciona o trecho encontrado pela busca local na nota. */
    fun focusMatch(blockIndex: Int, rangeInBlock: IntRange) {
        val block = doc.blocks.getOrNull(blockIndex) ?: return
        focusedId = block.id
        val start = rangeInBlock.first.coerceIn(0, block.source.length)
        val end = (rangeInBlock.last + 1).coerceIn(start, block.source.length)
        editValue = TextFieldValue(block.source, TextRange(start, end))
    }

    /**
     * Substitui todas as ocorrências na nota inteira em um único passo de undo
     * (como `replaceAll` do `SearchPanel` no Typora), mantendo a fonte de IDs.
     */
    fun replaceAll(
        query: String,
        replacement: String,
        caseSensitive: Boolean = false,
        wholeWord: Boolean = false,
    ): Int {
        val (updated, count) = replaceAllInText(text(), query, replacement, caseSensitive, wholeWord)
        if (count == 0) return 0

        val prevIndex = focusedId?.let { fid -> doc.blocks.indexOfFirst { it.id == fid } } ?: -1
        recordUndoCheckpoint(force = true)
        val newDoc = parse(updated, ids)
        doc = newDoc
        val target = if (prevIndex in newDoc.blocks.indices) newDoc.blocks[prevIndex] else null
        if (target != null) {
            focusedId = target.id
            editValue = TextFieldValue(target.source, TextRange(target.source.length))
        } else {
            focusedId = null
            editValue = TextFieldValue("")
        }
        return count
    }

    /** Toque no checkbox do item [itemIndex] de uma lista: só o `source` do bloco muda; `trailing` fica. */
    fun toggleTask(blockId: BlockId, itemIndex: Int) {
        val block = doc.blocks.find { it.id == blockId } ?: return
        val toggled = toggleTask(block.source, itemIndex)
        if (toggled == block.source) return
        recordUndoCheckpoint()
        doc = doc.copy(blocks = doc.blocks.map { if (it.id == blockId) it.copy(source = toggled) else it })
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

    private fun recordUndoCheckpoint(force: Boolean = false) {
        undo.recordBeforeChange(currentFrame(), force = force)
    }

    private fun hasFenceOpener(text: String): Boolean = text.contains("```") || text.contains("~~~")

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
