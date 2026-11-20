package io.github.walterfr.jotmd

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FileBrowserTest {
    private fun n(name: String, dir: Boolean = false) = DocNode(name, name, dir, 0)

    @Test fun markdownNames() {
        assertTrue(isMarkdownName("a.md"))
        assertTrue(isMarkdownName("B.MARKDOWN"))
        assertFalse(isMarkdownName("a.txt"))
        assertFalse(isMarkdownName("md"))
    }

    @Test fun foldersFirstThenCaseInsensitive() {
        val r = sortAndFilter(
            listOf(n("b.md"), n("Zeta", true), n("A.md"), n("x.png"), n("alfa", true)),
        )
        assertEquals(listOf("alfa", "Zeta", "A.md", "b.md"), r.map { it.name })
    }
}
