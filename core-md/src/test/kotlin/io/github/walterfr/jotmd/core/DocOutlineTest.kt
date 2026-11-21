package io.github.walterfr.jotmd.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DocOutlineTest {

    @Test
    fun `isTocMarker reconhece variantes válidas e rejeita texto comum`() {
        assertTrue(isTocMarker("[TOC]"))
        assertTrue(isTocMarker("[toc]"))
        assertTrue(isTocMarker("  {:toc} \n"))
        assertTrue(isTocMarker("{:TOC}"))
        assertTrue(isTocMarker("[[_toc_]]"))
        assertTrue(isTocMarker("  [[_TOC_]]  "))

        assertFalse(isTocMarker(""))
        assertFalse(isTocMarker("   "))
        assertFalse(isTocMarker("[TOC] extra"))
        assertFalse(isTocMarker("texto {:toc}"))
        assertFalse(isTocMarker("[[TOC]]"))
    }

    @Test
    fun `extrai todos os níveis ATX 1 a 6 e Setext com índice e BlockId reais`() {
        val md = """
            ---
            title: Doc
            ---

            [TOC]

            # Um

            Parágrafo entre títulos.

            ## Dois

            ### Três

            #### Quatro

            ##### Cinco

            ###### Seis

            Setext Um
            =========

            Setext Dois
            -----------
        """.trimIndent()
        val doc = parse(md)
        val entries = outlineOf(doc)

        assertEquals(listOf(1, 2, 3, 4, 5, 6, 1, 2), entries.map { it.level })
        assertEquals(
            listOf("Um", "Dois", "Três", "Quatro", "Cinco", "Seis", "Setext Um", "Setext Dois"),
            entries.map { it.title },
        )
        assertEquals(
            listOf("um", "dois", "três", "quatro", "cinco", "seis", "setext-um", "setext-dois"),
            entries.map { it.slug },
        )
        for (entry in entries) {
            assertEquals(doc.blocks[entry.blockIndex].id, entry.blockId)
            assertEquals(BlockType.HEADING, doc.blocks[entry.blockIndex].type)
        }
    }

    @Test
    fun `limpa marcadores inline, links, imagens, wikilinks e cerquilhas finais ATX`() {
        val md = """
            # **Negrito** __forte__ *itálico* _leve_ ~~riscado~~ ==destaque== `código` <u>sublinhado</u>

            ## Veja [documentação](https://example.com) e ![logo](logo.png)

            ### Sobre [[nota|Alias Aqui]] e [[outra]]

            #### Título fechado ###   

            ##### Linguagem C#
        """.trimIndent()
        val entries = outlineOf(parse(md))

        assertEquals(
            listOf(
                "Negrito forte itálico leve riscado destaque código sublinhado",
                "Veja documentação e logo",
                "Sobre Alias Aqui e outra",
                "Título fechado",
                "Linguagem C#",
            ),
            entries.map { it.title },
        )
    }

    @Test
    fun `gera slugs únicos para títulos repetidos e fallback section para vazios`() {
        val md = """
            # Intro

            ## Intro

            ### Intro

            #### ???

            ##### !!!
        """.trimIndent()
        val entries = outlineOf(parse(md))

        assertEquals(
            listOf("intro", "intro-1", "intro-2", "section", "section-1"),
            entries.map { it.slug },
        )
    }

    @Test
    fun `preserva underscore literal de identificadores e evita colisão com sufixo existente`() {
        val md = """
            # snake_case_name

            # Intro

            # Intro 1

            # Intro
        """.trimIndent()
        val entries = outlineOf(parse(md))

        assertEquals("snake_case_name", entries[0].title)
        assertEquals("snake-case-name", entries[0].slug)
        assertEquals(listOf("intro", "intro-1", "intro-2"), entries.drop(1).map { it.slug })
    }
}
