package io.github.walterfr.jotmd.editor

import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class InlineTextTest {

    /**
     * O parser tokeniza `(`, `)`, `!` e `*` sem par como tipos de marcador mesmo
     * em texto literal. Se o renderizador descartasse por tipo, esses caracteres
     * sumiriam da tela sem aviso. Este é o teste que prende isso.
     */
    @Test
    fun `pontuação literal sobrevive`() {
        val casos = listOf(
            "a (b) c",
            "2*3=6",
            "snake_case_aqui",
            "atenção! agora",
            "100% ~ aprox",
            "chaves {a} e [b] soltos", // `[b]` sem definição é texto, não link
            "ref [x][y] sem definição",
            "e-mail: a@b.com",
        )
        for (caso in casos) {
            assertEquals(caso, inlineText(caso).text, "texto alterado em: $caso")
        }
    }

    @Test
    fun `marcadores inline saem do texto`() {
        assertEquals("negrito", inlineText("**negrito**").text)
        assertEquals("itálico", inlineText("*itálico*").text)
        assertEquals("riscado", inlineText("~~riscado~~").text)
        assertEquals("code", inlineText("`code`").text)
        assertEquals("rótulo", inlineText("[rótulo](https://x.com)").text)
    }

    @Test
    fun `estilos são aplicados no trecho certo`() {
        val negrito = inlineText("um **dois** três")
        assertEquals("um dois três", negrito.text)
        val bold = negrito.spanStyles.single { it.item.fontWeight == FontWeight.Bold }
        assertEquals(3, bold.start)
        assertEquals(7, bold.end)

        assertTrue(inlineText("*i*").spanStyles.any { it.item.fontStyle == FontStyle.Italic })
        assertTrue(
            inlineText("~~r~~").spanStyles.any {
                it.item.textDecoration == TextDecoration.LineThrough
            },
        )
    }

    @Test
    fun `heading perde o marcador e o espaço da frente`() {
        assertEquals("Título nível 1", inlineText("# Título nível 1").text)
        assertEquals("Título 3", inlineText("### Título 3").text)
        assertEquals("Setext", inlineText("Setext\n======").text)
    }

    @Test
    fun `citação perde o sinal de continuação`() {
        assertEquals("citação\nlinha dois", inlineText("> citação\n> linha dois").text)
    }

    @Test
    fun `link vira anotação navegável`() {
        val texto = inlineText("veja [aqui](https://example.com) agora")
        assertEquals("veja aqui agora", texto.text)
        val links = texto.getLinkAnnotations(0, texto.length)
        assertEquals(1, links.size)
        assertEquals(5, links.single().start)
        assertEquals(9, links.single().end)
    }

    @Test
    fun `imagem mostra o texto alternativo`() {
        assertEquals("gato", inlineText("![gato](gato.png)").text)
    }

    @Test
    fun `lista achata níveis e marca as tarefas`() {
        val rows = listRows("- um\n- dois\n  - aninhado\n")
        assertEquals(listOf(0, 0, 1), rows.map { it.depth })
        assertEquals(listOf("um", "dois", "aninhado"), rows.map { it.content.text })

        val tarefas = listRows("- [ ] pendente\n- [x] feita\n")
        assertEquals(listOf(false, true), tarefas.map { it.checked })
        assertEquals(listOf("pendente", "feita"), tarefas.map { it.content.text })

        val ordenada = listRows("1. um\n2. dois\n")
        assertEquals(listOf("1.", "2."), ordenada.map { it.marker })
    }

    @Test
    fun `cerca de código entrega linguagem e conteúdo sem as cercas`() {
        val fence = fenceOf("```kotlin\nfun x() = 1\nval y = 2\n```")
        assertEquals("kotlin", fence.language)
        assertEquals("fun x() = 1\nval y = 2", fence.code)

        assertEquals(null, fenceOf("```\nsem linguagem\n```").language)
        assertEquals("indentado", dedentIndentedCode("    indentado"))
    }

    @Test
    fun `nível do heading`() {
        assertEquals(1, headingLevel("# a"))
        assertEquals(3, headingLevel("### a"))
        assertEquals(6, headingLevel("###### a"))
        assertEquals(1, headingLevel("Setext\n======"))
        assertEquals(2, headingLevel("Setext\n------"))
    }

    @Test
    fun `tabela preserva células e alinhamento`() {
        val table = checkNotNull(tableOf("| Nome | Valor | Estado |\n|:---|:---:|---:|\n| A | 10 | ok |"))
        assertEquals(listOf("Nome", "Valor", "Estado"), table.headers)
        assertEquals(listOf(TableAlignment.LEFT, TableAlignment.CENTER, TableAlignment.RIGHT), table.alignments)
        assertEquals(listOf(listOf("A", "10", "ok")), table.rows)
    }

    private fun links(s: androidx.compose.ui.text.AnnotatedString) =
        s.getLinkAnnotations(0, s.length).map {
            Triple(s.text.substring(it.start, it.end), (it.item as androidx.compose.ui.text.LinkAnnotation.Url).url, it.start)
        }

    @Test
    fun `wikilink some com os colchetes e vira link interno`() {
        val s = inlineText("veja [[nota|apelido]] e [[outra]].")
        assertEquals("veja apelido e outra.", s.text)
        assertEquals(
            listOf(Triple("apelido", "jotmd:wiki:nota", 5), Triple("outra", "jotmd:wiki:outra", 15)),
            links(s),
        )
    }

    @Test
    fun `wikilink com âncora mostra a âncora e aponta só pra nota`() {
        val s = inlineText("[[nota#Seção]] [[#topo]]")
        assertEquals("nota#Seção #topo", s.text)
        assertEquals(listOf("jotmd:wiki:nota", "jotmd:wiki:"), links(s).map { it.second })
    }

    @Test
    fun `tag fica visível com cerquilha e vira link de busca`() {
        val s = inlineText("um **#forte** e #a/b (#c)")
        assertEquals("um #forte e #a/b (#c)", s.text)
        assertEquals(
            listOf("jotmd:tag:forte", "jotmd:tag:a/b", "jotmd:tag:c"),
            links(s).map { it.second },
        )
    }

    @Test
    fun `dentro de código wikilink e tag ficam literais`() {
        val s = inlineText("`[[x]] #y`")
        assertEquals("[[x]] #y", s.text)
        assertEquals(emptyList(), links(s))
    }

    @Test
    fun `wikilink em item de lista também`() {
        val rows = listRows("- ver [[nota]]\n- [ ] #tarefa\n")
        assertEquals(listOf("ver nota", "#tarefa"), rows.map { it.content.text })
    }

    @Test
    fun `soleImage só aceita parágrafo que é apenas uma imagem`() {
        assertEquals(ImageRef("gato", "a.png"), soleImage("![gato](a.png)"))
        assertEquals(ImageRef("gato", "a.png"), soleImage("  ![gato](a.png \"título\")\n"))
        // Texto ao redor sumiria da tela se o parágrafo virasse só o preview:
        // começa com "![" e termina com ")", mas tem mais coisa dentro.
        assertEquals(null, soleImage("![logo](a.png) é o logo (2024)"))
        assertEquals(null, soleImage("antes ![gato](a.png)"))
        assertEquals(null, soleImage("![a](a.png) ![b](b.png)"))
    }

    @Test
    fun `imagens expõem alt e destino para preview`() {
        assertEquals(
            listOf(ImageRef("gato", "images/gato.png")),
            imageRefs("antes ![gato](images/gato.png) depois"),
        )
    }
}
