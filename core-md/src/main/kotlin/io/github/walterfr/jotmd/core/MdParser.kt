package io.github.walterfr.jotmd.core

import org.intellij.markdown.IElementType
import org.intellij.markdown.MarkdownElementTypes
import org.intellij.markdown.MarkdownTokenTypes
import org.intellij.markdown.ast.ASTNode
import org.intellij.markdown.flavours.gfm.GFMElementTypes
import org.intellij.markdown.flavours.gfm.GFMFlavourDescriptor
import org.intellij.markdown.parser.MarkdownParser

private val flavour = GFMFlavourDescriptor()

/**
 * Particiona [text] nos blocos de topo do documento.
 *
 * A partição é total e sem sobreposição: todo caractere da entrada cai em
 * [DocState.leading] ou no `source`/`trailing` de exatamente um bloco. Por isso
 * `serialize(parse(t)) == t` vale **por construção**, para qualquer entrada.
 * O teste de roundtrip guarda essa propriedade, não a produz.
 *
 * Note que a fatia vem do texto original, não de uma reimpressão do AST — o AST
 * só é consultado para saber onde cada bloco começa e de que tipo ele é.
 */
fun parse(text: String, ids: BlockIdSource = BlockIdSource()): DocState {
    if (text.isEmpty()) return DocState(leading = "", blocks = emptyList())

    val root = MarkdownParser(flavour).buildMarkdownTreeFromString(text)
    val nodes = root.children.filterNot { it.isFiller }
    if (nodes.isEmpty()) return DocState(leading = text, blocks = emptyList())

    val blocks = nodes.mapIndexed { i, node ->
        val start = node.startOffset
        val limit = (nodes.getOrNull(i + 1)?.startOffset ?: text.length).coerceAtLeast(start)
        val end = node.endOffset.coerceIn(start, limit)
        Block(
            id = ids.next(),
            type = node.type.toBlockType(),
            source = text.substring(start, end),
            trailing = text.substring(end, limit),
        )
    }
    return DocState(leading = text.substring(0, nodes.first().startOffset), blocks = blocks)
}

/**
 * AST de um trecho isolado, com offsets locais a ele.
 *
 * Use com `block.source`: o AST do documento inteiro tem offsets globais, e
 * misturar os dois é a origem clássica de bug de renderização por bloco.
 */
fun astOf(source: String): ASTNode =
    MarkdownParser(flavour).buildMarkdownTreeFromString(source)

/** Separadores entre blocos: viram `trailing` do bloco anterior, nunca um bloco. */
private val ASTNode.isFiller: Boolean
    get() = type == MarkdownTokenTypes.EOL || type == MarkdownTokenTypes.WHITE_SPACE

private fun IElementType.toBlockType(): BlockType = when (this) {
    MarkdownElementTypes.ATX_1,
    MarkdownElementTypes.ATX_2,
    MarkdownElementTypes.ATX_3,
    MarkdownElementTypes.ATX_4,
    MarkdownElementTypes.ATX_5,
    MarkdownElementTypes.ATX_6,
    MarkdownElementTypes.SETEXT_1,
    MarkdownElementTypes.SETEXT_2 -> BlockType.HEADING

    MarkdownElementTypes.PARAGRAPH -> BlockType.PARAGRAPH
    MarkdownElementTypes.BLOCK_QUOTE -> BlockType.BLOCKQUOTE
    MarkdownElementTypes.UNORDERED_LIST -> BlockType.UNORDERED_LIST
    MarkdownElementTypes.ORDERED_LIST -> BlockType.ORDERED_LIST
    MarkdownElementTypes.CODE_FENCE -> BlockType.CODE_FENCE
    MarkdownElementTypes.CODE_BLOCK -> BlockType.CODE_INDENTED
    MarkdownElementTypes.HTML_BLOCK -> BlockType.HTML
    MarkdownElementTypes.LINK_DEFINITION -> BlockType.LINK_DEFINITION
    GFMElementTypes.TABLE -> BlockType.TABLE
    MarkdownTokenTypes.HORIZONTAL_RULE -> BlockType.HORIZONTAL_RULE
    else -> BlockType.OTHER
}
