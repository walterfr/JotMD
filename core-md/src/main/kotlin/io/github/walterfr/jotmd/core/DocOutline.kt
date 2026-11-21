package io.github.walterfr.jotmd.core

import org.intellij.markdown.IElementType
import org.intellij.markdown.MarkdownElementTypes
import org.intellij.markdown.MarkdownTokenTypes
import org.intellij.markdown.ast.ASTNode
import org.intellij.markdown.ast.getTextInNode
import org.intellij.markdown.flavours.gfm.GFMElementTypes
import org.intellij.markdown.flavours.gfm.GFMTokenTypes
import java.text.Normalizer
import java.util.Locale

/** Entrada de índice (outline) extraída de um cabeçalho do documento. */
data class OutlineEntry(
    val blockIndex: Int,
    val blockId: BlockId,
    val level: Int,
    val title: String,
    val slug: String,
)

/**
 * Retorna `true` se [source] for um marcador de sumário (`[TOC]`, `{:toc}` ou
 * `[[_TOC_]]`, ignorando espaços nas pontas e maiúsculas/minúsculas).
 */
fun isTocMarker(source: String): Boolean {
    val trimmed = source.trim()
    return trimmed.equals("[TOC]", ignoreCase = true) ||
        trimmed.equals("{:toc}", ignoreCase = true) ||
        trimmed.equals("[[_TOC_]]", ignoreCase = true)
}

/**
 * Extrai a lista ordenada de cabeçalhos de [doc] com nível (`1..6`), título
 * limpo de sintaxe inline e `slug` único no documento.
 */
fun outlineOf(doc: DocState): List<OutlineEntry> {
    val usedSlugs = HashSet<String>()
    val entries = ArrayList<OutlineEntry>()
    for ((index, block) in doc.blocks.withIndex()) {
        if (block.type != BlockType.HEADING) continue
        val (level, title) = extractHeading(block.source)
        val slug = uniqueSlug(slugify(title), usedSlugs)
        entries += OutlineEntry(
            blockIndex = index,
            blockId = block.id,
            level = level,
            title = title,
            slug = slug,
        )
    }
    return entries
}

private val ATX_HEADING_TYPES = setOf(
    MarkdownElementTypes.ATX_1,
    MarkdownElementTypes.ATX_2,
    MarkdownElementTypes.ATX_3,
    MarkdownElementTypes.ATX_4,
    MarkdownElementTypes.ATX_5,
    MarkdownElementTypes.ATX_6,
)

private val SETEXT_HEADING_TYPES = setOf(
    MarkdownElementTypes.SETEXT_1,
    MarkdownElementTypes.SETEXT_2,
)

private val HEADING_ELEMENT_TYPES = ATX_HEADING_TYPES + SETEXT_HEADING_TYPES

private val ATX_LEADING_HASHES = Regex("""^[ \t]*#{1,6}(?:[ \t]+|$)""")
private val ATX_TRAILING_HASHES = Regex("""(?:^|[ \t]+)#+[ \t]*$""")
private val SETEXT_UNDERLINE = Regex("""^[ \t]{0,3}(=+|-+)[ \t]*$""")

private val OUTLINE_HIGHLIGHT_REGEX = Regex("""==(?![=\s])([^\r\n]*?)(?<![=\s])==(?!=)""")
private val OUTLINE_UNDERLINE_REGEX = Regex("""<u>([^\r\n]*?)</u>""", RegexOption.IGNORE_CASE)

private val OUTLINE_SKIP_TYPES = setOf(
    MarkdownElementTypes.CODE_SPAN,
    MarkdownElementTypes.CODE_FENCE,
    MarkdownElementTypes.CODE_BLOCK,
    MarkdownElementTypes.LINK_DESTINATION,
    MarkdownElementTypes.AUTOLINK,
    GFMTokenTypes.GFM_AUTOLINK,
)

