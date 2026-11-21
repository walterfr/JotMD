package io.github.walterfr.jotmd.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.walterfr.jotmd.core.Block
import io.github.walterfr.jotmd.core.BlockType
import io.github.walterfr.jotmd.core.DocRefs
import io.github.walterfr.jotmd.core.OutlineEntry
import io.github.walterfr.jotmd.core.extractMathBlock
import io.github.walterfr.jotmd.core.isFootnoteDefinitionBlock
import io.github.walterfr.jotmd.core.isTocMarker
import io.github.walterfr.jotmd.core.parseFootnoteDefinition
import io.github.walterfr.jotmd.core.renderLatexToUnicode
import io.github.walterfr.jotmd.editor.ops.highlightCode

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
    outline: List<OutlineEntry> = emptyList(),
    onSelectHeading: ((blockIndex: Int) -> Unit)? = null,
    onTableAddRow: (() -> Unit)? = null,
    onTableRemoveRow: (() -> Unit)? = null,
    onTableAddColumn: (() -> Unit)? = null,
    onTableRemoveColumn: (() -> Unit)? = null,
    onTableCycleAlign: ((Int) -> Unit)? = null,
    refs: DocRefs = DocRefs.EMPTY,
    searchQuery: String = "",
    searchCaseSensitive: Boolean = false,
    searchWholeWord: Boolean = false,
    searchRegex: Boolean = false,
    activeSearchRange: IntRange? = null,
    onTableMoveRow: ((Int) -> Unit)? = null,
    onTableMoveColumn: ((Int) -> Unit)? = null,
) {
    when (block.type) {
        BlockType.HEADING -> HeadingBlock(
            source = block.source,
            modifier = modifier,
            refs = refs,
            searchQuery = searchQuery,
            searchCaseSensitive = searchCaseSensitive,
            searchWholeWord = searchWholeWord,
            searchRegex = searchRegex,
            activeSearchRange = activeSearchRange,
        )
        BlockType.BLOCKQUOTE -> QuoteBlock(
            source = block.source,
            modifier = modifier,
            refs = refs,
            searchQuery = searchQuery,
            searchCaseSensitive = searchCaseSensitive,
            searchWholeWord = searchWholeWord,
            searchRegex = searchRegex,
            activeSearchRange = activeSearchRange,
        )
        BlockType.UNORDERED_LIST, BlockType.ORDERED_LIST -> ListBlock(
            source = block.source,
            modifier = modifier,
            onToggleTask = onToggleTask,
            searchQuery = searchQuery,
            searchCaseSensitive = searchCaseSensitive,
            searchWholeWord = searchWholeWord,
            searchRegex = searchRegex,
            activeSearchRange = activeSearchRange,
        )
        BlockType.TABLE -> TableBlock(
            source = block.source,
            modifier = modifier,
            onAddRow = onTableAddRow,
            onRemoveRow = onTableRemoveRow,
            onMoveRow = onTableMoveRow,
            onAddColumn = onTableAddColumn,
            onRemoveColumn = onTableRemoveColumn,
            onMoveColumn = onTableMoveColumn,
            onCycleAlign = onTableCycleAlign,
            refs = refs,
            searchQuery = searchQuery,
            searchCaseSensitive = searchCaseSensitive,
            searchWholeWord = searchWholeWord,
            searchRegex = searchRegex,
            activeSearchRange = activeSearchRange,
        )
        BlockType.CODE_FENCE -> CodeBlock(
            source = block.source,
            fenced = true,
            modifier = modifier,
            searchQuery = searchQuery,
            searchCaseSensitive = searchCaseSensitive,
            searchWholeWord = searchWholeWord,
            searchRegex = searchRegex,
            activeSearchRange = activeSearchRange,
        )
        BlockType.CODE_INDENTED -> CodeBlock(
            source = block.source,
            fenced = false,
            modifier = modifier,
            searchQuery = searchQuery,
            searchCaseSensitive = searchCaseSensitive,
            searchWholeWord = searchWholeWord,
            searchRegex = searchRegex,
            activeSearchRange = activeSearchRange,
        )
        BlockType.HORIZONTAL_RULE -> RuleBlock(modifier)
        BlockType.FRONT_MATTER -> MetaBlock(
            source = block.source,
            modifier = modifier,
            searchQuery = searchQuery,
            searchCaseSensitive = searchCaseSensitive,
            searchWholeWord = searchWholeWord,
            searchRegex = searchRegex,
            activeSearchRange = activeSearchRange,
        )
        BlockType.LINK_DEFINITION -> if (isFootnoteDefinitionBlock(block.source)) {
            FootnoteDefBlock(
                source = block.source,
                refs = refs,
                modifier = modifier,
                searchQuery = searchQuery,
                searchCaseSensitive = searchCaseSensitive,
                searchWholeWord = searchWholeWord,
                searchRegex = searchRegex,
                activeSearchRange = activeSearchRange,
            )
        } else {
            LinkDefBlock(
                source = block.source,
                modifier = modifier,
                searchQuery = searchQuery,
                searchCaseSensitive = searchCaseSensitive,
                searchWholeWord = searchWholeWord,
                searchRegex = searchRegex,
                activeSearchRange = activeSearchRange,
            )
        }
        BlockType.PARAGRAPH -> when {
            isTocMarker(block.source) -> TocBlock(outline, modifier, onSelectHeading)
            isFootnoteDefinitionBlock(block.source) -> FootnoteDefBlock(
                source = block.source,
                refs = refs,
                modifier = modifier,
                searchQuery = searchQuery,
                searchCaseSensitive = searchCaseSensitive,
                searchWholeWord = searchWholeWord,
                searchRegex = searchRegex,
                activeSearchRange = activeSearchRange,
            )
            else -> {
                val mathExpr = remember(block.source) { extractMathBlock(block.source) }
                if (mathExpr != null) {
                    MathBlock(
                        latex = mathExpr,
                        rawSource = block.source,
                        modifier = modifier,
                        searchQuery = searchQuery,
                        searchCaseSensitive = searchCaseSensitive,
                        searchWholeWord = searchWholeWord,
                        searchRegex = searchRegex,
                        activeSearchRange = activeSearchRange,
                    )
                } else {
                    val image = remember(block.source) { soleImage(block.source) }
                    if (image != null) {
                        ImagePreview(image, modifier)
                    } else {
                        ParagraphBlock(
                            source = block.source,
                            modifier = modifier,
                            refs = refs,
                            searchQuery = searchQuery,
                            searchCaseSensitive = searchCaseSensitive,
                            searchWholeWord = searchWholeWord,
                            searchRegex = searchRegex,
                            activeSearchRange = activeSearchRange,
                        )
                    }
                }
            }
        }
        else -> when {
            isFootnoteDefinitionBlock(block.source) -> FootnoteDefBlock(
                source = block.source,
                refs = refs,
                modifier = modifier,
                searchQuery = searchQuery,
                searchCaseSensitive = searchCaseSensitive,
                searchWholeWord = searchWholeWord,
                searchRegex = searchRegex,
                activeSearchRange = activeSearchRange,
            )
            else -> {
                val mathExpr = remember(block.source) { extractMathBlock(block.source) }
                if (mathExpr != null) {
                    MathBlock(
                        latex = mathExpr,
                        rawSource = block.source,
                        modifier = modifier,
                        searchQuery = searchQuery,
                        searchCaseSensitive = searchCaseSensitive,
                        searchWholeWord = searchWholeWord,
                        searchRegex = searchRegex,
                        activeSearchRange = activeSearchRange,
                    )
                } else {
                    ParagraphBlock(
                        source = block.source,
                        modifier = modifier,
                        refs = refs,
                        searchQuery = searchQuery,
                        searchCaseSensitive = searchCaseSensitive,
                        searchWholeWord = searchWholeWord,
                        searchRegex = searchRegex,
                        activeSearchRange = activeSearchRange,
                    )
                }
            }
        }
    }
}

