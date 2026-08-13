package io.github.walterfr.jotmd.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberUpdatedState
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
 * riscado, código) mais undo/redo. Heading/link/citação/cerca mexem com a
 * linha inteira ou abrem diálogo — ficam pra quando houver UI de diálogo.
 * Undo/redo aqui só aparece com bloco focado (o atalho Ctrl+Z funciona
 * independente disso) — subir pro cabeçalho sempre-visível é mudança pequena,
 * cortada por escopo, não por estar bloqueada em nada.
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
    BasicText(
        text = AnnotatedString(label),
        modifier = Modifier.pointerInput(Unit) { detectTapGestures(onTap = { currentOnClick.value() }) },
        style = emphasis.copy(color = MdTokens.text, fontSize = MdTokens.base),
    )
}
