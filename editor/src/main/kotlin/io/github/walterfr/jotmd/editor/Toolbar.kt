package io.github.walterfr.jotmd.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp

/**
 * Barra de formatação inline e de bloco exibida quando há um bloco focado.
 * Inclui estilos inline, alternância de título/lista/tarefa/citação, link, tabela e undo/redo.
 */
@Composable
fun EditorToolbar(state: EditorState, modifier: Modifier = Modifier) {
    if (state.focusedId == null) return
    Row(
        modifier
            .fillMaxWidth()
            .background(MdTokens.fenceBackground)
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        ToolbarButton("B", TextStyle(fontWeight = FontWeight.Bold)) { state.wrapSelection("**") }
        ToolbarButton("I", TextStyle(fontStyle = FontStyle.Italic)) { state.wrapSelection("*") }
        ToolbarButton("S", TextStyle(textDecoration = TextDecoration.LineThrough)) { state.wrapSelection("~~") }
        ToolbarButton(
            "==",
            TextStyle(background = MdTokens.highlightBackground, color = MdTokens.highlightText),
        ) { state.wrapSelection("==") }
        ToolbarButton("<>", TextStyle(fontFamily = FontFamily.Monospace)) { state.wrapSelection("`") }
        ToolbarButton("H", TextStyle(fontWeight = FontWeight.Bold)) { state.cycleHeadingLevel() }
        ToolbarButton("•", TextStyle()) { state.toggleLinePrefix("- ") }
        ToolbarButton("☐", TextStyle()) { state.toggleLinePrefix("- [ ] ") }
        ToolbarButton(">", TextStyle(fontFamily = FontFamily.Monospace)) { state.toggleLinePrefix("> ") }
        ToolbarButton("Link", TextStyle(textDecoration = TextDecoration.Underline)) { state.insertLink() }
        ToolbarButton("⊞", TextStyle()) { state.insertTable() }
        ToolbarButton("↶", TextStyle()) { state.undo() }
        ToolbarButton("↷", TextStyle()) { state.redo() }
    }
}

/**
 * `pointerInput` + `detectTapGestures`, não `Modifier.clickable`: clickable
 * marca o alvo focável e o toque tira o foco do BasicTextField ANTES do
 * onClick disparar — `wrapSelection` chegaria com `focusedId` já nulo. Visto
 * no tablet: o botão não fazia nada, silenciosamente.
 *
 * `pointerInput(Unit)`, não `pointerInput(onClick)`: a lambda `onClick` é um
 * literal novo a cada recomposição, então chavear nela reinicia o detector de
 * gesto toda vez que o pai recompõe — um toque que caia bem no meio do
 * reinício se perde. `rememberUpdatedState` deixa o detector vivo uma vez só
 * (chave `Unit`) e sempre lê o `onClick` mais recente por dentro. Visto de
 * novo no tablet: undo/redo, testados logo após edições pesadas (mais
 * recomposição por perto), falhavam silenciosamente onde B/I/S/`<>`, testados
 * em estado parado, funcionavam.
 */
@Composable
private fun ToolbarButton(label: String, emphasis: TextStyle, onClick: () -> Unit) {
    val currentOnClick = rememberUpdatedState(onClick)
    val color = if (emphasis.color == Color.Unspecified) MdTokens.text else emphasis.color
    BasicText(
        text = AnnotatedString(label),
        modifier = Modifier.pointerInput(Unit) { detectTapGestures(onTap = { currentOnClick.value() }) },
        style = emphasis.copy(color = color, fontSize = MdTokens.base),
    )
}
