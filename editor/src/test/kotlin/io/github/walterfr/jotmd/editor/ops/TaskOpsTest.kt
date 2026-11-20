package io.github.walterfr.jotmd.editor.ops

import kotlin.test.Test
import kotlin.test.assertEquals

class TaskOpsTest {
    @Test
    fun `marca e desmarca só o item pedido`() {
        assertEquals("- [x] a\n- [ ] b", toggleTask("- [ ] a\n- [ ] b", 0))
        assertEquals("- [ ] a\n- [ ] b", toggleTask("- [x] a\n- [ ] b", 0))
    }

    @Test
    fun `X maiusculo desmarca`() {
        assertEquals("- [ ] a", toggleTask("- [X] a", 0))
    }

    @Test
    fun `item aninhado conta na ordem achatada`() {
        val src = "- [ ] a\n  - [ ] b\n- [ ] c"
        assertEquals("- [ ] a\n  - [x] b\n- [ ] c", toggleTask(src, 1))
        assertEquals("- [ ] a\n  - [ ] b\n- [x] c", toggleTask(src, 2))
    }

    @Test
    fun `lista mista - item sem checkbox e no-op, indice conta todos os itens`() {
        val src = "- a\n- [ ] b"
        assertEquals(src, toggleTask(src, 0))
        assertEquals("- a\n- [x] b", toggleTask(src, 1))
    }

    @Test
    fun `indice fora do alcance e no-op`() {
        val src = "- [ ] a"
        assertEquals(src, toggleTask(src, 5))
        assertEquals(src, toggleTask(src, -1))
    }

    @Test
    fun `preserva CRLF e espacos byte a byte`() {
        val src = "*   [ ] a  \r\n*   [x] b\r\n"
        assertEquals("*   [x] a  \r\n*   [x] b\r\n", toggleTask(src, 0))
    }
}
