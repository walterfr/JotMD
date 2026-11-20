package io.github.walterfr.jotmd.core

import org.intellij.markdown.IElementType
import org.intellij.markdown.MarkdownElementTypes
import org.intellij.markdown.MarkdownTokenTypes
import org.intellij.markdown.ast.ASTNode
import org.intellij.markdown.ast.getTextInNode
import org.intellij.markdown.flavours.gfm.GFMElementTypes
import org.intellij.markdown.flavours.gfm.GFMTokenTypes
import java.io.ByteArrayOutputStream
import java.nio.CharBuffer
import java.nio.charset.Charset
import java.nio.charset.CharsetEncoder
import java.nio.charset.CodingErrorAction
import java.util.Locale

private const val PAGE_WIDTH = 595f
private const val PAGE_HEIGHT = 842f
private const val MARGIN_LEFT = 45f
private const val MARGIN_RIGHT = 45f
private const val MARGIN_TOP = 45f
private const val CONTENT_WIDTH = PAGE_WIDTH - MARGIN_LEFT - MARGIN_RIGHT // 505 pt
private const val START_Y = PAGE_HEIGHT - MARGIN_TOP // 797 pt
private const val BOTTOM_MARGIN = 55f
private const val FOOTER_Y = 26f
private const val LIST_INDENT = 18f
private const val QUOTE_INDENT = 14f

private val WIN_ANSI: Charset = Charset.forName("windows-1252")

/**
 * Exporta [markdown] para um documento PDF 1.4 nativo em folha A4 (`595 x 842 pt`),
 * com tipografia inspirada no Typora, paginação automática e rodapé em todas as páginas.
 */
fun exportToPdf(markdown: String, title: String = "Documento"): ByteArray {
    val doc = parse(markdown)
    val writer = PdfWriter()
    for (block in doc.blocks) {
        writer.renderBlock(block, MARGIN_LEFT, CONTENT_WIDTH, quoteTextStyle = null)
    }
    writer.addFooters(title)
    return writer.toByteArray(title)
}

private enum class PdfFont(val alias: String, val baseFont: String) {
    REGULAR("/F1", "Helvetica"),
    BOLD("/F2", "Helvetica-Bold"),
    OBLIQUE("/F3", "Helvetica-Oblique"),
    MONO("/F4", "Courier"),
}

private data class Rgb(val r: Float, val g: Float, val b: Float)

private val COLOR_DARK = Rgb(0.20f, 0.20f, 0.20f)
private val COLOR_MUTED = Rgb(0.47f, 0.47f, 0.47f)
private val COLOR_FOOTER = Rgb(0.50f, 0.50f, 0.50f)
private val COLOR_META_TEXT = Rgb(0.35f, 0.35f, 0.35f)
private val COLOR_HEADING_RULE = Rgb(0.93f, 0.93f, 0.93f)
private val COLOR_HR = Rgb(0.90f, 0.90f, 0.90f)
private val COLOR_QUOTE_BAR = Rgb(0.87f, 0.88f, 0.90f)
private val COLOR_META_BG = Rgb(0.96f, 0.96f, 0.96f)
private val COLOR_CODE_BG = Rgb(0.97f, 0.97f, 0.97f)
private val COLOR_TABLE_HEADER_BG = Rgb(0.96f, 0.96f, 0.96f)
private val COLOR_TABLE_ALT_BG = Rgb(0.98f, 0.98f, 0.98f)
private val COLOR_TABLE_BORDER = Rgb(0.87f, 0.88f, 0.90f)

private fun AlertKind.rgb(): Rgb = when (this) {
    AlertKind.NOTE -> Rgb(0.035f, 0.412f, 0.855f)
    AlertKind.TIP -> Rgb(0.122f, 0.533f, 0.239f)
    AlertKind.IMPORTANT -> Rgb(0.510f, 0.314f, 0.875f)
    AlertKind.WARNING -> Rgb(0.604f, 0.404f, 0.0f)
    AlertKind.CAUTION -> Rgb(0.812f, 0.133f, 0.180f)
}

private data class QuoteTextStyle(
    val font: PdfFont,
    val color: Rgb,
)

private class ActiveBar(
    val x: Float,
    val width: Float,
    val color: Rgb,
    var topY: Float,
    var bottomY: Float,
)

private data class BoxLine(
    val text: String,
    val font: PdfFont,
    val fontSize: Float,
    val lineHeight: Float,
    val color: Rgb,
)

private enum class PdfTableAlign { LEFT, CENTER, RIGHT }

private class PdfWriter {
    private val encoder: CharsetEncoder = WIN_ANSI.newEncoder()
        .onMalformedInput(CodingErrorAction.REPLACE)
        .onUnmappableCharacter(CodingErrorAction.REPLACE)

    private val pages = mutableListOf(StringBuilder())
    private var pageIndex = 0
    private var y = START_Y
    private val activeBars = mutableListOf<ActiveBar>()

    private val current: StringBuilder
        get() = pages[pageIndex]

    fun renderBlock(
        block: Block,
        left: Float,
        availWidth: Float,
        quoteTextStyle: QuoteTextStyle?,
    ) {
        when (block.type) {
            BlockType.FRONT_MATTER -> renderFrontMatter(block.source, left, availWidth)
            BlockType.HEADING -> renderHeading(block.source, left, availWidth)
            BlockType.BLOCKQUOTE -> renderBlockquote(block.source, left, availWidth)
            BlockType.UNORDERED_LIST, BlockType.ORDERED_LIST -> renderList(block.source, left, availWidth, quoteTextStyle)
            BlockType.CODE_FENCE -> renderCodeFence(block.source, left, availWidth)
            BlockType.CODE_INDENTED -> renderCodeIndented(block.source, left, availWidth)
            BlockType.TABLE -> renderTable(block.source, left, availWidth)
            BlockType.HORIZONTAL_RULE -> renderHorizontalRule(left, availWidth)
            BlockType.LINK_DEFINITION -> Unit
            BlockType.PARAGRAPH, BlockType.OTHER, BlockType.HTML -> {
                val style = quoteTextStyle ?: QuoteTextStyle(PdfFont.REGULAR, COLOR_DARK)
                renderParagraph(cleanInline(block.source), left, availWidth, style.font, 11f, 16f, style.color)
            }
        }
    }