internal val bodyStyle: TextStyle
    get() = TextStyle(
        color = MdTokens.text,
        fontSize = MdTokens.base,
        lineHeight = MdTokens.bodyLineHeight,
    )

@Composable
private fun rememberInlineText(
    source: String,
    refs: DocRefs,
    searchQuery: String,
    searchCaseSensitive: Boolean,
    searchWholeWord: Boolean,
    searchRegex: Boolean,
    activeSearchRange: IntRange?,
): AnnotatedString = remember(
    source,
    refs,
    MdTokens.themePreset,
    searchQuery,
    searchCaseSensitive,
    searchWholeWord,
    searchRegex,
    activeSearchRange,
) {
    val base = inlineText(source, refs)
    if (searchQuery.isEmpty()) {
        base
    } else {
        val activeOcc = activeOccurrenceIndex(
            source,
            activeSearchRange,
            searchQuery,
            searchCaseSensitive,
            searchWholeWord,
            searchRegex,
        )
        applySearchHighlight(
            base,
            searchQuery,
            searchCaseSensitive,
            searchWholeWord,
            searchRegex,
            activeOcc,
        )
    }
}

@Composable
private fun ParagraphBlock(
    source: String,
    modifier: Modifier = Modifier,
    refs: DocRefs = DocRefs.EMPTY,
    searchQuery: String = "",
    searchCaseSensitive: Boolean = false,
    searchWholeWord: Boolean = false,
    searchRegex: Boolean = false,
    activeSearchRange: IntRange? = null,
) {
    val text = rememberInlineText(
        source,
        refs,
        searchQuery,
        searchCaseSensitive,
        searchWholeWord,
        searchRegex,
        activeSearchRange,
    )
    BasicText(text, modifier.fillMaxWidth(), style = bodyStyle)
}

