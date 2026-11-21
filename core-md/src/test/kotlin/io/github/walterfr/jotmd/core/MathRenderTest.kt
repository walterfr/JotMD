package io.github.walterfr.jotmd.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MathRenderTest {

    @Test
    fun `extractMathBlock reconhece blocos em linha única e multilinha e rejeita inválidos`() {
        assertEquals("E = mc^2", extractMathBlock("$$ E = mc^2 $$"))
        assertEquals("a + b", extractMathBlock("\$\$a + b\$\$"))
        assertEquals("\\sum_{i=1}^n i", extractMathBlock("  \$\$\n\\sum_{i=1}^n i\n\$\$  "))
        assertEquals("x \\\\", extractMathBlock("\$\$x \\\\\$\$"))

        assertNull(extractMathBlock("texto \$\$a\$\$"))
        assertNull(extractMathBlock("\$\$a\$\$ + \$\$b\$\$"))
        assertNull(extractMathBlock("$$$$"))
        assertNull(extractMathBlock("$$   $$"))
        assertNull(extractMathBlock("\$\$x \\\$\$"))
    }

    @Test
    fun `inlineMathSpansIn segue regras Typora Pandoc e ignora moeda e skipRanges`() {
        val text = "Preço \$5 e \$10, fórmula \$x^2 + y^2\$ e \$z\$, bloco \$\$a\$\$ e código `\$code\$`."
        val codeStart = text.indexOf("`\$code\$`")
        val codeEnd = codeStart + "`\$code\$`".length - 1
        val spans = inlineMathSpansIn(text, skipRanges = listOf(codeStart..codeEnd))

        assertEquals(listOf("x^2 + y^2", "z"), spans.map { it.second })
        for ((range, expr) in spans) {
            assertEquals("\$$expr\$", text.substring(range))
        }
        assertTrue(inlineMathSpansIn("\$a\nb\$").isEmpty())
        assertTrue(inlineMathSpansIn("\$\$x\$\$").isEmpty())
        assertTrue(inlineMathSpansIn("\$5 e \$10").isEmpty())
        assertTrue(inlineMathSpansIn("\$5\$10").isEmpty())
        assertTrue(inlineMathSpansIn("\$ x\$").isEmpty())
        assertTrue(inlineMathSpansIn("\$x \$").isEmpty())
        assertTrue(inlineMathSpansIn("\$x\\\$").isEmpty())
        assertTrue(inlineMathSpansIn("\\\$nao\$").isEmpty())
    }

    @Test
    fun `renderLatexToUnicode converte frações, raízes, wrappers, gregas, operadores e scripts`() {
        assertEquals("1/2", renderLatexToUnicode("""\frac{1}{2}"""))
        assertEquals("(a + b)/(c - d)", renderLatexToUnicode("""\frac{a + b}{c - d}"""))
        assertEquals("(1/2)/3", renderLatexToUnicode("""\frac{\frac{1}{2}}{3}"""))

        assertEquals("√x", renderLatexToUnicode("""\sqrt{x}"""))
        assertEquals("√(x² + y²)", renderLatexToUnicode("""\sqrt{x^2 + y^2}"""))
        assertEquals("³√8", renderLatexToUnicode("""\sqrt[3]{8}"""))

        assertEquals(
            "se x > 0: d f v",
            renderLatexToUnicode("""\text{se } x > 0: \mathrm{d} \mathbf{f} \mathit{v}"""),
        )
        assertEquals("( a/b )", renderLatexToUnicode("""\left( \frac{a}{b} \right)"""))

        assertEquals(
            "α β γ δ ε θ λ μ π σ φ ω Γ Δ Θ Λ Π Σ Φ Ψ Ω",
            renderLatexToUnicode(
                """\alpha \beta \gamma \delta \epsilon \theta \lambda \mu \pi \sigma \phi \omega """ +
                    """\Gamma \Delta \Theta \Lambda \Pi \Sigma \Phi \Psi \Omega""",
            ),
        )

        assertEquals(
            "± × ÷ · ≤ ≤ ≥ ≥ ≠ ≠ ≈ ≡ ∞ ∑ ∏ ∫ ∂ ∇ → → ← ⇒ ⇔ ∈ ∉ ⊂ ⊆ ∪ ∩ ∀ ∃",
            renderLatexToUnicode(
                """\pm \times \div \cdot \le \leq \ge \geq \neq \ne \approx \equiv \infty """ +
                    """\sum \prod \int \partial \nabla \to \rightarrow \leftarrow \Rightarrow \Leftrightarrow """ +
                    """\in \notin \subset \subseteq \cup \cap \forall \exists""",
            ),
        )

        assertEquals(
            "x² + x³ + xⁿ + xⁱ + x¹⁰ + xⁿ⁺¹ + x^(2k)",
            renderLatexToUnicode("""x^2 + x^3 + x^n + x^i + x^{10} + x^{n+1} + x^{2k}"""),
        )
        assertEquals(
            "a₀ + a₉ + a₁₂ + a_(i+1)",
            renderLatexToUnicode("""a_0 + a_9 + a_{12} + a_{i+1}"""),
        )
        assertEquals("∑_(i=1)ⁿ i = (n(n+1))/2", renderLatexToUnicode("""\sum_{i=1}^{n} i = \frac{n(n+1)}{2}"""))
    }

    @Test
    fun `renderLatexToUnicode tolera entradas vazias ou malformadas sem exceção`() {
        assertEquals("", renderLatexToUnicode(""))
        assertEquals("", renderLatexToUnicode("   "))
        assertEquals("a/", renderLatexToUnicode("""\frac{a}{"""))
        assertEquals("√()", renderLatexToUnicode("""\sqrt{"""))
    }
}
