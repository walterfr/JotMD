package io.github.walterfr.jotmd.core

import org.intellij.markdown.IElementType
import org.intellij.markdown.MarkdownElementTypes
import org.intellij.markdown.ast.ASTNode
import org.intellij.markdown.ast.getTextInNode
import org.intellij.markdown.flavours.gfm.GFMTokenTypes
import java.util.Locale

/** Definição de nota de rodapé (`[^id]: conteúdo`) encontrada no documento. */
data class FootnoteDef(
    val id: String,
    val normalizedId: String,
    val blockIndex: Int,
    val blockId: BlockId,
    val content: String,
)

/** Índice de definições de links de referência (`[ref]: url`) e notas de rodapé (`[^id]`). */
data class DocRefs(
    val linkDefinitions: Map<String, String>,
    val footnotes: Map<String, FootnoteDef>,
    val footnoteOrder: List<String>,
) {
    /** Resolve o destino de um link de referência pelo rótulo, ignorando caixa e espaços extras. */
    fun resolveLink(label: String): String? = linkDefinitions[normalizeRefLabel(label)]

    /** Retorna a definição da nota de rodapé para [id], se existir. */
    fun resolveFootnote(id: String): FootnoteDef? = footnotes[normalizeRefLabel(id)]

    /** Retorna o número sequencial (`1..N`) da nota de rodapé [id] no documento, ou `null`. */
    fun footnoteNumber(id: String): Int? {
        val idx = footnoteOrder.indexOf(normalizeRefLabel(id))
        return if (idx >= 0) idx + 1 else null
    }

    companion object {
        val EMPTY = DocRefs(emptyMap(), emptyMap(), emptyList())
    }
}

private val WHITESPACE_RUN = Regex("""\s+""")
private val FOOTNOTE_DEF_HEADER = Regex("""^\[\^([^\]\s]+)\]:\s*(.*)$""")
private val FOOTNOTE_CONT_INDENT = Regex("""^(?:\t| {1,4})""")
private val FOOTNOTE_REF_REGEX = Regex("""\[\^([^\]\s]+)\]""")
private val LINK_DEF_LINE_REGEX = Regex(
    """^[ \t]{0,3}\[(?!\^)([^\]\r\n]+)\]:[ \t]*(?:<([^>\r\n]+)>|(\S+))(?:[ \t]+(?:"[^"\r\n]*"|'[^'\r\n]*'|\([^)\r\n]*\)))?[ \t]*$""",
)
private val LINK_DEF_HEADER_ONLY_REGEX = Regex("""^[ \t]{0,3}\[(?!\^)([^\]\r\n]+)\]:[ \t]*$""")
private val LINK_DEF_NEXT_DEST_REGEX = Regex(
    """^[ \t]*(?:<([^>\r\n]+)>|(\S+))(?:[ \t]+(?:"[^"\r\n]*"|'[^'\r\n]*'|\([^)\r\n]*\)))?[ \t]*$""",
)

private val REF_SKIP_TYPES = setOf(
    MarkdownElementTypes.CODE_SPAN,
    MarkdownElementTypes.CODE_FENCE,
    MarkdownElementTypes.CODE_BLOCK,
    MarkdownElementTypes.LINK_DESTINATION,
    MarkdownElementTypes.AUTOLINK,
    GFMTokenTypes.GFM_AUTOLINK,
)

/** Normaliza rótulos de referência/nota: apara pontas, colapsa whitespace em `' '` e converte para minúsculas. */
fun normalizeRefLabel(raw: String): String =
    raw.trim().replace(WHITESPACE_RUN, " ").lowercase(Locale.ROOT)

/** Indica se [source] inicia uma definição de nota de rodapé (`[^id]: ...`). */
fun isFootnoteDefinitionBlock(source: String): Boolean =
    parseFootnoteDefinition(source) != null

/**
 * Extrai `(id, conteúdo)` da definição de nota de rodapé na 1ª linha de [source],
 * preservando linhas seguintes indentadas (`  `/`\t`) ou continuação do parágrafo.
 */
