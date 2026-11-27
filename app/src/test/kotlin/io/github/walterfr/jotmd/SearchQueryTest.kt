package io.github.walterfr.jotmd

import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SearchQueryTest {

    private fun matchInfoBlob(phrases: Int, cols: Int, vararg triples: Int): ByteArray {
        val buf = ByteBuffer.allocate((2 + triples.size) * 4).order(ByteOrder.LITTLE_ENDIAN)
        buf.putInt(phrases)
        buf.putInt(cols)
        for (v in triples) buf.putInt(v)
        return buf.array()
    }

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

    @Test
    fun `ftsMatchInfoScore pondera titulo e tags acima do corpo e favorece termos raros`() {
        // 1 frase, 4 colunas (doc_id=0, name=1, body=2, tags=3)
        val titleHit = matchInfoBlob(
            1, 4,
            0, 0, 0, // doc_id
            1, 1, 1, // name
            0, 0, 0, // body
            0, 0, 0, // tags
        )
        val tagsHit = matchInfoBlob(
            1, 4,
            0, 0, 0,
            0, 0, 0,
            0, 0, 0,
            1, 1, 1,
        )
        val bodyHit = matchInfoBlob(
            1, 4,
            0, 0, 0,
            0, 0, 0,
            1, 1, 1,
            0, 0, 0,
        )
        val commonBodyHit = matchInfoBlob(
            1, 4,
            0, 0, 0,
            0, 0, 0,
            1, 20, 20,
            0, 0, 0,
        )

        val titleScore = ftsMatchInfoScore(titleHit)
        val tagsScore = ftsMatchInfoScore(tagsHit)
        val bodyScore = ftsMatchInfoScore(bodyHit)
        val commonBodyScore = ftsMatchInfoScore(commonBodyHit)

        assertTrue(titleScore > tagsScore, "título ($titleScore) deve pontuar acima de tags ($tagsScore)")
        assertTrue(tagsScore > bodyScore, "tags ($tagsScore) deve pontuar acima do corpo ($bodyScore)")
        assertTrue(bodyScore > commonBodyScore, "termo raro ($bodyScore) deve pontuar acima de comum ($commonBodyScore)")
        assertTrue(commonBodyScore > 0.0)
    }

    @Test
    fun `ftsMatchInfoScore retorna zero para blob nulo curto ou malformado`() {
        assertEquals(0.0, ftsMatchInfoScore(null))
        assertEquals(0.0, ftsMatchInfoScore(ByteArray(0)))
        assertEquals(0.0, ftsMatchInfoScore(ByteArray(4)))
        assertEquals(0.0, ftsMatchInfoScore(ByteArray(9))) // desalinhado
        assertEquals(0.0, ftsMatchInfoScore(matchInfoBlob(1, 4, 0, 0, 0))) // truncado
        assertEquals(0.0, ftsMatchInfoScore(matchInfoBlob(0, 4)))
    }
}
