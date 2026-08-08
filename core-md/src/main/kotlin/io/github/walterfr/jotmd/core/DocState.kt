package io.github.walterfr.jotmd.core

/**
 * Documento inteiro.
 *
 * [leading] é o espaço em branco antes do primeiro bloco. Existe só para
 * sustentar a invariante `serialize(parse(t)) == t` quando o arquivo começa com
 * linhas em branco. Sem ele a alternativa seria um bloco de `source` vazio no
 * topo — uma linha invisível e focável no `LazyColumn`, ou seja, um bug de UX
 * plantado para pagar uma dívida de serialização.
 */
data class DocState(
    val leading: String,
    val blocks: List<Block>,
    val focused: BlockId? = null,
    val selection: IntRange? = null,
)
