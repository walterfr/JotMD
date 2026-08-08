package io.github.walterfr.jotmd.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp

/**
 * Envolve a seleção do bloco focado. Só aparece com algo focado.
 *
 * ponytail: só os quatro wraps mecanicamente idênticos (negrito, itálico,
 * riscado, código). Heading/link/citação/cerca mexem com a linha inteira ou
 * abrem diálogo — ficam para quando o estado souber a posição da linha do
 * cursor, o que empurra pra F3 junto de splitAt/reparse.
 */
@Composable
fun EditorToolbar(state: EditorState, modifier: Modifier = Modifier) {
    if (state.focusedId == null) return
    Row(
        modifier
            .fillMaxWidth()
            .background(MdTokens.fenceBackground)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        ToolbarButton("B", TextStyle(fontWeight = FontWeight.Bold)) { state.wrapSelection("**") }
        ToolbarButton("I", TextStyle(fontStyle = FontStyle.Italic)) { state.wrapSelection("*") }
        ToolbarButton("S", TextStyle(textDecoration = TextDecoration.LineThrough)) { state.wrapSelection("~~") }
        ToolbarButton("<>", TextStyle(fontFamily = FontFamily.Monospace)) { state.wrapSelection("`") }
    }
}

/**
 * `pointerInput` + `detectTapGestures`, não `Modifier.clickable`: clickable
 * marca o alvo focável e o toque tira o foco do BasicTextField ANTES do
 * onClick disparar — `wrapSelection` chegaria com `focusedId` já nulo. Visto
 * no tablet: o botão não fazia nada, silenciosamente.
 */
@Composable
private fun ToolbarButton(label: String, emphasis: TextStyle, onClick: () -> Unit) {
    BasicText(
        text = AnnotatedString(label),
        modifier = Modifier.pointerInput(onClick) { detectTapGestures(onTap = { onClick() }) },
        style = emphasis.copy(color = MdTokens.text, fontSize = MdTokens.base),
    )
}
