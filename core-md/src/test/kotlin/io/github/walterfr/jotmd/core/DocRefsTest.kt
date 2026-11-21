package io.github.walterfr.jotmd.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DocRefsTest {

    @Test
    fun `normalizeRefLabel normaliza espaços, quebras de linha e caixa`() {
        assertEquals("meu link", normalizeRefLabel("  Meu\t  Link \n "))
        assertEquals("a b c", normalizeRefLabel("A   B\r\nC"))
        assertEquals("", normalizeRefLabel("   \t "))
    }

    @Test
    fun `isFootnoteDefinitionBlock e parseFootnoteDefinition cobrem continuações e rejeitam inválidos`() {
        assertTrue(isFootnoteDefinitionBlock("[^1]: simples"))
        assertEquals("1" to "simples", parseFootnoteDefinition("[^1]: simples"))

        assertEquals(
            "nota-2" to "primeira linha\nsegunda indentada\nterceira com tab\ncontinuação direta",
            parseFootnoteDefinition("[^nota-2]: primeira linha\n  segunda indentada\n\tterceira com tab\ncontinuação direta"),
        )
        assertEquals("x" to "na linha seguinte", parseFootnoteDefinition("[^x]:\n  na linha seguinte"))
        assertEquals("vazia" to "", parseFootnoteDefinition("[^vazia]:   "))
        assertEquals("url" to "https://example.com", parseFootnoteDefinition("[^url]: https://example.com"))

        assertFalse(isFootnoteDefinitionBlock("[ref]: https://example.com"))
        assertFalse(isFootnoteDefinitionBlock("[^]: sem id"))
        assertFalse(isFootnoteDefinitionBlock("[^a b]: espaco no id"))
        assertFalse(isFootnoteDefinitionBlock("Texto antes [^1]: nao eh def"))
        assertNull(parseFootnoteDefinition("[ref]: https://example.com"))
    }

    @Test
    fun `footnoteRefSpansIn encontra referências e ignora definições, escapes e skipRanges`() {
        val text = "Veja [^1], [^nota-2], \\[^esc], `[^code]` e [^1]: def."
        val codeStart = text.indexOf("`[^code]`")
        val codeEnd = codeStart + "`[^code]`".length - 1
        val spans = footnoteRefSpansIn(text, skipRanges = listOf(codeStart..codeEnd))

        assertEquals(listOf("1", "nota-2"), spans.map { it.second })
        for ((range, id) in spans) {
            assertEquals("[^$id]", text.substring(range))
        }
        assertTrue(footnoteRefSpansIn("[^1]: definicao isolada").isEmpty())
    }

    @Test
    fun `docRefsOf coleta links de referência, notas por ordem de uso e protege definições`() {
        val md = """
            Texto com [Link][Ref  Um], [Ref Dois][], [Título] e [InPara].
            Notas: primeiro [^beta], depois [^alfa], repete [^BETA], ignorar `[^nao]` e ${'$'}x[^math]${'$'}, e [^ausente].

            Parágrafo com def colada
            [InPara]: <https://inpara.org/path> (Título)

            [ref um]: https://um.example.com "Um"
            [REF DOIS]: <https://dois.example.com> 'Dois'
            [título]: https://tres.example.com

            [^alfa]: Nota alfa.
            [^beta]: Nota beta com continuação
              na linha dois.
            [^nao]: Não usada no corpo.
            [^urlfn]: https://fn-link.example.com
        """.trimIndent()
        val doc = parse(md)
        val refs = docRefsOf(doc)

        assertEquals("https://um.example.com", refs.resolveLink("ref um"))
        assertEquals("https://um.example.com", refs.resolveLink("  REF   UM "))
        assertEquals("https://dois.example.com", refs.resolveLink("ref dois"))
        assertEquals("https://tres.example.com", refs.resolveLink("TÍTULO"))
        assertEquals("https://inpara.org/path", refs.resolveLink("inpara"))
        assertNull(refs.resolveLink("^urlfn"))
        assertNull(refs.resolveLink("inexistente"))

        assertEquals(listOf("beta", "alfa", "nao", "urlfn"), refs.footnoteOrder)
        assertEquals(1, refs.footnoteNumber("beta"))
        assertEquals(1, refs.footnoteNumber("BETA"))
        assertEquals(2, refs.footnoteNumber("alfa"))
        assertEquals(3, refs.footnoteNumber("nao"))
        assertEquals(4, refs.footnoteNumber("urlfn"))
        assertNull(refs.footnoteNumber("ausente"))

        val betaDef = refs.resolveFootnote("BETA")!!
        assertEquals("beta", betaDef.normalizedId)
        assertEquals("Nota beta com continuação\nna linha dois.", betaDef.content)
        assertEquals(doc.blocks[betaDef.blockIndex].id, betaDef.blockId)

        val urlFnDef = refs.resolveFootnote("urlfn")!!
        assertEquals("https://fn-link.example.com", urlFnDef.content)
    }

    @Test
    fun `docRefsOf trata notas consecutivas sem linha em branco e DocRefs EMPTY`() {
        val md = """
            Usa [^dois] e [^um].

            [^um]: Primeira
            [^dois]: Segunda
        """.trimIndent()
        val refs = docRefsOf(parse(md))
        assertEquals(listOf("dois", "um"), refs.footnoteOrder)
        assertEquals(1, refs.footnoteNumber("dois"))
        assertEquals(2, refs.footnoteNumber("um"))
        assertEquals("Primeira", refs.resolveFootnote("um")?.content)
        assertEquals("Segunda", refs.resolveFootnote("dois")?.content)

        assertNull(DocRefs.EMPTY.resolveLink("a"))
        assertNull(DocRefs.EMPTY.resolveFootnote("1"))
        assertNull(DocRefs.EMPTY.footnoteNumber("1"))
    }
}
