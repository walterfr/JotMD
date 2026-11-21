package io.github.walterfr.jotmd.editor

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.BaselineShift
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import io.github.walterfr.jotmd.core.DocRefs
import io.github.walterfr.jotmd.core.astOf
import io.github.walterfr.jotmd.core.footnoteRefSpansIn
import io.github.walterfr.jotmd.core.inlineMathSpansIn
import io.github.walterfr.jotmd.core.normalizeRefLabel
import io.github.walterfr.jotmd.core.renderLatexToUnicode
import io.github.walterfr.jotmd.core.tagSpansIn
import io.github.walterfr.jotmd.core.wikilinksIn
import org.intellij.markdown.IElementType
import org.intellij.markdown.MarkdownElementTypes
import org.intellij.markdown.MarkdownTokenTypes
import org.intellij.markdown.ast.ASTNode
import org.intellij.markdown.ast.getTextInNode
import org.intellij.markdown.flavours.gfm.GFMElementTypes
import org.intellij.markdown.flavours.gfm.GFMTokenTypes
import java.util.regex.PatternSyntaxException

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
fun inlineText(
    source: String,
    refs: DocRefs = DocRefs.EMPTY,
    highlightQuery: String = "",
    highlightCaseSensitive: Boolean = false,
    highlightWholeWord: Boolean = false,
    highlightRegex: Boolean = false,
    highlightActive: Boolean = false,
): AnnotatedString {
    val ast = astOf(source)
    val built = buildAnnotatedString { appendChildren(ast, Ctx.of(source, ast, refs), emptySet()) }
    // O `======` do setext e afins são descartados depois da quebra de linha que
    // os precede, então sobra um "\n" pendurado no fim.
    val end = built.text.indexOfLast { it != '\n' } + 1
    val trimmed = if (end == built.length) built else built.subSequence(0, end)
    if (highlightQuery.isEmpty()) return trimmed
    return applySearchHighlight(
        base = trimmed,
        query = highlightQuery,
        caseSensitive = highlightCaseSensitive,
        wholeWord = highlightWholeWord,
        regex = highlightRegex,
        activeOccurrenceInBlock = if (highlightActive) 0 else null,
    )
}

/**
 * Sobrepõe destaque de busca em [base], preservando todos os estilos e links
 * existentes. Padrões regex inválidos são ignorados com segurança.
 */
fun applySearchHighlight(
    base: AnnotatedString,
    query: String,
    caseSensitive: Boolean = false,
    wholeWord: Boolean = false,
    regex: Boolean = false,
    activeOccurrenceInBlock: Int? = null,
): AnnotatedString {
    if (query.isEmpty() || base.isEmpty()) return base
    val matches = findSearchMatchRanges(base.text, query, caseSensitive, wholeWord, regex)
    if (matches.isEmpty()) return base
    val hitStyle = SpanStyle(background = MdTokens.searchHitBackground)
    val activeStyle = SpanStyle(
        background = MdTokens.searchActiveBackground,
        color = MdTokens.searchActiveText,
    )
    return buildAnnotatedString {
        append(base)
        for ((index, range) in matches.withIndex()) {
            val style = if (index == activeOccurrenceInBlock) activeStyle else hitStyle
            addStyle(style, range.first, range.last + 1)
        }
    }
}

internal fun activeOccurrenceIndex(
    source: String,
    activeRange: IntRange?,
    query: String,
    caseSensitive: Boolean,
    wholeWord: Boolean,
    regex: Boolean,
): Int? {
    if (activeRange == null || query.isEmpty()) return null
    val matches = findSearchMatchRanges(source, query, caseSensitive, wholeWord, regex)
    val idx = matches.indexOfFirst { it == activeRange }
        .let { if (it >= 0) it else matches.indexOfFirst { r -> activeRange.first in r } }
    return if (idx >= 0) idx else null
}

