package io.github.walterfr.jotmd.core

@JvmInline
value class BlockId(val v: Long)

/** Fonte de IDs. Uma por documento, para que os testes sejam determinísticos. */
class BlockIdSource(private var next: Long = 0) {
    fun next(): BlockId = BlockId(next++)
}

enum class BlockType {
    HEADING,
    PARAGRAPH,
    BLOCKQUOTE,
    UNORDERED_LIST,
    ORDERED_LIST,
    CODE_FENCE,
    CODE_INDENTED,
    TABLE,
    HORIZONTAL_RULE,
    HTML,
    LINK_DEFINITION,
    OTHER,
}

/**
 * Um bloco de topo do documento.
 *
 * [source] é o markdown cru do bloco, sem os separadores que vêm depois dele.
 * [trailing] guarda esses separadores ("\n", "\n\n", "\r\n\r\n"...) para que a
 * serialização volte byte a byte ao original.
 */
data class Block(
    val id: BlockId,
    val type: BlockType,
    val source: String,
    val trailing: String,
)
