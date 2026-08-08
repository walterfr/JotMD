package io.github.walterfr.jotmd.core

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * A invariante do projeto: quebrar isso corrompe o arquivo do usuário em
 * silêncio. Nenhum commit passa sem esta suíte verde.
 */
class RoundtripTest {

    private val edgeCases = mapOf(
        "vazio" to "",
        "só newline" to "\n",
        "só linhas em branco" to "\n\n\n",
        "só espaços" to "   ",
        "espaços e newlines" to "  \n\t\n  ",
        "sem newline final" to "# Sem newline final",
        "começa com linhas em branco" to "\n\n# Depois de linhas em branco\n",
        "termina com muitas linhas em branco" to "parágrafo\n\n\n\n",
        "CRLF" to "linha um\r\n\r\nlinha dois\r\n",
        "espaços à direita" to "quebra dura   \npróxima linha\n",
        "heading" to "# Título\n",
        "dois parágrafos" to "primeiro\n\nsegundo\n",
        "parágrafos com folga extra" to "primeiro\n\n\n\nsegundo\n",
        "lista" to "- um\n- dois\n  - aninhado\n",
        "lista ordenada" to "1. um\n2. dois\n",
        "tarefas" to "- [ ] pendente\n- [x] feita\n",
        "citação" to "> citação\n> segunda linha\n\ndepois\n",
        "cerca de código" to "```kotlin\nfun x() = 1\n```\n",
        "cerca não fechada" to "```\nsem fechar\n",
        "cerca com linha em branco dentro" to "```\na\n\nb\n```\n",
        "código indentado" to "    indentado\n\ntexto\n",
        "tabela" to "| a | b |\n|:--|--:|\n| 1 | 2 |\n",
        "regra horizontal" to "---\n\ndepois\n",
        "definição de link" to "[ref]: https://example.com\n\nusa [ref]\n",
        "html" to "<div>\n  <b>oi</b>\n</div>\n\ntexto\n",
        "acentos e emoji" to "# Ação, coração 🎉\n\ntexto\n",
        "tab dentro de parágrafo" to "a\tb\n",
        "underscore e asterisco soltos" to "a * b _ c ** d\n",
    )

    @Test
    fun `casos de borda fazem roundtrip byte a byte`() {
        for ((label, text) in edgeCases) {
            assertEquals(text, serialize(parse(text)), "roundtrip falhou em: $label")
        }
    }

    @Test
    fun `documento de referência do Typora faz roundtrip byte a byte`() {
        val text = readResource("typora-ref.md")
        assertEquals(text, serialize(parse(text)), "roundtrip falhou no documento de referência")
    }

    /**
     * Roundtrip sozinho não basta: um `parse` que devolvesse o documento inteiro
     * como um único bloco também passaria. Este teste prende a partição nas
     * fronteiras reais de bloco.
     */
    @Test
    fun `parse separa os blocos nas fronteiras certas`() {
        val doc = parse(
            "# Título\n" +
                "\n" +
                "Parágrafo.\n" +
                "\n" +
                "> citação\n" +
                "\n" +
                "- um\n" +
                "- dois\n" +
                "\n" +
                "```kotlin\nfun x() = 1\n```\n" +
                "\n" +
                "| a | b |\n|---|---|\n| 1 | 2 |\n" +
                "\n" +
                "---\n"
        )
        assertEquals("", doc.leading)
        assertContentEquals(
            listOf(
                BlockType.HEADING,
                BlockType.PARAGRAPH,
                BlockType.BLOCKQUOTE,
                BlockType.UNORDERED_LIST,
                BlockType.CODE_FENCE,
                BlockType.TABLE,
                BlockType.HORIZONTAL_RULE,
            ),
            doc.blocks.map { it.type },
        )
        assertEquals("# Título", doc.blocks[0].source)
        assertEquals("\n\n", doc.blocks[0].trailing)
        assertEquals("- um\n- dois", doc.blocks[3].source)
    }

    @Test
    fun `linhas em branco iniciais viram leading, não um bloco vazio`() {
        val doc = parse("\n\n# Título\n")
        assertEquals("\n\n", doc.leading)
        assertEquals(1, doc.blocks.size)
        assertTrue(doc.blocks.none { it.source.isEmpty() })
    }

    @Test
    fun `ids dos blocos são únicos`() {
        val doc = parse(readResource("typora-ref.md"))
        val ids = doc.blocks.map { it.id }
        assertEquals(ids.size, ids.toSet().size, "ids repetidos quebram o key do LazyColumn")
    }

    private fun readResource(name: String): String =
        checkNotNull(javaClass.classLoader.getResourceAsStream(name)) { "recurso ausente: $name" }
            .bufferedReader()
            .readText()
}
