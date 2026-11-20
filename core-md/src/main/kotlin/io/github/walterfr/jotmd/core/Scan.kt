package io.github.walterfr.jotmd.core

import org.intellij.markdown.MarkdownElementTypes
import org.intellij.markdown.ast.ASTNode
import org.intellij.markdown.flavours.gfm.GFMTokenTypes

/**
 * Texto pronto para varredura por regex: front matter apagado e intervalos
 * onde `[[..]]`/`#tag` não valem (código, destino de link, autolink).
 *
 * O front matter é trocado por espaços (quebras de linha mantidas) em vez de
 * cortado, para que todo offset continue valendo no texto original e para que
 * o parser não leia o `---` como régua/setext.
 */
internal class Scan(text: String, frontMatter: FrontMatter?) {
    val body: String = frontMatter?.let { fm ->
        buildString(text.length) {
            for (i in text.indices) append(if (i < fm.end && text[i] != '\n' && text[i] != '\r') ' ' else text[i])
        }
    } ?: text

    // O AST decide o que é código (cerca, indentado, code span); regex ingênuo erraria os três.
    private val skips: List<IntRange> = buildList { collect(astOf(body), this) }

    fun skipped(offset: Int): Boolean = skips.any { offset in it }

    private fun collect(node: ASTNode, out: MutableList<IntRange>) {
        if (node.type in SKIP_TYPES) {
            out += node.startOffset until node.endOffset
            return
        }
        node.children.forEach { collect(it, out) }
    }

    private companion object {
        val SKIP_TYPES = setOf(
            MarkdownElementTypes.CODE_SPAN,
            MarkdownElementTypes.CODE_FENCE,
            MarkdownElementTypes.CODE_BLOCK,
            MarkdownElementTypes.LINK_DESTINATION,
            MarkdownElementTypes.AUTOLINK,
            GFMTokenTypes.GFM_AUTOLINK,
        )
    }
}