    private fun renderFrontMatter(source: String, left: Float, availWidth: Float) {
        val yaml = source.lines().drop(1).dropLast(1).joinToString("\n")
        val fontSize = 9.5f
        val lineHeight = 13.5f
        val padX = 12f
        val innerWidth = (availWidth - 2 * padX).coerceAtLeast(40f)
        val wrapped = if (yaml.isEmpty()) {
            listOf("")
        } else {
            wrapText(normalizeForPdf(yaml, encoder), PdfFont.MONO, fontSize, innerWidth, preserveIndent = true)
        }
        val lines = wrapped.map { BoxLine(it, PdfFont.MONO, fontSize, lineHeight, COLOR_META_TEXT) }
        drawBoxedLines(lines, left, availWidth, COLOR_META_BG, padX, padY = 8f)
        addGap(10f)
    }

    private fun renderHeading(source: String, left: Float, availWidth: Float) {
        val root = astOf(source)
        val node = root.children.firstOrNull { it.type in HEADING_TYPES }
        val level = when (node?.type) {
            MarkdownElementTypes.ATX_1, MarkdownElementTypes.SETEXT_1 -> 1
            MarkdownElementTypes.ATX_2, MarkdownElementTypes.SETEXT_2 -> 2
            MarkdownElementTypes.ATX_3 -> 3
            MarkdownElementTypes.ATX_4 -> 4
            MarkdownElementTypes.ATX_5 -> 5
            MarkdownElementTypes.ATX_6 -> 6
            else -> fallbackHeadingLevel(source)
        }
        val contentNode = node?.pick(MarkdownTokenTypes.ATX_CONTENT)
            ?: node?.pick(MarkdownTokenTypes.SETEXT_CONTENT)
            ?: node
            ?: root
        val text = buildString {
            appendInlineChildren(contentNode, PdfInlineCtx.of(source, root), emptySet(), softBreakAsSpace = true)
        }.trim()

        val fontSize = when (level) {
            1 -> 22f
            2 -> 18f
            3 -> 15f
            4 -> 13f
            5 -> 11.5f
            else -> 11f
        }
        val lineHeight = fontSize * 1.32f
        val color = if (level == 6) COLOR_MUTED else COLOR_DARK
        val spaceBefore = if (level <= 2) 8f else 5f
        if (y < START_Y) addGap(spaceBefore)

        val extraRule = if (level == 1 || level == 2) 6f else 0f
        val wrapped = wrapText(normalizeForPdf(text, encoder), PdfFont.BOLD, fontSize, availWidth)
        if (wrapped.isEmpty() || (wrapped.size == 1 && wrapped[0].isEmpty())) {
            ensureLineSpace(lineHeight + extraRule)
            advanceY(lineHeight)
        } else {
            for ((idx, line) in wrapped.withIndex()) {
                val needed = lineHeight + if (idx == wrapped.lastIndex) extraRule else 0f
                ensureLineSpace(needed)
                drawText(line, left, y - fontSize * 0.85f, PdfFont.BOLD, fontSize, color)
                advanceY(lineHeight)
            }
        }

        if (level == 1 || level == 2) {
            val ruleY = y - 1f
            drawLine(left, ruleY, left + availWidth, ruleY, 0.75f, COLOR_HEADING_RULE)
            advanceY(5f)
        }
        addGap(6f)
    }

    private fun renderBlockquote(source: String, left: Float, availWidth: Float) {
        val alert = alertOf(source)
        val innerLeft = left + QUOTE_INDENT
        val innerWidth = (availWidth - QUOTE_INDENT).coerceAtLeast(40f)
        ensureLineSpace(18f)

        val barColor = alert?.kind?.rgb() ?: COLOR_QUOTE_BAR
        val bar = ActiveBar(x = left + 1.5f, width = 3f, color = barColor, topY = y, bottomY = y)
        activeBars += bar

        if (alert != null) {
            val titleLineHeight = 16f
            ensureLineSpace(titleLineHeight)
            drawText(normalizeForPdf(alert.kind.label, encoder), innerLeft, y - 11f * 0.85f, PdfFont.BOLD, 11f, barColor)
            advanceY(titleLineHeight)
            if (alert.body.isNotEmpty()) {
                addGap(2f)
                val innerBlocks = parse(alert.body, frontMatter = false).blocks
                val style = QuoteTextStyle(PdfFont.REGULAR, COLOR_DARK)
                for (b in innerBlocks) {
                    renderBlock(b, innerLeft, innerWidth, style)
                }
            }
        } else {
            val stripped = source.lines().joinToString("\n") { it.replaceFirst(QUOTE_LINE_PREFIX, "") }
            val style = QuoteTextStyle(PdfFont.OBLIQUE, COLOR_MUTED)
            if (stripped == source) {
                renderParagraph(cleanInline(source), innerLeft, innerWidth, style.font, 11f, 16f, style.color)
            } else {
                val innerBlocks = parse(stripped, frontMatter = false).blocks
                for (b in innerBlocks) {
                    renderBlock(b, innerLeft, innerWidth, style)
                }
            }
        }

        activeBars.remove(bar)
        flushBarSegment(bar)
        addGap(6f)
    }

    private fun renderList(
        source: String,
        left: Float,
        availWidth: Float,
        quoteTextStyle: QuoteTextStyle?,
    ) {
        val root = astOf(source)
        val ctx = PdfInlineCtx.of(source, root)
        val listNode = root.children.firstOrNull {
            it.type == MarkdownElementTypes.UNORDERED_LIST || it.type == MarkdownElementTypes.ORDERED_LIST
        }
        if (listNode == null) {
            val style = quoteTextStyle ?: QuoteTextStyle(PdfFont.REGULAR, COLOR_DARK)
            renderParagraph(cleanInline(source), left, availWidth, style.font, 11f, 16f, style.color)
            return
        }
        renderListNode(listNode, ctx, level = 0, baseLeft = left, rightEdge = left + availWidth, quoteTextStyle = quoteTextStyle)
        addGap(6f)
    }

    private fun renderListNode(
        node: ASTNode,
        ctx: PdfInlineCtx,
        level: Int,
        baseLeft: Float,
        rightEdge: Float,
        quoteTextStyle: QuoteTextStyle?,
    ) {
        val ordered = node.type == MarkdownElementTypes.ORDERED_LIST
        var sequential = 1
        for (child in node.children) {
            if (child.type != MarkdownElementTypes.LIST_ITEM) continue
            val rawNumber = child.pick(MarkdownTokenTypes.LIST_NUMBER)
                ?.getTextInNode(ctx.src)
                ?.toString()
                ?.trim()
                ?.takeIf { it.isNotEmpty() }
            val checkBox = child.pick(GFMTokenTypes.CHECK_BOX)?.getTextInNode(ctx.src)?.toString()
            val marker = when {
                checkBox != null -> if (checkBox.contains('x', ignoreCase = true)) "[x]" else "[ ]"
                ordered -> rawNumber ?: "${sequential}."
                else -> "\u2022"
            }
            sequential++
            renderListItem(child, ctx, level, marker, baseLeft, rightEdge, quoteTextStyle)
        }
    }

