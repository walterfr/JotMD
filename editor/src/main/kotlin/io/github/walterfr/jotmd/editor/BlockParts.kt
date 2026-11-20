package io.github.walterfr.jotmd.editor

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.buildAnnotatedString
import io.github.walterfr.jotmd.core.astOf
import org.intellij.markdown.MarkdownElementTypes
import org.intellij.markdown.MarkdownTokenTypes
import org.intellij.markdown.ast.ASTNode
import org.intellij.markdown.ast.getTextInNode
import org.intellij.markdown.flavours.gfm.GFMTokenTypes

/**
 * Funções puras que preparam um bloco para desenho. Ficam fora dos composables
 * de propósito: é aqui que mora a lógica que erra, e daqui ela é testável em JVM.
 */

/** Uma linha de lista já achatada. Lista aninhada vira mais linhas, não recursão na UI. */
data class ListRow(
    val depth: Int,
    val marker: String,
    val checked: Boolean?,
    val content: AnnotatedString,
    /** Offset do `[` do checkbox no source; `toggleTask` troca o caractere logo depois. */
    val checkOffset: Int? = null,
)

fun listRows(source: String): List<ListRow> {
    val out = mutableListOf<ListRow>()
    val ast = astOf(source)
    collectRows(ast, Ctx.of(source, ast), depth = 0, out = out)
    return out
}

private fun collectRows(node: ASTNode, ctx: Ctx, depth: Int, out: MutableList<ListRow>) {
    val src = ctx.src
    for (child in node.children) {
        when (child.type) {
            MarkdownElementTypes.UNORDERED_LIST,
            MarkdownElementTypes.ORDERED_LIST -> collectRows(child, ctx, depth, out)

            MarkdownElementTypes.LIST_ITEM -> {
                val marker = (
                    child.pick(MarkdownTokenTypes.LIST_BULLET)
                        ?: child.pick(MarkdownTokenTypes.LIST_NUMBER)
                    )?.getTextInNode(src)?.toString()?.trim().orEmpty()

                val box = child.pick(GFMTokenTypes.CHECK_BOX)
                val checked = box
                    ?.getTextInNode(src)
                    ?.let { it.contains('x') || it.contains('X') }

                out += ListRow(
                    depth = depth,
                    marker = marker,
                    checked = checked,
                    checkOffset = box?.startOffset,
                    content = buildAnnotatedString {
                        for (part in child.children) {
                            if (part.type == MarkdownElementTypes.PARAGRAPH) {
                                appendChildren(part, ctx, emptySet())
                            }
                        }
                    },
                )
                // Sublista é filha do item, então o nível vem daqui, não da indentação.
                for (part in child.children) {
                    if (part.type == MarkdownElementTypes.UNORDERED_LIST ||
                        part.type == MarkdownElementTypes.ORDERED_LIST
                    ) {
                        collectRows(part, ctx, depth + 1, out)
                    }
                }
            }

            else -> Unit
        }
    }
}

/** Nível 1..6. Setext não tem `#`: `===` é 1, `---` é 2. */
fun headingLevel(source: String): Int {
    val hashes = source.takeWhile { it == '#' }.length
    if (hashes in 1..6) return hashes
    return if (source.lineSequence().drop(1).firstOrNull()?.startsWith("=") == true) 1 else 2
}

/** Linguagem declarada na cerca, e o código sem as cercas. */
data class Fence(val language: String?, val code: String)

fun fenceOf(source: String): Fence {
    val node = astOf(source).children.firstOrNull { it.type == MarkdownElementTypes.CODE_FENCE }
        ?: return Fence(null, source)

    val code = buildString {
        for (child in node.children) {
            when (child.type) {
                MarkdownTokenTypes.CODE_FENCE_CONTENT -> append(child.getTextInNode(source))
                MarkdownTokenTypes.EOL -> append('\n')
                else -> Unit
            }
        }
    }
    return Fence(
        language = node.pick(MarkdownTokenTypes.FENCE_LANG)?.getTextInNode(source)?.toString()?.trim(),
        code = code.trim('\n'),
    )
}

