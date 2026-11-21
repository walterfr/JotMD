package io.github.walterfr.jotmd.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DocSearchTest {

    @Test
    fun `query vazia retorna vazio e zero substituições`() {
        val doc = parse("texto qualquer")
        assertEquals(emptyList(), findInDocument(doc, ""))
        assertEquals("texto" to 0, replaceAllInText("texto", "", "x"))
    }

    @Test
    fun `encontra ocorrências em múltiplos blocos com ranges consistentes com serialize`() {
        val text = "\n\n# Título casa\r\n\r\nPrimeira casa e segunda casa.\n\n> casa no fim"
        val doc = parse(text)
        assertEquals(text, serialize(doc))

        val hits = findInDocument(doc, "casa")
        assertEquals(4, hits.size)
        assertEquals(listOf(0, 1, 1, 2), hits.map { it.blockIndex })
        assertEquals(doc.blocks[0].id, hits[0].blockId)
        assertEquals(doc.blocks[1].id, hits[1].blockId)
        assertEquals(doc.blocks[2].id, hits[3].blockId)

        for (hit in hits) {
            val inBlock = doc.blocks[hit.blockIndex].source.substring(hit.rangeInBlock)
            val inDoc = text.substring(hit.globalRange)
            assertEquals("casa", inBlock)
            assertEquals("casa", inDoc)
        }
    }

    @Test
    fun `respeita caseSensitive inclusive com acentos`() {
        val doc = parse("Ação e ação e AÇÃO")
        assertEquals(3, findInDocument(doc, "ação", caseSensitive = false).size)
        val exact = findInDocument(doc, "ação", caseSensitive = true)
        assertEquals(1, exact.size)
        assertEquals("ação", doc.blocks[0].source.substring(exact.single().rangeInBlock))
    }

    @Test
    fun `wholeWord valida limites de palavra com letra, dígito e underscore`() {
        val doc = parse("foo foobar bar_foo foo1 _foo (foo) foo, xfoo foo")
        val hits = findInDocument(doc, "foo", wholeWord = true)
        assertEquals(4, hits.size)
        val src = doc.blocks[0].source
        assertEquals(listOf(0..2, 30..32, 35..37, 45..47), hits.map { it.rangeInBlock })
        assertTrue(hits.all { src.substring(it.rangeInBlock) == "foo" })
    }

    @Test
    fun `ocorrências são não-sobrepostas e não perdem palavra após candidato rejeitado`() {
        val nonOverlap = findInDocument(parse("aaaa"), "aa")
        assertEquals(listOf(0..1, 2..3), nonOverlap.map { it.rangeInBlock })

        val docAfterRejected = parse("ba a a")
        val wholePhrase = findInDocument(docAfterRejected, "a a", wholeWord = true)
        assertEquals(listOf(3..5), wholePhrase.map { it.rangeInBlock })
    }

    @Test
    fun `limita resultados em MAX_HITS_COUNT`() {
        val many = "hit ".repeat(MAX_HITS_COUNT + 500)
        val hits = findInDocument(parse(many), "hit")
        assertEquals(MAX_HITS_COUNT, hits.size)
    }

    @Test
    fun `replaceAllInText substitui respeitando opções e sem loop quando replacement contém query`() {
        assertEquals("sem troca" to 0, replaceAllInText("sem troca", "xyz", "abc"))

        val (ciText, ciCount) = replaceAllInText("Casa casa CASA", "casa", "lar")
        assertEquals("lar lar lar", ciText)
        assertEquals(3, ciCount)

        val (csText, csCount) = replaceAllInText("Casa casa CASA", "casa", "lar", caseSensitive = true)
        assertEquals("Casa lar CASA", csText)
        assertEquals(1, csCount)

        val (wwText, wwCount) = replaceAllInText(
            "foo foobar _foo foo_bar (foo) ba a a",
            "foo",
            "foo2",
            wholeWord = true,
        )
        assertEquals("foo2 foobar _foo foo_bar (foo2) ba a a", wwText)
        assertEquals(2, wwCount)

        val (phraseText, phraseCount) = replaceAllInText("ba a a", "a a", "X", wholeWord = true)
        assertEquals("ba X", phraseText)
        assertEquals(1, phraseCount)
    }

    @Test
    fun `replaceRangeInText substitui faixa válida e preserva texto em faixa inválida`() {
        assertEquals("aXe", replaceRangeInText("abcde", 1..3, "X"))
        assertEquals("Zbcde", replaceRangeInText("abcde", 0..0, "Z"))
        assertEquals("abcd!", replaceRangeInText("abcde", 4..4, "!"))
        assertEquals("", replaceRangeInText("abcde", 0..4, ""))

        assertEquals("abcde", replaceRangeInText("abcde", -1..2, "X"))
        assertEquals("abcde", replaceRangeInText("abcde", 2..5, "X"))
        assertEquals("abcde", replaceRangeInText("abcde", 3..1, "X"))
        assertEquals("abcde", replaceRangeInText("abcde", IntRange.EMPTY, "X"))
        assertEquals("", replaceRangeInText("", 0..0, "X"))
    }
}