    private fun renderListItem(
        item: ASTNode,
        ctx: PdfInlineCtx,
        level: Int,
        marker: String,
        baseLeft: Float,
        rightEdge: Float,
        quoteTextStyle: QuoteTextStyle?,
    ) {
        val font = quoteTextStyle?.font ?: PdfFont.REGULAR
        val color = quoteTextStyle?.color ?: COLOR_DARK
        val fontSize = 11f
        val lineHeight = 16f
        val markerX = baseLeft + level * LIST_INDENT + 3f
        val normMarker = normalizeForPdf(marker, encoder)
        val markerWidth = measureWidth(normMarker, font, fontSize)
        val defaultTextX = baseLeft + (level + 1) * LIST_INDENT
        val textX = maxOf(defaultTextX, markerX + markerWidth + 4f)
        val textWidth = (rightEdge - textX).coerceAtLeast(40f)

        val parts = item.children.filter {
            it.type != MarkdownTokenTypes.LIST_BULLET &&
                it.type != MarkdownTokenTypes.LIST_NUMBER &&
                it.type != GFMTokenTypes.CHECK_BOX &&
                it.type != MarkdownTokenTypes.EOL &&
                it.type != MarkdownTokenTypes.WHITE_SPACE
        }

        var markerDrawn = false
        for (part in parts) {
            when (part.type) {
                MarkdownElementTypes.UNORDERED_LIST, MarkdownElementTypes.ORDERED_LIST -> {
                    if (!markerDrawn) {
                        ensureLineSpace(lineHeight)
                        drawText(normMarker, markerX, y - fontSize * 0.85f, font, fontSize, color)
                        advanceY(lineHeight)
                        markerDrawn = true
                    }
                    renderListNode(part, ctx, level + 1, baseLeft, rightEdge, quoteTextStyle)
                }

                MarkdownElementTypes.PARAGRAPH -> {
                    val plain = buildString {
                        appendInlineChildren(part, ctx, emptySet(), softBreakAsSpace = true)
                    }.trim()
                    val wrapped = wrapText(normalizeForPdf(plain, encoder), font, fontSize, textWidth)
                    if (wrapped.isEmpty()) {
                        if (!markerDrawn) {
                            ensureLineSpace(lineHeight)
                            drawText(normMarker, markerX, y - fontSize * 0.85f, font, fontSize, color)
                            advanceY(lineHeight)
                            markerDrawn = true
                        }
                    } else {
                        for ((idx, line) in wrapped.withIndex()) {
                            ensureLineSpace(lineHeight)
                            val baseline = y - fontSize * 0.85f
                            if (!markerDrawn && idx == 0) {
                                drawText(normMarker, markerX, baseline, font, fontSize, color)
                                markerDrawn = true
                            }
                            drawText(line, textX, baseline, font, fontSize, color)
                            advanceY(lineHeight)
                        }
                    }
                }

                else -> {
                    if (!markerDrawn) {
                        ensureLineSpace(lineHeight)
                        drawText(normMarker, markerX, y - fontSize * 0.85f, font, fontSize, color)
                        advanceY(lineHeight)
                        markerDrawn = true
                    }
                    val sub = part.getTextInNode(ctx.src).toString()
                    for (b in parse(sub, frontMatter = false).blocks) {
                        renderBlock(b, defaultTextX, (rightEdge - defaultTextX).coerceAtLeast(40f), quoteTextStyle)
                    }
                }
            }
        }
        if (!markerDrawn) {
            ensureLineSpace(lineHeight)
            drawText(normMarker, markerX, y - fontSize * 0.85f, font, fontSize, color)
            advanceY(lineHeight)
        }
        addGap(2f)
    }

    private fun renderCodeFence(source: String, left: Float, availWidth: Float) {
        val node = astOf(source).children.firstOrNull { it.type == MarkdownElementTypes.CODE_FENCE }
        if (node == null) {
            renderCodeBlock(lang = null, code = source, left = left, availWidth = availWidth)
            return
        }
        val rawLang = node.pick(MarkdownTokenTypes.FENCE_LANG)?.getTextInNode(source)?.toString()?.trim()
        val lang = rawLang?.split(Regex("\\s+"))?.firstOrNull()?.takeIf { it.isNotEmpty() }
        val code = buildString {
            for (child in node.children) {
                when (child.type) {
                    MarkdownTokenTypes.CODE_FENCE_CONTENT -> append(child.getTextInNode(source))
                    MarkdownTokenTypes.EOL -> append('\n')
                    else -> Unit
                }
            }
        }.trim('\n', '\r')
        renderCodeBlock(lang, code, left, availWidth)
    }

    private fun renderCodeIndented(source: String, left: Float, availWidth: Float) {
        val code = source.lines().joinToString("\n") { line ->
            when {
                line.startsWith("    ") -> line.substring(4)
                line.startsWith("\t") -> line.substring(1)
                else -> line
            }
        }.trim('\n', '\r')
        renderCodeBlock(lang = null, code = code, left = left, availWidth = availWidth)
    }

    private fun renderCodeBlock(lang: String?, code: String, left: Float, availWidth: Float) {
        val padX = 10f
        val padY = 8f
        val innerWidth = (availWidth - 2 * padX).coerceAtLeast(40f)
        val lines = buildList {
            if (lang != null) {
                val safeLang = normalizeForPdf(lang, encoder)
                add(BoxLine(safeLang, PdfFont.REGULAR, 8.5f, 12f, COLOR_FOOTER))
            }
            val codeFontSize = 9.5f
            val codeLineHeight = 13f
            val wrappedCode = if (code.isEmpty()) {
                listOf("")
            } else {
                wrapText(normalizeForPdf(code, encoder), PdfFont.MONO, codeFontSize, innerWidth, preserveIndent = true)
            }
            for (line in wrappedCode) {
                add(BoxLine(line, PdfFont.MONO, codeFontSize, codeLineHeight, COLOR_DARK))
            }
        }
        drawBoxedLines(lines, left, availWidth, COLOR_CODE_BG, padX, padY)
        addGap(8f)
    }

