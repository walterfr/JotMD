package io.github.walterfr.jotmd.editor.ops

import io.github.walterfr.jotmd.core.BlockIdSource
import io.github.walterfr.jotmd.core.BlockType
import io.github.walterfr.jotmd.core.parse
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

class BlockOpsTest {

    // ---- splitAt ----

    @Test
    fun `splitAt corta o texto e separa com linha em branco de verdade`() {
        val ids = BlockIdSource()
        val doc = parse("um dois tres\n", ids)
        val (before, after) = splitAt(doc.blocks[0], offset = 7, ids)
        assertEquals("um dois", before.source)
        assertEquals("\n\n", before.trailing)
        assertEquals(" tres", after.source)
        assertEquals("\n", after.trailing)
    }

    @Test
    fun `splitAt de um único newline reparseia como dois blocos separados, não um só`() {
        val ids = BlockIdSource()
        val doc = parse("um dois tres\n", ids)
        val (before, after) = splitAt(doc.blocks[0], offset = 7, ids)
        val reparsed = parse(before.source + before.trailing + after.source + after.trailing)
        assertEquals(2, reparsed.blocks.size, "trailing de \\n\\n devia manter os blocos separados")
    }

    @Test
    fun `splitAt no meio de um heading produz heading e parágrafo`() {
        val ids = BlockIdSource()
        val doc = parse("# Título Grande\n", ids)
        val (before, after) = splitAt(doc.blocks[0], offset = 8, ids) // "# Título" | " Grande"
        assertEquals(BlockType.HEADING, before.type)
        assertEquals(BlockType.PARAGRAPH, after.type)
    }

    @Test
    fun `splitAt nas pontas produz uma metade vazia sem quebrar`() {
        val ids = BlockIdSource()
        val doc = parse("texto\n", ids)
        val (before, after) = splitAt(doc.blocks[0], offset = 0, ids)
        assertEquals("", before.source)
        assertEquals("texto", after.source)
    }

    @Test
    fun `splitAt sempre dá IDs novos e distintos`() {
        val ids = BlockIdSource()
        val doc = parse("abc\n", ids)
        val (before, after) = splitAt(doc.blocks[0], offset = 1, ids)
        assertTrue(before.id != doc.blocks[0].id)
        assertTrue(after.id != doc.blocks[0].id)
        assertTrue(before.id != after.id)
    }

    // ---- mergeWithPrevious ----

    @Test
    fun `mergeWithPrevious com trailing de uma linha só vira um parágrafo`() {
        // splitAt sempre separa com "\n\n" (Armadilha de CommonMark: um "\n" só
        // não separa blocos). Pra testar o caso de fato virar UM bloco,
        // construo os blocos à mão como se fossem vizinhos por uma quebra
        // suave — o cenário real de "\n\n" fica no teste de fronteira abaixo.
        val ids = BlockIdSource()
        val prev = io.github.walterfr.jotmd.core.Block(ids.next(), BlockType.PARAGRAPH, "um", "\n")
        val target = io.github.walterfr.jotmd.core.Block(ids.next(), BlockType.PARAGRAPH, "dois", "\n")
        val merged = mergeWithPrevious(listOf(prev, target), target.id, ids)
        assertEquals(1, merged.size)
        assertEquals("um\ndois", merged[0].source)
        assertEquals("\n", merged[0].trailing)
    }

    @Test
    fun `mergeWithPrevious no primeiro bloco não faz nada`() {
        val ids = BlockIdSource()
        val doc = parse("um\n\ndois\n", ids)
        val result = mergeWithPrevious(doc.blocks, doc.blocks[0].id, ids)
        assertSame(doc.blocks, result)
    }

    @Test
    fun `mergeWithPrevious com id desconhecido não faz nada`() {
        val ids = BlockIdSource()
        val doc = parse("um\n\ndois\n", ids)
        val result = mergeWithPrevious(doc.blocks, io.github.walterfr.jotmd.core.BlockId(999), ids)
        assertSame(doc.blocks, result)
    }

    @Test
    fun `split seguido de merge não é bit a bit reversível, e é assim mesmo`() {
        // splitAt insere "\n\n" que não existia no texto original (é o que
        // CommonMark exige pra separar blocos). Fundir de volta reparseia esse
        // "\n\n" como divisor de parágrafo de novo — não devolve o texto
        // original, devolve dois blocos de novo. Documentado, não é bug.
        val ids = BlockIdSource()
        val doc = parse("um parágrafo comprido de teste\n", ids)
        val (before, after) = splitAt(doc.blocks[0], offset = 3, ids)
        val merged = mergeWithPrevious(listOf(before, after), after.id, ids)
        assertEquals(2, merged.size)
        assertEquals("um ", merged[0].source)
        assertEquals("parágrafo comprido de teste", merged[1].source)
    }

    @Test
    fun `mergeWithPrevious sobre uma fronteira de linha em branco de verdade não obriga virar um bloco só`() {
        val ids = BlockIdSource()
        val doc = parse("um\n\ndois\n", ids)
        val merged = mergeWithPrevious(doc.blocks, doc.blocks[1].id, ids)
        // a linha em branco literal continua no texto — Markdown não junta os dois
        assertEquals(2, merged.size)
        assertEquals("um", merged[0].source)
        assertEquals("dois", merged[1].source)
    }

    // ---- reparse ----

    @Test
    fun `reparse reaproveita IDs de blocos que não mudaram`() {
        val ids = BlockIdSource()
        val doc = parse("um\n\ndois\n\ntres\n", ids)
        val originalIds = doc.blocks.map { it.id }
        val result = reparse(doc.blocks, fromIndex = 0, ids)
        assertEquals(originalIds, result.map { it.id })
    }

    @Test
    fun `reparse detecta cerca não fechada engolindo o bloco seguinte`() {
        val ids = BlockIdSource()
        val doc = parse("texto\n\noutro\n", ids)
        val comCercaAberta = doc.blocks[0].copy(source = "```kotlin")
        val editado = listOf(comCercaAberta, doc.blocks[1])
        val result = reparse(editado, fromIndex = 0, ids)
        // a cerca sem fechar absorve o resto do documento num bloco só
        assertEquals(1, result.size)
        assertEquals(BlockType.CODE_FENCE, result[0].type)
        assertTrue(result[0].source.contains("outro"))
    }

    @Test
    fun `reparse com fromIndex além do fim devolve a lista intocada`() {
        val ids = BlockIdSource()
        val doc = parse("um\n", ids)
        val result = reparse(doc.blocks, fromIndex = 5, ids)
        assertSame(doc.blocks, result)
    }

    @Test
    fun `reparse preserva o roundtrip do documento`() {
        val ids = BlockIdSource()
        val text = "# T\n\num **b** parágrafo\n\n- item\n"
        val doc = parse(text, ids)
        val result = reparse(doc.blocks, fromIndex = 1, ids)
        assertEquals(text, doc.leading + result.joinToString("") { it.source + it.trailing })
    }
}