fun parseFootnoteDefinition(source: String): Pair<String, String>? =
    parseFootnoteDefinitions(source).firstOrNull()

internal fun parseFootnoteDefinitions(source: String): List<Pair<String, String>> {
    val lines = source.lines()
    val first = lines.firstOrNull() ?: return emptyList()
    if (!FOOTNOTE_DEF_HEADER.matches(first)) return emptyList()

    val defs = ArrayList<Pair<String, String>>()
    var currentId: String? = null
    val bodyLines = ArrayList<String>()

    fun flushCurrent() {
        val id = currentId ?: return
        defs += id to bodyLines.joinToString("\n").trimEnd()
        currentId = null
        bodyLines.clear()
    }

    for (line in lines) {
        val header = FOOTNOTE_DEF_HEADER.matchEntire(line)
        if (header != null) {
            flushCurrent()
            currentId = header.groupValues[1]
            val rest = header.groupValues[2].trimEnd()
            if (rest.isNotEmpty()) bodyLines += rest
        } else if (currentId != null) {
            val cleaned = line.replaceFirst(FOOTNOTE_CONT_INDENT, "")
            if (cleaned.isNotEmpty() || bodyLines.isNotEmpty()) {
                bodyLines += cleaned
            }
        }
    }
    flushCurrent()
    return defs
}

/**
 * Encontra referências de nota de rodapé `[^id]` em [source] que não sejam seguidas
 * imediatamente por `:` (definição) e não colidam com [skipRanges].
 */
fun footnoteRefSpansIn(
    source: String,
    skipRanges: List<IntRange> = emptyList(),
): List<Pair<IntRange, String>> {
    if (source.isEmpty()) return emptyList()
    val out = ArrayList<Pair<IntRange, String>>()
    for (m in FOOTNOTE_REF_REGEX.findAll(source)) {
        val start = m.range.first
        val endExclusive = m.range.last + 1
        if (source.getOrNull(endExclusive) == ':') continue
        if (isEscapedBracket(source, start)) continue
        if (overlapsAny(m.range, skipRanges)) continue
        out += m.range to m.groupValues[1]
    }
    return out
}

/**
 * Coleta todas as definições de links de referência e notas de rodapé de [doc],
 * ordenando `footnoteOrder` primeiro pela ordem de aparição no texto e depois pelas
 * definições remanescentes.
 */
fun docRefsOf(doc: DocState): DocRefs {
    val linkDefinitions = LinkedHashMap<String, String>()
    val footnotes = LinkedHashMap<String, FootnoteDef>()

    for ((blockIndex, block) in doc.blocks.withIndex()) {
        if (block.type != BlockType.LINK_DEFINITION &&
            block.type != BlockType.PARAGRAPH &&
            block.type != BlockType.OTHER
        ) {
            continue
        }

        val defs = parseFootnoteDefinitions(block.source)
        if (defs.isNotEmpty()) {
            for ((id, content) in defs) {
                val norm = normalizeRefLabel(id)
                if (norm.isNotEmpty() && norm !in footnotes) {
                    footnotes[norm] = FootnoteDef(
                        id = id,
                        normalizedId = norm,
                        blockIndex = blockIndex,
                        blockId = block.id,
                        content = content,
                    )
                }
            }
            continue
        }

        val ast = astOf(block.source)
        collectAstLinkDefinitions(ast, block.source, linkDefinitions)
        if (block.type == BlockType.PARAGRAPH || block.type == BlockType.OTHER) {
            collectParagraphLinkDefinitions(block.source, linkDefinitions)
        }
    }

    if (footnotes.isEmpty() && linkDefinitions.isEmpty()) return DocRefs.EMPTY

    val order = ArrayList<String>(footnotes.size)
    val seenOrder = HashSet<String>()
    if (footnotes.isNotEmpty()) {
        for (block in doc.blocks) {
            when (block.type) {
                BlockType.FRONT_MATTER,
                BlockType.CODE_FENCE,
                BlockType.CODE_INDENTED,
                BlockType.HORIZONTAL_RULE -> continue
                else -> Unit
            }
            val skips = collectSkipRanges(astOf(block.source))
            val mathSkips = inlineMathSpansIn(block.source, skips).map { it.first }
            for ((_, id) in footnoteRefSpansIn(block.source, skips + mathSkips)) {
                val norm = normalizeRefLabel(id)
                if (norm in footnotes && seenOrder.add(norm)) {
                    order += norm
                }
            }
        }
        for (norm in footnotes.keys) {
            if (seenOrder.add(norm)) order += norm
        }
    }

    return DocRefs(
        linkDefinitions = linkDefinitions,
        footnotes = footnotes,
        footnoteOrder = order,
    )
}