    private fun drawBoxedLines(
        lines: List<BoxLine>,
        left: Float,
        availWidth: Float,
        bgColor: Rgb,
        padX: Float,
        padY: Float,
    ) {
        if (lines.isEmpty()) return
        var idx = 0
        while (idx < lines.size) {
            val minHeight = padY * 2 + lines[idx].lineHeight
            if (y - minHeight < BOTTOM_MARGIN) {
                newPage()
            }
            val availableHeight = y - BOTTOM_MARGIN
            var endIdx = idx
            var contentHeight = 0f
            while (endIdx < lines.size) {
                val nextH = lines[endIdx].lineHeight
                if (endIdx > idx && padY * 2 + contentHeight + nextH > availableHeight) break
                contentHeight += nextH
                endIdx++
            }
            val boxHeight = padY * 2 + contentHeight
            val boxBottom = y - boxHeight
            drawRect(left, boxBottom, availWidth, boxHeight, bgColor)

            var curY = y - padY
            for (i in idx until endIdx) {
                val line = lines[i]
                drawText(line.text, left + padX, curY - line.fontSize * 0.85f, line.font, line.fontSize, line.color)
                curY -= line.lineHeight
            }
            advanceY(boxHeight)
            idx = endIdx
            if (idx < lines.size) newPage()
        }
    }

    private fun renderTable(source: String, left: Float, availWidth: Float) {
        val root = astOf(source)
        val tableNode = root.children.firstOrNull { it.type == GFMElementTypes.TABLE }
        val lines = source.lineSequence().filter { it.isNotBlank() }.toList()
        if (tableNode == null || lines.size < 2) {
            renderParagraph(cleanInline(source), left, availWidth, PdfFont.REGULAR, 11f, 16f, COLOR_DARK)
            return
        }

        val headers = tableNode.pick(GFMElementTypes.HEADER)
            ?.children
            ?.filter { it.type == GFMTokenTypes.CELL }
            ?.map { it.getTextInNode(source).toString().trim() }
            ?: splitTableCells(lines[0])
        if (headers.isEmpty()) {
            renderParagraph(cleanInline(source), left, availWidth, PdfFont.REGULAR, 11f, 16f, COLOR_DARK)
            return
        }

        val alignments = splitTableCells(lines[1]).map { sep ->
            val alignLeft = sep.startsWith(":")
            val alignRight = sep.endsWith(":")
            when {
                alignLeft && alignRight -> PdfTableAlign.CENTER
                alignLeft -> PdfTableAlign.LEFT
                alignRight -> PdfTableAlign.RIGHT
                else -> PdfTableAlign.LEFT
            }
        }

        val rows = tableNode.children
            .filter { it.type == GFMElementTypes.ROW }
            .map { rowNode ->
                val cells = rowNode.children
                    .filter { it.type == GFMTokenTypes.CELL }
                    .map { it.getTextInNode(source).toString().trim() }
                padCells(cells, headers.size)
            }

        val colCount = headers.size
        val colWidth = availWidth / colCount
        val cellPadX = 6f
        val cellPadY = 5f
        val fontSize = 10f
        val lineHeight = 13.5f
        val cellTextWidth = (colWidth - 2 * cellPadX).coerceAtLeast(12f)

        fun prepareRow(cells: List<String>, font: PdfFont): Pair<List<List<String>>, Float> {
            val wrapped = cells.map { raw ->
                val cleaned = normalizeForPdf(cleanInline(raw), encoder)
                wrapText(cleaned, font, fontSize, cellTextWidth).ifEmpty { listOf("") }
            }
            val maxLines = wrapped.maxOfOrNull { it.size }?.coerceAtLeast(1) ?: 1
            return wrapped to (maxLines * lineHeight + 2 * cellPadY)
        }

        fun drawRow(
            wrappedCells: List<List<String>>,
            rowHeight: Float,
            font: PdfFont,
            bgColor: Rgb?,
        ) {
            val rowBottom = y - rowHeight
            if (bgColor != null) {
                drawRect(left, rowBottom, availWidth, rowHeight, bgColor)
            }
            // Bordas externas, divisória inferior e divisórias de coluna.
            drawLine(left, y, left + availWidth, y, 0.5f, COLOR_TABLE_BORDER)
            drawLine(left, rowBottom, left + availWidth, rowBottom, 0.5f, COLOR_TABLE_BORDER)
            for (c in 0..colCount) {
                val x = left + c * colWidth
                drawLine(x, y, x, rowBottom, 0.5f, COLOR_TABLE_BORDER)
            }
            for ((col, cellLines) in wrappedCells.withIndex()) {
                val cellLeft = left + col * colWidth
                val align = alignments.getOrNull(col) ?: PdfTableAlign.LEFT
                var lineTop = y - cellPadY
                for (text in cellLines) {
                    val w = measureWidth(text, font, fontSize)
                    val x = when (align) {
                        PdfTableAlign.LEFT -> cellLeft + cellPadX
                        PdfTableAlign.CENTER -> maxOf(cellLeft + cellPadX, cellLeft + (colWidth - w) / 2f)
                        PdfTableAlign.RIGHT -> maxOf(cellLeft + cellPadX, cellLeft + colWidth - cellPadX - w)
                    }
                    drawText(text, x, lineTop - fontSize * 0.85f, font, fontSize, COLOR_DARK)
                    lineTop -= lineHeight
                }
            }
            advanceY(rowHeight)
        }

        val (headerWrapped, headerHeight) = prepareRow(headers, PdfFont.BOLD)
        if (y - headerHeight < BOTTOM_MARGIN) newPage()
        drawRow(headerWrapped, headerHeight, PdfFont.BOLD, COLOR_TABLE_HEADER_BG)

        for ((rowIdx, row) in rows.withIndex()) {
            val (rowWrapped, rowHeight) = prepareRow(row, PdfFont.REGULAR)
            if (y - rowHeight < BOTTOM_MARGIN) {
                newPage()
                if (y - headerHeight >= BOTTOM_MARGIN) {
                    drawRow(headerWrapped, headerHeight, PdfFont.BOLD, COLOR_TABLE_HEADER_BG)
                }
            }
            val bg = if (rowIdx % 2 == 1) COLOR_TABLE_ALT_BG else null
            drawRow(rowWrapped, rowHeight, PdfFont.REGULAR, bg)
        }
        addGap(8f)
    }

    private fun renderHorizontalRule(left: Float, availWidth: Float) {
        ensureLineSpace(14f)
        val lineY = y - 6f
        drawLine(left, lineY, left + availWidth, lineY, 1.2f, COLOR_HR)
        advanceY(14f)
    }

    private fun renderParagraph(
        text: String,
        left: Float,
        availWidth: Float,
        font: PdfFont,
        fontSize: Float,
        lineHeight: Float,
        color: Rgb,
    ) {
        val norm = normalizeForPdf(text, encoder)
        val wrapped = wrapText(norm, font, fontSize, availWidth)
        if (wrapped.isEmpty() || (wrapped.size == 1 && wrapped[0].isEmpty())) return
        for (line in wrapped) {
            ensureLineSpace(lineHeight)
            drawText(line, left, y - fontSize * 0.85f, font, fontSize, color)
            advanceY(lineHeight)
        }
        addGap(8f)
    }