/** Bloco indentado: tira os 4 espaços (ou o tab) de cada linha. */
fun dedentIndentedCode(source: String): String =
    source.lines().joinToString("\n") { line ->
        when {
            line.startsWith("    ") -> line.substring(4)
            line.startsWith("\t") -> line.substring(1)
            else -> line
        }
    }.trim('\n')

data class TableModel(
    val headers: List<String>,
    val alignments: List<TableAlignment?>,
    val rows: List<List<String>>,
)

enum class TableAlignment { LEFT, CENTER, RIGHT }

fun tableOf(source: String): TableModel? {
    val lines = source.lineSequence().filter { it.isNotBlank() }.toList()
    if (lines.size < 2) return null
    val header = tableCells(lines[0])
    val separator = tableCells(lines[1])
    if (header.isEmpty() || separator.size != header.size ||
        separator.any { !it.matches(Regex(":?-{1,}:?")) }
    ) return null
    val alignments = separator.map {
        when {
            it.startsWith(":") && it.endsWith(":") -> TableAlignment.CENTER
            it.startsWith(":") -> TableAlignment.LEFT
            it.endsWith(":") -> TableAlignment.RIGHT
            else -> null
        }
    }
    return TableModel(
        headers = header,
        alignments = alignments,
        rows = lines.drop(2).map { tableCells(it).padTo(header.size) },
    )
}

private fun tableCells(line: String): List<String> {
    val trimmed = line.trim()
    val content = trimmed.removePrefix("|").removeSuffix("|")
    return content.split('|').map(String::trim)
}

private fun List<String>.padTo(size: Int): List<String> =
    take(size).let { it + List(size - it.size) { "" } }

data class ImageRef(val alt: String, val destination: String)

private val IMAGE_REGEX = Regex("""!\[([^\]]*)\]\(([^)\s]+)(?:\s+["'][^)]*["'])?\)""")

fun imageRefs(source: String): List<ImageRef> =
    IMAGE_REGEX.findAll(source)
        .map { ImageRef(it.groupValues[1], it.groupValues[2]) }
        .toList()

/**
 * A imagem, se o parágrafo INTEIRO for só ela. Checar "começa com `![` e termina
 * com `)`" não basta: `![a](b.png) é o logo (2024)` passa nisso, e trocar o
 * parágrafo pelo preview apagaria o resto do texto da tela.
 */
fun soleImage(source: String): ImageRef? =
    IMAGE_REGEX.matchEntire(source.trim())?.let { ImageRef(it.groupValues[1], it.groupValues[2]) }

/** Tipos de GitHub Alert suportados em citações (`> [!NOTE]`, etc.). */
enum class QuoteAlertType(val label: String) {
    NOTE("Note"),
    TIP("Tip"),
    IMPORTANT("Important"),
    WARNING("Warning"),
    CAUTION("Caution"),
}

data class QuoteAlert(val type: QuoteAlertType, val bodySource: String)

private val ALERT_HEADER_REGEX = Regex(
    """^\s*>\s*\[!(NOTE|TIP|IMPORTANT|WARNING|CAUTION)]\s*(.*)$""",
    RegexOption.IGNORE_CASE,
)
private val QUOTE_LINE_PREFIX_REGEX = Regex("""^\s*>\s?""")

/**
 * Extrai alerta do GitHub da 1ª linha de uma citação, ou null para citação normal.
 */
fun quoteAlertOf(source: String): QuoteAlert? {
    val lines = source.lines()
    val first = lines.firstOrNull() ?: return null
    val match = ALERT_HEADER_REGEX.matchEntire(first) ?: return null
    val type = QuoteAlertType.valueOf(match.groupValues[1].uppercase())
    val firstRest = match.groupValues[2].trim()
    val bodyLines = buildList {
        if (firstRest.isNotBlank()) add(firstRest)
        for (line in lines.drop(1)) {
            add(QUOTE_LINE_PREFIX_REGEX.replaceFirst(line, ""))
        }
    }
        .dropWhile { it.isBlank() }
        .dropLastWhile { it.isBlank() }
    return QuoteAlert(type, bodyLines.joinToString("\n"))
}