internal fun findSearchMatchRanges(
    text: String,
    query: String,
    caseSensitive: Boolean,
    wholeWord: Boolean,
    regex: Boolean,
): List<IntRange> {
    if (query.isEmpty() || text.isEmpty()) return emptyList()
    val out = ArrayList<IntRange>()
    if (regex) {
        val options = if (caseSensitive) setOf(RegexOption.MULTILINE) else setOf(RegexOption.MULTILINE, RegexOption.IGNORE_CASE)
        val compiled = try {
            Regex(query, options)
        } catch (_: PatternSyntaxException) {
            return emptyList()
        }
        var searchFrom = 0
        while (searchFrom <= text.length) {
            val m = compiled.find(text, searchFrom) ?: break
            val start = m.range.first
            val endExclusive = m.range.last + 1
            if (endExclusive <= start) {
                searchFrom = maxOf(start, endExclusive) + 1
                continue
            }
            if (wholeWord && !isSearchWholeWord(text, start, endExclusive)) {
                searchFrom = start + 1
                continue
            }
            out += start until endExclusive
            searchFrom = endExclusive
        }
    } else {
        val ignoreCase = !caseSensitive
        var searchFrom = 0
        while (searchFrom <= text.length - query.length) {
            val idx = text.indexOf(query, searchFrom, ignoreCase = ignoreCase)
            if (idx < 0) break
            val endExclusive = idx + query.length
            if (wholeWord && !isSearchWholeWord(text, idx, endExclusive)) {
                searchFrom = idx + 1
                continue
            }
            out += idx until endExclusive
            searchFrom = endExclusive
        }
    }
    return out
}

private fun isSearchWholeWord(text: String, start: Int, endExclusive: Int): Boolean {
    val before = text.getOrNull(start - 1)
    val after = text.getOrNull(endExclusive)
    return (before == null || !before.isSearchWordChar()) && (after == null || !after.isSearchWordChar())
}

private fun Char.isSearchWordChar(): Boolean = isLetterOrDigit() || this == '_'

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
const val FOOTNOTE_URI = "jotmd:footnote:"

/** Trecho do fonte que sai trocado: [display] no lugar de [range], com [link] ou [style]. */
internal class Special(
    val range: IntRange,
    val display: String,
    val link: LinkAnnotation? = null,
    val style: SpanStyle? = null,
)

private val HIGHLIGHT_REGEX = Regex("""==(?=\S)([^=\r\n]*?\S)==""")
private val UNDERLINE_REGEX = Regex("""<u>([^\r\n]+?)</u>""", RegexOption.IGNORE_CASE)

private val SKIP_TYPES: Set<IElementType> = setOf(
    MarkdownElementTypes.CODE_SPAN,
    MarkdownElementTypes.CODE_FENCE,
    MarkdownElementTypes.CODE_BLOCK,
    MarkdownElementTypes.LINK_DESTINATION,
    MarkdownElementTypes.AUTOLINK,
    GFMTokenTypes.GFM_AUTOLINK,
)

/**
 * Fonte + trechos especiais (wikilinks, tags e marca-texto) com offsets nesse mesmo fonte.
 *
 * Os especiais são resolvidos por posição no texto, não por nó do AST: o
 * parser fragmenta `[[nota]]` em `[`, um link de referência `[nota]` e `]`, e
 * o que importa é que nenhum desses pedaços seja emitido — todo texto do fonte
 * passa por [appendSource], que troca a faixa inteira pelo texto de exibição.
 */
