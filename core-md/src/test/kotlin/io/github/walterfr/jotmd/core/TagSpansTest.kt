package io.github.walterfr.jotmd.core

import kotlin.test.Test
import kotlin.test.assertEquals

class TagSpansTest {

    @Test
    fun `faixa inclui o cerquilha e cobre toda ocorrência, inclusive repetida`() {
        val src = "a #um e #um/dois."
        assertEquals(
            listOf((2..4) to "um", (8..15) to "um/dois"),
            tagSpansIn(src),
        )
        assertEquals("#um/dois", src.substring(8..15))
    }

    @Test
    fun `tag dentro de ênfase conta`() {
        assertEquals(listOf("a", "b", "c"), tagSpansIn("**#a** *#b* ~~#c~~").map { it.second })
    }

    @Test
    fun `mesmas exclusões de tagsIn`() {
        // código, número, heading, âncora de URL
        assertEquals(emptyList(), tagSpansIn("`#x` #123 # titulo http://a/#frag"))
    }
}