    fun addFooters(title: String) {
        val baseTitle = normalizeForPdf(title.replace('\r', ' ').replace('\n', ' ').trim(), encoder)
        val total = pages.size
        for (i in pages.indices) {
            val suffix = normalizeForPdf(" - Página ${i + 1} de $total", encoder)
            val suffixWidth = measureWidth(suffix, PdfFont.REGULAR, 9f)
            val maxTitleWidth = (CONTENT_WIDTH - suffixWidth).coerceAtLeast(0f)
            val fittedTitle = truncateToWidth(baseTitle, PdfFont.REGULAR, 9f, maxTitleWidth)
            val footer = fittedTitle + suffix
            val width = measureWidth(footer, PdfFont.REGULAR, 9f)
            val x = ((PAGE_WIDTH - width) / 2f).coerceAtLeast(MARGIN_LEFT)
            val literal = toPdfLiteral(footer, encoder)
            pages[i].append("q\n")
            pages[i].append(COLOR_FOOTER.rg()).append('\n')
            pages[i].append("BT\n/F1 9 Tf\n1 0 0 1 ")
            pages[i].append(x.fmtCoord()).append(' ').append(FOOTER_Y.fmtCoord()).append(" Tm\n")
            pages[i].append(literal).append(" Tj\nET\nQ\n")
        }
    }

    fun toByteArray(title: String): ByteArray {
        val totalObjects = 7 + pages.size * 2
        val offsets = IntArray(totalObjects + 1)
        val out = ByteArrayOutputStream()

        fun writeAscii(s: String) {
            out.write(s.toByteArray(Charsets.US_ASCII))
        }

        fun beginObject(id: Int) {
            offsets[id] = out.size()
            writeAscii("$id 0 obj\n")
        }

        fun endObject() {
            writeAscii("endobj\n")
        }

        writeAscii("%PDF-1.4\n")

        beginObject(1)
        writeAscii("<< /Type /Catalog /Pages 2 0 R >>\n")
        endObject()

        beginObject(2)
        val kids = (0 until pages.size).joinToString(" ") { "${9 + it * 2} 0 R" }
        writeAscii("<< /Type /Pages /Kids [$kids] /Count ${pages.size} >>\n")
        endObject()

        val fonts = PdfFont.entries
        for (idx in fonts.indices) {
            beginObject(3 + idx)
            writeAscii("<< /Type /Font /Subtype /Type1 /BaseFont /${fonts[idx].baseFont} /Encoding /WinAnsiEncoding >>\n")
            endObject()
        }

        val safeTitle = normalizeForPdf(title.replace('\r', ' ').replace('\n', ' ').trim(), encoder)
        beginObject(7)
        writeAscii("<< /Title ${toPdfLiteral(safeTitle, encoder)} /Producer (JotMD) >>\n")
        endObject()

        for (i in pages.indices) {
            val contentId = 8 + i * 2
            val pageId = 9 + i * 2
            val streamBytes = pages[i].toString().toByteArray(Charsets.ISO_8859_1)

            beginObject(contentId)
            writeAscii("<< /Length ${streamBytes.size} >>\nstream\n")
            out.write(streamBytes)
            writeAscii("\nendstream\n")
            endObject()

            beginObject(pageId)
            writeAscii(
                "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 595 842] " +
                    "/Resources << /Font << /F1 3 0 R /F2 4 0 R /F3 5 0 R /F4 6 0 R >> >> " +
                    "/Contents $contentId 0 R >>\n",
            )
            endObject()
        }

        val xrefOffset = out.size()
        writeAscii("xref\n0 ${totalObjects + 1}\n")
        writeAscii("0000000000 65535 f \n")
        for (id in 1..totalObjects) {
            writeAscii(offsets[id].toString().padStart(10, '0') + " 00000 n \n")
        }
        writeAscii("trailer\n<< /Size ${totalObjects + 1} /Root 1 0 R /Info 7 0 R >>\n")
        writeAscii("startxref\n$xrefOffset\n%%EOF\n")

        return out.toByteArray()
    }

    private fun ensureLineSpace(height: Float) {
        if (y - height < BOTTOM_MARGIN) newPage()
    }

    private fun newPage() {
        for (bar in activeBars) {
            flushBarSegment(bar)
        }
        pages += StringBuilder()
        pageIndex = pages.lastIndex
        y = START_Y
        for (bar in activeBars) {
            bar.topY = y
            bar.bottomY = y
        }
    }

    private fun advanceY(delta: Float) {
        y -= delta
        for (bar in activeBars) {
            if (y < bar.bottomY) bar.bottomY = y
        }
    }

    private fun addGap(delta: Float) {
        y = (y - delta).coerceAtLeast(BOTTOM_MARGIN)
    }

    private fun flushBarSegment(bar: ActiveBar) {
        if (bar.topY > bar.bottomY) {
            drawLine(bar.x, bar.topY, bar.x, bar.bottomY, bar.width, bar.color)
        }
    }

    private fun drawText(
        normalizedText: String,
        x: Float,
        baselineY: Float,
        font: PdfFont,
        fontSize: Float,
        color: Rgb,
    ) {
        if (normalizedText.isEmpty()) return
        val literal = toPdfLiteral(normalizedText, encoder)
        current.append("q\n")
        current.append(color.rg()).append('\n')
        current.append("BT\n")
        current.append(font.alias).append(' ').append(fontSize.fmtCoord()).append(" Tf\n")
        current.append("1 0 0 1 ").append(x.fmtCoord()).append(' ').append(baselineY.fmtCoord()).append(" Tm\n")
        current.append(literal).append(" Tj\n")
        current.append("ET\nQ\n")
    }

    private fun drawRect(x: Float, bottomY: Float, width: Float, height: Float, color: Rgb) {
        current.append("q\n")
        current.append(color.rg()).append('\n')
        current.append(x.fmtCoord()).append(' ').append(bottomY.fmtCoord()).append(' ')
        current.append(width.fmtCoord()).append(' ').append(height.fmtCoord()).append(" re\nf\nQ\n")
    }

    private fun drawLine(x1: Float, y1: Float, x2: Float, y2: Float, width: Float, color: Rgb) {
        current.append("q\n")
        current.append(color.stroke()).append('\n')
        current.append(width.fmtCoord()).append(" w\n0 J\n")
        current.append(x1.fmtCoord()).append(' ').append(y1.fmtCoord()).append(" m\n")
        current.append(x2.fmtCoord()).append(' ').append(y2.fmtCoord()).append(" l\nS\nQ\n")
    }
}

