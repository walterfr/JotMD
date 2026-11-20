package io.github.walterfr.jotmd.editor

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import io.github.walterfr.jotmd.core.Block
import io.github.walterfr.jotmd.core.BlockType

/**
 * Um bloco editável: focado mostra o markdown cru num [BasicTextField],
 * desfocado mostra o [BlockRenderer]. Fonte crua em monoespaçada, como o
 * source mode do Typora real (CodeMirror) — ver DESIGN.md.
 *
 * Aproximação por bloco inteiro, não por span como o Typora hoje: expõe a
 * sintaxe do bloco todo, não só do trecho sob o cursor. Deliberado, ver
 * DESIGN.md "Modelo de foco".
 */
@Composable
fun BlockEditor(block: Block, state: EditorState, modifier: Modifier = Modifier) {
    if (state.focusedId == block.id) {
        var hadFocus by remember(block.id) { mutableStateOf(false) }
        val focusRequester = remember(block.id) { FocusRequester() }
        LaunchedEffect(block.id) { focusRequester.requestFocus() }
        BasicTextField(
            value = state.editValue,
            onValueChange = { state.edit(it, block.id) },
            modifier = modifier
                .fillMaxWidth()
                .focusRequester(focusRequester)
                .onFocusChanged { info ->
                    if (info.isFocused) hadFocus = true
                    else if (hadFocus) state.release(block.id)
                }
                .onPreviewKeyEvent { event -> handleKey(event, block, state) },
            textStyle = sourceStyle,
            cursorBrush = SolidColor(MdTokens.link),
        )
    } else if (state.isSourceMode) {
        BasicText(
            text = block.source,
            modifier = modifier.fillMaxWidth().clickable { state.focus(block) },
            style = sourceStyle,
        )
    } else {
        BlockRenderer(
            block = block,
            modifier = modifier.fillMaxWidth().clickable { state.focus(block) },
            onToggleTask = { state.toggleTask(block.id, it) },
        )
    }
}

/**
 * Enter, Backspace no início, Delete no fim, `Tab`/`Shift+Tab`, `↑`/`↓` nas
 * bordas e atalhos `Ctrl+*` (`B`, `I`, `U`, `K`, `Z`, `Y`, `Shift+Z`, `/`).
 */
private fun handleKey(event: KeyEvent, block: Block, state: EditorState): Boolean {
    if (event.type != KeyEventType.KeyDown) return false
    val cursor = state.editValue.selection
    val text = state.editValue.text

    return when {
        event.isCtrlPressed && event.key == Key.Z && event.isShiftPressed -> {
            state.redo()
            true
        }
        event.isCtrlPressed && event.key == Key.Y -> {
            state.redo()
            true
        }
        event.isCtrlPressed && event.key == Key.Z -> {
            state.undo()
            true
        }
        event.isCtrlPressed && event.key == Key.B -> {
            state.wrapSelection("**")
            true
        }
        event.isCtrlPressed && event.key == Key.I -> {
            state.wrapSelection("*")
            true
        }
        event.isCtrlPressed && event.key == Key.U -> {
            state.wrapSelection("<u>", "</u>")
            true
        }
        event.isCtrlPressed && event.key == Key.K -> {
            state.wrapSelection("[", "]()")
            true
        }
        event.isCtrlPressed && event.key == Key.Slash -> {
            state.toggleSourceMode()
            true
        }
        event.key == Key.Tab -> state.tabAtCursor(shift = event.isShiftPressed)
        event.key == Key.Enter && block.type == BlockType.FRONT_MATTER && cursor.collapsed ->
            state.exitFrontMatterOnEnter()
        event.key == Key.Enter && cursor.collapsed && hasLineMarkers(block.type) &&
            state.smartEnterAtCursor() -> true
        event.key == Key.Enter && cursor.collapsed && isSplittable(block.type) -> {
            state.splitAtCursor()
            true
        }
        event.key == Key.Backspace && cursor.collapsed && state.backspaceAtCursor() -> true
        event.key == Key.Delete && cursor.collapsed && cursor.start == text.length &&
            state.deleteAtCursor() -> true
        event.key == Key.DirectionUp && cursor.collapsed && cursor.start == 0 -> {
            state.focusPrevious(block.id)
            true
        }
        event.key == Key.DirectionDown && cursor.collapsed && cursor.start == text.length -> {
            state.focusNext(block.id)
            true
        }
        else -> false
    }
}

private fun isSplittable(type: BlockType) = type == BlockType.PARAGRAPH || type == BlockType.HEADING

/** Blocos cujo Enter continua o marcador da linha, como o Typora (ver ops/SmartEnter.kt). */
private fun hasLineMarkers(type: BlockType) =
    type == BlockType.UNORDERED_LIST || type == BlockType.ORDERED_LIST || type == BlockType.BLOCKQUOTE

private val sourceStyle: TextStyle
    get() = TextStyle(
        color = MdTokens.text,
        fontSize = MdTokens.base,
        lineHeight = MdTokens.bodyLineHeight,
        fontFamily = FontFamily.Monospace,
    )
