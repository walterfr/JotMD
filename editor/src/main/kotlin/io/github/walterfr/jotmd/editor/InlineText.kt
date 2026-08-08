package io.github.walterfr.jotmd.editor

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import io.github.walterfr.jotmd.core.astOf
import org.intellij.markdown.IElementType
import org.intellij.markdown.MarkdownElementTypes
import org.intellij.markdown.MarkdownTokenTypes
import org.intellij.markdown.ast.ASTNode
import org.intellij.markdown.ast.getTextInNode
import org.intellij.markdown.flavours.gfm.GFMElementTypes
import org.intellij.markdown.flavours.gfm.GFMTokenTypes

/**
 * Markdown inline de [source] como [AnnotatedString], sem os marcadores.
 *
 * **Regra que sustenta tudo:** um token só é marcador pela posição que ocupa na
 * árvore, nunca pelo tipo. O parser tokeniza `(`, `)`, `!` e `*` sem par como
 * tipos de marcador mesmo em texto literal — descartar por tipo apagaria
 * pontuação do usuário em silêncio. Então cada elemento conhecido descarta os
 * próprios marcadores e todo o resto é emitido literal. Construção
 * desconhecida aparece como o texto cru dela; nunca some.
 */
fun inlineText(source: String): AnnotatedString {
    val built = buildAnnotatedString { appendChildren(astOf(source), source, emptySet()) }
    // O `======` do setext e afins são descartados depois da quebra de linha que
    // os precede, então sobra um "\n" pendurado no fim.
    val end = built.text.indexOfLast { it != '\n' } + 1
    return if (end == built.length) built else built.subSequence(0, end)
}

/**
 * Marcadores estruturais que nunca são conteúdo, onde quer que apareçam.
 *
 * Cuidado: token e elemento podem ter o mesmo nome e serem tipos diferentes.
 * `MarkdownTokenTypes.BLOCK_QUOTE` é o `>` de cada linha; `MarkdownElementTypes
 * .BLOCK_QUOTE` é a citação inteira. Trocar um pelo outro faz o `>` vazar para
 * a tela sem quebrar compilação.
 */
private val ALWAYS_DROP: Set<IElementType> = setOf(
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

private fun AnnotatedString.Builder.appendNode(node: ASTNode, src: String) {
    when (node.type) {
        MarkdownElementTypes.STRONG ->
            withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                appendChildren(node, src, EMPH_MARKER)
            }

        MarkdownElementTypes.EMPH ->
            withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                appendChildren(node, src, EMPH_MARKER)
            }

        GFMElementTypes.STRIKETHROUGH ->
            withStyle(SpanStyle(textDecoration = TextDecoration.LineThrough)) {
                appendChildren(node, src, TILDE_MARKER)
            }

        MarkdownElementTypes.CODE_SPAN ->
            withStyle(MdTokens.inlineCode) {
                appendChildren(node, src, BACKTICK_MARKER)
            }

        MarkdownElementTypes.INLINE_LINK,
        MarkdownElementTypes.FULL_REFERENCE_LINK,
        MarkdownElementTypes.SHORT_REFERENCE_LINK -> appendLink(node, src)

        // A imagem em si é F4. Por ora vale o texto alternativo, sem estilo de
        // link — melhor que sumir e melhor que fingir que é clicável.
        MarkdownElementTypes.IMAGE -> {
            val label = node.pick(MarkdownElementTypes.LINK_TEXT)
                ?: node.pick(MarkdownElementTypes.INLINE_LINK)?.pick(MarkdownElementTypes.LINK_TEXT)
            if (label != null) appendChildren(label, src, BRACKET_MARKERS) else appendLeaf(node, src)
        }

        MarkdownElementTypes.AUTOLINK, GFMTokenTypes.GFM_AUTOLINK -> {
            val url = node.getTextInNode(src).toString().trim('<', '>')
            withLink(urlLink(url)) { append(url) }
        }

        else ->
            if (node.children.isEmpty()) appendLeaf(node, src)
            else appendChildren(node, src, emptySet())
    }
}

private fun AnnotatedString.Builder.appendLink(node: ASTNode, src: String) {
    val label = node.pick(MarkdownElementTypes.LINK_TEXT)
        ?: node.pick(MarkdownElementTypes.LINK_LABEL)
    val url = node.pick(MarkdownElementTypes.LINK_DESTINATION)?.getTextInNode(src)?.toString()

    // ponytail: link de referência sai literal, com colchetes. Resolver `[ref]`
    // exige varrer as definições do documento inteiro, e aqui só existe o bloco.
    // Some junto o caso de `[b]` solto no meio do texto, que por CommonMark é
    // texto mesmo. Resolver de verdade em F5, junto com o índice do workspace.
    if (url == null) {
        appendLeaf(node, src)
        return
    }
    withLink(urlLink(url)) {
        if (label != null) appendChildren(label, src, BRACKET_MARKERS) else append(url)
    }
}

private fun urlLink(url: String) = LinkAnnotation.Url(
    url,
    TextLinkStyles(SpanStyle(color = MdTokens.link)),
)

/**
 * Percorre os filhos de [node] emitindo conteúdo e descartando [drop].
 *
 * `suppressSpace` come o espaço que vem logo depois de um marcador ou de uma
 * quebra de linha: é a indentação de continuação (`# `, `> `), não texto. Sem
 * isso todo heading nasce com um espaço na frente.
 */
internal fun AnnotatedString.Builder.appendChildren(
    node: ASTNode,
    src: String,
    drop: Set<IElementType>,
) {
    var suppressSpace = true
    for (child in node.children) {
        when {
            child.type in drop || child.type in ALWAYS_DROP -> suppressSpace = true

            child.type == MarkdownTokenTypes.EOL -> {
                append('\n')
                suppressSpace = true
            }

            child.type == MarkdownTokenTypes.WHITE_SPACE && suppressSpace -> Unit

            else -> {
                appendNode(child, src)
                suppressSpace = false
            }
        }
    }
}

private fun AnnotatedString.Builder.appendLeaf(node: ASTNode, src: String) {
    append(node.getTextInNode(src).toString())
}

internal fun ASTNode.pick(type: IElementType): ASTNode? = children.firstOrNull { it.type == type }
