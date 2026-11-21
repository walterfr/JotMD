package io.github.walterfr.jotmd.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class HtmlToMarkdownTest {

    @Test
    fun `ignora head, script, style, doctype e comentários`() {
        val html = """
            <!DOCTYPE html>
            <html>
            <head>
              <title>Título</title>
              <style>body { color: red; }</style>
              <script>if (a < b) { alert("x"); }</script>
            </head>
            <body>
              <!-- comentário ignorado -->
              <p>Conteúdo visível</p>
              <script>console.log("</p>");</script>
            </body>
            </html>
        """.trimIndent()
        assertEquals("Conteúdo visível", htmlToMarkdown(html))
    }

    @Test
    fun `converte títulos, parágrafos, divs, quebra br e régua horizontal`() {
        val html = """
            <h1>H1</h1>
            <h2>H2</h2>
            <h6>H6</h6>
            <p>Linha um<br />
            Linha dois</p>
            <div>Bloco div</div>
            <hr />
        """.trimIndent()
        val expected = """
            # H1

            ## H2

            ###### H6

            Linha um  
            Linha dois

            Bloco div

            ---
        """.trimIndent()
        assertEquals(expected, htmlToMarkdown(html))
    }

    @Test
    fun `converte formatação inline, código, links e imagens`() {
        val html = """
            <p>Texto <strong>bold</strong>, <b>b</b>, <em>em</em>, <i>i</i>, <del>del</del>, <s>s</s>, <strike>st</strike>, <u>u</u>, <mark>mark</mark>, <code>x &lt; y</code> e <code>com `crase`</code>.</p>
            <p>Veja <a href="https://a.com?x=1&amp;y=2">site</a> e <img src="img.png" alt="foto &quot;1&quot;" />.</p>
            <p>Espaço<b> dentro </b>corrigido.</p>
        """.trimIndent()
        val md = htmlToMarkdown(html)
        assertTrue("**bold**, **b**, *em*, *i*, ~~del~~, ~~s~~, ~~st~~, <u>u</u>, ==mark==, `x < y` e `` com `crase` ``." in md)
        assertTrue("Veja [site](https://a.com?x=1&y=2) e ![foto \"1\"](img.png)." in md)
        assertTrue("Espaço **dentro** corrigido." in md)
    }

    @Test
    fun `converte bloco pre code preservando linguagem e whitespace exato`() {
        val html = "<pre class=\"md-fences\"><code class=\"language-kotlin\">fun x() {\n    val a = 1\n\n    println(a)\n}</code></pre>"
        val expected = "```kotlin\nfun x() {\n    val a = 1\n\n    println(a)\n}\n```"
        assertEquals(expected, htmlToMarkdown(html))

        val nestedBackticks = "<pre><code>```\ndentro\n```</code></pre>"
        assertEquals("````\n```\ndentro\n```\n````", htmlToMarkdown(nestedBackticks))
    }

    @Test
    fun `converte citações, listas aninhadas, ordenadas e tarefas`() {
        val html = """
            <blockquote><p>Linha 1</p><p>Linha 2</p></blockquote>
            <ul>
              <li>item 1<ul><li>subitem</li></ul></li>
              <li><input type="checkbox" disabled /> pendente</li>
              <li><input type="checkbox" disabled checked /> concluída</li>
            </ul>
            <ol start="3">
              <li>terceiro</li>
              <li>quarto</li>
            </ol>
        """.trimIndent()
        val expected = """
            > Linha 1
            >
            > Linha 2

            - item 1
              - subitem
            - [ ] pendente
            - [x] concluída

            3. terceiro
            4. quarto
        """.trimIndent()
        assertEquals(expected, htmlToMarkdown(html))
    }

    @Test
    fun `converte tabela HTML para tabela Markdown GFM com alinhamentos`() {
        val html = """
            <table>
              <thead>
                <tr>
                  <th style="text-align: left">Esq</th>
                  <th style="text-align: center">Cen</th>
                  <th style="text-align: right">Dir</th>
                  <th>Padrão</th>
                </tr>
              </thead>
              <tbody>
                <tr>
                  <td><strong>a</strong></td>
                  <td>a | b</td>
                  <td><code>c</code></td>
                  <td>d</td>
                </tr>
              </tbody>
            </table>
        """.trimIndent()
        val expected = """
            | Esq | Cen | Dir | Padrão |
            | :--- | :---: | ---: | --- |
            | **a** | a \| b | `c` | d |
        """.trimIndent()
        assertEquals(expected, htmlToMarkdown(html))
    }

    @Test
    fun `decodifica entidades HTML comuns, decimais e hexadecimais`() {
        val html = "<p>&amp; &lt; &gt; &quot; &#39; &apos; a&nbsp;b &#123; &#x1F600; &#x3C;</p>"
        assertEquals("& < > \" ' ' a b { 😀 <", htmlToMarkdown(html))
    }

    @Test
    fun `tolera HTML vazio e malformado sem lançar exceção`() {
        assertEquals("", htmlToMarkdown(""))
        assertEquals("", htmlToMarkdown("   \n\t "))

        val malformed = "<div><p>Texto <b>forte e 2 < 3<a href=\"x\" title=\"a > b\">link</a></p></div></section><"
        val md = htmlToMarkdown(malformed)
        assertTrue("Texto **forte e 2 < 3[link](x)**" in md || "**forte" in md)
        assertTrue("<" in md)
    }

    @Test
    fun `faz ida e volta limpa a partir de exportToHtml`() {
        val original = """
            # Título

            Parágrafo com **negrito**, *itálico* e `código`.

            - [ ] tarefa
            - [x] feita
        """.trimIndent()
        val roundtripped = htmlToMarkdown(exportToHtml(original))
        assertTrue("# Título" in roundtripped)
        assertTrue("**negrito**" in roundtripped)
        assertTrue("*itálico*" in roundtripped)
        assertTrue("`código`" in roundtripped)
        assertTrue("- [ ] tarefa" in roundtripped)
        assertTrue("- [x] feita" in roundtripped)
    }
}
