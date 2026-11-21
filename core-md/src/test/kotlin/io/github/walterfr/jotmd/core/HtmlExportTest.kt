package io.github.walterfr.jotmd.core

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class HtmlExportTest {

    @Test
    fun `estrutura Typora e escape de título`() {
        val html = exportToHtml("Olá", title = "Nota <1> & \"A\"")
        assertTrue(html.startsWith("<!doctype html>"))
        assertTrue("<meta charset=\"utf-8\" />" in html)
        assertTrue("<title>Nota &lt;1&gt; &amp; &quot;A&quot;</title>" in html)
        assertTrue("<body class=\"typora-export\">" in html)
        assertTrue("<div class=\"typora-export-content\">" in html)
        assertTrue("<div id=\"write\">" in html)
    }

    @Test
    fun `emite tokens de tema claro e escuro com classes esperadas`() {
        val light = exportToHtml("x", darkTheme = false)
        for (token in listOf(
            "#ffffff", "#333333", "#4183C4", "#dfe2e5", "#f8f8f8", "#f3f4f4",
            "#e7eaed", "#f7f7f7", "#eeeeee", "#e7e7e7", "max-width: 860px",
            "#0969da", "#8250df", "#9a6700", "#1f883d", "#cf222e",
            ".md-meta-block", ".md-fences", ".md-alert", ".md-alert-note",
            ".md-alert-tip", ".md-alert-important", ".md-alert-warning",
            ".md-alert-caution", ".md-alert-title", "input[type=\"checkbox\"]",
            ".md-math-block", ".md-inline-math", ".md-footnote-def",
        )) {
            assertTrue(token in light, "token claro ausente: $token")
        }

        val dark = exportToHtml("x", darkTheme = true)
        for (token in listOf(
            "#363B40", "#b8bfc6", "#DEDEDE", "#6dc1e7", "#474d54",
            "#333333", "#2E3033", "#D3D40E", "#222",
            "#2f81f7", "#a371f7", "#d29922", "#1f883d", "#cf222e",
        )) {
            assertTrue(token in dark, "token escuro ausente: $token")
        }
    }

    @Test
    fun `converte front matter e mantém traços no meio como hr`() {
        val md = "---\ntitle: <Teste>\ntags: [a, b]\n---\n\n---\n"
        val html = exportToHtml(md)
        assertTrue("<pre class=\"md-meta-block\">title: &lt;Teste&gt;\ntags: [a, b]</pre>" in html)
        assertTrue("<hr />" in html)
    }

    @Test
    fun `converte headings ATX e Setext com id de slug sem vazar marcadores`() {
        val md = "# H1\n\n###### H6\n\nSetext Um\n=========\n\nSetext Dois\n-----------\n"
        val html = exportToHtml(md)
        assertTrue("<h1 id=\"h1\">H1</h1>" in html)
        assertTrue("<h6 id=\"h6\">H6</h6>" in html)
        assertTrue("<h1 id=\"setext-um\">Setext Um</h1>" in html)
        assertTrue("<h2 id=\"setext-dois\">Setext Dois</h2>" in html)
    }

    @Test
    fun `converte citação comum e todos os tipos de alerta`() {
        val md = """
            > Citação normal
            > segunda linha

            > [!NOTE]
            > Nota simples

            > [!TIP] Dica direta

            > [!IMPORTANT]
            > Aviso importante

            > [!WARNING]
            > Cuidado

            > [!CAUTION]
            > Perigo
        """.trimIndent()
        val html = exportToHtml(md)
        assertTrue("<blockquote><p>Citação normal\nsegunda linha</p></blockquote>" in html)
        assertFalse("&gt; Citação" in html)
        assertTrue("<div class=\"md-alert md-alert-note\"><p class=\"md-alert-title\">Note</p><p>Nota simples</p></div>" in html)
        assertTrue("<div class=\"md-alert md-alert-tip\"><p class=\"md-alert-title\">Tip</p><p>Dica direta</p></div>" in html)
        assertTrue("<div class=\"md-alert md-alert-important\"><p class=\"md-alert-title\">Important</p><p>Aviso importante</p></div>" in html)
        assertTrue("<div class=\"md-alert md-alert-warning\"><p class=\"md-alert-title\">Warning</p><p>Cuidado</p></div>" in html)
        assertTrue("<div class=\"md-alert md-alert-caution\"><p class=\"md-alert-title\">Caution</p><p>Perigo</p></div>" in html)
    }

    @Test
    fun `converte listas aninhadas, ordenadas e tarefas`() {
        val md = """
            - item 1
              - subitem
            - [ ] pendente
            - [x] concluída

            3. terceiro
            4. quarto
        """.trimIndent()
        val html = exportToHtml(md)
        assertTrue("<ul>\n<li>item 1\n<ul>\n<li>subitem</li>\n</ul></li>" in html)
        assertTrue("<li><input type=\"checkbox\" disabled /> pendente</li>" in html)
        assertTrue("<li><input type=\"checkbox\" disabled checked /> concluída</li>" in html)
        assertTrue("<ol start=\"3\">\n<li>terceiro</li>\n<li>quarto</li>\n</ol>" in html)
    }

    @Test
    fun `converte blocos de código cercado e indentado com escape HTML`() {
        val md = """
            ```kotlin
            val x = a < b && c > "d"
            ```

            ```
            sem linguagem
            ```

                indentado <tag>
        """.trimIndent()
        val html = exportToHtml(md)
        assertTrue(
            "<pre class=\"md-fences\"><code class=\"language-kotlin\">val x = a &lt; b &amp;&amp; c &gt; &quot;d&quot;</code></pre>" in html,
        )
        assertTrue("<pre class=\"md-fences\"><code>sem linguagem</code></pre>" in html)
        assertTrue("<pre class=\"md-fences\"><code>indentado &lt;tag&gt;</code></pre>" in html)
    }

    @Test
    fun `converte tabela GFM com alinhamento e formatação inline`() {
        val md = """
            | Esq | Cen | Dir | Padrão |
            |:----|:---:|----:|--------|
            | **a** | `b` | ==c== | d |
        """.trimIndent()
        val html = exportToHtml(md)
        assertTrue(
            "<table>\n<thead>\n<tr>" +
                "<th style=\"text-align: left\">Esq</th>" +
                "<th style=\"text-align: center\">Cen</th>" +
                "<th style=\"text-align: right\">Dir</th>" +
                "<th>Padrão</th>" +
                "</tr>\n</thead>\n<tbody>\n<tr>" +
                "<td style=\"text-align: left\"><strong>a</strong></td>" +
                "<td style=\"text-align: center\"><code>b</code></td>" +
                "<td style=\"text-align: right\"><mark>c</mark></td>" +
                "<td>d</td>" +
                "</tr>\n</tbody>\n</table>" in html,
        )
    }

    @Test
    fun `converte formatação inline, wikilinks, tags, imagens e protege código e pontuação`() {
        val md = """
            Texto **bold**, *em*, ~~del~~, `code <x>`, ==mark==, [site](https://a.com?x=1&y=2), <https://auto.org>, ![foto "1"](img.png), [[nota|apelido]], #tag/sub e a (b) c 2*3=6.

            `==fora== #naotag [[naowiki]]` e `==a` ==dentro==
        """.trimIndent()
        val html = exportToHtml(md)
        assertTrue("<strong>bold</strong>" in html)
        assertTrue("<em>em</em>" in html)
        assertTrue("<del>del</del>" in html)
        assertTrue("<code>code &lt;x&gt;</code>" in html)
        assertTrue("<mark>mark</mark>" in html)
        assertTrue("<a href=\"https://a.com?x=1&amp;y=2\">site</a>" in html)
        assertTrue("<a href=\"https://auto.org\">https://auto.org</a>" in html)
        assertTrue("<img src=\"img.png\" alt=\"foto &quot;1&quot;\" />" in html)
        assertTrue("<a class=\"md-wikilink\" href=\"#nota\">apelido</a>" in html)
        assertTrue("<span class=\"md-tag\">#tag/sub</span>" in html)
        assertTrue("a (b) c 2*3=6." in html)
        assertTrue("<code>==fora== #naotag [[naowiki]]</code>" in html)
        assertTrue("<code>==a</code> <mark>dentro</mark>" in html)
    }

    @Test
    fun `exporta documento de referência do Typora completo`() {
        val ref = checkNotNull(javaClass.classLoader.getResourceAsStream("typora-ref.md"))
            .bufferedReader()
            .readText()
        val html = exportToHtml(ref, title = "Ref")
        assertTrue("<h1 id=\"título-nível-1\">Título nível 1</h1>" in html)
        assertTrue("<blockquote><p>Citação em bloco.\nSegunda linha da citação.</p></blockquote>" in html)
        assertTrue("<pre class=\"md-fences\"><code class=\"language-kotlin\">" in html)
        assertTrue("<th style=\"text-align: center\">Coluna B</th>" in html)
        assertTrue("<hr />" in html)
        assertTrue("<pre class=\"md-fences\"><code>bloco de código indentado</code></pre>" in html)
    }

    @Test
    fun `converte marcadores TOC em navegação com âncoras únicas`() {
        val md = """
            [TOC]

            # Intro

            ## Intro

            ### **Detalhes**
        """.trimIndent()
        val html = exportToHtml(md)
        assertTrue(".md-toc" in html)
        assertTrue(".md-toc-content" in html)
        assertTrue(".md-toc-item" in html)
        assertTrue(".md-toc-h1" in html)
        assertTrue(".md-toc-h6" in html)
        assertTrue("<div class=\"md-toc\" role=\"navigation\"><p class=\"md-toc-content\">" in html)
        assertTrue(
            "<span class=\"md-toc-item md-toc-h1\"><a class=\"md-toc-inner\" href=\"#intro\">Intro</a></span>" in html,
        )
        assertTrue(
            "<span class=\"md-toc-item md-toc-h2\"><a class=\"md-toc-inner\" href=\"#intro-1\">Intro</a></span>" in html,
        )
        assertTrue(
            "<span class=\"md-toc-item md-toc-h3\"><a class=\"md-toc-inner\" href=\"#detalhes\">Detalhes</a></span>" in html,
        )
        assertTrue("<h1 id=\"intro\">Intro</h1>" in html)
        assertTrue("<h2 id=\"intro-1\">Intro</h2>" in html)
        assertTrue("<h3 id=\"detalhes\"><strong>Detalhes</strong></h3>" in html)
    }

    @Test
    fun `suporta tag inline u com formatação aninhada e protege código`() {
        val md = """
            Texto <u>sublinhado</u>, <u>**negrito**</u>, **<u>forte</u>**, <U>==marca==</U> e `<u>literal</u>`.
        """.trimIndent()
        val html = exportToHtml(md)
        assertTrue("<u>sublinhado</u>" in html)
        assertTrue("<u><strong>negrito</strong></u>" in html)
        assertTrue("<strong><u>forte</u></strong>" in html)
        assertTrue("<u><mark>marca</mark></u>" in html)
        assertTrue("<code>&lt;u&gt;literal&lt;/u&gt;</code>" in html)
    }

    @Test
    fun `resolve links de referência e imagens por referência sem emitir definições`() {
        val md = """
            Veja [Guia][Ref Um], [Colapsado][], [Curto], ![Logo][img] e [Indefinido].

            [ref um]: https://um.example.com "Título"
            [colapsado]: <https://dois.example.com>
            [curto]: https://tres.example.com
            [img]: logo.png
        """.trimIndent()
        val html = exportToHtml(md)
        assertTrue("<a href=\"https://um.example.com\">Guia</a>" in html)
        assertTrue("<a href=\"https://dois.example.com\">Colapsado</a>" in html)
        assertTrue("<a href=\"https://tres.example.com\">Curto</a>" in html)
        assertTrue("<img src=\"logo.png\" alt=\"Logo\" />" in html)
        assertTrue("[Indefinido]" in html)
        assertFalse("https://um.example.com \"Título\"" in html)
    }

    @Test
    fun `converte notas de rodapé inline e definições inclusive tipo link_definition`() {
        val md = """
            Texto[^beta] depois[^alfa], indefinida[^falta] e código `[^code]`.

            [^alfa]: Nota **A** com [Ref].
            [^beta]: https://beta.example.com

            [ref]: https://ref.example.com
        """.trimIndent()
        val html = exportToHtml(md)
        assertTrue("<sup class=\"md-footnote\"><a href=\"#fn-beta\">[1]</a></sup>" in html)
        assertTrue("<sup class=\"md-footnote\"><a href=\"#fn-alfa\">[2]</a></sup>" in html)
        assertTrue("indefinida[^falta]" in html)
        assertTrue("<code>[^code]</code>" in html)
        assertTrue(
            "<div class=\"md-footnote-def\" id=\"fn-alfa\"><span class=\"md-footnote-num\">[2]</span> Nota <strong>A</strong> com <a href=\"https://ref.example.com\">Ref</a>.</div>" in html,
        )
        assertTrue(
            "<div class=\"md-footnote-def\" id=\"fn-beta\"><span class=\"md-footnote-num\">[1]</span>" in html,
        )
        assertTrue("https://beta.example.com" in html)
    }

    @Test
    fun `converte blocos e spans matemáticos LaTeX protegendo moedas e código`() {
        val md = """
            Inline ${'$'}\alpha^2 + \beta \le \gamma${'$'}, ==${'$'}x^2${'$'}==, preço ${'$'}5 e ${'$'}10, e `${'$'}raw${'$'}`.

            ${'$'}${'$'}E = mc^2${'$'}${'$'}

            ${'$'}${'$'}
            \frac{a + b}{2}
            ${'$'}${'$'}
        """.trimIndent()
        val html = exportToHtml(md)
        assertTrue("<span class=\"md-inline-math\">α² + β ≤ γ</span>" in html)
        assertTrue("<mark><span class=\"md-inline-math\">x²</span></mark>" in html)
        assertTrue("preço \$5 e \$10" in html)
        assertTrue("<code>\$raw\$</code>" in html)
        assertTrue("<div class=\"md-math-block\">E = mc²</div>" in html)
        assertTrue("<div class=\"md-math-block\">(a + b)/2</div>" in html)
    }
}
