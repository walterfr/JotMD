package io.github.walterfr.jotmd.editor.ops

import io.github.walterfr.jotmd.core.Block
import io.github.walterfr.jotmd.core.BlockId
import io.github.walterfr.jotmd.core.BlockIdSource
import io.github.walterfr.jotmd.core.BlockType
import io.github.walterfr.jotmd.core.parse

/**
 * Divide [block] no [offset] (Enter no meio do texto). Cada metade recebe o
 * tipo que teria se fosse reparseada sozinha — cortar um heading no meio do
 * texto produz heading+parágrafo, o que é o comportamento correto de Markdown,
 * não um bug.
 *
 * `before.trailing = "\n\n"`: uma linha em branco de verdade. Um único `"\n"`
 * deixaria as duas metades como o MESMO parágrafo na próxima vez que o texto
 * fosse reparseado — CommonMark exige linha em branco pra separar blocos de
 * topo. `after` herda o `trailing` original do bloco cortado.
 */
fun splitAt(block: Block, offset: Int, ids: BlockIdSource): Pair<Block, Block> {
    require(offset in 0..block.source.length) { "offset $offset fora de 0..${block.source.length}" }
    val beforeText = block.source.substring(0, offset)
    val afterText = block.source.substring(offset)
    return Block(ids.next(), typeOfOrElse(beforeText, block.type), beforeText, "\n\n") to
        Block(ids.next(), typeOfOrElse(afterText, block.type), afterText, block.trailing)
}

/**
 * Funde o bloco [targetId] com o anterior (Backspace no offset 0). Sem
 * anterior (índice 0 ou id ausente), devolve [blocks] intocada — é um no-op,
 * não um erro.
 *
 * O texto fundido é reparseado: se o `trailing` do bloco anterior já era uma
 * linha em branco de verdade (`"\n\n"`), a linha em branco continua lá no
 * texto e o resultado pode muito bem continuar sendo dois blocos — fundir a
 * lista não obriga o Markdown a virar uma coisa só, só remove a fronteira que
 * o editor mantinha entre eles.
 */
fun mergeWithPrevious(blocks: List<Block>, targetId: BlockId, ids: BlockIdSource): List<Block> {
    val index = blocks.indexOfFirst { it.id == targetId }
    if (index <= 0) return blocks

    val prev = blocks[index - 1]
    val target = blocks[index]
    val mergedText = prev.source + prev.trailing + target.source

    val pieces = parse(mergedText, ids).blocks
    val replacement = if (pieces.isEmpty()) {
        listOf(Block(ids.next(), prev.type, mergedText, target.trailing))
    } else {
        pieces.dropLast(1) + pieces.last().copy(trailing = target.trailing)
    }

    return blocks.subList(0, index - 1) + replacement + blocks.subList(index + 1, blocks.size)
}

/**
 * Reparseia [blocks] a partir de [fromIndex] até o fim do documento e
 * estabiliza: todo bloco cujo `source`/`trailing`/`type` bater exatamente com
 * o resultado reaproveita o ID antigo — LazyColumn e foco não pulam por causa
 * de um reparse que na prática não mudou nada dali pra frente.
 *
 * Existe pra pegar absorção em cascata (Armadilha 1 do AI_CONTEXT): um
 * ` ``` ` digitado no meio pode abrir uma cerca que engole os blocos
 * seguintes. `splitAt`/`mergeWithPrevious` só mexem no ponto do cursor;
 * `reparse` depois deles é quem confirma que nada mais adiante mudou de tipo.
 *
 * ponytail: reparseia a cauda inteira sempre, sem parar cedo — custo
 * proporcional ao que resta do documento. Teto real quando a Armadilha 3
 * (5k+ blocos) virar reclamação de verdade; até lá, correto > incremental.
 */
fun reparse(blocks: List<Block>, fromIndex: Int, ids: BlockIdSource): List<Block> {
    if (fromIndex >= blocks.size) return blocks

    val tail = blocks.subList(fromIndex, blocks.size)
    val tailText = tail.joinToString("") { it.source + it.trailing }
    val reparsed = parse(tailText, ids).blocks

    val stabilized = reparsed.mapIndexed { i, fresh ->
        val old = tail.getOrNull(i)
        if (old != null && old.type == fresh.type && old.source == fresh.source && old.trailing == fresh.trailing) {
            old
        } else {
            fresh
        }
    }
    return blocks.subList(0, fromIndex) + stabilized
}

private fun typeOfOrElse(text: String, fallback: BlockType) =
    parse(text).blocks.firstOrNull()?.type ?: fallback