private val OUTLINE_INLINE_ELEMENT_TYPES = setOf(
    MarkdownElementTypes.STRONG,
    MarkdownElementTypes.EMPH,
    GFMElementTypes.STRIKETHROUGH,
    MarkdownElementTypes.CODE_SPAN,
    MarkdownElementTypes.INLINE_LINK,
    MarkdownElementTypes.FULL_REFERENCE_LINK,
    MarkdownElementTypes.SHORT_REFERENCE_LINK,
    MarkdownElementTypes.IMAGE,
)

private val OUTLINE_ALWAYS_DROP = setOf(
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

private fun extractHeading(source: String): Pair<Int, String> {
    val root = astOf(source)
    val node = root.children.firstOrNull { it.type in HEADING_ELEMENT_TYPES }
    val level = headingLevelOf(node, source)

    val title = when {
        node?.type in SETEXT_HEADING_TYPES -> {
            val contentNode = node?.pick(MarkdownTokenTypes.SETEXT_CONTENT) ?: node ?: root
            extractPlainFromNode(contentNode, source, minOffset = 0).trim()
        }

        node?.type in ATX_HEADING_TYPES -> {
            val cleaned = stripAtxClosingHashes(source)
            val cleanRoot = if (cleaned == source) root else astOf(cleaned)
            val cleanNode = cleanRoot.children.firstOrNull { it.type in ATX_HEADING_TYPES }
            if (cleanNode != null) {
                val contentNode = cleanNode.pick(MarkdownTokenTypes.ATX_CONTENT) ?: cleanNode
                extractPlainFromNode(contentNode, cleaned, minOffset = 0).trim()
            } else {
                // Cabeçalho só com `#` finais (ex.: `### ###`) vira vazio após remover o fechamento.
                val raw = cleaned.replaceFirst(ATX_LEADING_HASHES, "")
                extractInlinePlain(raw).trim()
            }
        }

        else -> fallbackHeadingTitle(source)
    }
    return level to title
}

private fun headingLevelOf(node: ASTNode?, source: String): Int = when (node?.type) {
    MarkdownElementTypes.ATX_1, MarkdownElementTypes.SETEXT_1 -> 1
    MarkdownElementTypes.ATX_2, MarkdownElementTypes.SETEXT_2 -> 2
    MarkdownElementTypes.ATX_3 -> 3
    MarkdownElementTypes.ATX_4 -> 4
    MarkdownElementTypes.ATX_5 -> 5
    MarkdownElementTypes.ATX_6 -> 6
    else -> {
        val hashes = source.trimStart().takeWhile { it == '#' }.length
        if (hashes in 1..6) {
            hashes
        } else {
            val lastLine = source.lines().lastOrNull()?.trimStart().orEmpty()
            if (lastLine.startsWith("=")) 1 else 2
        }
    }
}

private fun stripAtxClosingHashes(source: String): String =
    source.replaceFirst(ATX_TRAILING_HASHES, "")

private fun fallbackHeadingTitle(source: String): String {
    val lines = source.lines()
    if (lines.size >= 2 && SETEXT_UNDERLINE.matches(lines.last())) {
        return extractInlinePlain(lines.dropLast(1).joinToString(" ")).trim()
    }
    val raw = stripAtxClosingHashes(source).replaceFirst(ATX_LEADING_HASHES, "")
    return extractInlinePlain(raw).trim()
}

private fun extractInlinePlain(raw: String): String {
    if (raw.isEmpty()) return ""
    // Prefixo `"x "` evita que `- item`, `1. item` ou `> citação` dentro de `==...==`/`<u>...</u>` virem bloco.
    val padded = "x $raw"
    val root = astOf(padded)
    return extractPlainFromNode(root, padded, minOffset = 2)
}

private fun extractPlainFromNode(node: ASTNode, src: String, minOffset: Int): String {
    val ctx = PlainCtx.of(src, node)
    return buildString {
        appendPlainChildren(node, ctx, emptySet(), minOffset)
    }
}

private data class PlainSpecial(
    val range: IntRange,
    val text: String,
)

private class PlainCtx private constructor(
    val src: String,
    val specials: List<PlainSpecial>,
) {
    fun coversNode(node: ASTNode): Boolean {
        if (node.startOffset >= node.endOffset) return false
        val last = node.endOffset - 1
        return specials.any { node.startOffset >= it.range.first && last <= it.range.last }
    }

    companion object {
        fun of(src: String, root: ASTNode): PlainCtx {
            val skips = buildList { collectRanges(root, OUTLINE_SKIP_TYPES, this) }
            val nodeBounds = buildList { collectElementRanges(root, this) }

            val wikis = wikilinksIn(src).map { w ->
                val last = src.indexOf("]]", w.offset) + 1
                val dest = w.target + (w.anchor?.let { "#$it" } ?: "")
                val display = w.alias?.let { extractInlinePlain(it) } ?: dest
                PlainSpecial(w.offset..last, display)
            }
            val masked = maskRanges(src, skips)
            val marks = findRegexSpecials(src, masked, nodeBounds, OUTLINE_HIGHLIGHT_REGEX, 2) {
                extractInlinePlain(it)
            }
            val underlines = findRegexSpecials(src, masked, nodeBounds, OUTLINE_UNDERLINE_REGEX, 3) {
                extractInlinePlain(it)
            }

            val sorted = (wikis + marks + underlines).sortedBy { it.range.first }
            val kept = ArrayList<PlainSpecial>()
            for (s in sorted) {
                if (kept.isEmpty() || s.range.first > kept.last().range.last) kept += s
            }
            return PlainCtx(src, kept)
        }

        private fun collectRanges(node: ASTNode, types: Set<IElementType>, out: MutableList<IntRange>) {
            if (node.type in types) {
                if (node.startOffset < node.endOffset) out += node.startOffset until node.endOffset
                return
            }
            node.children.forEach { collectRanges(it, types, out) }
        }

        private fun collectElementRanges(node: ASTNode, out: MutableList<IntRange>) {
            if (node.type in OUTLINE_INLINE_ELEMENT_TYPES && node.startOffset < node.endOffset) {
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
            transform: (String) -> String,
        ): List<PlainSpecial> {
            val out = ArrayList<PlainSpecial>()
            var pos = 0
            while (pos < masked.length) {
                val m = regex.find(masked, pos) ?: break
                val start = m.range.first
                if ((start > 0 && src[start - 1] == '\\') || crossesAnyBoundary(m.range, nodeBounds)) {
                    pos = start + retryAdvance
                    continue
                }
                val innerRange = m.groups[1]!!.range
                val inner = if (innerRange.isEmpty()) "" else src.substring(innerRange.first, innerRange.last + 1)
                out += PlainSpecial(m.range, transform(inner))
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

private fun StringBuilder.appendPlainNode(node: ASTNode, ctx: PlainCtx, minOffset: Int) {
    if (ctx.coversNode(node)) {
        appendPlainSource(maxOf(minOffset, node.startOffset), node.endOffset, ctx)
        return
    }
    when (node.type) {
        MarkdownElementTypes.STRONG,
        MarkdownElementTypes.EMPH -> appendPlainChildren(node, ctx, EMPH_MARKER, minOffset)

        GFMElementTypes.STRIKETHROUGH -> appendPlainChildren(node, ctx, TILDE_MARKER, minOffset)

        MarkdownElementTypes.CODE_SPAN -> appendPlainCodeSpan(node, ctx)

        MarkdownElementTypes.INLINE_LINK,
        MarkdownElementTypes.FULL_REFERENCE_LINK,
        MarkdownElementTypes.SHORT_REFERENCE_LINK -> appendPlainLink(node, ctx, minOffset)

        MarkdownElementTypes.IMAGE -> appendPlainImage(node, ctx, minOffset)

        MarkdownElementTypes.AUTOLINK, GFMTokenTypes.GFM_AUTOLINK -> {
            append(node.getTextInNode(ctx.src).toString().trim('<', '>'))
        }

        else ->
            if (node.children.isEmpty()) {
                appendPlainSource(maxOf(minOffset, node.startOffset), node.endOffset, ctx)
            } else {
                appendPlainChildren(node, ctx, emptySet(), minOffset)
            }
    }
}

private fun StringBuilder.appendPlainCodeSpan(node: ASTNode, ctx: PlainCtx) {
    val children = node.children
    val firstContent = children.indexOfFirst { it.type !in BACKTICK_MARKER }
    val lastContent = children.indexOfLast { it.type !in BACKTICK_MARKER }
    if (firstContent < 0) return
    val raw = buildString {
        for (i in firstContent..lastContent) {
            val child = children[i]
            append(if (child.type == MarkdownTokenTypes.EOL) " " else child.getTextInNode(ctx.src))
        }
    }
    val normalized = if (raw.length >= 2 && raw.startsWith(' ') && raw.endsWith(' ') && raw.any { it != ' ' }) {
        raw.substring(1, raw.length - 1)
    } else {
        raw
    }
    append(normalized)
}

private fun StringBuilder.appendPlainLink(node: ASTNode, ctx: PlainCtx, minOffset: Int) {
    val label = node.pick(MarkdownElementTypes.LINK_TEXT) ?: node.pick(MarkdownElementTypes.LINK_LABEL)
    val hasDestination = node.type == MarkdownElementTypes.INLINE_LINK ||
        node.pick(MarkdownElementTypes.LINK_DESTINATION) != null
    if (!hasDestination || label == null) {
        appendPlainSource(maxOf(minOffset, node.startOffset), node.endOffset, ctx)
        return
    }
    appendPlainChildren(label, ctx, BRACKET_MARKERS, minOffset)
}

private fun StringBuilder.appendPlainImage(node: ASTNode, ctx: PlainCtx, minOffset: Int) {
    val linkNode = node.pick(MarkdownElementTypes.INLINE_LINK) ?: node
    val label = linkNode.pick(MarkdownElementTypes.LINK_TEXT)
    if (label != null) {
        appendPlainChildren(label, ctx, BRACKET_MARKERS, minOffset)
    } else {
        appendPlainSource(maxOf(minOffset, node.startOffset), node.endOffset, ctx)
    }
}

private fun StringBuilder.appendPlainChildren(
    node: ASTNode,
    ctx: PlainCtx,
    drop: Set<IElementType>,
    minOffset: Int,
) {
    var suppressSpace = minOffset == 0
    for (child in node.children) {
        if (child.endOffset <= minOffset) continue
        when {
            child.type in drop || child.type in OUTLINE_ALWAYS_DROP -> suppressSpace = true

            child.type == MarkdownTokenTypes.HARD_LINE_BREAK ||
                child.type == MarkdownTokenTypes.EOL -> {
                append(' ')
                suppressSpace = true
            }

            child.type == MarkdownTokenTypes.WHITE_SPACE && suppressSpace -> Unit

            else -> {
                appendPlainNode(child, ctx, minOffset)
                suppressSpace = false
            }
        }
    }
}

private fun StringBuilder.appendPlainSource(start: Int, end: Int, ctx: PlainCtx) {
    var i = start
    for (sp in ctx.specials) {
        if (sp.range.last < i || sp.range.first >= end) continue
        if (sp.range.first > i) append(ctx.src, i, sp.range.first)
        if (sp.range.first >= start) append(sp.text)
        i = minOf(end, sp.range.last + 1)
    }
    if (i < end) append(ctx.src, i, end)
}

private fun ASTNode.pick(type: IElementType): ASTNode? = children.firstOrNull { it.type == type }

private fun slugify(title: String): String {
    val normalized = Normalizer.normalize(title, Normalizer.Form.NFC).lowercase(Locale.ROOT)
    val out = StringBuilder(normalized.length)
    var pendingDash = false
    for (ch in normalized) {
        if (ch.isLetter() || ch in '0'..'9') {
            if (pendingDash && out.isNotEmpty()) out.append('-')
            pendingDash = false
            out.append(ch)
        } else {
            pendingDash = true
        }
    }
    return if (out.isEmpty()) "section" else out.toString()
}

private fun uniqueSlug(base: String, usedSlugs: MutableSet<String>): String {
    if (usedSlugs.add(base)) return base
    var suffix = 1
    while (!usedSlugs.add("$base-$suffix")) {
        suffix++
    }
    return "$base-$suffix"
}
