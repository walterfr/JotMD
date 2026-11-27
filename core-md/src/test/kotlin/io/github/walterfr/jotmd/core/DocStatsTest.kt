package io.github.walterfr.jotmd.core

import kotlin.test.Test
import kotlin.test.assertEquals

class DocStatsTest {

    @Test
    fun `documento vazio zera todas as métricas`() {
        assertEquals(
            DocStats(words = 0, chars = 0, charsNoSpaces = 0, lines = 0, readingTimeMinutes = 0),
            docStatsOf(""),
        )
    }

    @Test
    fun `conta contrações, hifens, versão numérica e ignora marcadores isolados`() {
        val md = """
            # Título

            > Citação: don't use quarta-feira na v0.1.0 --- ok!

            - * | > # ---
        """.trimIndent()
        val stats = docStatsOf(md)

        // Palavras: Título(1), Citação(2), don't(3), use(4), quarta-feira(5), na(6), v0.1.0(7), ok(8)
        assertEquals(8, stats.words)
        assertEquals(5, stats.lines)
        assertEquals(1, stats.readingTimeMinutes)
    }

    @Test
    fun `conta cada caractere CJK individualmente sem somar emojis`() {
        assertEquals(4, docStatsOf("Olá 日本語").words)
        assertEquals(3, docStatsOf("日本語").words)
        assertEquals(4, docStatsOf("Olá 🎉 日本語").words)
    }

    @Test
    fun `exclui front matter, regra horizontal, definição de link, TOC e cercas de código`() {
        val md = """
            ---
            title: Ignorado
            tags: [a, b]
            ---

            [TOC]

            {:toc}

            [[_TOC_]]

            ---

            [ref]: https://example.com

            ```kotlin
            val x = 1
            ```

            Texto final
        """.trimIndent()
        val stats = docStatsOf(md)

        // Só contabiliza "val x = 1" (3 palavras: val, x, 1) e "Texto final" (2 palavras)
        assertEquals(5, stats.words)
        // "val x = 1" (9) + "Texto final" (11) = 20
        assertEquals(20, stats.chars)
        // "valx=1" (6) + "Textofinal" (10) = 16
        assertEquals(16, stats.charsNoSpaces)
        assertEquals(1, stats.readingTimeMinutes)
    }

    @Test
    fun `cerca de código vazia e não fechada ignoram apenas delimitadores presentes`() {
        val emptyFence = docStatsOf("```\n```")
        assertEquals(0, emptyFence.words)
        assertEquals(0, emptyFence.chars)
        assertEquals(0, emptyFence.charsNoSpaces)

        val unclosed = docStatsOf("~~~python\nprint(1)\n")
        assertEquals(2, unclosed.words)
        assertEquals(8, unclosed.chars)
        assertEquals(8, unclosed.charsNoSpaces)
    }

    @Test
    fun `tempo de leitura usa 382 WPM com piso 1 e arredondamento`() {
        val oneWord = docStatsOf("palavra")
        assertEquals(1, oneWord.readingTimeMinutes)

        val belowHalf = docStatsOf("w ".repeat(572).trim())
        assertEquals(572, belowHalf.words)
        assertEquals(1, belowHalf.readingTimeMinutes)

        val atHalf = docStatsOf("w ".repeat(573).trim())
        assertEquals(573, atHalf.words)
        assertEquals(2, atHalf.readingTimeMinutes)

        val customWpm = docStatsOf("w ".repeat(200).trim(), wordsPerMinute = 100)
        assertEquals(2, customWpm.readingTimeMinutes)
    }

    @Test
    fun `selectionStatsOf retorna zeros para string vazia`() {
        assertEquals(
            SelectionStats(words = 0, chars = 0, charsNoSpaces = 0),
            selectionStatsOf(""),
        )
    }

    @Test
    fun `selectionStatsOf conta apenas chars brutos para string só com espaços`() {
        assertEquals(
            SelectionStats(words = 0, chars = 8, charsNoSpaces = 0),
            selectionStatsOf("   \n\t\r  "),
        )
    }

    @Test
    fun `selectionStatsOf conta contrações, termos hifenizados e decimais em prosa ASCII`() {
        assertEquals(
            SelectionStats(words = 5, chars = 34, charsNoSpaces = 30),
            selectionStatsOf("don't use quarta-feira v0.1.0, ok!"),
        )
        assertEquals(
            SelectionStats(words = 3, chars = 14, charsNoSpaces = 12),
            selectionStatsOf("don’t pay 3.14"),
        )
    }

    @Test
    fun `selectionStatsOf conta cada caractere CJK sem contar emojis como palavra`() {
        assertEquals(
            SelectionStats(words = 4, chars = 7, charsNoSpaces = 6),
            selectionStatsOf("Olá 日本語"),
        )
        assertEquals(
            SelectionStats(words = 6, chars = 7, charsNoSpaces = 6),
            selectionStatsOf("日本語\nテスト"),
        )
        assertEquals(
            SelectionStats(words = 4, chars = 10, charsNoSpaces = 8),
            selectionStatsOf("Olá 🎉 日本語"),
        )
    }
}
