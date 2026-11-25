package io.github.walterfr.jotmd

import kotlin.test.Test
import kotlin.test.assertEquals

class ExternalIntentsTest {
    @Test fun classifyMarkdownExtensionsCaseInsensitive() {
        assertEquals(IncomingKind.MARKDOWN, classifyIncomingMimeOrName(null, "nota.md"))
        assertEquals(IncomingKind.MARKDOWN, classifyIncomingMimeOrName(null, "README.MD"))
        assertEquals(IncomingKind.MARKDOWN, classifyIncomingMimeOrName(null, "artigo.markdown"))
        assertEquals(IncomingKind.MARKDOWN, classifyIncomingMimeOrName(null, "rascunho.mmd"))
        assertEquals(IncomingKind.MARKDOWN, classifyIncomingMimeOrName(null, "doc.mkd"))
        assertEquals(IncomingKind.MARKDOWN, classifyIncomingMimeOrName(null, "guia.mdown"))
        assertEquals(IncomingKind.MARKDOWN, classifyIncomingMimeOrName(null, "anotacoes.txt"))
        assertEquals(IncomingKind.MARKDOWN, classifyIncomingMimeOrName(null, "pasta/sub/nota.MD"))
    }

    @Test fun classifyImportableExtensionsCaseInsensitive() {
        assertEquals(IncomingKind.IMPORTABLE, classifyIncomingMimeOrName(null, "pagina.html"))
        assertEquals(IncomingKind.IMPORTABLE, classifyIncomingMimeOrName(null, "INDEX.HTM"))
        assertEquals(IncomingKind.IMPORTABLE, classifyIncomingMimeOrName(null, "doc.xhtml"))
        assertEquals(IncomingKind.IMPORTABLE, classifyIncomingMimeOrName(null, "relatorio.docx"))
        assertEquals(IncomingKind.IMPORTABLE, classifyIncomingMimeOrName(null, "RELATORIO.DOCX"))
        assertEquals(IncomingKind.IMPORTABLE, classifyIncomingMimeOrName(null, "livro.epub"))
    }

    @Test fun classifyMimeTypesWithAndWithoutFilename() {
        assertEquals(IncomingKind.MARKDOWN, classifyIncomingMimeOrName("text/markdown", null))
        assertEquals(IncomingKind.MARKDOWN, classifyIncomingMimeOrName("text/x-markdown", null))
        assertEquals(IncomingKind.MARKDOWN, classifyIncomingMimeOrName("text/plain", null))
        assertEquals(IncomingKind.IMPORTABLE, classifyIncomingMimeOrName("text/html", null))
        assertEquals(IncomingKind.IMPORTABLE, classifyIncomingMimeOrName("text/html; charset=utf-8", null))
        assertEquals(IncomingKind.IMPORTABLE, classifyIncomingMimeOrName("application/xhtml+xml", null))
        assertEquals(
            IncomingKind.IMPORTABLE,
            classifyIncomingMimeOrName(
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                null,
            ),
        )
        assertEquals(IncomingKind.IMPORTABLE, classifyIncomingMimeOrName("application/epub+zip", null))
    }

    @Test fun extensionOverridesGenericMimeAndUnknownIsRejected() {
        assertEquals(
            IncomingKind.IMPORTABLE,
            classifyIncomingMimeOrName("application/octet-stream", "arquivo.docx"),
        )
        assertEquals(
            IncomingKind.MARKDOWN,
            classifyIncomingMimeOrName("application/octet-stream", "arquivo.md"),
        )
        assertEquals(
            IncomingKind.IMPORTABLE,
            classifyIncomingMimeOrName("text/plain", "export.html"),
        )
        assertEquals(IncomingKind.UNKNOWN, classifyIncomingMimeOrName("image/png", null))
        assertEquals(IncomingKind.UNKNOWN, classifyIncomingMimeOrName("image/png", "foto.png"))
        assertEquals(IncomingKind.UNKNOWN, classifyIncomingMimeOrName(null, "sem_extensao"))
        assertEquals(IncomingKind.UNKNOWN, classifyIncomingMimeOrName(null, null))
    }
}
