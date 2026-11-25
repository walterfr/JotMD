package io.github.walterfr.jotmd.editor

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
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
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.walterfr.jotmd.core.Block
import io.github.walterfr.jotmd.core.BlockType
import io.github.walterfr.jotmd.core.DocRefs
import io.github.walterfr.jotmd.core.OutlineEntry
import io.github.walterfr.jotmd.editor.ops.activeLine1Based
import io.github.walterfr.jotmd.editor.ops.formatSourceGutter
import io.github.walterfr.jotmd.editor.ops.highlightMarkdownSource

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
fun BlockEditor(
    block: Block,
    state: EditorState,
    modifier: Modifier = Modifier,
    outline: List<OutlineEntry> = emptyList(),
    refs: DocRefs = DocRefs.EMPTY,
) {
    val itemModifier = if (state.isFocusMode && state.focusedId != null && state.focusedId != block.id) {
        modifier.alpha(0.38f)
    } else {
        modifier
    }
    val activeSearchRange = state.activeSearchMatch
        ?.takeIf { it.blockId == block.id }
        ?.rangeInBlock

    when {
        state.focusedId == block.id -> {
            val text = state.editValue.text
            val totalLines = text.count { it == '\n' } + 1
            val activeLine = activeLine1Based(text, state.editValue.selection.start)
            Row(itemModifier.fillMaxWidth()) {
                if (state.isSourceMode) {
                    SourceGutter(formatSourceGutter(totalLines, activeLine))
                    Spacer(Modifier.width(8.dp))
                }
                key(block.id) {
                    FocusedBlockField(block, state, activeSearchRange, Modifier.weight(1f))
                }
            }
        }
        state.isSourceMode -> {
            val totalLines = block.source.count { it == '\n' } + 1
            val highlighted = remember(
                block.source,
                MdTokens.themePreset,
                state.searchHighlightQuery,
                state.searchHighlightCaseSensitive,
                state.searchHighlightWholeWord,
                state.searchHighlightRegex,
                activeSearchRange,
            ) {
                buildSourceHighlight(block.source, state, activeSearchRange)
            }
            Row(itemModifier.fillMaxWidth().clickable { state.focus(block) }) {
                SourceGutter(formatSourceGutter(totalLines, activeLine1Based = null))
                Spacer(Modifier.width(8.dp))
                BasicText(
                    text = highlighted,
                    modifier = Modifier.weight(1f),
                    style = sourceStyle,
                )
            }
        }
        else -> {
            BlockRenderer(
                block = block,
                modifier = itemModifier.fillMaxWidth().clickable { state.focus(block) },
                onToggleTask = { state.toggleTask(block.id, it) },
                outline = outline,
                onSelectHeading = { state.focusBlockIndex(it) },
                onTableAddRow = { state.tableAddRow(block.id) },
                onTableRemoveRow = { state.tableRemoveRow(block.id) },
                onTableAddColumn = { state.tableAddColumn(block.id) },
                onTableRemoveColumn = { state.tableRemoveColumn(block.id) },
                onTableCycleAlign = { col -> state.tableCycleAlignment(block.id, col) },
                refs = refs,
                searchQuery = state.searchHighlightQuery,
                searchCaseSensitive = state.searchHighlightCaseSensitive,
                searchWholeWord = state.searchHighlightWholeWord,
                searchRegex = state.searchHighlightRegex,
                activeSearchRange = activeSearchRange,
                onTableMoveRow = { delta -> state.tableMoveRow(block.id, delta) },
                onTableMoveColumn = { delta ->
                    state.tableMoveColumn(block.id, if (delta < 0) 1 else 0, delta)
                },
            )
        }
    }
}

@Composable
private fun FocusedBlockField(
    block: Block,
    state: EditorState,
    activeSearchRange: IntRange?,
    modifier: Modifier = Modifier,
) {
    var hadFocus by remember(block.id) { mutableStateOf(false) }
    val focusRequester = remember(block.id) { FocusRequester() }
    LaunchedEffect(block.id) { focusRequester.requestFocus() }
    val visualTransformation = remember(
        state.isSourceMode,
        MdTokens.themePreset,
        state.searchHighlightQuery,
        state.searchHighlightCaseSensitive,
        state.searchHighlightWholeWord,
        state.searchHighlightRegex,
        activeSearchRange,
    ) {
        if (!state.isSourceMode) {
            VisualTransformation.None
        } else {
            VisualTransformation { text ->
                TransformedText(
                    buildSourceHighlight(text.text, state, activeSearchRange),
                    OffsetMapping.Identity,
                )
            }
        }
    }
    BasicTextField(
        value = state.editValue,
        onValueChange = { state.edit(it, block.id) },
        modifier = modifier
            .focusRequester(focusRequester)
            .onFocusChanged { info ->
                if (info.isFocused) hadFocus = true
                else if (hadFocus) state.release(block.id)
            }
            .onPreviewKeyEvent { event -> handleKey(event, block, state) },
        textStyle = sourceStyle,
        cursorBrush = SolidColor(MdTokens.link),
        visualTransformation = visualTransformation,
    )
}

private fun buildSourceHighlight(
    source: String,
    state: EditorState,
    activeSearchRange: IntRange?,
): AnnotatedString {
    val base = highlightMarkdownSource(source)
    if (state.searchHighlightQuery.isEmpty()) return base
    val activeOcc = activeOccurrenceIndex(
        source,
        activeSearchRange,
        state.searchHighlightQuery,
        state.searchHighlightCaseSensitive,
        state.searchHighlightWholeWord,
        state.searchHighlightRegex,
    )
    return applySearchHighlight(
        base,
        state.searchHighlightQuery,
        state.searchHighlightCaseSensitive,
        state.searchHighlightWholeWord,
        state.searchHighlightRegex,
        activeOcc,
    )
}

@Composable
private fun SourceGutter(text: String) {
    BasicText(
        text = text,
        modifier = Modifier.width(36.dp),
        style = sourceStyle.copy(
            color = MdTokens.muted,
            textAlign = TextAlign.End,
        ),
        softWrap = false,
    )
}

/**
 * Enter, Backspace no início, Delete no fim, `Tab`/`Shift+Tab`, `↑`/`↓` nas
 * bordas e atalhos `Ctrl+*` (`B`, `I`, `U`, `K`, `T`, `Z`, `Y`, `Shift+Z`, `/`).
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
        event.isCtrlPressed && event.key == Key.T -> state.insertTable()
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