@Composable
private fun HeadingBlock(
    source: String,
    modifier: Modifier = Modifier,
    refs: DocRefs = DocRefs.EMPTY,
    searchQuery: String = "",
    searchCaseSensitive: Boolean = false,
    searchWholeWord: Boolean = false,
    searchRegex: Boolean = false,
    activeSearchRange: IntRange? = null,
) {
    val level = remember(source) { headingLevel(source) }
    val text = rememberInlineText(
        source,
        refs,
        searchQuery,
        searchCaseSensitive,
        searchWholeWord,
        searchRegex,
        activeSearchRange,
    )
    val scale = MdTokens.headingScale[level - 1]

    Column(modifier.fillMaxWidth()) {
        BasicText(
            text,
            style = bodyStyle.copy(
                fontSize = MdTokens.base * scale,
                lineHeight = MdTokens.base * scale * MdTokens.headingLineHeight[level - 1],
                fontWeight = FontWeight.Bold,
                color = if (level == 6) MdTokens.muted else MdTokens.headingText,
            ),
        )
        if (MdTokens.hasRule(level)) {
            Spacer(Modifier.height(8.dp))
            Box(Modifier.fillMaxWidth().height(1.dp).background(MdTokens.headingRule))
        }
    }
}

private fun alertColor(type: QuoteAlertType): Color = when (type) {
    QuoteAlertType.NOTE -> MdTokens.palette.alertNote
    QuoteAlertType.TIP -> MdTokens.palette.alertTip
    QuoteAlertType.IMPORTANT -> MdTokens.palette.alertImportant
    QuoteAlertType.WARNING -> MdTokens.palette.alertWarning
    QuoteAlertType.CAUTION -> MdTokens.palette.alertCaution
}

@Composable
private fun QuoteBlock(
    source: String,
    modifier: Modifier = Modifier,
    refs: DocRefs = DocRefs.EMPTY,
    searchQuery: String = "",
    searchCaseSensitive: Boolean = false,
    searchWholeWord: Boolean = false,
    searchRegex: Boolean = false,
    activeSearchRange: IntRange? = null,
) {
    val alert = remember(source) { quoteAlertOf(source) }
    val barColor = if (alert != null) alertColor(alert.type) else MdTokens.quoteBar
    // IntrinsicSize.Min faz a Row medir a altura do texto primeiro, e só então a
    // barra pode acompanhá-la com fillMaxHeight.
    Row(modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
        Box(
            Modifier
                .width(MdTokens.quoteBarWidth)
                .fillMaxHeight()
                .background(barColor),
        )
        if (alert != null) {
            val accent = alertColor(alert.type)
            Column(Modifier.padding(horizontal = MdTokens.quotePadding)) {
                BasicText(
                    AnnotatedString(alert.type.label),
                    style = bodyStyle.copy(color = accent, fontWeight = FontWeight.Bold),
                )
                if (alert.bodySource.isNotBlank()) {
                    Spacer(Modifier.height(4.dp))
                    val body = rememberInlineText(
                        alert.bodySource,
                        refs,
                        searchQuery,
                        searchCaseSensitive,
                        searchWholeWord,
                        searchRegex,
                        activeSearchRange = null,
                    )
                    BasicText(body, style = bodyStyle)
                }
            }
        } else {
            val text = rememberInlineText(
                source,
                refs,
                searchQuery,
                searchCaseSensitive,
                searchWholeWord,
                searchRegex,
                activeSearchRange,
            )
            BasicText(
                text,
                Modifier.padding(horizontal = MdTokens.quotePadding),
                style = bodyStyle.copy(color = MdTokens.muted),
            )
        }
    }
}

