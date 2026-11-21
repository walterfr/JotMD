package io.github.walterfr.jotmd.editor.ops

import io.github.walterfr.jotmd.editor.MdThemePreset
import io.github.walterfr.jotmd.editor.MdTokens
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SyntaxHighlightTest {

    @Test
    fun `highlightCode preserva o texto original byte a byte`() {
        val samples = listOf(
            "" to null,
            "fun soma(a: Int, b: Int) = a + b // soma simples\nval s = \"fun class 123\"" to "kotlin",
            "# comentário\ndef fib(n):\n    return n if n <= 1 else fib(n - 1)\n" to "python",
            "-- consulta\nSELECT id, 'FROM' FROM notas WHERE id = 42;\n" to "sql",
            "{\n  \"chave\": \"valor\",\n  \"ativo\": true,\n  \"total\": 3.14\n}" to "json",
            "title: Teste\ncount: 10 # nota\n" to "yaml",
            "/* bloco */\nconst msg = `olá \${nome}`;\nlet x = 0xFF;" to "typescript",
        )
        for ((code, lang) in samples) {
            val highlighted = highlightCode(code, lang)
            assertEquals(code, highlighted.text)
        }
    }

    @Test
    fun `highlightCode aplica cores para comentários, strings, números e palavras-chave`() {
        try {
            MdTokens.themePreset = MdThemePreset.GITHUB
            val code = "val x = 42 // return \"fake\"\nval s = \"if 99\""
            val s = highlightCode(code, "kotlin")

            assertEquals(code, s.text)
            assertTrue(s.spanStyles.any { it.item.color == MdTokens.syntaxKeyword && code.substring(it.start, it.end) == "val" })
            assertTrue(s.spanStyles.any { it.item.color == MdTokens.syntaxNumber && code.substring(it.start, it.end) == "42" })
            assertTrue(s.spanStyles.any { it.item.color == MdTokens.syntaxComment && code.substring(it.start, it.end).startsWith("//") })
            assertTrue(s.spanStyles.any { it.item.color == MdTokens.syntaxString && code.substring(it.start, it.end) == "\"if 99\"" })
            // Palavras dentro de string/comentário não ganham cor de keyword.
            assertTrue(s.spanStyles.none { it.item.color == MdTokens.syntaxKeyword && code.substring(it.start, it.end) in setOf("return", "if") })
        } finally {
            MdTokens.themePreset = MdThemePreset.GITHUB
        }
    }

    @Test
    fun `highlightCode reage ao tema escuro e suporta SQL, Python e JSON`() {
        try {
            MdTokens.themePreset = MdThemePreset.NIGHT
            val sql = highlightCode("SELECT name FROM users -- id\n", "sql")
            assertTrue(sql.spanStyles.any { it.item.color == MdTokens.syntaxKeyword && sql.text.substring(it.start, it.end) == "SELECT" })
            assertTrue(sql.spanStyles.any { it.item.color == MdTokens.syntaxComment && sql.text.substring(it.start, it.end) == "-- id" })

            val py = highlightCode("# py\ndef f(): return True\n", "python")
            assertTrue(py.spanStyles.any { it.item.color == MdTokens.syntaxComment && py.text.substring(it.start, it.end) == "# py" })
            assertTrue(py.spanStyles.any { it.item.color == MdTokens.syntaxKeyword && py.text.substring(it.start, it.end) == "def" })

            val json = highlightCode("{\"name\": \"JotMD\", \"n\": 7}", "json")
            assertTrue(json.spanStyles.any { it.item.color == MdTokens.syntaxType && json.text.substring(it.start, it.end) == "\"name\"" })
            assertTrue(json.spanStyles.any { it.item.color == MdTokens.syntaxString && json.text.substring(it.start, it.end) == "\"JotMD\"" })
            assertTrue(json.spanStyles.any { it.item.color == MdTokens.syntaxNumber && json.text.substring(it.start, it.end) == "7" })
        } finally {
            MdTokens.themePreset = MdThemePreset.GITHUB
        }
    }

    @Test
    fun `highlightMarkdownSource preserva o texto original e colore sintaxe do modo fonte`() {
        val md = """
            # Título

            > Citação com **negrito**, *itálico*, `código`, [link](https://x.com) e [[wiki]]

            - item com ${'$'}x^2${'$'}
            1. segundo

            ```kotlin
            val a = 1
            ```

            ---
        """.trimIndent()

        val s = highlightMarkdownSource(md)
        assertEquals(md, s.text)
        assertTrue(s.spanStyles.any { it.item.color == MdTokens.syntaxHeading && md.substring(it.start, it.end) == "Título" })
        assertTrue(s.spanStyles.any { it.item.color == MdTokens.syntaxMarker && md.substring(it.start, it.end) == ">" || md.substring(it.start, it.end) == "> " })
        assertTrue(s.spanStyles.any { it.item.color == MdTokens.syntaxMarker && md.substring(it.start, it.end) == "---" })
        assertTrue(s.spanStyles.any { it.item.color == MdTokens.syntaxLink && md.substring(it.start, it.end) == "[link](https://x.com)" })
        assertTrue(s.spanStyles.any { it.item.color == MdTokens.syntaxLink && md.substring(it.start, it.end) == "[[wiki]]" })
        assertTrue(s.spanStyles.any { it.item.color == MdTokens.syntaxNumber && md.substring(it.start, it.end) == "\$x^2\$" })
        assertTrue(s.spanStyles.any { it.item.color == MdTokens.syntaxComment && md.substring(it.start, it.end) == "val a = 1" })
        assertTrue(s.spanStyles.any { it.item.background == MdTokens.inlineCodeBackground && md.substring(it.start, it.end) == "`código`" })
    }
}