private fun Rgb.rg(): String = "${r.fmtColor()} ${g.fmtColor()} ${b.fmtColor()} rg"
private fun Rgb.stroke(): String = "${r.fmtColor()} ${g.fmtColor()} ${b.fmtColor()} RG"

private fun Float.fmtCoord(): String = String.format(Locale.US, "%.2f", this)

private fun Float.fmtColor(): String {
    val s = String.format(Locale.US, "%.3f", this)
    var end = s.length
    while (end > 3 && s[end - 1] == '0' && s[end - 2] != '.') end--
    return s.substring(0, end)
}

/**
 * Normaliza caracteres para WinAnsi (`windows-1252`), substituindo checkboxes
 * e mapeando code points não representáveis para `'?'`.
 */
private fun normalizeForPdf(text: String, encoder: CharsetEncoder): String {
    if (text.isEmpty()) return ""
    val sb = StringBuilder(text.length)
    var i = 0
    while (i < text.length) {
        val cp = Character.codePointAt(text, i)
        val count = Character.charCount(cp)
        when (cp) {
            '\r'.code -> Unit
            '\t'.code -> sb.append("    ")
            '\n'.code -> sb.append('\n')
            0x2610 -> sb.append("[ ]") // ☐
            0x2611 -> sb.append("[x]") // ☑
            0x2022, 0x2013, 0x2014 -> sb.append(Char(cp)) // •, –, —
            in 0x00..0x1F -> sb.append(' ')
            in 0x20..0x7E, in 0xA0..0xFF -> sb.append(Char(cp))
            0x7F -> sb.append(' ')
            else -> {
                val slice = text.substring(i, i + count)
                if (encoder.canEncode(slice)) sb.append(slice) else sb.append('?')
            }
        }
        i += count
    }
    return sb.toString()
}

private fun toPdfLiteral(normalized: String, encoder: CharsetEncoder): String {
    val bytes = if (normalized.isEmpty()) {
        ByteArray(0)
    } else {
        encoder.reset()
        val buf = encoder.encode(CharBuffer.wrap(normalized))
        ByteArray(buf.remaining()).also { buf.get(it) }
    }
    val sb = StringBuilder(bytes.size + 8)
    sb.append('(')
    for (b in bytes) {
        val v = b.toInt() and 0xFF
        when (v) {
            '\\'.code -> sb.append("\\\\")
            '('.code -> sb.append("\\(")
            ')'.code -> sb.append("\\)")
            '\n'.code -> sb.append("\\n")
            '\r'.code -> sb.append("\\r")
            '\t'.code -> sb.append("\\t")
            in 0x20..0x7E -> sb.append(Char(v))
            else -> {
                sb.append('\\')
                sb.append(Char('0'.code + ((v ushr 6) and 7)))
                sb.append(Char('0'.code + ((v ushr 3) and 7)))
                sb.append(Char('0'.code + (v and 7)))
            }
        }
    }
    sb.append(')')
    return sb.toString()
}

private fun measureWidth(text: String, font: PdfFont, fontSize: Float): Float {
    var total = 0f
    for (c in text) total += charWidth(c, font, fontSize)
    return total
}

private fun charWidth(c: Char, font: PdfFont, fontSize: Float): Float {
    if (font == PdfFont.MONO) return 0.60f * fontSize
    val factor = when (c) {
        ' ' -> 0.28f
        'i', 'l', 'j', 't', 'f', 'r', 'I', '.', ',', ':', ';', '!', '|', '\'', '"', '(', ')', '[', ']', '/', '\\', '-' -> 0.34f
        'M', 'W', '@', '%', '&', '\u2014' -> 0.86f
        'm', 'w' -> 0.78f
        else -> if (c.isUpperCase()) 0.68f else 0.54f
    }
    val boldBoost = if (font == PdfFont.BOLD) 1.06f else 1.0f
    return factor * boldBoost * fontSize
}

private fun truncateToWidth(text: String, font: PdfFont, fontSize: Float, maxWidth: Float): String {
    if (measureWidth(text, font, fontSize) <= maxWidth) return text
    if (maxWidth <= 0f) return ""
    val ellipsis = "..."
    val ellipsisWidth = measureWidth(ellipsis, font, fontSize)
    if (ellipsisWidth >= maxWidth) return ""
    var w = 0f
    var end = 0
    while (end < text.length) {
        val cw = charWidth(text[end], font, fontSize)
        if (w + cw + ellipsisWidth > maxWidth) break
        w += cw
        end++
    }
    return text.substring(0, end) + ellipsis
}

private fun wrapText(
    normalized: String,
    font: PdfFont,
    fontSize: Float,
    maxWidth: Float,
    preserveIndent: Boolean = false,
): List<String> {
    if (normalized.isEmpty()) return emptyList()
    val out = mutableListOf<String>()
    for (rawLine in normalized.split('\n')) {
        if (rawLine.isEmpty()) {
            out += ""
            continue
        }
        if (preserveIndent) {
            wrapRawLine(rawLine, font, fontSize, maxWidth, out)
        } else {
            wrapProseLine(rawLine, font, fontSize, maxWidth, out)
        }
    }
    return out
}

private fun wrapRawLine(
    line: String,
    font: PdfFont,
    fontSize: Float,
    maxWidth: Float,
    out: MutableList<String>,
) {
    var start = 0
    while (start < line.length) {
        var w = 0f
        var end = start
        var lastSpace = -1
        while (end < line.length) {
            val cw = charWidth(line[end], font, fontSize)
            if (end > start && w + cw > maxWidth) break
            if (line[end] == ' ') lastSpace = end
            w += cw
            end++
        }
        if (end >= line.length) {
            out += line.substring(start)
            break
        }
        if (lastSpace > start && line.substring(start, lastSpace).any { it != ' ' }) {
            out += line.substring(start, lastSpace)
            start = lastSpace + 1
        } else {
            out += line.substring(start, end)
            start = end
        }
    }
}

private fun wrapProseLine(
    line: String,
    font: PdfFont,
    fontSize: Float,
    maxWidth: Float,
    out: MutableList<String>,
) {
    val words = line.trim().split(Regex(" +")).filter { it.isNotEmpty() }
    if (words.isEmpty()) {
        out += ""
        return
    }
    val current = StringBuilder()
    var currentWidth = 0f
    val spaceWidth = charWidth(' ', font, fontSize)

    fun flushWordChunks(word: String) {
        var i = 0
        while (i < word.length) {
            var w = 0f
            var end = i
            while (end < word.length) {
                val cw = charWidth(word[end], font, fontSize)
                if (end > i && w + cw > maxWidth) break
                w += cw
                end++
            }
            if (end == word.length) {
                current.append(word, i, end)
                currentWidth = w
                break
            }
            out += word.substring(i, end)
            i = end
        }
    }

    for (word in words) {
        val wordWidth = measureWidth(word, font, fontSize)
        if (current.isEmpty()) {
            if (wordWidth <= maxWidth) {
                current.append(word)
                currentWidth = wordWidth
            } else {
                flushWordChunks(word)
            }
        } else if (currentWidth + spaceWidth + wordWidth <= maxWidth) {
            current.append(' ').append(word)
            currentWidth += spaceWidth + wordWidth
        } else {
            out += current.toString()
            current.setLength(0)
            currentWidth = 0f
            if (wordWidth <= maxWidth) {
                current.append(word)
                currentWidth = wordWidth
            } else {
                flushWordChunks(word)
            }
        }
    }
    if (current.isNotEmpty()) out += current.toString()
}

