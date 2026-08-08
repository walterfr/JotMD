package io.github.walterfr.jotmd.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.walterfr.jotmd.core.Block
import io.github.walterfr.jotmd.core.BlockType

/**
 * Um bloco renderizado, sem edição. F1 do roadmap.
 *
 * Tipo sem tratamento cai no ramo de parágrafo, que mostra o markdown inline do
 * jeito que der. Tabela é F4: até lá aparece o texto cru — feio, mas nada some.
 */
@Composable
fun BlockRenderer(block: Block, modifier: Modifier = Modifier) {
    when (block.type) {
        BlockType.HEADING -> HeadingBlock(block.source, modifier)
        BlockType.BLOCKQUOTE -> QuoteBlock(block.source, modifier)
        BlockType.UNORDERED_LIST, BlockType.ORDERED_LIST -> ListBlock(block.source, modifier)
        BlockType.CODE_FENCE -> CodeBlock(block.source, fenced = true, modifier = modifier)
        BlockType.CODE_INDENTED -> CodeBlock(block.source, fenced = false, modifier = modifier)
        BlockType.HORIZONTAL_RULE -> RuleBlock(modifier)
        else -> ParagraphBlock(block.source, modifier)
    }
}

private val bodyStyle = TextStyle(
    color = MdTokens.text,
    fontSize = MdTokens.base,
    lineHeight = MdTokens.bodyLineHeight,
)

@Composable
private fun ParagraphBlock(source: String, modifier: Modifier = Modifier) {
    val text = remember(source) { inlineText(source) }
    BasicText(text, modifier.fillMaxWidth(), style = bodyStyle)
}

@Composable
private fun HeadingBlock(source: String, modifier: Modifier = Modifier) {
    val level = remember(source) { headingLevel(source) }
    val text = remember(source) { inlineText(source) }
    val scale = MdTokens.headingScale[level - 1]

    Column(modifier.fillMaxWidth()) {
        BasicText(
            text,
            style = bodyStyle.copy(
                fontSize = MdTokens.base * scale,
                lineHeight = MdTokens.base * scale * MdTokens.headingLineHeight[level - 1],
                fontWeight = FontWeight.Bold,
                color = if (level == 6) MdTokens.muted else MdTokens.text,
            ),
        )
        if (MdTokens.hasRule(level)) {
            Spacer(Modifier.height(8.dp))
            Box(Modifier.fillMaxWidth().height(1.dp).background(MdTokens.headingRule))
        }
    }
}

@Composable
private fun QuoteBlock(source: String, modifier: Modifier = Modifier) {
    val text = remember(source) { inlineText(source) }
    // IntrinsicSize.Min faz a Row medir a altura do texto primeiro, e só então a
    // barra pode acompanhá-la com fillMaxHeight.
    Row(modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
        Box(
            Modifier
                .width(MdTokens.quoteBarWidth)
                .fillMaxHeight()
                .background(MdTokens.quoteBar),
        )
        BasicText(
            text,
            Modifier.padding(horizontal = MdTokens.quotePadding),
            style = bodyStyle.copy(color = MdTokens.muted),
        )
    }
}

@Composable
private fun ListBlock(source: String, modifier: Modifier = Modifier) {
    val rows = remember(source) { listRows(source) }
    Column(modifier.fillMaxWidth()) {
        for ((index, row) in rows.withIndex()) {
            if (index > 0) Spacer(Modifier.height(4.dp))
            Row(Modifier.padding(start = MdTokens.listIndent * row.depth)) {
                BasicText(
                    AnnotatedString(markerLabel(row)),
                    Modifier.width(28.dp),
                    style = bodyStyle,
                )
                BasicText(row.content, style = bodyStyle)
            }
        }
    }
}

private fun markerLabel(row: ListRow): String = when {
    row.checked == true -> "☑"
    row.checked == false -> "☐"
    row.marker.firstOrNull()?.isDigit() == true -> row.marker
    row.depth == 0 -> "•"
    else -> "◦"
}

@Composable
private fun CodeBlock(source: String, fenced: Boolean, modifier: Modifier = Modifier) {
    val code = remember(source, fenced) {
        if (fenced) fenceOf(source).code else dedentIndentedCode(source)
    }
    val shape = RoundedCornerShape(MdTokens.cornerRadius)
    Box(
        modifier
            .fillMaxWidth()
            .background(MdTokens.fenceBackground, shape)
            .border(1.dp, MdTokens.codeBorder, shape)
            .padding(MdTokens.codePadding),
    ) {
        BasicText(
            AnnotatedString(code),
            Modifier.horizontalScroll(rememberScrollState()),
            style = bodyStyle.copy(
                fontFamily = FontFamily.Monospace,
                fontSize = MdTokens.base * 0.9f,
                lineHeight = MdTokens.base * 0.9f * 1.45f,
            ),
            softWrap = false,
        )
    }
}

@Composable
private fun RuleBlock(modifier: Modifier = Modifier) {
    Box(
        modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .height(MdTokens.ruleThickness)
            .background(MdTokens.ruleColor),
    )
}

/** Espaço antes do bloco. Heading respira mais, como no tema do Typora. */
fun gapBefore(type: BlockType) =
    if (type == BlockType.HEADING) MdTokens.headingGap else MdTokens.blockGap
