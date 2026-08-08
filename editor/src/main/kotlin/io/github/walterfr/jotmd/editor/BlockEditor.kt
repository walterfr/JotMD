package io.github.walterfr.jotmd.editor

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import io.github.walterfr.jotmd.core.Block

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
            onValueChange = state::edit,
            modifier = modifier
                .fillMaxWidth()
                .focusRequester(focusRequester)
                .onFocusChanged { info ->
                    if (info.isFocused) hadFocus = true
                    else if (hadFocus) state.release(block.id)
                },
            textStyle = sourceStyle,
            cursorBrush = SolidColor(MdTokens.link),
        )
    } else {
        BlockRenderer(
            block = block,
            modifier = modifier.fillMaxWidth().clickable { state.focus(block) },
        )
    }
}

private val sourceStyle = TextStyle(
    color = MdTokens.text,
    fontSize = MdTokens.base,
    lineHeight = MdTokens.bodyLineHeight,
    fontFamily = FontFamily.Monospace,
)