internal class Ctx private constructor(
    val src: String,
    val specials: List<Special>,
    val refs: DocRefs = DocRefs.EMPTY,
) {
    fun coversNode(node: ASTNode): Boolean {
        if (node.startOffset >= node.endOffset) return false
        val last = node.endOffset - 1
        return specials.any { node.startOffset >= it.range.first && last <= it.range.last }
    }

    companion object {
        fun of(src: String, ast: ASTNode = astOf(src), refs: DocRefs = DocRefs.EMPTY): Ctx {
            val wikis = wikilinksIn(src).map { w ->
                // A regex do wikilink proíbe `]` dentro: o primeiro `]]` fecha.
                val last = src.indexOf("]]", w.offset) + 1
                val display = w.alias ?: (w.target + (w.anchor?.let { "#$it" } ?: ""))
                Special(w.offset..last, display, link = internalLink(WIKI_URI + w.target))
            }
            val tags = tagSpansIn(src).map { (range, name) ->
                Special(range, src.substring(range), link = internalLink(TAG_URI + name))
            }
            val skipRanges = ArrayList<IntRange>()
            collectSkipRanges(ast, skipRanges)
            val mathStyle = SpanStyle(
                fontFamily = FontFamily.Serif,
                fontStyle = FontStyle.Italic,
                background = MdTokens.inlineCodeBackground,
            )
            val mathSpans = inlineMathSpansIn(src, skipRanges).map { (range, expr) ->
                Special(range, renderLatexToUnicode(expr), style = mathStyle)
            }
            val extendedSkips = skipRanges + mathSpans.map { it.range }
            val footnoteStyle = SpanStyle(
                baselineShift = BaselineShift.Superscript,
                fontSize = MdTokens.base * 0.78f,
            )
            val footnotes = footnoteRefSpansIn(src, extendedSkips).map { (range, id) ->
                val display = "[${refs.footnoteNumber(id) ?: id}]"
                Special(
                    range = range,
                    display = display,
                    link = internalLink(FOOTNOTE_URI + normalizeRefLabel(id)),
                    style = footnoteStyle,
                )
            }
            val highlightStyle = SpanStyle(
                background = MdTokens.highlightBackground,
                color = MdTokens.highlightText,
            )
            val highlights = regexSpecials(src, HIGHLIGHT_REGEX, extendedSkips, highlightStyle)
            val underlines = regexSpecials(
                src,
                UNDERLINE_REGEX,
                extendedSkips,
                SpanStyle(textDecoration = TextDecoration.Underline),
            )
            // `[[#topo]]` também casa como tag; o que começa antes vence.
            val sorted = (wikis + footnotes + mathSpans + tags + highlights + underlines).sortedBy { it.range.first }
            val kept = ArrayList<Special>()
            for (s in sorted) if (kept.isEmpty() || s.range.first > kept.last().range.last) kept += s
            return Ctx(src, kept, refs)
        }

        private fun regexSpecials(
            src: String,
            regex: Regex,
            skipRanges: List<IntRange>,
            style: SpanStyle,
        ): List<Special> = regex.findAll(src).mapNotNull { m ->
            if (skipRanges.any { r -> m.range.first <= r.last && r.first <= m.range.last }) {
                null
            } else {
                Special(m.range, m.groupValues[1], style = style)
            }
        }.toList()

        private fun collectSkipRanges(node: ASTNode, out: MutableList<IntRange>) {
            if (node.type in SKIP_TYPES) {
                if (node.endOffset > node.startOffset) out += node.startOffset until node.endOffset
                return
            }
            node.children.forEach { collectSkipRanges(it, out) }
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
    if (ctx.coversNode(node)) {
        appendLeaf(node, ctx)
        return
    }
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
                ?: node.pick(MarkdownElementTypes.FULL_REFERENCE_LINK)?.pick(MarkdownElementTypes.LINK_TEXT)
                ?: node.pick(MarkdownElementTypes.SHORT_REFERENCE_LINK)?.pick(MarkdownElementTypes.LINK_LABEL)
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
    val inlineUrl = node.pick(MarkdownElementTypes.LINK_DESTINATION)
        ?.getTextInNode(ctx.src)
        ?.toString()
    if (inlineUrl != null) {
        val label = node.pick(MarkdownElementTypes.LINK_TEXT)
            ?: node.pick(MarkdownElementTypes.LINK_LABEL)
        withLink(urlLink(inlineUrl)) {
            if (label != null) appendChildren(label, ctx, BRACKET_MARKERS) else append(inlineUrl)
        }
        return
    }

    val textNode = node.pick(MarkdownElementTypes.LINK_TEXT)
    val labelNode = node.pick(MarkdownElementTypes.LINK_LABEL)
    val labelKey = labelNode?.getTextInNode(ctx.src)?.toString()?.removeSurrounding("[", "]")
    val textKey = textNode?.getTextInNode(ctx.src)?.toString()?.removeSurrounding("[", "]")
    val lookupKey = labelKey?.takeIf { it.isNotBlank() } ?: textKey
    val resolvedUrl = lookupKey?.let { ctx.refs.resolveLink(it) }
    if (resolvedUrl == null) {
        appendLeaf(node, ctx)
        return
    }
    val display = textNode ?: labelNode
    withLink(urlLink(resolvedUrl)) {
        if (display != null) appendChildren(display, ctx, BRACKET_MARKERS) else append(resolvedUrl)
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
 * exibição com link ou estilo. O especial sai uma vez só, no pedaço onde ele
 * COMEÇA; os pedaços seguintes que caem dentro dele não emitem nada.
 */
private fun AnnotatedString.Builder.appendSource(start: Int, end: Int, ctx: Ctx) {
    var i = start
    for (sp in ctx.specials) {
        if (sp.range.last < i || sp.range.first >= end) continue
        if (sp.range.first > i) append(ctx.src.substring(i, sp.range.first))
        if (sp.range.first >= start) {
            when {
                sp.link != null && sp.style != null -> withLink(sp.link) {
                    withStyle(sp.style) { append(sp.display) }
                }
                sp.link != null -> withLink(sp.link) { append(sp.display) }
                sp.style != null -> withStyle(sp.style) { append(sp.display) }
                else -> append(sp.display)
            }
        }
        i = minOf(end, sp.range.last + 1)
    }
    if (i < end) append(ctx.src.substring(i, end))
}

internal fun ASTNode.pick(type: IElementType): ASTNode? = children.firstOrNull { it.type == type }