@Composable
private fun ListBlock(
    source: String,
    modifier: Modifier = Modifier,
    onToggleTask: (Int) -> Unit = {},
    searchQuery: String = "",
    searchCaseSensitive: Boolean = false,
    searchWholeWord: Boolean = false,
    searchRegex: Boolean = false,
    activeSearchRange: IntRange? = null,
) {
    val currentToggle = rememberUpdatedState(onToggleTask)
    val rows = remember(
        source,
        MdTokens.themePreset,
        searchQuery,
        searchCaseSensitive,
        searchWholeWord,
        searchRegex,
        activeSearchRange,
    ) {
        val rawRows = listRows(source)
        if (searchQuery.isEmpty()) {
            rawRows
        } else {
            val blockActiveOcc = activeOccurrenceIndex(
                source,
                activeSearchRange,
                searchQuery,
                searchCaseSensitive,
                searchWholeWord,
                searchRegex,
            )
            var seenHits = 0
            rawRows.map { row ->
                val hitCount = findSearchMatchRanges(
                    row.content.text,
                    searchQuery,
                    searchCaseSensitive,
                    searchWholeWord,
                    searchRegex,
                ).size
                val localActive = blockActiveOcc?.let { it - seenHits }?.takeIf { it in 0 until hitCount }
                seenHits += hitCount
                row.copy(
                    content = applySearchHighlight(
                        row.content,
                        searchQuery,
                        searchCaseSensitive,
                        searchWholeWord,
                        searchRegex,
                        localActive,
                    ),
                )
            }
        }
    }
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
private fun TocBlock(
    outline: List<OutlineEntry>,
    modifier: Modifier = Modifier,
    onSelectHeading: ((Int) -> Unit)? = null,
) {
    val shape = RoundedCornerShape(MdTokens.cornerRadius)
    Column(
        modifier
            .fillMaxWidth()
            .background(MdTokens.metaBackground, shape)
            .border(1.dp, MdTokens.codeBorder, shape)
            .padding(12.dp),
    ) {
        BasicText(
            AnnotatedString("Índices"),
            style = bodyStyle.copy(fontWeight = FontWeight.Bold, color = MdTokens.headingText),
        )
        Spacer(Modifier.height(6.dp))
        if (outline.isEmpty()) {
            BasicText(
                AnnotatedString("Sem títulos no documento"),
                style = bodyStyle.copy(color = MdTokens.muted, fontSize = MdTokens.base * 0.9f),
            )
        } else {
            for (entry in outline) {
                TocEntryRow(entry, onSelectHeading)
            }
        }
    }
}

@Composable
private fun TocEntryRow(entry: OutlineEntry, onSelectHeading: ((Int) -> Unit)?) {
    val currentIndex = rememberUpdatedState(entry.blockIndex)
    val currentSelect = rememberUpdatedState(onSelectHeading)
    val indent = ((entry.level - 1).coerceAtLeast(0) * 14).dp
    BasicText(
        AnnotatedString(entry.title),
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = indent, top = 3.dp, bottom = 3.dp)
            .then(
                if (onSelectHeading != null) {
                    Modifier.pointerInput(Unit) {
                        detectTapGestures(onTap = { currentSelect.value?.invoke(currentIndex.value) })
                    }
                } else {
                    Modifier
                },
            ),
        style = bodyStyle.copy(color = MdTokens.link, fontSize = MdTokens.base * 0.95f),
    )
}