// --- Parsing auxiliar alinhado a HtmlExport.kt ---

private val QUOTE_LINE_PREFIX = Regex("""^\s*>\s?""")
private val HIGHLIGHT_REGEX = Regex("""==(?![=\s])([^\r\n]*?)(?<![=\s])==(?!=)""")

private val HEADING_TYPES = setOf(
    MarkdownElementTypes.ATX_1,
    MarkdownElementTypes.ATX_2,
    MarkdownElementTypes.ATX_3,
    MarkdownElementTypes.ATX_4,
    MarkdownElementTypes.ATX_5,
    MarkdownElementTypes.ATX_6,
    MarkdownElementTypes.SETEXT_1,
    MarkdownElementTypes.SETEXT_2,
)

private val SKIP_TYPES = setOf(
    MarkdownElementTypes.CODE_SPAN,
    MarkdownElementTypes.CODE_FENCE,
    MarkdownElementTypes.CODE_BLOCK,
    MarkdownElementTypes.LINK_DESTINATION,
    MarkdownElementTypes.AUTOLINK,
    GFMTokenTypes.GFM_AUTOLINK,
)

private val INLINE_ELEMENT_TYPES = setOf(
    MarkdownElementTypes.STRONG,
    MarkdownElementTypes.EMPH,
    GFMElementTypes.STRIKETHROUGH,
    MarkdownElementTypes.CODE_SPAN,
    MarkdownElementTypes.INLINE_LINK,
    MarkdownElementTypes.FULL_REFERENCE_LINK,
    MarkdownElementTypes.SHORT_REFERENCE_LINK,
    MarkdownElementTypes.IMAGE,
)

private val ALWAYS_DROP = setOf(
    MarkdownTokenTypes.ATX_HEADER,
    MarkdownTokenTypes.SETEXT_1,
    MarkdownTokenTypes.SETEXT_2,
    MarkdownTokenTypes.BLOCK_QUOTE,
    MarkdownTokenTypes.LIST_BULLET,
    MarkdownTokenTypes.LIST_NUMBER,
    MarkdownTokenTypes.CODE_FENCE_START,
    MarkdownTokenTypes.CODE_FENCE_END,
    MarkdownTokenTypes.FENCE_LANG,
    GFMTokenTypes.CHECK_BOX,
    GFMTokenTypes.TABLE_SEPARATOR,
)

private val EMPH_MARKER = setOf(MarkdownTokenTypes.EMPH)
private val TILDE_MARKER = setOf(GFMTokenTypes.TILDE)
private val BACKTICK_MARKER = setOf(MarkdownTokenTypes.BACKTICK)
private val BRACKET_MARKERS = setOf(MarkdownTokenTypes.LBRACKET, MarkdownTokenTypes.RBRACKET)

private fun fallbackHeadingLevel(source: String): Int {
    val hashes = source.trimStart().takeWhile { it == '#' }.length
    if (hashes in 1..6) return hashes
    return if (source.lineSequence().drop(1).firstOrNull()?.startsWith("=") == true) 1 else 2
}

private fun splitTableCells(line: String): List<String> =
    line.trim().removePrefix("|").removeSuffix("|").split('|').map { it.trim() }

private fun padCells(cells: List<String>, size: Int): List<String> =
    if (cells.size >= size) cells.take(size) else cells + List(size - cells.size) { "" }

private fun cleanInline(source: String): String {
    val root = astOf(source)
    return buildString {
        appendInlineChildren(root, PdfInlineCtx.of(source, root), emptySet(), softBreakAsSpace = true)
    }.trim()
}

private data class TextSpecial(
    val range: IntRange,
    val text: String,
)

private class PdfInlineCtx private constructor(
    val src: String,
    val specials: List<TextSpecial>,
) {
    fun coversNode(node: ASTNode): Boolean {
        if (node.startOffset >= node.endOffset) return false
        val last = node.endOffset - 1
        return specials.any { node.startOffset >= it.range.first && last <= it.range.last }
    }

    companion object {
        fun of(src: String, root: ASTNode): PdfInlineCtx {
            val skips = buildList { collectRanges(root, SKIP_TYPES, this) }
            val nodeBounds = buildList { collectElementRanges(root, this) }

            val wikis = wikilinksIn(src).map { w ->
                val last = src.indexOf("]]", w.offset) + 1
                val dest = w.target + (w.anchor?.let { "#$it" } ?: "")
                val display = w.alias ?: dest
                TextSpecial(w.offset..last, display)
            }
            val masked = maskRanges(src, skips)
            val marks = findHighlights(src, masked, nodeBounds)

            val sorted = (wikis + marks).sortedBy { it.range.first }
            val kept = ArrayList<TextSpecial>()
            for (s in sorted) {
                if (kept.isEmpty() || s.range.first > kept.last().range.last) kept += s
            }
            return PdfInlineCtx(src, kept)
        }

        private fun collectRanges(node: ASTNode, types: Set<IElementType>, out: MutableList<IntRange>) {
            if (node.type in types) {
                if (node.startOffset < node.endOffset) out += node.startOffset until node.endOffset
                return
            }
            node.children.forEach { collectRanges(it, types, out) }
        }

        private fun collectElementRanges(node: ASTNode, out: MutableList<IntRange>) {
            if (node.type in INLINE_ELEMENT_TYPES && node.startOffset < node.endOffset) {
                out += node.startOffset until node.endOffset
            }
            node.children.forEach { collectElementRanges(it, out) }
        }

        private fun maskRanges(src: String, skips: List<IntRange>): String {
            if (skips.isEmpty()) return src
            val chars = src.toCharArray()
            for (r in skips) {
                val from = r.first.coerceAtLeast(0)
                val to = r.last.coerceAtMost(chars.lastIndex)
                for (i in from..to) {
                    if (chars[i] != '\n' && chars[i] != '\r') chars[i] = '_'
                }
            }
            return String(chars)
        }

        private fun findHighlights(
            src: String,
            masked: String,
            nodeBounds: List<IntRange>,
        ): List<TextSpecial> {
            val out = ArrayList<TextSpecial>()
            var pos = 0
            while (pos < masked.length) {
                val m = HIGHLIGHT_REGEX.find(masked, pos) ?: break
                val start = m.range.first
                if ((start > 0 && src[start - 1] == '\\') || crossesAnyBoundary(m.range, nodeBounds)) {
                    pos = start + 2
                    continue
                }
                val inner = src.substring(start + 2, m.range.last - 1)
                out += TextSpecial(m.range, cleanInline(inner))
                pos = m.range.last + 1
            }
            return out
        }

        private fun crossesAnyBoundary(range: IntRange, bounds: List<IntRange>): Boolean =
            bounds.any { b ->
                val startsBefore = range.first < b.first && range.last in b.first until b.last
                val endsAfter = range.first in (b.first + 1)..b.last && range.last > b.last
                startsBefore || endsAfter
            }
    }
}

