package io.github.walterfr.jotmd.editor

import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.walterfr.jotmd.core.Block
import io.github.walterfr.jotmd.core.BlockType

/**
 * Um bloco renderizado, sem edição. F1 do roadmap.
 *
 * Tipo sem tratamento cai no ramo de parágrafo, que mostra o markdown inline do
 * jeito que der.
 */
@Composable
fun BlockRenderer(
    block: Block,
    modifier: Modifier = Modifier,
    onToggleTask: (itemIndex: Int) -> Unit = {},
) {
    when (block.type) {
        BlockType.HEADING -> HeadingBlock(block.source, modifier)
        BlockType.BLOCKQUOTE -> QuoteBlock(block.source, modifier)
        BlockType.UNORDERED_LIST, BlockType.ORDERED_LIST -> ListBlock(block.source, modifier, onToggleTask)
        BlockType.TABLE -> TableBlock(block.source, modifier)
        BlockType.CODE_FENCE -> CodeBlock(block.source, fenced = true, modifier = modifier)
        BlockType.CODE_INDENTED -> CodeBlock(block.source, fenced = false, modifier = modifier)
        BlockType.HORIZONTAL_RULE -> RuleBlock(modifier)
        BlockType.PARAGRAPH -> {
            val image = remember(block.source) { soleImage(block.source) }
            if (image != null) {
                ImagePreview(image, modifier)
            } else {
                ParagraphBlock(block.source, modifier)
            }
        }
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
private fun ListBlock(source: String, modifier: Modifier = Modifier, onToggleTask: (Int) -> Unit = {}) {
    val currentToggle = rememberUpdatedState(onToggleTask)
    val rows = remember(source) { listRows(source) }
    Column(modifier.fillMaxWidth()) {
        for ((index, row) in rows.withIndex()) {
            if (index > 0) Spacer(Modifier.height(4.dp))
            Row(Modifier.padding(start = MdTokens.listIndent * row.depth)) {
                // pointerInput(Unit) + detectTapGestures, não clickable: clickable tira o foco
                // do BasicTextField antes do toque chegar (ver ToolbarButton). Área >= 40dp.
                val tap = if (row.checked != null) {
                    Modifier
                        .defaultMinSize(minWidth = 40.dp, minHeight = 40.dp)
                        .pointerInput(Unit) {
                            detectTapGestures(onTap = { currentToggle.value(index) })
                        }
                } else {
                    Modifier.width(28.dp)
                }
                BasicText(
                    AnnotatedString(markerLabel(row)),
                    tap,
                    style = bodyStyle,
                )
                BasicText(row.content, style = bodyStyle)
            }

        }
    }
}

@Composable
private fun ImagePreview(image: ImageRef, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val bitmap by produceState<android.graphics.Bitmap?>(initialValue = null, image.destination) {
        value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            runCatching {
                context.contentResolver.openInputStream(Uri.parse(image.destination)).use {
                    BitmapFactory.decodeStream(it)
                }
            }.getOrNull()
        }
    }
    Box(
        modifier
            .fillMaxWidth()
            .background(MdTokens.fenceBackground, RoundedCornerShape(MdTokens.cornerRadius))
            .border(1.dp, MdTokens.codeBorder, RoundedCornerShape(MdTokens.cornerRadius))
            .padding(12.dp),
    ) {
        Column {
            if (bitmap != null) {
                Image(
                    bitmap = bitmap!!.asImageBitmap(),
                    contentDescription = image.alt,
                    modifier = Modifier.fillMaxWidth().heightIn(max = 320.dp),
                )
            } else {
                BasicText(
                    AnnotatedString("[imagem] ${image.alt.ifBlank { "Imagem" }}"),
                    style = bodyStyle.copy(fontWeight = FontWeight.Medium),
                )
            }
            BasicText(
                AnnotatedString(image.destination),
                style = bodyStyle.copy(color = MdTokens.muted, fontSize = MdTokens.base * 0.8f),
            )
        }
    }
}

@Composable
private fun TableBlock(source: String, modifier: Modifier = Modifier) {
    val table = remember(source) { tableOf(source) }
    if (table == null) {
        ParagraphBlock(source, modifier)
        return
    }
    // Coluna por coluna, não linha por linha: com `width(IntrinsicSize.Max)` a
    // coluna tem a largura da célula mais larga, e as células preenchem essa
    // largura. Célula com largura própria por linha (o que havia) desalinhava as
    // colunas e deixava centro/direita sem efeito — visto no tablet.
    Row(
        modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
    ) {
        table.headers.indices.forEach { col ->
            val align = when (table.alignments.getOrNull(col)) {
                TableAlignment.CENTER -> TextAlign.Center
                TableAlignment.RIGHT -> TextAlign.End
                else -> TextAlign.Start
            }
            Column(Modifier.width(IntrinsicSize.Max).widthIn(min = 72.dp)) {
                TableCell(table.headers[col], align, header = true)
                for (row in table.rows) TableCell(row[col], align)
            }
        }
    }
}

@Composable
private fun TableCell(text: String, align: TextAlign, header: Boolean = false) {
    BasicText(
        inlineText(text),
        Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp),
        style = bodyStyle.copy(
            fontWeight = if (header) FontWeight.Bold else FontWeight.Normal,
            textAlign = align,
        ),
    )
    Box(Modifier.fillMaxWidth().height(1.dp).background(MdTokens.tableBorder))
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
    val fence = remember(source, fenced) {
        if (fenced) fenceOf(source) else Fence(null, dedentIndentedCode(source))
    }
    val shape = RoundedCornerShape(MdTokens.cornerRadius)
    Box(
        modifier
            .fillMaxWidth()
            .background(MdTokens.fenceBackground, shape)
            .border(1.dp, MdTokens.codeBorder, shape)
            .padding(MdTokens.codePadding),
    ) {
        Column {
            if (!fence.language.isNullOrBlank()) {
                BasicText(
                    fence.language,
                    style = bodyStyle.copy(color = MdTokens.muted, fontSize = MdTokens.base * 0.8f),
                )
                Spacer(Modifier.height(4.dp))
            }
            BasicText(
                AnnotatedString(fence.code),
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
