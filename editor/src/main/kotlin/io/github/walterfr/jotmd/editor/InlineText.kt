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
import io.github.walterfr.jotmd.core.tagSpansIn
import io.github.walterfr.jotmd.core.wikilinksIn
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
    val built = buildAnnotatedString { appendChildren(astOf(source), Ctx.of(source), emptySet()) }
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

/** Prefixos dos links internos; o app os intercepta via LocalUriHandler. */
const val WIKI_URI = "jotmd:wiki:"
const val TAG_URI = "jotmd:tag:"

/** Trecho do fonte que sai trocado: [display] no lugar de [range], com [link]. */
internal class Special(val range: IntRange, val display: String, val link: LinkAnnotation)

/**
 * Fonte + trechos especiais (wikilinks e tags) com offsets nesse mesmo fonte.
 *
 * Os especiais são resolvidos por posição no texto, não por nó do AST: o
 * parser fragmenta `[[nota]]` em `[`, um link de referência `[nota]` e `]`, e
 * o que importa é que nenhum desses pedaços seja emitido — todo texto do fonte
 * passa por [appendSource], que troca a faixa inteira pelo texto de exibição.
 */
internal class Ctx private constructor(val src: String, val specials: List<Special>) {
    companion object {
        fun of(src: String): Ctx {
            val wikis = wikilinksIn(src).map { w ->
                // A regex do wikilink proíbe `]` dentro: o primeiro `]]` fecha.
                val last = src.indexOf("]]", w.offset) + 1
                val display = w.alias ?: (w.target + (w.anchor?.let { "#$it" } ?: ""))
                Special(w.offset..last, display, internalLink(WIKI_URI + w.target))
            }
            val tags = tagSpansIn(src).map { (range, name) ->
                Special(range, src.substring(range), internalLink(TAG_URI + name))
            }
            // `[[#topo]]` também casa como tag; o que começa antes vence.
            val sorted = (wikis + tags).sortedBy { it.range.first }
            val kept = ArrayList<Special>()
            for (s in sorted) if (kept.isEmpty() || s.range.first > kept.last().range.last) kept += s
            return Ctx(src, kept)
        }

        private fun internalLink(uri: String) =
            LinkAnnotation.Url(uri, TextLinkStyles(SpanStyle(color = MdTokens.link)))
    }
}

private val EMPH_MARKER = setOf(MarkdownTokenTypes.EMPH)
private val TILDE_MARKER = setOf(GFMTokenTypes.TILDE)
private val BACKTICK_MARKER = setOf(MarkdownTokenTypes.BACKTICK)
private val BRACKET_MARKERS = setOf(MarkdownTokenTypes.LBRACKET, MarkdownTokenTypes.RBRACKET)

private fun AnnotatedString.Builder.appendNode(node: ASTNode, ctx: Ctx) {
    when (node.type) {
        MarkdownElementTypes.STRONG ->
            withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                appendChildren(node, ctx, EMPH_MARKER)
            }

        MarkdownElementTypes.EMPH ->
            withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                appendChildren(node, ctx, EMPH_MARKER)
            }

        GFMElementTypes.STRIKETHROUGH ->
            withStyle(SpanStyle(textDecoration = TextDecoration.LineThrough)) {
                appendChildren(node, ctx, TILDE_MARKER)
            }

        MarkdownElementTypes.CODE_SPAN ->
            withStyle(MdTokens.inlineCode) {
                appendChildren(node, ctx, BACKTICK_MARKER)
            }

        MarkdownElementTypes.INLINE_LINK,
        MarkdownElementTypes.FULL_REFERENCE_LINK,
        MarkdownElementTypes.SHORT_REFERENCE_LINK -> appendLink(node, ctx)

        // Imagens isoladas recebem preview no BlockRenderer; dentro de um
        // parágrafo misto, o texto alternativo mantém o fluxo inline legível.
        MarkdownElementTypes.IMAGE -> {
            val label = node.pick(MarkdownElementTypes.LINK_TEXT)
                ?: node.pick(MarkdownElementTypes.INLINE_LINK)?.pick(MarkdownElementTypes.LINK_TEXT)
            if (label != null) appendChildren(label, ctx, BRACKET_MARKERS) else appendLeaf(node, ctx)
        }

        MarkdownElementTypes.AUTOLINK, GFMTokenTypes.GFM_AUTOLINK -> {
            val url = node.getTextInNode(ctx.src).toString().trim('<', '>')
            withLink(urlLink(url)) { append(url) }
        }

        else ->
            if (node.children.isEmpty()) appendLeaf(node, ctx)
            else appendChildren(node, ctx, emptySet())
    }
}

private fun AnnotatedString.Builder.appendLink(node: ASTNode, ctx: Ctx) {
    val label = node.pick(MarkdownElementTypes.LINK_TEXT)
        ?: node.pick(MarkdownElementTypes.LINK_LABEL)
    val url = node.pick(MarkdownElementTypes.LINK_DESTINATION)?.getTextInNode(ctx.src)?.toString()

    // ponytail: link de referência sai literal, com colchetes. Resolver `[ref]`
    // exige varrer as definições do documento inteiro, e aqui só existe o bloco.
    // Some junto o caso de `[b]` solto no meio do texto, que por CommonMark é
    // texto mesmo. Resolver de verdade em F5, junto com o índice do workspace.
    if (url == null) {
        appendLeaf(node, ctx)
        return
    }
    withLink(urlLink(url)) {
        if (label != null) appendChildren(label, ctx, BRACKET_MARKERS) else append(url)
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
    ctx: Ctx,
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
                appendNode(child, ctx)
                suppressSpace = false
            }
        }
    }
}

private fun AnnotatedString.Builder.appendLeaf(node: ASTNode, ctx: Ctx) =
    appendSource(node.startOffset, node.endOffset, ctx)

/**
 * Emite `src[start, end)`, trocando cada trecho especial pelo texto de
 * exibição com link. O especial sai uma vez só, no pedaço onde ele COMEÇA; os
 * pedaços seguintes que caem dentro dele não emitem nada.
 */
private fun AnnotatedString.Builder.appendSource(start: Int, end: Int, ctx: Ctx) {
    var i = start
    for (sp in ctx.specials) {
        if (sp.range.last < i || sp.range.first >= end) continue
        if (sp.range.first > i) append(ctx.src.substring(i, sp.range.first))
        if (sp.range.first >= start) withLink(sp.link) { append(sp.display) }
        i = minOf(end, sp.range.last + 1)
    }
    if (i < end) append(ctx.src.substring(i, end))
}

internal fun ASTNode.pick(type: IElementType): ASTNode? = children.firstOrNull { it.type == type }
