package io.github.walterfr.jotmd

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SearchQueryTest {

    @Test
    fun `cada palavra vira prefixo entre aspas, todas obrigatórias`() {
        assertEquals("\"plano\"* \"aula\"*", ftsQuery("plano aula"))
    }

    @Test
    fun `sintaxe do FTS digitada pelo usuário não vaza pro MATCH`() {
        // Aspas soltas, OR/NOT/NEAR, parênteses e * quebrariam ou mudariam a
        // consulta se fossem passados crus.
        // Entre aspas, OR deixa de ser operador e vira só mais uma palavra.
        assertEquals("\"a\"* \"or\"* \"b\"*", ftsQuery("\"a\" OR (b)*"))
        assertEquals("\"not\"*", ftsQuery("-NOT"))
    }

    @Test
    fun `tag busca a palavra sem o cerquilha`() {
        // O tokenizer trata # como separador: no índice "#projeto" é "projeto".
        assertEquals("\"projeto\"*", ftsQuery("#projeto"))
    }

    @Test
    fun `acentos e maiúsculas passam, o tokenizer cuida deles`() {
        assertEquals("\"ação\"*", ftsQuery("Ação"))
    }

    @Test
    fun `trecho achata quebras de linha e devolve onde destacar`() {
        // Quebra de linha no corpo fazia o termo cair fora das 2 linhas
        // exibidas — visto no tablet: só aparecia "# Plano de aula".
        val raw = "# Plano\n\nRevisão de \u0002ação\u0003 e [link](x)"
        val (text, marks) = snippetMarks(raw)
        assertEquals("# Plano Revisão de ação e [link](x)", text)
        assertEquals(listOf(19 until 23), marks)
        assertEquals("ação", text.substring(marks.single()))
    }

    @Test
    fun `entrada sem nenhuma palavra não vira consulta`() {
        assertNull(ftsQuery(""))
        assertNull(ftsQuery("   "))
        assertNull(ftsQuery("\"* # ()"))
    }
}
