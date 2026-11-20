package io.github.walterfr.jotmd.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class IndexExtractionTest {
    // ---- front matter ----

    @Test fun `front matter basico`() {
        val t = "---\ntitle: Olá\nautor: \"Ana\"\n---\n# corpo"
        val fm = assertNotNull(frontMatterOf(t))
        assertEquals("---\ntitle: Olá\nautor: \"Ana\"\n---", fm.raw)
        assertEquals(mapOf("title" to "Olá", "autor" to "Ana"), fm.fields)
        assertEquals(fm.raw.length, fm.end)
    }

    @Test fun `tags em lista inline, em lista yaml e em texto`() {
        assertEquals(listOf("a", "b"), frontMatterOf("---\ntags: [a, b]\n---")!!.tags)
        assertEquals(listOf("a", "b/c"), frontMatterOf("---\ntags:\n  - a\n  - \"#b/c\"\n---\n")!!.tags)
        assertEquals(listOf("x", "y"), frontMatterOf("---\ntags: x, #y\n---")!!.tags)
        assertEquals(listOf("x", "y"), frontMatterOf("---\ntags: x y\n---")!!.tags)
    }

    @Test fun `front matter nao fechado nao e front matter`() {
        assertNull(frontMatterOf("---\ntitle: x\nsem fim"))
        assertNull(frontMatterOf("---"))
    }

    @Test fun `--- fora da linha 1 nao e front matter`() {
        assertNull(frontMatterOf("\n---\ntitle: x\n---\n"))
        assertNull(frontMatterOf("texto\n---\na: b\n---\n"))
        assertNull(frontMatterOf(" ---\na: b\n---\n"))
    }

    @Test fun `CRLF e fechamento com pontos`() {
        val fm = assertNotNull(frontMatterOf("---\r\ntags: [a]\r\ntitle: T\r\n...\r\nresto"))
        assertEquals(listOf("a"), fm.tags)
        assertEquals("T", fm.fields["title"])
        assertEquals("---\r\ntags: [a]\r\ntitle: T\r\n...", fm.raw)
    }

    @Test fun `front matter vazio e documento vazio`() {
        assertEquals(emptyMap(), frontMatterOf("---\n---")!!.fields)
        assertNull(frontMatterOf(""))
        assertEquals(DocIndex(null, emptyList(), emptyList()), docIndex(""))
    }

    // ---- wikilinks ----

    @Test fun `wikilink simples, alias, ancora e ambos`() {
        val t = "a [[Nota]] b [[N|apelido]] c [[N#Sec]] d [[N#Sec|x]]"
        val l = wikilinksIn(t)
        assertEquals(
            listOf(
                Wikilink("Nota", null, null, 2),
                Wikilink("N", "apelido", null, t.indexOf("[[N|")),
                Wikilink("N", null, "Sec", t.indexOf("[[N#Sec]]")),
                Wikilink("N", "x", "Sec", t.indexOf("[[N#Sec|")),
            ),
            l,
        )
    }

    @Test fun `wikilink com acento, emoji e espacos`() {
        val t = "😀 [[ Ação Ótima ]] e [[café ☕]]"
        val l = wikilinksIn(t)
        assertEquals(listOf("Ação Ótima", "café ☕"), l.map { it.target })
        assertEquals(t.indexOf("[["), l[0].offset)
    }

    @Test fun `wikilink invalido e ignorado`() {
        assertEquals(emptyList(), wikilinksIn("[[]] [[|x]] [[a\nb]] [[a [b]] ]]"))
        assertEquals("", wikilinksIn("[[#sec]]").single().target)
    }

    @Test fun `wikilink em code span, cerca e bloco indentado e ignorado`() {
        assertEquals(emptyList(), wikilinksIn("texto `[[a]]` fim"))
        assertEquals(emptyList(), wikilinksIn("```\n[[a]]\n```\n"))
        assertEquals(emptyList(), wikilinksIn("~~~kt\n[[a]]\n~~~"))
        assertEquals(emptyList(), wikilinksIn("par\n\n    [[a]]\n"))
        assertEquals(listOf("b"), wikilinksIn("```\n[[a]]\n```\n[[b]]").map { it.target })
    }

    @Test fun `wikilink em CRLF mantem offset`() {
        val t = "a\r\n[[x]]\r\n"
        assertEquals(3, wikilinksIn(t).single().offset)
    }

    @Test fun `wikilink dentro do front matter nao conta e offsets seguem o original`() {
        val t = "---\nrel: \"[[fm]]\"\n---\n[[corpo]]"
        val l = wikilinksIn(t).single()
        assertEquals("corpo", l.target)
        assertEquals(t.indexOf("[[corpo"), l.offset)
    }

    // ---- tags ----

    @Test fun `tags basicas, aninhadas, acentuadas`() {
        assertEquals(
            listOf("tag", "a/b/c", "ação", "x_y-z", "2024-plano"),
            tagsIn("#tag, #a/b/c e #ação (#x_y-z) \"#2024-plano\""),
        )
    }

    @Test fun `tag termina na pontuacao e deduplica`() {
        assertEquals(listOf("fim"), tagsIn("veja #fim. #fim! #fim"))
        assertEquals(listOf("a"), tagsIn("#a/ #a"))
    }

    @Test fun `nao e tag`() {
        assertEquals(emptyList(), tagsIn("# Titulo\n## Outro\n"))
        assertEquals(emptyList(), tagsIn("#123 e #2024"))
        assertEquals(emptyList(), tagsIn("a#b colado"))
        assertEquals(emptyList(), tagsIn("veja http://x.com/#frag e [l](#ancora) e <http://y/#z>"))
        assertEquals(emptyList(), tagsIn("##dupla"))
        assertEquals(emptyList(), tagsIn("😀#colado"))
    }

    @Test fun `tag em code span, cerca e indentado e ignorada`() {
        assertEquals(emptyList(), tagsIn("`#a` fim"))
        assertEquals(emptyList(), tagsIn("```\n#a\n```\n"))
        assertEquals(emptyList(), tagsIn("par\n\n    #a\n"))
        assertEquals(listOf("b"), tagsIn("```\n#a\n```\n\n#b"))
    }

    @Test fun `tag sem espaco depois do hash em inicio de linha e tag`() {
        assertEquals(listOf("tag"), tagsIn("#tag texto"))
        assertEquals(listOf("tag"), tagsIn("a\r\n#tag\r\n"))
    }

    @Test fun `tags do front matter nao saem de tagsIn mas entram em tagsOf`() {
        val t = "---\ntags: [fm, comum]\n---\ncorpo #comum #corpo"
        assertEquals(listOf("comum", "corpo"), tagsIn(t))
        assertEquals(listOf("fm", "comum", "corpo"), tagsOf(docIndex(t)))
    }

    // ---- conveniência + roundtrip ----

    @Test fun `docIndex junta tudo`() {
        val t = "---\ntitle: T\ntags: [x]\n---\n\nVeja [[Outra#S|ali]] #y\n"
        val d = docIndex(t)
        assertEquals("T", d.frontMatter!!.fields["title"])
        assertEquals(listOf("Outra"), d.wikilinks.map { it.target })
        assertEquals(listOf("x", "y"), tagsOf(d))
    }

    @Test fun `extracao nao altera parse nem serialize`() {
        val t = "---\ntags: [a]\n---\n\n[[x]] #t\n\n```\n#c\n```\n"
        docIndex(t)
        assertEquals(t, serialize(parse(t)))
        assertTrue(t.isNotEmpty())
    }
}
