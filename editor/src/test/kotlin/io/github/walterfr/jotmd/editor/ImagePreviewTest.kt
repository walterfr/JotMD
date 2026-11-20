package io.github.walterfr.jotmd.editor

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ImagePreviewTest {
    @Test
    fun sampleSize() {
        assertEquals(1, sampleSizeFor(800, 600))
        assertEquals(1, sampleSizeFor(2048, 1536))
        assertEquals(2, sampleSizeFor(4000, 3000))
        assertEquals(4, sampleSizeFor(8000, 6000))
        assertEquals(1, sampleSizeFor(0, 100))
        assertEquals(1, sampleSizeFor(-1, -1))
        assertEquals(1, sampleSizeFor(4000, 3000, target = 0))
    }

    @Test
    fun scheme() {
        assertTrue(isAllowedImageScheme("content://media/external/images/1"))
        assertTrue(isAllowedImageScheme("CONTENT://x/y"))
        assertFalse(isAllowedImageScheme("file:///sdcard/a.png"))
        assertFalse(isAllowedImageScheme("https://x.com/a.png"))
        assertFalse(isAllowedImageScheme("http://x.com/a.png"))
        assertFalse(isAllowedImageScheme("img/a.png"))
        assertFalse(isAllowedImageScheme(""))
    }
}
