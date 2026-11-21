package io.github.walterfr.jotmd.core

import org.intellij.markdown.IElementType
import org.intellij.markdown.MarkdownElementTypes
import org.intellij.markdown.MarkdownTokenTypes
import org.intellij.markdown.ast.ASTNode
import org.intellij.markdown.ast.getTextInNode
import org.intellij.markdown.flavours.gfm.GFMElementTypes
import org.intellij.markdown.flavours.gfm.GFMTokenTypes

/**
 * Exporta [markdown] para um documento HTML autocontido compatível com o layout
 * do Typora (`body.typora-export`, `#write`), nos temas claro (`github`) ou
 * escuro (`night`).
 */
fun exportToHtml(
    markdown: String,
    title: String = "Documento",
    darkTheme: Boolean = false,
): String {
    val doc = parse(markdown)
    val outline = outlineOf(doc)
    val outlineByBlock = outline.associateBy { it.blockId }
    val body = renderBlocks(doc.blocks, outline, outlineByBlock)
    return buildString {
        append("<!doctype html>\n<html>\n<head>\n<meta charset=\"utf-8\" />\n")
        append("<title>").append(escapeHtml(title)).append("</title>\n")
        append("<style>\n").append(buildCss(darkTheme)).append("\n</style>\n")
        append("</head>\n")
        append("<body class=\"typora-export\">\n")
        append("<div class=\"typora-export-content\">\n")
        append("<div id=\"write\">\n")
        if (body.isNotEmpty()) append(body).append('\n')
        append("</div>\n</div>\n</body>\n</html>\n")
    }
}

private fun renderBlocks(
    blocks: List<Block>,
    outline: List<OutlineEntry> = emptyList(),
    outlineByBlock: Map<BlockId, OutlineEntry> = emptyMap(),
): String = blocks.mapNotNull { renderBlock(it, outline, outlineByBlock) }.joinToString("\n")

private fun renderBlock(
    block: Block,
    outline: List<OutlineEntry>,
    outlineByBlock: Map<BlockId, OutlineEntry>,
): String? = when (block.type) {
    BlockType.FRONT_MATTER -> renderFrontMatter(block.source)
    BlockType.HEADING -> renderHeading(block.source, outlineByBlock[block.id]?.slug)
    BlockType.BLOCKQUOTE -> renderBlockquote(block.source)
    BlockType.UNORDERED_LIST, BlockType.ORDERED_LIST -> renderList(block.source)
    BlockType.CODE_FENCE -> renderCodeFence(block.source)
    BlockType.CODE_INDENTED -> renderCodeIndented(block.source)
    BlockType.TABLE -> renderTable(block.source)
    BlockType.HORIZONTAL_RULE -> "<hr />"
    BlockType.LINK_DEFINITION -> null
    BlockType.PARAGRAPH ->
        if (isTocMarker(block.source)) renderToc(outline) else "<p>${renderInline(block.source)}</p>"
    BlockType.OTHER, BlockType.HTML -> "<p>${renderInline(block.source)}</p>"
}

private fun renderToc(outline: List<OutlineEntry>): String = buildString {
    append("<div class=\"md-toc\" role=\"navigation\"><p class=\"md-toc-content\">")
    if (outline.isNotEmpty()) append('\n')
    for (entry in outline) {
        append("<span class=\"md-toc-item md-toc-h").append(entry.level).append("\">")
        append("<a class=\"md-toc-inner\" href=\"#").append(escapeHtml(entry.slug)).append("\">")
        append(escapeHtml(entry.title))
        append("</a></span>\n")
    }
    append("</p></div>")
}

private fun renderFrontMatter(source: String): String {
    val yaml = source.lines().drop(1).dropLast(1).joinToString("\n")
    return "<pre class=\"md-meta-block\">${escapeHtml(yaml)}</pre>"
}

