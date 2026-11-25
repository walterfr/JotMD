package io.github.walterfr.jotmd.core

import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DocumentImportTest {

    @Test
    fun `importHtmlBytes remove BOM UTF-8 e converte HTML para Markdown`() {
        val html = "\uFEFF<h1>Título</h1><p>Texto com <strong>negrito</strong> e <em>itálico</em>.</p>"
        val md = importHtmlBytes(html.toByteArray(Charsets.UTF_8))
        assertEquals("# Título\n\nTexto com **negrito** e *itálico*.", md)
    }

    @Test
    fun `importDocxBytes converte títulos, formatação inline, links, listas, quebras e tabela`() {
        val documentXml = """
            <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
            <w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main"
                        xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">
              <w:body>
                <w:p>
                  <w:pPr><w:pStyle w:val="Title"/></w:pPr>
                  <w:r><w:t>Documento Principal</w:t></w:r>
                </w:p>
                <w:p>
                  <w:pPr><w:pStyle w:val="Título 2"/></w:pPr>
                  <w:r><w:t>Seção Dois</w:t></w:r>
                </w:p>
                <w:p>
                  <w:pPr><w:outlineLvl w:val="2"/></w:pPr>
                  <w:r><w:t>Subseção Outline</w:t></w:r>
                </w:p>
                <w:p>
                  <w:r><w:rPr><w:b/></w:rPr><w:t>Negrito</w:t></w:r>
                  <w:r><w:t xml:space="preserve"> </w:t></w:r>
                  <w:r><w:rPr><w:b w:val="0"/><w:i/></w:rPr><w:t>Itálico</w:t></w:r>
                  <w:r><w:t xml:space="preserve"> </w:t></w:r>
                  <w:r><w:rPr><w:strike/></w:rPr><w:t>Riscado</w:t></w:r>
                  <w:r><w:t xml:space="preserve"> </w:t></w:r>
                  <w:r><w:rPr><w:u w:val="single"/></w:rPr><w:t>Sublinhado</w:t></w:r>
                  <w:r><w:t xml:space="preserve"> </w:t></w:r>
                  <w:r><w:rPr><w:u w:val="none"/><w:rFonts w:ascii="Consolas"/></w:rPr><w:t>println()</w:t></w:r>
                  <w:r><w:t xml:space="preserve"> </w:t></w:r>
                  <w:hyperlink r:id="rId1">
                    <w:r><w:t>Site</w:t></w:r>
                  </w:hyperlink>
                </w:p>
                <w:p>
                  <w:r><w:t>Linha A</w:t><w:br/><w:t>Col1</w:t><w:tab/><w:t>Col2</w:t></w:r>
                </w:p>
                <w:p>
                  <w:pPr><w:numPr><w:ilvl w:val="0"/><w:numId w:val="1"/></w:numPr></w:pPr>
                  <w:r><w:t>Item raiz</w:t></w:r>
                </w:p>
                <w:p>
                  <w:pPr><w:numPr><w:ilvl w:val="1"/><w:numId w:val="1"/></w:numPr></w:pPr>
                  <w:r><w:t>Subitem</w:t></w:r>
                </w:p>
                <w:tbl>
                  <w:tr>
                    <w:tc><w:p><w:r><w:t>Nome</w:t></w:r></w:p></w:tc>
                    <w:tc><w:p><w:r><w:t>Valor</w:t></w:r></w:p></w:tc>
                  </w:tr>
                  <w:tr>
                    <w:tc><w:p><w:r><w:t>A | B</w:t></w:r></w:p></w:tc>
                    <w:tc><w:p><w:r><w:t>10</w:t></w:r></w:p></w:tc>
                  </w:tr>
                </w:tbl>
              </w:body>
            </w:document>
        """.trimIndent()

        val relsXml = """
            <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
            <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
              <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/hyperlink" Target="https://example.org" TargetMode="External"/>
            </Relationships>
        """.trimIndent()

        val zip = buildZip(
            "word/document.xml" to documentXml.toByteArray(Charsets.UTF_8),
            "word/_rels/document.xml.rels" to relsXml.toByteArray(Charsets.UTF_8),
        )

        val md = importDocxBytes(zip)
        val expected = """
            # Documento Principal

            ## Seção Dois

            ### Subseção Outline

            **Negrito** *Itálico* ~~Riscado~~ <u>Sublinhado</u> `println()` [Site](https://example.org)

            Linha A  
            Col1	Col2

            - Item raiz
              - Subitem

            | Nome | Valor |
            | --- | --- |
            | A \| B | 10 |
        """.trimIndent()
        assertEquals(expected, md)
    }

    @Test
    fun `importEpubBytes segue ordem do spine e resolve caminhos relativos com segurança`() {
        val containerXml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <container version="1.0" xmlns="urn:oasis:names:tc:opendocument:xmlns:container">
              <rootfiles>
                <rootfile full-path="OEBPS/package.opf" media-type="application/oebps-package+xml"/>
              </rootfiles>
            </container>
        """.trimIndent()

        val opfXml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <package xmlns="http://www.idpf.org/2007/opf" version="3.0">
              <manifest>
                <item id="ch2" href="text/ch2.xhtml" media-type="application/xhtml+xml"/>
                <item id="ch1" href="text/ch1.xhtml" media-type="application/xhtml+xml"/>
                <item id="evil" href="../../secret.xhtml" media-type="application/xhtml+xml"/>
              </manifest>
              <spine>
                <itemref idref="ch1"/>
                <itemref idref="evil"/>
                <itemref idref="ch2"/>
              </spine>
            </package>
        """.trimIndent()

        val ch1 = "<html><body><h1>Capítulo 1</h1><p>Primeiro.</p></body></html>"
        val ch2 = "<html><body><h1>Capítulo 2</h1><p>Segundo.</p></body></html>"

        val epub = buildZip(
            "META-INF/container.xml" to containerXml.toByteArray(Charsets.UTF_8),
            "OEBPS/package.opf" to opfXml.toByteArray(Charsets.UTF_8),
            "OEBPS/text/ch1.xhtml" to ch1.toByteArray(Charsets.UTF_8),
            "OEBPS/text/ch2.xhtml" to ch2.toByteArray(Charsets.UTF_8),
        )

        val md = importEpubBytes(epub)
        assertEquals("# Capítulo 1\n\nPrimeiro.\n\n---\n\n# Capítulo 2\n\nSegundo.", md)
    }

    @Test
    fun `importEpubBytes faz fallback ordenado por nome quando OPF está ausente`() {
        val epub = buildZip(
            "chapters/b.xhtml" to "<html><body><p>B</p></body></html>".toByteArray(),
            "chapters/a.xhtml" to "<html><body><p>A</p></body></html>".toByteArray(),
        )
        assertEquals("A\n\n---\n\nB", importEpubBytes(epub))
    }

    @Test
    fun `importDocumentBytes despacha extensões e retorna null para ZIP corrompido ou desconhecido`() {
        assertEquals("# Olá", importDocumentBytes("Nota.HTML", "<h1>Olá</h1>".toByteArray()))
        assertEquals("texto puro", importDocumentBytes("leia.TXT", "texto puro".toByteArray()))
        assertNull(importDocumentBytes("imagem.png", byteArrayOf(1, 2, 3)))
        assertNull(importDocumentBytes("quebrado.docx", "not a zip".toByteArray()))
        assertNull(importDocumentBytes("vazio.docx", buildZip("word/other.xml" to "<x/>".toByteArray())))
        assertNull(importDocumentBytes("quebrado.epub", "not a zip".toByteArray()))
        assertNull(importDocumentBytes("vazio.epub", buildZip("mimetype" to "application/epub+zip".toByteArray())))
    }

    @Test
    fun `importDocumentBytes rejeita XML com DOCTYPE XXE`() {
        val xxeDoc = """
            <?xml version="1.0"?>
            <!DOCTYPE foo [ <!ENTITY xxe SYSTEM "file:///etc/passwd"> ]>
            <w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">
              <w:body><w:p><w:r><w:t>&xxe;</w:t></w:r></w:p></w:body>
            </w:document>
        """.trimIndent()
        val zip = buildZip("word/document.xml" to xxeDoc.toByteArray(Charsets.UTF_8))
        assertNull(importDocumentBytes("xxe.docx", zip))
    }

    @Test
    fun `suggestedMarkdownTitle substitui ou adiciona extensão e usa fallback`() {
        assertEquals("relatorio.md", suggestedMarkdownTitle("relatorio.docx"))
        assertEquals("livro.v2.md", suggestedMarkdownTitle("livro.v2.EPUB"))
        assertEquals("rascunho.md", suggestedMarkdownTitle("rascunho"))
        assertEquals("Sem Título.md", suggestedMarkdownTitle(""))
        assertEquals("Sem Título.md", suggestedMarkdownTitle("   "))
        assertEquals("nota.md", suggestedMarkdownTitle("pasta/nota.html"))
        assertNotNull(importDocumentBytes("ok.md", "# Teste".toByteArray()))
        assertTrue(suggestedMarkdownTitle("a.txt").endsWith(".md"))
    }

    private fun buildZip(vararg entries: Pair<String, ByteArray>): ByteArray {
        val baos = ByteArrayOutputStream()
        ZipOutputStream(baos).use { zos ->
            for ((name, data) in entries) {
                zos.putNextEntry(ZipEntry(name))
                zos.write(data)
                zos.closeEntry()
            }
        }
        return baos.toByteArray()
    }
}