@Composable
private fun TableBlock(
    source: String,
    modifier: Modifier = Modifier,
    onAddRow: (() -> Unit)? = null,
    onRemoveRow: (() -> Unit)? = null,
    onMoveRow: ((Int) -> Unit)? = null,
    onAddColumn: (() -> Unit)? = null,
    onRemoveColumn: (() -> Unit)? = null,
    onMoveColumn: ((Int) -> Unit)? = null,
    onCycleAlign: ((Int) -> Unit)? = null,
    refs: DocRefs = DocRefs.EMPTY,
    searchQuery: String = "",
    searchCaseSensitive: Boolean = false,
    searchWholeWord: Boolean = false,
    searchRegex: Boolean = false,
    activeSearchRange: IntRange? = null,
) {
    val table = remember(source) { tableOf(source) }
    if (table == null) {
        ParagraphBlock(
            source = source,
            modifier = modifier,
            refs = refs,
            searchQuery = searchQuery,
            searchCaseSensitive = searchCaseSensitive,
            searchWholeWord = searchWholeWord,
            searchRegex = searchRegex,
            activeSearchRange = activeSearchRange,
        )
        return
    }
    val hasActions = onAddRow != null ||
        onRemoveRow != null ||
        onMoveRow != null ||
        onAddColumn != null ||
        onRemoveColumn != null ||
        onMoveColumn != null ||
        onCycleAlign != null

    val content: @Composable (Modifier) -> Unit = { rowModifier ->
        // Coluna por coluna, não linha por linha: com `width(IntrinsicSize.Max)` a
        // coluna tem a largura da célula mais larga, e as células preenchem essa
        // largura. Célula com largura própria por linha (o que havia) desalinhava as
        // colunas e deixava centro/direita sem efeito — visto no tablet.
        Row(
            rowModifier
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
                    TableCell(
                        text = table.headers[col],
                        align = align,
                        header = true,
                        refs = refs,
                        searchQuery = searchQuery,
                        searchCaseSensitive = searchCaseSensitive,
                        searchWholeWord = searchWholeWord,
                        searchRegex = searchRegex,
                    )
                    for (row in table.rows) {
                        TableCell(
                            text = row[col],
                            align = align,
                            refs = refs,
                            searchQuery = searchQuery,
                            searchCaseSensitive = searchCaseSensitive,
                            searchWholeWord = searchWholeWord,
                            searchRegex = searchRegex,
                        )
                    }
                }
            }
        }
    }

    if (!hasActions) {
        content(modifier)
        return
    }

    Column(modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(bottom = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (onAddRow != null) TableEditButton("+Lin", onAddRow)
            if (onRemoveRow != null) TableEditButton("-Lin", onRemoveRow)
            if (onMoveRow != null) {
                TableEditButton("↑Lin") { onMoveRow(-1) }
                TableEditButton("↓Lin") { onMoveRow(1) }
            }
            if (onAddColumn != null) TableEditButton("+Col", onAddColumn)
            if (onRemoveColumn != null) TableEditButton("-Col", onRemoveColumn)
            if (onMoveColumn != null) {
                TableEditButton("←Col") { onMoveColumn(-1) }
                TableEditButton("→Col") { onMoveColumn(1) }
            }
            if (onCycleAlign != null) TableEditButton("Alinhar") { onCycleAlign(0) }
        }
        content(Modifier)
    }
}

@Composable
private fun TableEditButton(label: String, onClick: () -> Unit) {
    val currentOnClick = rememberUpdatedState(onClick)
    val shape = RoundedCornerShape(MdTokens.cornerRadius)
    BasicText(
        text = AnnotatedString(label),
        modifier = Modifier
            .background(MdTokens.fenceBackground, shape)
            .border(1.dp, MdTokens.codeBorder, shape)
            .pointerInput(Unit) { detectTapGestures(onTap = { currentOnClick.value() }) }
            .padding(horizontal = 8.dp, vertical = 4.dp),
        style = bodyStyle.copy(
            fontSize = MdTokens.base * 0.8f,
            color = MdTokens.muted,
            fontWeight = FontWeight.Medium,
        ),
    )
}