private fun renderHeading(source: String, slug: String?): String {
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
    val html = buildString {
        appendChildren(contentNode, InlineCtx.of(source, root), emptySet())
    }.trimEnd()
    val idAttr = if (slug != null) " id=\"${escapeHtml(slug)}\"" else ""
    return "<h$level$idAttr>$html</h$level>"
}

private fun fallbackHeadingLevel(source: String): Int {
    val hashes = source.trimStart().takeWhile { it == '#' }.length
    if (hashes in 1..6) return hashes
    return if (source.lineSequence().drop(1).firstOrNull()?.startsWith("=") == true) 1 else 2
}

private val QUOTE_LINE_PREFIX = Regex("""^\s*>\s?""")

private fun renderBlockquote(source: String): String {
    val alert = alertOf(source)
    if (alert != null) {
        val inner = if (alert.body.isEmpty()) "" else renderInnerMarkdown(alert.body)
        return "<div class=\"md-alert md-alert-${alert.kind.tag}\">" +
            "<p class=\"md-alert-title\">${alert.kind.label}</p>" +
            "$inner</div>"
    }
    val stripped = source.lines().joinToString("\n") { it.replaceFirst(QUOTE_LINE_PREFIX, "") }
    val inner = if (stripped == source) "<p>${renderInline(source)}</p>" else renderInnerMarkdown(stripped)
    return "<blockquote>$inner</blockquote>"
}

private fun renderInnerMarkdown(markdown: String): String {
    val blocks = parse(markdown, frontMatter = false).blocks
    if (blocks.isEmpty()) return ""
    return renderBlocks(blocks)
}

private fun renderList(source: String): String {
    val root = astOf(source)
    val ctx = InlineCtx.of(source, root)
    val listNode = root.children.firstOrNull {
        it.type == MarkdownElementTypes.UNORDERED_LIST || it.type == MarkdownElementTypes.ORDERED_LIST
    } ?: return "<p>${renderInline(source)}</p>"
    return buildString { appendListNode(listNode, ctx) }
}

private fun StringBuilder.appendListNode(node: ASTNode, ctx: InlineCtx) {
    val ordered = node.type == MarkdownElementTypes.ORDERED_LIST
    val startAttr = if (ordered) orderedStartAttr(node, ctx.src) else ""
    val tag = if (ordered) "ol" else "ul"
    append('<').append(tag).append(startAttr).append(">\n")
    for (child in node.children) {
        if (child.type == MarkdownElementTypes.LIST_ITEM) {
            appendListItem(child, ctx)
            append('\n')
        }
    }
    append("</").append(tag).append('>')
}

private fun orderedStartAttr(listNode: ASTNode, src: String): String {
    val firstItem = listNode.pick(MarkdownElementTypes.LIST_ITEM) ?: return ""
    val rawNumber = firstItem.pick(MarkdownTokenTypes.LIST_NUMBER)?.getTextInNode(src)?.toString()?.trim()
        ?: return ""
    val number = rawNumber.dropLast(1).toIntOrNull() ?: return ""
    return if (number == 1) "" else " start=\"$number\""
}

