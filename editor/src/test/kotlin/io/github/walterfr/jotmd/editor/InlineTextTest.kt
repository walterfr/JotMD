package io.github.walterfr.jotmd.editor

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.BaselineShift
import androidx.compose.ui.text.style.TextDecoration
import io.github.walterfr.jotmd.core.docRefsOf
import io.github.walterfr.jotmd.core.parse
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
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

    @Test
    fun `marca-texto aplica destaque e remove delimitadores`() {
        val s = inlineText("antes ==marcação== depois")
        assertEquals("antes marcação depois", s.text)
        val span = s.spanStyles.single {
            it.item.background == MdTokens.highlightBackground && it.item.color == MdTokens.highlightText
        }
        assertEquals(6, span.start)
        assertEquals(14, span.end)

        val boldHighlight = inlineText("**==forte==**")
        assertEquals("forte", boldHighlight.text)
        assertTrue(boldHighlight.spanStyles.any { it.item.fontWeight == FontWeight.Bold })
        assertTrue(boldHighlight.spanStyles.any { it.item.background == MdTokens.highlightBackground })
    }

    @Test
    fun `igualdade e código inline com == ficam literais`() {
        val expr = inlineText("a == b && c == d")
        assertEquals("a == b && c == d", expr.text)
        assertTrue(expr.spanStyles.none { it.item.background == MdTokens.highlightBackground })

        val code = inlineText("`==código==`")
        assertEquals("==código==", code.text)
        assertTrue(code.spanStyles.none { it.item.background == MdTokens.highlightBackground })

        val acrossCode = inlineText("`==a` e `b==`")
        assertEquals("==a e b==", acrossCode.text)
        assertTrue(acrossCode.spanStyles.none { it.item.background == MdTokens.highlightBackground })
    }

    @Test
    fun `quoteAlertOf reconhece alertas do GitHub e preserva corpo`() {
        assertEquals(QuoteAlert(QuoteAlertType.NOTE, ""), quoteAlertOf("> [!NOTE]"))
        assertEquals(
            QuoteAlert(QuoteAlertType.WARNING, "linha um\nlinha dois"),
            quoteAlertOf("> [!WARNING]\n> linha um\n> linha dois"),
        )
        assertEquals(
            QuoteAlert(QuoteAlertType.CAUTION, "perigo"),
            quoteAlertOf("> [!caution] perigo"),
        )
        assertEquals(
            QuoteAlert(QuoteAlertType.TIP, "dica útil"),
            quoteAlertOf("> [!Tip]\n>\n> dica útil"),
        )
        assertEquals(
            QuoteAlert(QuoteAlertType.IMPORTANT, "algo\n\noutro parágrafo"),
            quoteAlertOf("> [!IMPORTANT]\n> algo\n>\n> outro parágrafo"),
        )
        assertNull(quoteAlertOf("> citação normal"))
        assertNull(quoteAlertOf("> texto antes [!NOTE]"))
        assertNull(quoteAlertOf("> [!DESCONHECIDO]\n> corpo"))
    }

    @Test
    fun `alternar MdTokens isDark atualiza cores da paleta e inlineCode`() {
        try {
            MdTokens.isDark = false
            assertEquals(LightMdPalette, MdTokens.palette)
            assertEquals(LightMdPalette.background, MdTokens.background)
            assertEquals(LightMdPalette.text, MdTokens.text)
            assertEquals(LightMdPalette.highlightBackground, MdTokens.highlightBackground)
            assertEquals(LightMdPalette.inlineCodeBackground, MdTokens.inlineCode.background)

            MdTokens.isDark = true
            assertEquals(DarkMdPalette, MdTokens.palette)
            assertEquals(DarkMdPalette.background, MdTokens.background)
            assertEquals(DarkMdPalette.text, MdTokens.text)
            assertEquals(DarkMdPalette.highlightBackground, MdTokens.highlightBackground)
            assertEquals(DarkMdPalette.inlineCodeBackground, MdTokens.inlineCode.background)

            val darkHighlight = inlineText("==escuro==")
            assertTrue(
                darkHighlight.spanStyles.any {
                    it.item.background == DarkMdPalette.highlightBackground &&
                        it.item.color == DarkMdPalette.highlightText
                },
            )
        } finally {
            MdTokens.themePreset = MdThemePreset.GITHUB
        }
    }

    @Test
    fun `sublinhado com tag HTML remove delimitadores e ignora dentro de backticks`() {
        val s = inlineText("antes <u>sublinhado</u> e <U>caixa</U>")
        assertEquals("antes sublinhado e caixa", s.text)
        val spans = s.spanStyles.filter { it.item.textDecoration == TextDecoration.Underline }
        assertEquals(2, spans.size)
        assertEquals(6, spans[0].start)
        assertEquals(16, spans[0].end)
        assertEquals(19, spans[1].start)
        assertEquals(24, spans[1].end)

        val code = inlineText("`<u>x</u>`")
        assertEquals("<u>x</u>", code.text)
        assertTrue(code.spanStyles.none { it.item.textDecoration == TextDecoration.Underline })
    }

    @Test
    fun `MdTokens cicla presets entre temas claros e escuro`() {
        try {
            MdTokens.themePreset = MdThemePreset.GITHUB
            assertEquals(MdThemePreset.NEWSPRINT, MdTokens.cycleThemePreset())
            assertEquals(NewsprintMdPalette, MdTokens.palette)
            assertEquals(false, MdTokens.isDark)

            // isDark = false preserva tema claro customizado
            MdTokens.isDark = false
            assertEquals(MdThemePreset.NEWSPRINT, MdTokens.themePreset)

            assertEquals(MdThemePreset.PIXYLL, MdTokens.cycleThemePreset())
            assertEquals(PixyllMdPalette, MdTokens.palette)

            assertEquals(MdThemePreset.WHITEY, MdTokens.cycleThemePreset())
            assertEquals(WhiteyMdPalette, MdTokens.palette)

            assertEquals(MdThemePreset.NIGHT, MdTokens.cycleThemePreset())
            assertEquals(DarkMdPalette, MdTokens.palette)
            assertEquals(true, MdTokens.isDark)
            assertEquals(Color(0xFF686868), MdTokens.blurText)

            assertEquals(MdThemePreset.GITHUB, MdTokens.cycleThemePreset())
            assertEquals(LightMdPalette, MdTokens.palette)
        } finally {
            MdTokens.themePreset = MdThemePreset.GITHUB
        }
    }

    @Test
    fun `links de referência usam DocRefs e mantêm desconhecidos literais`() {
        val doc = parse(
            """
            Veja [completo][Guia], [atalho], [colapsado][] e [faltando][nada].

            [guia]: https://example.com/guia
            [ATALHO]: https://example.com/atalho
            [colapsado]: https://example.com/col
            """.trimIndent(),
        )
        val refs = docRefsOf(doc)
        val s = inlineText(doc.blocks[0].source, refs = refs)
        assertEquals("Veja completo, atalho, colapsado e [faltando][nada].", s.text)
        assertEquals(
            listOf(
                Triple("completo", "https://example.com/guia", 5),
                Triple("atalho", "https://example.com/atalho", 15),
                Triple("colapsado", "https://example.com/col", 23),
            ),
            links(s),
        )
    }

    @Test
    fun `notas de rodapé inline viram sobrescrito numerado com link interno`() {
        val doc = parse(
            """
            Primeiro[^Beta], depois[^Alfa], desconhecida[^Missing] e código `[^Alfa]`.

            [^alfa]: Nota A
            [^beta]: Nota B
            """.trimIndent(),
        )
        val refs = docRefsOf(doc)
        val s = inlineText(doc.blocks[0].source, refs = refs)
        assertEquals("Primeiro[1], depois[2], desconhecida[Missing] e código [^Alfa].", s.text)
        assertEquals(
            listOf(
                Triple("[1]", "${FOOTNOTE_URI}beta", 8),
                Triple("[2]", "${FOOTNOTE_URI}alfa", 19),
                Triple("[Missing]", "${FOOTNOTE_URI}missing", 36),
            ),
            links(s),
        )
        val superscripts = s.spanStyles.filter { it.item.baselineShift == BaselineShift.Superscript }
        assertEquals(3, superscripts.size)
        assertTrue(superscripts.all { it.item.fontSize == MdTokens.base * 0.78f })
    }

    @Test
    fun `LaTeX inline converte para Unicode e preserva subscritos sem virar ênfase`() {
        val s = inlineText("Fórmula \$\\alpha + a_1 + x^2\$ e preço \$5 e \$10, código `\$x\$`.")
        assertEquals("Fórmula α + a₁ + x² e preço \$5 e \$10, código \$x\$.", s.text)
        val mathSpan = s.spanStyles.single {
            it.item.fontFamily == FontFamily.Serif &&
                it.item.fontStyle == FontStyle.Italic &&
                it.item.background == MdTokens.inlineCodeBackground
        }
        assertEquals("α + a₁ + x²", s.text.substring(mathSpan.start, mathSpan.end))
    }

    @Test
    fun `applySearchHighlight destaca ocorrências e ocorrência ativa preservando estilos`() {
        val base = inlineText("um **alfa** e alfa e ALFA")
        val highlighted = applySearchHighlight(
            base = base,
            query = "alfa",
            caseSensitive = false,
            wholeWord = true,
            regex = false,
            activeOccurrenceInBlock = 1,
        )
        assertEquals("um alfa e alfa e ALFA", highlighted.text)
        // Mantém negrito no primeiro "alfa"
        assertTrue(highlighted.spanStyles.any { it.item.fontWeight == FontWeight.Bold && it.start == 3 && it.end == 7 })

        val hits = highlighted.spanStyles.filter { it.item.background == MdTokens.searchHitBackground }
        val active = highlighted.spanStyles.filter {
            it.item.background == MdTokens.searchActiveBackground && it.item.color == MdTokens.searchActiveText
        }
        assertEquals(2, hits.size)
        assertEquals(1, active.size)
        assertEquals(10, active.single().start)
        assertEquals(14, active.single().end)

        // Regex inválida não falha nem altera o texto
        val invalid = applySearchHighlight(base, query = "[", regex = true)
        assertEquals(base, invalid)

        // inlineText com destaque ativo marca a 1ª ocorrência como ativa
        val inline = inlineText("alfa e alfa", highlightQuery = "alfa", highlightActive = true)
        assertTrue(inline.spanStyles.any { it.item.background == MdTokens.searchActiveBackground && it.start == 0 && it.end == 4 })
        assertTrue(inline.spanStyles.any { it.item.background == MdTokens.searchHitBackground && it.start == 7 && it.end == 11 })
    }

    @Test
    fun `MdTokens de busca e sintaxe reagem a isDark`() {
        try {
            MdTokens.themePreset = MdThemePreset.GITHUB
            val lightHit = MdTokens.searchHitBackground
            val lightKeyword = MdTokens.syntaxKeyword

            MdTokens.themePreset = MdThemePreset.NIGHT
            assertEquals(Color(0xCFC78C3C), MdTokens.searchHitBackground)
            assertEquals(Color(0xFF428BCA), MdTokens.searchActiveBackground)
            assertEquals(Color.White, MdTokens.searchActiveText)
            assertEquals(Color(0xFFC88FD0), MdTokens.syntaxKeyword)
            assertTrue(lightHit != MdTokens.searchHitBackground)
            assertTrue(lightKeyword != MdTokens.syntaxKeyword)
        } finally {
            MdTokens.themePreset = MdThemePreset.GITHUB
        }
    }
}
