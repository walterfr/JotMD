package io.github.walterfr.jotmd.editor.ops

import io.github.walterfr.jotmd.core.BlockId
import io.github.walterfr.jotmd.core.DocState

data class UndoFrame(val doc: DocState, val focusedId: BlockId?, val timestamp: Long)

/**
 * Undo/redo com coalescência por pausa (Armadilha 2 do AI_CONTEXT).
 *
 * Grava o estado de ANTES de cada mudança, mas só cria um checkpoint novo se
 * já passou [coalesceWindowMs] desde o último — teclas seguidas dentro da
 * janela viram um Ctrl+Z só, não um por tecla. Um undo/redo sempre quebra a
 * janela: a edição seguinte nunca cola com o que veio antes dele.
 */
class UndoStack(private val coalesceWindowMs: Long = 500) {
    private val past = ArrayDeque<UndoFrame>()
    private val future = ArrayDeque<UndoFrame>()

    // null, não Long.MIN_VALUE: "frame.timestamp - MIN_VALUE" estoura (Long
    // overflow) e o resultado vira negativo, então o PRIMEIRO checkpoint nunca
    // era gravado. null lateja a checagem sem fazer a subtração.
    private var lastCheckpointAt: Long? = null

    fun recordBeforeChange(frame: UndoFrame) {
        val last = lastCheckpointAt
        if (last == null || frame.timestamp - last > coalesceWindowMs) {
            past.addLast(frame)
            future.clear()
        }
        lastCheckpointAt = frame.timestamp
    }

    fun undo(current: UndoFrame): UndoFrame? {
        if (past.isEmpty()) return null
        val previous = past.removeLast()
        future.addLast(current)
        lastCheckpointAt = null
        return previous
    }

    fun redo(current: UndoFrame): UndoFrame? {
        if (future.isEmpty()) return null
        val next = future.removeLast()
        past.addLast(current)
        lastCheckpointAt = null
        return next
    }

    fun clear() {
        past.clear()
        future.clear()
        lastCheckpointAt = null
    }
}
