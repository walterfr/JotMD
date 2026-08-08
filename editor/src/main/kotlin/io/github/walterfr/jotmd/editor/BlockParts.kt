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
)

fun listRows(source: String): List<ListRow> {
    val out = mutableListOf<ListRow>()
    collectRows(astOf(source), source, depth = 0, out = out)
    return out
}

private fun collectRows(node: ASTNode, src: String, depth: Int, out: MutableList<ListRow>) {
    for (child in node.children) {
        when (child.type) {
            MarkdownElementTypes.UNORDERED_LIST,
            MarkdownElementTypes.ORDERED_LIST -> collectRows(child, src, depth, out)

            MarkdownElementTypes.LIST_ITEM -> {
                val marker = (
                    child.pick(MarkdownTokenTypes.LIST_BULLET)
                        ?: child.pick(MarkdownTokenTypes.LIST_NUMBER)
                    )?.getTextInNode(src)?.toString()?.trim().orEmpty()

                val checked = child.pick(GFMTokenTypes.CHECK_BOX)
                    ?.getTextInNode(src)
                    ?.let { it.contains('x') || it.contains('X') }

                out += ListRow(
                    depth = depth,
                    marker = marker,
                    checked = checked,
                    content = buildAnnotatedString {
                        for (part in child.children) {
                            if (part.type == MarkdownElementTypes.PARAGRAPH) {
                                appendChildren(part, src, emptySet())
                            }
                        }
                    },
                )
                // Sublista é filha do item, então o nível vem daqui, não da indentação.
                for (part in child.children) {
                    if (part.type == MarkdownElementTypes.UNORDERED_LIST ||
                        part.type == MarkdownElementTypes.ORDERED_LIST
                    ) {
                        collectRows(part, src, depth + 1, out)
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