private fun StringBuilder.appendInlineChildren(
    node: ASTNode,
    ctx: PdfInlineCtx,
    drop: Set<IElementType>,
    softBreakAsSpace: Boolean,
) {
    var suppressSpace = true
    for (child in node.children) {
        when {
            child.type in drop || child.type in ALWAYS_DROP -> suppressSpace = true

            child.type == MarkdownTokenTypes.HARD_LINE_BREAK -> {
                append('\n')
                suppressSpace = true
            }

            child.type == MarkdownTokenTypes.EOL -> {
                if (softBreakAsSpace) {
                    if (isNotEmpty() && last() != ' ' && last() != '\n') append(' ')
                } else {
                    append('\n')
                }
                suppressSpace = true
            }

            child.type == MarkdownTokenTypes.WHITE_SPACE && suppressSpace -> Unit

            else -> {
                appendInlineNode(child, ctx, softBreakAsSpace)
                suppressSpace = false
            }
        }
    }
}

private fun StringBuilder.appendInlineNode(node: ASTNode, ctx: PdfInlineCtx, softBreakAsSpace: Boolean) {
    if (ctx.coversNode(node)) {
        appendInlineSource(node.startOffset, node.endOffset, ctx)
        return
    }
    when (node.type) {
        MarkdownElementTypes.STRONG, MarkdownElementTypes.EMPH ->
            appendInlineChildren(node, ctx, EMPH_MARKER, softBreakAsSpace)

        GFMElementTypes.STRIKETHROUGH ->
            appendInlineChildren(node, ctx, TILDE_MARKER, softBreakAsSpace)

        MarkdownElementTypes.CODE_SPAN -> appendCodeSpanText(node, ctx)

        MarkdownElementTypes.INLINE_LINK,
        MarkdownElementTypes.FULL_REFERENCE_LINK,
        MarkdownElementTypes.SHORT_REFERENCE_LINK -> appendLinkText(node, ctx, softBreakAsSpace)

        MarkdownElementTypes.IMAGE -> appendImageText(node, ctx)

        MarkdownElementTypes.AUTOLINK, GFMTokenTypes.GFM_AUTOLINK ->
            append(node.getTextInNode(ctx.src).toString().trim('<', '>'))

        else ->
            if (node.children.isEmpty()) appendInlineSource(node.startOffset, node.endOffset, ctx)
            else appendInlineChildren(node, ctx, emptySet(), softBreakAsSpace)
    }
}

private fun StringBuilder.appendCodeSpanText(node: ASTNode, ctx: PdfInlineCtx) {
    val children = node.children
    val firstContent = children.indexOfFirst { it.type !in BACKTICK_MARKER }
    val lastContent = children.indexOfLast { it.type !in BACKTICK_MARKER }
    val raw = if (firstContent < 0) {
        ""
    } else {
        buildString {
            for (i in firstContent..lastContent) {
                val child = children[i]
                append(if (child.type == MarkdownTokenTypes.EOL) " " else child.getTextInNode(ctx.src))
            }
        }
    }
    val normalized = if (raw.length >= 2 && raw.startsWith(' ') && raw.endsWith(' ') && raw.any { it != ' ' }) {
        raw.substring(1, raw.length - 1)
    } else {
        raw
    }
    append(normalized)
}

private fun StringBuilder.appendLinkText(node: ASTNode, ctx: PdfInlineCtx, softBreakAsSpace: Boolean) {
    val label = node.pick(MarkdownElementTypes.LINK_TEXT) ?: node.pick(MarkdownElementTypes.LINK_LABEL)
    val url = node.pick(MarkdownElementTypes.LINK_DESTINATION)
        ?.getTextInNode(ctx.src)
        ?.toString()
        ?.trim()
        ?.removeSurrounding("<", ">")
    if (url == null) {
        appendInlineSource(node.startOffset, node.endOffset, ctx)
        return
    }
    val labelText = if (label != null) {
        buildString { appendInlineChildren(label, ctx, BRACKET_MARKERS, softBreakAsSpace) }.trim()
    } else {
        ""
    }
    if (labelText.isEmpty() || labelText == url) {
        append(url)
    } else {
        append(labelText).append(" (").append(url).append(')')
    }
}

private fun StringBuilder.appendImageText(node: ASTNode, ctx: PdfInlineCtx) {
    val linkNode = node.pick(MarkdownElementTypes.INLINE_LINK) ?: node
    val label = linkNode.pick(MarkdownElementTypes.LINK_TEXT)
    val src = linkNode.pick(MarkdownElementTypes.LINK_DESTINATION)
        ?.getTextInNode(ctx.src)
        ?.toString()
        ?.trim()
        ?.removeSurrounding("<", ">")
    if (src == null) {
        appendInlineSource(node.startOffset, node.endOffset, ctx)
        return
    }
    val alt = if (label != null) {
        buildString { appendInlineChildren(label, ctx, BRACKET_MARKERS, softBreakAsSpace = true) }.trim()
    } else {
        ""
    }
    if (alt.isNotEmpty()) {
        append("[Imagem: ").append(alt).append(']')
    } else {
        append("[Imagem]")
    }
}

private fun StringBuilder.appendInlineSource(start: Int, end: Int, ctx: PdfInlineCtx) {
    var i = start
    for (sp in ctx.specials) {
        if (sp.range.last < i || sp.range.first >= end) continue
        if (sp.range.first > i) append(ctx.src.substring(i, sp.range.first))
        if (sp.range.first >= start) append(sp.text)
        i = minOf(end, sp.range.last + 1)
    }
    if (i < end) append(ctx.src.substring(i, end))
}

private fun ASTNode.pick(type: IElementType): ASTNode? = children.firstOrNull { it.type == type }
