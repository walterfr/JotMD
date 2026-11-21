package io.github.walterfr.jotmd.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PdfExportTest {

    @Test
    fun `gera cabeçalho PDF 1_4, trailer EOF, catálogo, páginas, fontes e WinAnsiEncoding`() {
        val bytes = exportToPdf("Olá mundo", title = "Nota de Teste")
        val pdf = bytes.toString(Charsets.ISO_8859_1)

        assertTrue(pdf.startsWith("%PDF-1.4\n"), "PDF deve iniciar com %PDF-1.4")
        assertTrue(pdf.endsWith("%%EOF\n"), "PDF deve terminar com %%EOF")
        assertTrue("/Type /Catalog" in pdf)
        assertTrue("/Type /Pages" in pdf)
        assertTrue("/Type /Page" in pdf)
        assertTrue("/MediaBox [0 0 595 842]" in pdf)
        assertTrue("/WinAnsiEncoding" in pdf)
        assertTrue("/F1 3 0 R" in pdf)
        assertTrue("/F2 4 0 R" in pdf)
        assertTrue("/F3 5 0 R" in pdf)
        assertTrue("/F4 6 0 R" in pdf)
        assertTrue("/BaseFont /Helvetica" in pdf)
        assertTrue("/BaseFont /Helvetica-Bold" in pdf)
        assertTrue("/BaseFont /Helvetica-Oblique" in pdf)
        assertTrue("/BaseFont /Courier" in pdf)
        assertTrue("/Title (Nota de Teste)" in pdf)
        assertTrue("/Producer (JotMD)" in pdf)

        assertValidPdfStructure(bytes)
    }

    @Test
    fun `startxref aponta exatamente para xref e offsets batem com objetos e streams`() {
        val md = "# Título\n\nParágrafo com **negrito** e `código`."
        val bytes = exportToPdf(md, title = "Verificação XRef")
        assertValidPdfStructure(bytes)
    }

    @Test
    fun `documento vazio gera PDF válido com uma página e rodapé`() {
        val bytes = exportToPdf("", title = "Vazio")
        val pdf = bytes.toString(Charsets.ISO_8859_1)

        assertValidPdfStructure(bytes)
        assertTrue("/Count 1" in pdf)
        assertTrue("(Vazio - P\\341gina 1 de 1)" in pdf)
    }

    @Test
    fun `converte front matter YAML sem os delimitadores em fonte monoespaçada`() {
        val md = "---\ntitle: Meu Documento\ntags: [kotlin, pdf]\n---\n\nCorpo."
        val bytes = exportToPdf(md)
        val pdf = bytes.toString(Charsets.ISO_8859_1)

        assertValidPdfStructure(bytes)
        assertTrue("0.96 0.96 0.96 rg" in pdf)
        assertTrue("/F4 9.50 Tf" in pdf)
        assertTrue("(title: Meu Documento)" in pdf)
        assertTrue("(tags: [kotlin, pdf])" in pdf)
        assertFalse("(---)" in pdf)
    }

    @Test
    fun `converte headings H1 a H6 com tamanhos, cores e linha abaixo de H1 e H2`() {
        val md = """
            # Título 1

            ## Título 2

            ### Título 3

            #### Título 4

            ##### Título 5

            ###### Título 6

            Setext Um
            =========

            Setext Dois
            -----------
        """.trimIndent()
        val bytes = exportToPdf(md)
        val pdf = bytes.toString(Charsets.ISO_8859_1)

        assertValidPdfStructure(bytes)
        assertTrue("/F2 22.00 Tf" in pdf)
        assertTrue("/F2 18.00 Tf" in pdf)
        assertTrue("/F2 15.00 Tf" in pdf)
        assertTrue("/F2 13.00 Tf" in pdf)
        assertTrue("/F2 11.50 Tf" in pdf)
        assertTrue("/F2 11.00 Tf" in pdf)
        assertTrue("0.47 0.47 0.47 rg" in pdf)
        assertTrue("0.93 0.93 0.93 RG" in pdf)
        assertTrue("(T\\355tulo 1)" in pdf)
        assertTrue("(Setext Um)" in pdf)
        assertTrue("(Setext Dois)" in pdf)
    }

    @Test
    fun `converte listas aninhadas, ordenadas e tarefas com indentação e marcadores WinAnsi`() {
        val md = """
            - Item raiz
              - Subitem aninhado
            - [ ] Tarefa pendente
            - [x] Tarefa concluída

            1. Primeiro
            2. Segundo
        """.trimIndent()
        val bytes = exportToPdf(md)
        val pdf = bytes.toString(Charsets.ISO_8859_1)

        assertValidPdfStructure(bytes)
        assertTrue("(\\225) Tj" in pdf, "Bullet deve ser emitido em octal WinAnsi \\225")
        assertTrue("([ ]) Tj" in pdf)
        assertTrue("([x]) Tj" in pdf)
        assertTrue("(1.) Tj" in pdf)
        assertTrue("(2.) Tj" in pdf)
        assertTrue("(Item raiz)" in pdf)
        assertTrue("(Subitem aninhado)" in pdf)
        assertTrue("(Tarefa pendente)" in pdf)
        assertTrue("(Tarefa conclu\\355da)" in pdf)
        // Nível 0 começa em textX = 45 + 18 = 63; nível 1 em textX = 45 + 36 = 81.
        assertTrue("1 0 0 1 63.00 " in pdf)
        assertTrue("1 0 0 1 81.00 " in pdf)
    }

    @Test
    fun `converte código cercado com linguagem e código indentado`() {
        val md = """
            ```kotlin
            fun soma(a: Int, b: Int) = a + b
            ```

                linha indentada (x)
        """.trimIndent()
        val bytes = exportToPdf(md)
        val pdf = bytes.toString(Charsets.ISO_8859_1)

        assertValidPdfStructure(bytes)
        assertTrue("0.97 0.97 0.97 rg" in pdf)
        assertTrue("(kotlin)" in pdf)
        assertTrue("/F4 9.50 Tf" in pdf)
        assertTrue("(fun soma\\(a: Int, b: Int\\) = a + b)" in pdf)
        assertTrue("(linha indentada \\(x\\))" in pdf)
    }

    @Test
    fun `converte tabela GFM com alinhamento e texto inline limpo`() {
        val md = """
            | Esq | Cen | Dir |
            |:----|:---:|----:|
            | **a** | `b` | ==c== |
        """.trimIndent()
        val bytes = exportToPdf(md)
        val pdf = bytes.toString(Charsets.ISO_8859_1)

        assertValidPdfStructure(bytes)
        assertTrue("(Esq)" in pdf)
        assertTrue("(Cen)" in pdf)
        assertTrue("(Dir)" in pdf)
        assertTrue("(a)" in pdf)
        assertTrue("(b)" in pdf)
        assertTrue("(c)" in pdf)
        assertFalse("(**a**)" in pdf)
        assertFalse("(==c==)" in pdf)
    }

    @Test
    fun `converte citação comum e GitHub Alerts com cores RGB corretas`() {
        val md = """
            > Citação simples em itálico

            > [!NOTE]
            > Nota azul

            > [!TIP]
            > Dica verde

            > [!IMPORTANT]
            > Importante roxo

            > [!WARNING]
            > Aviso âmbar

            > [!CAUTION]
            > Perigo vermelho
        """.trimIndent()
        val bytes = exportToPdf(md)
        val pdf = bytes.toString(Charsets.ISO_8859_1)

        assertValidPdfStructure(bytes)
        assertTrue("/F3 11.00 Tf" in pdf)
        assertTrue("0.87 0.88 0.9 RG" in pdf)
        assertTrue("0.035 0.412 0.855" in pdf)
        assertTrue("0.122 0.533 0.239" in pdf)
        assertTrue("0.51 0.314 0.875" in pdf)
        assertTrue("0.604 0.404 0.0" in pdf)
        assertTrue("0.812 0.133 0.18" in pdf)
        assertTrue("(Note)" in pdf)
        assertTrue("(Tip)" in pdf)
        assertTrue("(Important)" in pdf)
        assertTrue("(Warning)" in pdf)
        assertTrue("(Caution)" in pdf)
        assertTrue("(Cita\\347\\343o simples em it\\341lico)" in pdf)
    }

    @Test
    fun `converte régua horizontal, marcações inline, escape de parênteses e acentos em português`() {
        val md = """
            Texto **negrito**, *itálico*, ~~riscado~~, ==destaque==, `==protegido==` e `a\b`.

            [site](https://a.com), ![foto](a.png), [[nota|apelido]], #tag/sub e (parênteses).

            ---

            Acentuação: ação, café, coração, limão, • item — travessão – meia-risca ☐ ☑ 😀.
        """.trimIndent()
        val bytes = exportToPdf(md)
        val pdf = bytes.toString(Charsets.ISO_8859_1)

        assertValidPdfStructure(bytes)
        assertTrue("0.9 0.9 0.9 RG" in pdf)
        assertTrue("a\\347\\343o" in pdf)
        assertTrue("caf\\351" in pdf)
        assertTrue("cora\\347\\343o" in pdf)
        assertTrue("lim\\343o" in pdf)
        assertTrue("\\225" in pdf) // •
        assertTrue("\\227" in pdf) // —
        assertTrue("\\226" in pdf) // –
        assertTrue("[ ]" in pdf) // ☐
        assertTrue("[x]" in pdf) // ☑
        assertTrue("site \\(https://a.com\\)" in pdf)
        assertTrue("[Imagem: foto]" in pdf)
        assertTrue("apelido" in pdf)
        assertTrue("==protegido==" in pdf)
        assertTrue("\\(par\\352nteses\\)" in pdf)
        assertTrue("a\\\\b" in pdf)
        // Emoji fora do CP1252 vira '?' sem quebrar ASCII 7-bit do stream.
        assertTrue("?." in pdf)
        assertTrue(bytes.all { (it.toInt() and 0xFF) < 0x80 }, "Stream PDF deve permanecer ASCII seguro de 7 bits")
    }

    @Test
    fun `pagina automaticamente documento longo e adiciona rodapé em todas as páginas`() {
        val paragraphs = (1..75).joinToString("\n\n") { i ->
            "Parágrafo número $i com texto suficiente para preencher a página A4 e forçar paginação automática limpa."
        }
        val bytes = exportToPdf(paragraphs, title = "Longo")
        val pdf = bytes.toString(Charsets.ISO_8859_1)

        assertValidPdfStructure(bytes)
        val count = Regex("""/Count (\d+)""").find(pdf)?.groupValues?.get(1)?.toInt() ?: 0
        assertTrue(count > 1, "Esperava múltiplas páginas, obteve $count")
        for (p in 1..count) {
            assertTrue("(Longo - P\\341gina $p de $count)" in pdf, "Rodapé da página $p ausente")
        }
        assertStreamCoordsWithinBounds(pdf)
    }

    @Test
    fun `pagina bloco de código longo maior que uma página`() {
        val codeLines = (1..120).joinToString("\n") { i -> "val linha$i = $i * 2 // comentário longo" }
        val md = "```kotlin\n$codeLines\n```"
        val bytes = exportToPdf(md, title = "Código Longo")
        val pdf = bytes.toString(Charsets.ISO_8859_1)

        assertValidPdfStructure(bytes)
        val count = Regex("""/Count (\d+)""").find(pdf)?.groupValues?.get(1)?.toInt() ?: 0
        assertTrue(count >= 2, "Bloco de código de 120 linhas deve gerar ao menos 2 páginas, obteve $count")
        assertTrue("(val linha120 = 120 * 2 // coment\\341rio longo)" in pdf)
        assertStreamCoordsWithinBounds(pdf)
    }

    @Test
    fun `quebra linha longa sem espaços sem ultrapassar largura útil`() {
        val token = "A".repeat(180)
        val bytes = exportToPdf(token, title = "Token")
        val pdf = bytes.toString(Charsets.ISO_8859_1)

        assertValidPdfStructure(bytes)
        assertFalse("($token)" in pdf, "Linha sem espaços de 180 chars deveria quebrar dentro de 505 pt")
        assertTrue("(AAAA" in pdf)
    }

    @Test
    fun `exporta documento de referência do Typora completo`() {
        val ref = checkNotNull(javaClass.classLoader.getResourceAsStream("typora-ref.md"))
            .bufferedReader()
            .readText()
        val bytes = exportToPdf(ref, title = "Typora Ref")
        val pdf = bytes.toString(Charsets.ISO_8859_1)

        assertValidPdfStructure(bytes)
        assertTrue("(T\\355tulo n\\355vel 1)" in pdf)
        assertTrue("(Cita\\347\\343o em bloco. Segunda linha da cita\\347\\343o.)" in pdf)
        assertTrue("(kotlin)" in pdf)
        assertTrue("(Coluna B)" in pdf)
        assertTrue("0.9 0.9 0.9 RG" in pdf)
        assertTrue("(bloco de c\\363digo indentado)" in pdf)
        assertTrue("(Typora Ref - P\\341gina 1 de " in pdf)
        assertStreamCoordsWithinBounds(pdf)
    }

    @Test
    fun `renderiza marcador TOC com título Índices e entradas indentadas por nível`() {
        val md = """
            [TOC]

            # Cap 1

            ## Sub 1.1

            ### Detalhe
        """.trimIndent()
        val bytes = exportToPdf(md, title = "Com Sumário")
        val pdf = bytes.toString(Charsets.ISO_8859_1)

        assertValidPdfStructure(bytes)
        assertStreamCoordsWithinBounds(pdf)
        assertTrue("(\\315ndices)" in pdf)
        assertTrue("(Cap 1)" in pdf)
        assertTrue("(Sub 1.1)" in pdf)
        assertTrue("(Detalhe)" in pdf)
        assertFalse("([TOC])" in pdf)

        // Nível 1 em x=45, nível 2 em x=57 (+12pt), nível 3 em x=69 (+24pt).
        assertTrue(Regex("""1 0 0 1 45\.00 [0-9.]+ Tm\n\(Cap 1\) Tj""").containsMatchIn(pdf))
        assertTrue(Regex("""1 0 0 1 57\.00 [0-9.]+ Tm\n\(Sub 1\.1\) Tj""").containsMatchIn(pdf))
        assertTrue(Regex("""1 0 0 1 69\.00 [0-9.]+ Tm\n\(Detalhe\) Tj""").containsMatchIn(pdf))
    }

    @Test
    fun `renderiza matemática inline e em bloco em ASCII seguro sem interrogação em WinAnsi`() {
        val md = """
            Inline ${'$'}\alpha + \beta \le \pi${'$'}, ${'$'}x^2 + y^3 = z^1${'$'}, ${'$'}\sum_{i=1}^n \sqrt{x} \neq \infty \to 0${'$'}.

            ${'$'}${'$'}
            \frac{\alpha + \beta}{2} \ge \int_0^1 x \, dx \approx \leftarrow \Rightarrow \Leftrightarrow
            ${'$'}${'$'}
        """.trimIndent()
        val bytes = exportToPdf(md, title = "Matemática")
        val pdf = bytes.toString(Charsets.ISO_8859_1)

        assertValidPdfStructure(bytes)
        assertStreamCoordsWithinBounds(pdf)
        assertTrue(bytes.all { (it.toInt() and 0xFF) < 0x80 })
        assertFalse("?" in pdf, "Símbolos matemáticos não devem virar '?' em WinAnsi")

        assertTrue("alpha + beta <= pi" in pdf)
        assertTrue("x^2 + y^3 = z^1" in pdf)
        assertTrue("sum_\\(i=1\\)^n sqrt x != inf -> 0" in pdf)
        assertTrue("0.97 0.97 0.97 rg" in pdf)
        assertTrue("\\(alpha + beta\\)/2 >= int_0^1 x dx ~= <- => <=>" in pdf)
    }

    @Test
    fun `limpa tag u, resolve links de referência e renderiza notas de rodapé no PDF`() {
        val md = """
            Texto <u>sublinhado</u>, <u>**forte**</u> e `<u>literal</u>`.

            Links: [Site][ref], [Curto] e ![Foto][img].

            Nota[^b] antes de[^a] e indefinida[^xyz].

            [ref]: https://a.com
            [curto]: https://b.com
            [img]: foto.png

            [^a]: Primeira definição
            [^b]: https://nota.example.com
        """.trimIndent()
        val bytes = exportToPdf(md, title = "Refs")
        val pdf = bytes.toString(Charsets.ISO_8859_1)

        assertValidPdfStructure(bytes)
        assertStreamCoordsWithinBounds(pdf)
        assertTrue("(Texto sublinhado, forte e <u>literal</u>.)" in pdf)
        assertFalse("<u>sublinhado" in pdf)
        assertFalse("sublinhado</u>" in pdf)

        assertTrue("Site \\(https://a.com\\)" in pdf)
        assertTrue("Curto \\(https://b.com\\)" in pdf)
        assertTrue("[Imagem: Foto]" in pdf)
        assertFalse("[ref]" in pdf)
        assertFalse("[img]" in pdf)

        assertTrue("Nota[1] antes de[2] e indefinida[xyz]." in pdf)
        assertTrue("([1] https://nota.example.com)" in pdf)
        assertTrue("([2] Primeira defini\\347\\343o)" in pdf)
    }

    private fun assertValidPdfStructure(bytes: ByteArray) {
        val pdf = bytes.toString(Charsets.ISO_8859_1)
        assertTrue(pdf.startsWith("%PDF-1.4\n"))
        assertTrue(pdf.endsWith("%%EOF\n"))

        val startxrefMatch = Regex("""startxref\n(\d+)\n%%EOF\n$""").find(pdf)
        assertTrue(startxrefMatch != null, "startxref ausente ou malformado")
        val xrefOffset = startxrefMatch.groupValues[1].toInt()
        assertTrue(xrefOffset in 0 until bytes.size)
        val atXref = bytes.copyOfRange(xrefOffset, minOf(bytes.size, xrefOffset + 5)).toString(Charsets.ISO_8859_1)
        assertEquals("xref\n", atXref, "startxref deve apontar exatamente para 'xref\\n'")

        val xrefHeader = Regex("""^xref\n0 (\d+)\n""").find(pdf.substring(xrefOffset))
        assertTrue(xrefHeader != null, "Cabeçalho da tabela xref inválido")
        val size = xrefHeader.groupValues[1].toInt()
        assertTrue("/Size $size" in pdf, "trailer /Size deve bater com quantidade de entradas xref")

        val entriesStart = xrefOffset + xrefHeader.value.length
        for (id in 0 until size) {
            val lineStart = entriesStart + id * 20
            val entry = bytes.copyOfRange(lineStart, lineStart + 20).toString(Charsets.ISO_8859_1)
            if (id == 0) {
                assertEquals("0000000000 65535 f \n", entry)
            } else {
                assertTrue(entry.matches(Regex("""\d{10} 00000 n \n""")), "Entrada xref $id inválida: '$entry'")
                val objOffset = entry.substring(0, 10).toInt()
                val expectedPrefix = "$id 0 obj\n"
                val actualPrefix = bytes.copyOfRange(objOffset, objOffset + expectedPrefix.length)
                    .toString(Charsets.ISO_8859_1)
                assertEquals(expectedPrefix, actualPrefix, "Offset do objeto $id não aponta para '$expectedPrefix'")
            }
        }

        val pageCount = Regex("""/Type /Page\b""").findAll(pdf).count()
        val declaredCount = Regex("""/Count (\d+)""").find(pdf)?.groupValues?.get(1)?.toInt()
        assertEquals(pageCount, declaredCount, "/Count em /Pages deve bater com número real de /Type /Page")

        // Verifica /Length exato em cada stream.
        val streamHeaderRegex = Regex("""<< /Length (\d+) >>\nstream\n""")
        for (m in streamHeaderRegex.findAll(pdf)) {
            val len = m.groupValues[1].toInt()
            val dataStart = m.range.last + 1
            val afterData = pdf.substring(dataStart + len, dataStart + len + "\nendstream\n".length)
            assertEquals("\nendstream\n", afterData, "/Length $len não corresponde ao fim do stream")
        }
    }

    private fun assertStreamCoordsWithinBounds(pdf: String) {
        val tmRegex = Regex("""1 0 0 1 ([0-9.-]+) ([0-9.-]+) Tm""")
        for (m in tmRegex.findAll(pdf)) {
            val x = m.groupValues[1].toFloat()
            val y = m.groupValues[2].toFloat()
            assertTrue(x >= 45f && x < 550f, "Coordenada x fora da área útil: $x")
            assertTrue(y == 26f || (y >= 55f && y <= 797f), "Coordenada y fora das margens: $y")
        }
    }
}