private fun collectAstLinkDefinitions(
    node: ASTNode,
    source: String,
    out: MutableMap<String, String>,
) {
    if (node.type == MarkdownElementTypes.LINK_DEFINITION) {
        val rawLabel = node.children.firstOrNull { it.type == MarkdownElementTypes.LINK_LABEL }
            ?.getTextInNode(source)
            ?.toString()
            ?.removeSurrounding("[", "]")
            ?: return
        if (!rawLabel.startsWith("^")) {
            val dest = node.children.firstOrNull { it.type == MarkdownElementTypes.LINK_DESTINATION }
                ?.getTextInNode(source)
                ?.toString()
                ?.trim()
                ?.removeSurrounding("<", ">")
            if (dest != null) {
                val norm = normalizeRefLabel(rawLabel)
                if (norm.isNotEmpty()) out.putIfAbsent(norm, dest)
            }
        }
        return
    }
    for (child in node.children) {
        collectAstLinkDefinitions(child, source, out)
    }
}

private fun collectParagraphLinkDefinitions(source: String, out: MutableMap<String, String>) {
    val lines = source.lines()
    var i = 0
    while (i < lines.size) {
        val line = lines[i]
        val m = LINK_DEF_LINE_REGEX.matchEntire(line)
        if (m != null) {
            val norm = normalizeRefLabel(m.groupValues[1])
            val dest = m.groupValues[2].ifEmpty { m.groupValues[3] }
            if (norm.isNotEmpty() && dest.isNotEmpty()) out.putIfAbsent(norm, dest)
            i++
            continue
        }
        val headerOnly = LINK_DEF_HEADER_ONLY_REGEX.matchEntire(line)
        val nextLine = lines.getOrNull(i + 1)
        if (headerOnly != null && nextLine != null) {
            val destMatch = LINK_DEF_NEXT_DEST_REGEX.matchEntire(nextLine)
            if (destMatch != null) {
                val norm = normalizeRefLabel(headerOnly.groupValues[1])
                val dest = destMatch.groupValues[1].ifEmpty { destMatch.groupValues[2] }
                if (norm.isNotEmpty() && dest.isNotEmpty()) out.putIfAbsent(norm, dest)
                i += 2
                continue
            }
        }
        i++
    }
}

private fun collectSkipRanges(root: ASTNode): List<IntRange> {
    val out = ArrayList<IntRange>()
    fun visit(node: ASTNode) {
        if (node.type in REF_SKIP_TYPES) {
            if (node.startOffset < node.endOffset) out += node.startOffset until node.endOffset
            return
        }
        for (child in node.children) visit(child)
    }
    visit(root)
    return out
}

private fun isEscapedBracket(text: String, index: Int): Boolean {
    var backslashes = 0
    var i = index - 1
    while (i >= 0 && text[i] == '\\') {
        backslashes++
        i--
    }
    return backslashes % 2 == 1
}

private fun overlapsAny(range: IntRange, skips: List<IntRange>): Boolean =
    skips.any { r -> !r.isEmpty() && range.first <= r.last && r.first <= range.last }