private fun StringBuilder.appendListItem(item: ASTNode, ctx: InlineCtx) {
    append("<li>")
    val checkBox = item.pick(GFMTokenTypes.CHECK_BOX)?.getTextInNode(ctx.src)?.toString()
    val checkHtml = when {
        checkBox == null -> ""
        checkBox.contains('x', ignoreCase = true) -> "<input type=\"checkbox\" disabled checked />"
        else -> "<input type=\"checkbox\" disabled />"
    }
    val contentParts = item.children.filter {
        it.type != MarkdownTokenTypes.LIST_BULLET &&
            it.type != MarkdownTokenTypes.LIST_NUMBER &&
            it.type != GFMTokenTypes.CHECK_BOX &&
            it.type != MarkdownTokenTypes.EOL &&
            it.type != MarkdownTokenTypes.WHITE_SPACE
    }
    val blockNonListCount = contentParts.count {
        it.type != MarkdownElementTypes.UNORDERED_LIST && it.type != MarkdownElementTypes.ORDERED_LIST
    }
    var checkEmitted = checkHtml.isEmpty()
    for ((i, part) in contentParts.withIndex()) {
        when (part.type) {
            MarkdownElementTypes.UNORDERED_LIST, MarkdownElementTypes.ORDERED_LIST -> {
                if (i > 0) append('\n')
                appendListNode(part, ctx)
            }
            MarkdownElementTypes.PARAGRAPH -> {
                val inlineHtml = buildString { appendChildren(part, ctx, emptySet()) }
                val prefix = if (!checkEmitted) {
                    checkEmitted = true
                    if (inlineHtml.isNotEmpty()) "$checkHtml " else checkHtml
                } else ""
                if (blockNonListCount > 1) {
                    append("<p>").append(prefix).append(inlineHtml).append("</p>")
                } else {
                    append(prefix).append(inlineHtml)
                }
            }
            else -> {
                val sub = part.getTextInNode(ctx.src).toString()
                if (!checkEmitted) {
                    checkEmitted = true
                    append(checkHtml)
                    if (sub.isNotBlank()) append(' ')
                }
                append(renderInnerMarkdown(sub))
            }
        }
    }
    if (!checkEmitted) append(checkHtml)
    append("</li>")
}

private fun renderCodeFence(source: String): String {
    val node = astOf(source).children.firstOrNull { it.type == MarkdownElementTypes.CODE_FENCE }
    if (node == null) return "<pre class=\"md-fences\"><code>${escapeHtml(source)}</code></pre>"
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
    }.trim('\n')
    val classAttr = if (lang != null) " class=\"language-${escapeHtml(lang)}\"" else ""
    return "<pre class=\"md-fences\"><code$classAttr>${escapeHtml(code)}</code></pre>"
}

private fun renderCodeIndented(source: String): String {
    val code = source.lines().joinToString("\n") { line ->
        when {
            line.startsWith("    ") -> line.substring(4)
            line.startsWith("\t") -> line.substring(1)
            else -> line
        }
    }.trim('\n')
    return "<pre class=\"md-fences\"><code>${escapeHtml(code)}</code></pre>"
}

private enum class TableAlign(val css: String) {
    LEFT("left"),
    CENTER("center"),
    RIGHT("right"),
}