@Composable
private fun TableCell(
    text: String,
    align: TextAlign,
    header: Boolean = false,
    refs: DocRefs = DocRefs.EMPTY,
    searchQuery: String = "",
    searchCaseSensitive: Boolean = false,
    searchWholeWord: Boolean = false,
    searchRegex: Boolean = false,
) {
    val rendered = rememberInlineText(
        source = text,
        refs = refs,
        searchQuery = searchQuery,
        searchCaseSensitive = searchCaseSensitive,
        searchWholeWord = searchWholeWord,
        searchRegex = searchRegex,
        activeSearchRange = null,
    )
    BasicText(
        rendered,
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
private fun CodeBlock(
    source: String,
    fenced: Boolean,
    modifier: Modifier = Modifier,
    searchQuery: String = "",
    searchCaseSensitive: Boolean = false,
    searchWholeWord: Boolean = false,
    searchRegex: Boolean = false,
    activeSearchRange: IntRange? = null,
) {
    val fence = remember(source, fenced) {
        if (fenced) fenceOf(source) else Fence(null, dedentIndentedCode(source))
    }
    val highlighted = remember(
        source,
        fence.code,
        fence.language,
        fenced,
        MdTokens.themePreset,
        searchQuery,
        searchCaseSensitive,
        searchWholeWord,
        searchRegex,
        activeSearchRange,
    ) {
        val base = highlightCode(fence.code, fence.language)
        if (searchQuery.isEmpty()) {
            base
        } else {
            val activeOcc = if (activeSearchRange == null) {
                null
            } else if (fenced) {
                val codeStart = source.indexOf('\n').let { if (it < 0) 0 else it + 1 }
                val codeEnd = codeStart + fence.code.length
                val codeMatches = findSearchMatchRanges(
                    source,
                    searchQuery,
                    searchCaseSensitive,
                    searchWholeWord,
                    searchRegex,
                ).filter { it.first >= codeStart && it.last < codeEnd }
                codeMatches.indexOfFirst { it == activeSearchRange || activeSearchRange.first in it }
                    .takeIf { it >= 0 }
            } else {
                activeOccurrenceIndex(
                    source,
                    activeSearchRange,
                    searchQuery,
                    searchCaseSensitive,
                    searchWholeWord,
                    searchRegex,
                )
            }
            applySearchHighlight(
                base,
                searchQuery,
                searchCaseSensitive,
                searchWholeWord,
                searchRegex,
                activeOcc,
            )
        }
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
                highlighted,
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
private fun rememberHighlightedPlainText(
    rawSource: String,
    displayText: String,
    searchQuery: String,
    searchCaseSensitive: Boolean,
    searchWholeWord: Boolean,
    searchRegex: Boolean,
    activeSearchRange: IntRange?,
): AnnotatedString = remember(
    rawSource,
    displayText,
    MdTokens.themePreset,
    searchQuery,
    searchCaseSensitive,
    searchWholeWord,
    searchRegex,
    activeSearchRange,
) {
    val base = AnnotatedString(displayText)
    if (searchQuery.isEmpty()) {
        base
    } else {
        val activeOcc = activeOccurrenceIndex(
            rawSource,
            activeSearchRange,
            searchQuery,
            searchCaseSensitive,
            searchWholeWord,
            searchRegex,
        )
        applySearchHighlight(
            base,
            searchQuery,
            searchCaseSensitive,
            searchWholeWord,
            searchRegex,
            activeOcc,
        )
    }
}

@Composable
private fun MathBlock(
    latex: String,
    rawSource: String,
    modifier: Modifier = Modifier,
    searchQuery: String = "",
    searchCaseSensitive: Boolean = false,
    searchWholeWord: Boolean = false,
    searchRegex: Boolean = false,
    activeSearchRange: IntRange? = null,
) {
    val rendered = remember(latex) { renderLatexToUnicode(latex) }
    val display = rememberHighlightedPlainText(
        rawSource = rawSource,
        displayText = rendered,
        searchQuery = searchQuery,
        searchCaseSensitive = searchCaseSensitive,
        searchWholeWord = searchWholeWord,
        searchRegex = searchRegex,
        activeSearchRange = activeSearchRange,
    )
    val shape = RoundedCornerShape(MdTokens.cornerRadius)
    Box(
        modifier
            .fillMaxWidth()
            .background(MdTokens.fenceBackground, shape)
            .border(1.dp, MdTokens.codeBorder, shape)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        BasicText(
            text = display,
            style = bodyStyle.copy(
                fontFamily = FontFamily.Serif,
                fontStyle = FontStyle.Italic,
                fontSize = MdTokens.base * 1.1f,
                textAlign = TextAlign.Center,
            ),
        )
    }
}

@Composable
private fun FootnoteDefBlock(
    source: String,
    refs: DocRefs,
    modifier: Modifier = Modifier,
    searchQuery: String = "",
    searchCaseSensitive: Boolean = false,
    searchWholeWord: Boolean = false,
    searchRegex: Boolean = false,
    activeSearchRange: IntRange? = null,
) {
    val parsed = remember(source) { parseFootnoteDefinition(source) }
    if (parsed == null) {
        ParagraphBlock(
            source = source,
            modifier = modifier,
            refs = refs,
            searchQuery = searchQuery,
            searchCaseSensitive = searchCaseSensitive,
            searchWholeWord = searchWholeWord,
            searchRegex = searchRegex,
            activeSearchRange = activeSearchRange,
        )
        return
    }
    val (id, content) = parsed
    val number = refs.footnoteNumber(id)
    val prefix = "[${number ?: id}]"
    val body = rememberInlineText(
        source = content,
        refs = refs,
        searchQuery = searchQuery,
        searchCaseSensitive = searchCaseSensitive,
        searchWholeWord = searchWholeWord,
        searchRegex = searchRegex,
        activeSearchRange = activeSearchRange,
    )
    Row(modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        BasicText(
            text = AnnotatedString(prefix),
            style = bodyStyle.copy(
                color = MdTokens.link,
                fontWeight = FontWeight.SemiBold,
                fontSize = MdTokens.base * 0.9f,
            ),
        )
        Spacer(Modifier.width(8.dp))
        BasicText(
            text = body,
            modifier = Modifier.weight(1f),
            style = bodyStyle.copy(fontSize = MdTokens.base * 0.92f),
        )
    }
}

@Composable
private fun LinkDefBlock(
    source: String,
    modifier: Modifier = Modifier,
    searchQuery: String = "",
    searchCaseSensitive: Boolean = false,
    searchWholeWord: Boolean = false,
    searchRegex: Boolean = false,
    activeSearchRange: IntRange? = null,
) {
    val display = rememberHighlightedPlainText(
        rawSource = source,
        displayText = source,
        searchQuery = searchQuery,
        searchCaseSensitive = searchCaseSensitive,
        searchWholeWord = searchWholeWord,
        searchRegex = searchRegex,
        activeSearchRange = activeSearchRange,
    )
    BasicText(
        text = display,
        modifier = modifier.fillMaxWidth(),
        style = bodyStyle.copy(
            color = MdTokens.muted,
            fontFamily = FontFamily.Monospace,
            fontSize = MdTokens.base * 0.85f,
            lineHeight = MdTokens.base * 0.85f * 1.45f,
        ),
    )
}

/**
 * Front matter como o Typora (`pre.md-meta-block` do tema github): YAML cru sem
 * os delimitadores `---`, monoespaçado, 85%, cinza, fundo #f7f7f7.
 */
@Composable
private fun MetaBlock(
    source: String,
    modifier: Modifier = Modifier,
    searchQuery: String = "",
    searchCaseSensitive: Boolean = false,
    searchWholeWord: Boolean = false,
    searchRegex: Boolean = false,
    activeSearchRange: IntRange? = null,
) {
    val rawYaml = remember(source) { source.lines().drop(1).dropLast(1).joinToString("\n") }
    val yaml = rememberHighlightedPlainText(
        rawSource = source,
        displayText = rawYaml,
        searchQuery = searchQuery,
        searchCaseSensitive = searchCaseSensitive,
        searchWholeWord = searchWholeWord,
        searchRegex = searchRegex,
        activeSearchRange = activeSearchRange,
    )
    BasicText(
        yaml,
        modifier
            .fillMaxWidth()
            .background(MdTokens.metaBackground, RoundedCornerShape(MdTokens.cornerRadius))
            .padding(16.dp),
        style = bodyStyle.copy(
            color = MdTokens.muted,
            fontFamily = FontFamily.Monospace,
            fontSize = MdTokens.base * 0.85f,
            lineHeight = MdTokens.base * 0.85f * 1.45f,
        ),
    )
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
