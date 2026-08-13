package io.github.walterfr.jotmd.editor.ops

import io.github.walterfr.jotmd.core.DocState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class UndoStackTest {

    private fun frame(text: String, at: Long) =
        UndoFrame(DocState(leading = text, blocks = emptyList()), focusedId = null, timestamp = at)

    @Test
    fun `edições dentro da janela colam num checkpoint só`() {
        val stack = UndoStack(coalesceWindowMs = 500)
        stack.recordBeforeChange(frame("a", at = 0))
        stack.recordBeforeChange(frame("ab", at = 100))
        stack.recordBeforeChange(frame("abc", at = 200))

        val undone = stack.undo(frame("abcd", at = 250))
        assertEquals("a", undone?.doc?.leading, "devia voltar pro primeiro checkpoint da rajada, não pro último")
    }

    @Test
    fun `edições fora da janela criam checkpoints separados`() {
        val stack = UndoStack(coalesceWindowMs = 500)
        stack.recordBeforeChange(frame("a", at = 0))
        stack.recordBeforeChange(frame("ab", at = 2000)) // passou da janela

        val undone1 = stack.undo(frame("abc", at = 2100))
        assertEquals("ab", undone1?.doc?.leading)

        val undone2 = stack.undo(undone1!!)
        assertEquals("a", undone2?.doc?.leading)
    }

    @Test
    fun `undo sem histórico devolve nulo`() {
        val stack = UndoStack()
        assertNull(stack.undo(frame("x", at = 0)))
    }

    @Test
    fun `redo desfaz o undo`() {
        val stack = UndoStack(coalesceWindowMs = 500)
        stack.recordBeforeChange(frame("a", at = 0))
        stack.recordBeforeChange(frame("ab", at = 2000))

        val current = frame("abc", at = 2100)
        val undone = stack.undo(current)!!
        val redone = stack.redo(undone)
        assertEquals("abc", redone?.doc?.leading)
    }

    @Test
    fun `editar depois de um undo quebra a colagem com o que veio antes`() {
        val stack = UndoStack(coalesceWindowMs = 500)
        stack.recordBeforeChange(frame("a", at = 0))
        val undone = stack.undo(frame("b", at = 100))!!

        // mesmo dentro da janela de 500ms, undo força um novo checkpoint
        stack.recordBeforeChange(frame("c", at = 150))
        val undoneAgain = stack.undo(frame("d", at = 200))
        assertEquals("c", undoneAgain?.doc?.leading)
    }

    @Test
    fun `nova edição depois do undo apaga o redo`() {
        val stack = UndoStack(coalesceWindowMs = 500)
        stack.recordBeforeChange(frame("a", at = 0))
        stack.recordBeforeChange(frame("ab", at = 2000))
        val undone = stack.undo(frame("abc", at = 2100))!!

        stack.recordBeforeChange(frame("xyz", at = 5000))
        assertNull(stack.redo(frame("xyz2", at = 5100)))
    }
}