private fun renderTable(source: String): String {
    val root = astOf(source)
    val tableNode = root.children.firstOrNull { it.type == GFMElementTypes.TABLE }
        ?: return "<p>${renderInline(source)}</p>"
    val lines = source.lineSequence().filter { it.isNotBlank() }.toList()
    if (lines.size < 2) return "<p>${renderInline(source)}</p>"

    val headers = tableNode.pick(GFMElementTypes.HEADER)
        ?.children
        ?.filter { it.type == GFMTokenTypes.CELL }
        ?.map { it.getTextInNode(source).toString().trim() }
        ?: splitTableCells(lines[0])
    if (headers.isEmpty()) return "<p>${renderInline(source)}</p>"

    val alignments = splitTableCells(lines[1]).map { sep ->
        val left = sep.startsWith(":")
        val right = sep.endsWith(":")
        when {
            left && right -> TableAlign.CENTER
            left -> TableAlign.LEFT
            right -> TableAlign.RIGHT
            else -> null
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

    return buildString {
        append("<table>\n<thead>\n<tr>")
        for ((col, cell) in headers.withIndex()) {
            appendCell("th", cell, alignments.getOrNull(col))
        }
        append("</tr>\n</thead>\n<tbody>\n")
        for (row in rows) {
            append("<tr>")
            for ((col, cell) in row.withIndex()) {
                appendCell("td", cell, alignments.getOrNull(col))
            }
            append("</tr>\n")
        }
        append("</tbody>\n</table>")
    }
}

private fun StringBuilder.appendCell(tag: String, raw: String, align: TableAlign?) {
    val styleAttr = if (align != null) " style=\"text-align: ${align.css}\"" else ""
    append('<').append(tag).append(styleAttr).append('>')
    append(renderInline(raw))
    append("</").append(tag).append('>')
}

private fun splitTableCells(line: String): List<String> =
    line.trim().removePrefix("|").removeSuffix("|").split('|').map { it.trim() }

private fun padCells(cells: List<String>, size: Int): List<String> =
    if (cells.size >= size) cells.take(size) else cells + List(size - cells.size) { "" }

private fun renderInline(source: String): String {
    val root = astOf(source)
    return buildString {
        appendChildren(root, InlineCtx.of(source, root), emptySet())
    }.trimEnd('\n')
}

private data class HtmlSpecial(
    val range: IntRange,
    val html: String,
)

private val HIGHLIGHT_REGEX = Regex("""==(?![=\s])([^\r\n]*?)(?<![=\s])==(?!=)""")
private val UNDERLINE_REGEX = Regex("""<u>([^\r\n]+?)</u>""", RegexOption.IGNORE_CASE)

private class InlineCtx private constructor(
    val src: String,
    val specials: List<HtmlSpecial>,
) {
    fun coversNode(node: ASTNode): Boolean {
        if (node.startOffset >= node.endOffset) return false
        val last = node.endOffset - 1
        return specials.any { node.startOffset >= it.range.first && last <= it.range.last }
    }

    companion object {
        fun of(src: String, root: ASTNode): InlineCtx {
            val skips = buildList { collectRanges(root, SKIP_TYPES, this) }
            val nodeBounds = buildList { collectElementRanges(root, this) }

            val wikis = wikilinksIn(src).map { w ->
                val last = src.indexOf("]]", w.offset) + 1
                val dest = w.target + (w.anchor?.let { "#$it" } ?: "")
                val display = w.alias ?: dest
                HtmlSpecial(
                    range = w.offset..last,
                    html = "<a class=\"md-wikilink\" href=\"#${escapeHtml(dest)}\">${escapeHtml(display)}</a>",
                )
            }
            val tags = tagSpansIn(src).map { (range, _) ->
                HtmlSpecial(
                    range = range,
                    html = "<span class=\"md-tag\">${escapeHtml(src.substring(range))}</span>",
                )
            }
            val masked = maskRanges(src, skips)
            val marks = findRegexSpecials(src, masked, nodeBounds, HIGHLIGHT_REGEX, 2) { inner ->
                "<mark>${renderInline(inner)}</mark>"
            }
            val underlines = findRegexSpecials(src, masked, nodeBounds, UNDERLINE_REGEX, 3) { inner ->
                "<u>${renderInline(inner)}</u>"
            }

            val sorted = (wikis + tags + marks + underlines).sortedBy { it.range.first }
            val kept = ArrayList<HtmlSpecial>()
            for (s in sorted) {
                if (kept.isEmpty() || s.range.first > kept.last().range.last) kept += s
            }
            return InlineCtx(src, kept)
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

        private inline fun findRegexSpecials(
            src: String,
            masked: String,
            nodeBounds: List<IntRange>,
            regex: Regex,
            retryAdvance: Int,
            render: (String) -> String,
        ): List<HtmlSpecial> {
            val out = ArrayList<HtmlSpecial>()
            var pos = 0
            while (pos < masked.length) {
                val m = regex.find(masked, pos) ?: break
                val start = m.range.first
                if ((start > 0 && src[start - 1] == '\\') || crossesAnyBoundary(m.range, nodeBounds)) {
                    pos = start + retryAdvance
                    continue
                }
                val innerRange = m.groups[1]!!.range
                val inner = src.substring(innerRange.first, innerRange.last + 1)
                out += HtmlSpecial(m.range, render(inner))
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

private fun StringBuilder.appendNode(node: ASTNode, ctx: InlineCtx) {
    if (ctx.coversNode(node)) {
        appendSource(node.startOffset, node.endOffset, ctx)
        return
    }
    when (node.type) {
        MarkdownElementTypes.STRONG -> {
            append("<strong>")
            appendChildren(node, ctx, EMPH_MARKER)
            append("</strong>")
        }

        MarkdownElementTypes.EMPH -> {
            append("<em>")
            appendChildren(node, ctx, EMPH_MARKER)
            append("</em>")
        }

        GFMElementTypes.STRIKETHROUGH -> {
            append("<del>")
            appendChildren(node, ctx, TILDE_MARKER)
            append("</del>")
        }

        MarkdownElementTypes.CODE_SPAN -> appendCodeSpan(node, ctx)

        MarkdownElementTypes.INLINE_LINK,
        MarkdownElementTypes.FULL_REFERENCE_LINK,
        MarkdownElementTypes.SHORT_REFERENCE_LINK -> appendLink(node, ctx)

        MarkdownElementTypes.IMAGE -> appendImage(node, ctx)

        MarkdownElementTypes.AUTOLINK, GFMTokenTypes.GFM_AUTOLINK -> {
            val url = node.getTextInNode(ctx.src).toString().trim('<', '>')
            val escaped = escapeHtml(url)
            append("<a href=\"").append(escaped).append("\">").append(escaped).append("</a>")
        }

        else ->
            if (node.children.isEmpty()) appendSource(node.startOffset, node.endOffset, ctx)
            else appendChildren(node, ctx, emptySet())
    }
}

private fun StringBuilder.appendCodeSpan(node: ASTNode, ctx: InlineCtx) {
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
    append("<code>").append(escapeHtml(normalized)).append("</code>")
}

private fun StringBuilder.appendLink(node: ASTNode, ctx: InlineCtx) {
    val label = node.pick(MarkdownElementTypes.LINK_TEXT) ?: node.pick(MarkdownElementTypes.LINK_LABEL)
    val url = node.pick(MarkdownElementTypes.LINK_DESTINATION)
        ?.getTextInNode(ctx.src)
        ?.toString()
        ?.trim()
        ?.removeSurrounding("<", ">")
    if (url == null) {
        appendSource(node.startOffset, node.endOffset, ctx)
        return
    }
    append("<a href=\"").append(escapeHtml(url)).append("\">")
    if (label != null) appendChildren(label, ctx, BRACKET_MARKERS) else append(escapeHtml(url))
    append("</a>")
}

private fun StringBuilder.appendImage(node: ASTNode, ctx: InlineCtx) {
    val linkNode = node.pick(MarkdownElementTypes.INLINE_LINK) ?: node
    val label = linkNode.pick(MarkdownElementTypes.LINK_TEXT)
    val src = linkNode.pick(MarkdownElementTypes.LINK_DESTINATION)
        ?.getTextInNode(ctx.src)
        ?.toString()
        ?.trim()
        ?.removeSurrounding("<", ">")
    if (src == null) {
        appendSource(node.startOffset, node.endOffset, ctx)
        return
    }
    val alt = if (label != null) {
        buildString { appendPlainText(label, ctx, BRACKET_MARKERS) }
    } else {
        ""
    }
    append("<img src=\"").append(escapeHtml(src)).append("\" alt=\"").append(escapeHtml(alt)).append("\" />")
}

private fun StringBuilder.appendPlainText(node: ASTNode, ctx: InlineCtx, drop: Set<IElementType>) {
    var suppressSpace = true
    for (child in node.children) {
        when {
            child.type in drop || child.type in ALWAYS_DROP -> suppressSpace = true
            child.type == MarkdownTokenTypes.EOL -> {
                append(' ')
                suppressSpace = true
            }
            child.type == MarkdownTokenTypes.WHITE_SPACE && suppressSpace -> Unit
            child.type == MarkdownElementTypes.STRONG ||
                child.type == MarkdownElementTypes.EMPH -> appendPlainText(child, ctx, EMPH_MARKER)
            child.type == GFMElementTypes.STRIKETHROUGH -> appendPlainText(child, ctx, TILDE_MARKER)
            child.type == MarkdownElementTypes.CODE_SPAN -> appendPlainText(child, ctx, BACKTICK_MARKER)
            child.children.isEmpty() -> {
                append(child.getTextInNode(ctx.src))
                suppressSpace = false
            }
            else -> {
                appendPlainText(child, ctx, emptySet())
                suppressSpace = false
            }
        }
    }
}

private fun StringBuilder.appendChildren(
    node: ASTNode,
    ctx: InlineCtx,
    drop: Set<IElementType>,
) {
    var suppressSpace = true
    for (child in node.children) {
        when {
            child.type in drop || child.type in ALWAYS_DROP -> suppressSpace = true

            child.type == MarkdownTokenTypes.HARD_LINE_BREAK -> {
                append("<br />\n")
                suppressSpace = true
            }

            child.type == MarkdownTokenTypes.EOL -> {
                append('\n')
                suppressSpace = true
            }

            child.type == MarkdownTokenTypes.WHITE_SPACE && suppressSpace -> Unit

            else -> {
                appendNode(child, ctx)
                suppressSpace = false
            }
        }
    }
}

private fun StringBuilder.appendSource(start: Int, end: Int, ctx: InlineCtx) {
    var i = start
    for (sp in ctx.specials) {
        if (sp.range.last < i || sp.range.first >= end) continue
        if (sp.range.first > i) append(escapeHtml(ctx.src.substring(i, sp.range.first)))
        if (sp.range.first >= start) append(sp.html)
        i = minOf(end, sp.range.last + 1)
    }
    if (i < end) append(escapeHtml(ctx.src.substring(i, end)))
}

private fun ASTNode.pick(type: IElementType): ASTNode? = children.firstOrNull { it.type == type }

private fun escapeHtml(text: String): String = buildString(text.length) {
    for (c in text) {
        when (c) {
            '&' -> append("&amp;")
            '<' -> append("&lt;")
            '>' -> append("&gt;")
            '"' -> append("&quot;")
            else -> append(c)
        }
    }
}

private fun buildCss(darkTheme: Boolean): String {
    val bg = if (darkTheme) "#363B40" else "#ffffff"
    val text = if (darkTheme) "#b8bfc6" else "#333333"
    val heading = if (darkTheme) "#DEDEDE" else "#333333"
    val strong = if (darkTheme) "#DEDEDE" else "inherit"
    val muted = if (darkTheme) "#9DA2A6" else "#777777"
    val link = if (darkTheme) "#6dc1e7" else "#4183C4"
    val quoteBorder = if (darkTheme) "#474d54" else "#dfe2e5"
    val headingBorder = if (darkTheme) "#474d54" else "#eeeeee"
    val ruleColor = if (darkTheme) "#474d54" else "#e7e7e7"
    val inlineCodeBg = if (darkTheme) "#333333" else "#f3f4f4"
    val codeBorder = if (darkTheme) "#474d54" else "#e7eaed"
    val fenceBg = if (darkTheme) "#333333" else "#f8f8f8"
    val metaBg = if (darkTheme) "#2E3033" else "#f7f7f7"
    val tableBorder = if (darkTheme) "#474d54" else "#dfe2e5"
    val tableAltBg = if (darkTheme) "#2E3033" else "#f8f8f8"
    val markBg = if (darkTheme) "#D3D40E" else "#fff3a8"
    val markText = if (darkTheme) "#222" else "#333333"
    val noteColor = if (darkTheme) "#2f81f7" else "#0969da"
    val importantColor = if (darkTheme) "#a371f7" else "#8250df"
    val warningColor = if (darkTheme) "#d29922" else "#9a6700"
    val tipColor = "#1f883d"
    val cautionColor = "#cf222e"

    return """
        html { font-size: 14px; }
        body.typora-export { margin: 0; padding: 0; background: $bg; color: $text; font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Helvetica, Arial, sans-serif; line-height: 1.6; }
        #write { max-width: 860px; margin: 0 auto; padding: 30px; }
        h1, h2, h3, h4, h5, h6 { color: $heading; font-weight: bold; margin: 1rem 0; }
        h1 { font-size: 2.25em; line-height: 1.2; padding-bottom: 0.3em; border-bottom: 1px solid $headingBorder; }
        h2 { font-size: 1.75em; line-height: 1.225; padding-bottom: 0.3em; border-bottom: 1px solid $headingBorder; }
        h3 { font-size: 1.5em; line-height: 1.43; }
        h4 { font-size: 1.25em; }
        h5 { font-size: 1em; }
        h6 { font-size: 1em; color: $muted; }
        strong { color: $strong; }
        a { color: $link; text-decoration: none; }
        a:hover { text-decoration: underline; }
        p, blockquote, ul, ol, table { margin: 0.8em 0; }
        blockquote { border-left: 4px solid $quoteBorder; padding: 0 15px; color: $muted; }
        ul, ol { padding-left: 30px; }
        code { font-family: "Lucida Console", Consolas, Courier, monospace; font-size: 0.9em; background: $inlineCodeBg; border: 1px solid $codeBorder; border-radius: 3px; padding: 0 4px; }
        pre.md-fences { background: $fenceBg; border: 1px solid $codeBorder; border-radius: 3px; padding: 12px 16px; margin: 15px 0; overflow-x: auto; }
        pre.md-fences code { background: transparent; border: none; padding: 0; }
        pre.md-meta-block { font-family: "Lucida Console", Consolas, Courier, monospace; font-size: 0.85em; color: $muted; background: $metaBg; border-radius: 3px; padding: 16px; white-space: pre-wrap; }
        table { border-collapse: collapse; width: 100%; }
        th, td { border: 1px solid $tableBorder; padding: 6px 13px; text-align: left; }
        thead tr, tbody tr:nth-child(2n) { background: $tableAltBg; }
        hr { height: 2px; border: none; background: $ruleColor; margin: 16px 0; }
        mark { background: $markBg; color: $markText; padding: 0 2px; border-radius: 2px; }
        input[type="checkbox"] { margin-right: 6px; vertical-align: middle; }
        img { max-width: 100%; height: auto; }
        .md-tag { color: $link; }
        .md-toc { margin: 1em 0; padding: 0.4em 0; }
        .md-toc-content { margin: 0; font-size: 0.95em; }
        .md-toc-item { display: block; margin: 0.2em 0; }
        .md-toc-inner { display: inline-block; }
        .md-toc-h1 { margin-left: 0; font-weight: bold; }
        .md-toc-h2 { margin-left: 1.2em; }
        .md-toc-h3 { margin-left: 2.4em; }
        .md-toc-h4 { margin-left: 3.6em; }
        .md-toc-h5 { margin-left: 4.8em; }
        .md-toc-h6 { margin-left: 6em; }
        .md-alert { padding: 0 1em; margin: 0.8em 0; border-left: 0.25em solid $quoteBorder; }
        .md-alert-title { font-weight: bold; margin-bottom: 0.4em; }
        .md-alert-note { border-left-color: $noteColor; }
        .md-alert-note .md-alert-title { color: $noteColor; }
        .md-alert-tip { border-left-color: $tipColor; }
        .md-alert-tip .md-alert-title { color: $tipColor; }
        .md-alert-important { border-left-color: $importantColor; }
        .md-alert-important .md-alert-title { color: $importantColor; }
        .md-alert-warning { border-left-color: $warningColor; }
        .md-alert-warning .md-alert-title { color: $warningColor; }
        .md-alert-caution { border-left-color: $cautionColor; }
        .md-alert-caution .md-alert-title { color: $cautionColor; }
    """.trimIndent()
}
