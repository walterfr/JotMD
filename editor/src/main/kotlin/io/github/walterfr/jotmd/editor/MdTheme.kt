package io.github.walterfr.jotmd.editor

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Tokens tirados do tema `github` do Typora instalado — ver DESIGN.md.
 *
 * O Typora usa raiz de 14px no desktop. Aqui a raiz é 16sp, porque 14sp num
 * tablet segurado à distância de leitura fica pequeno. As proporções entre
 * níveis são as do Typora; só a base mudou.
 *
 * ponytail: valores fixos, tema claro só. Vira CompositionLocal em F6, quando
 * existir o tema escuro para alternar.
 */
object MdTokens {
    val base = 16.sp
    val bodyLineHeight = 25.6.sp // 1.6

    val text = Color(0xFF333333)
    val muted = Color(0xFF777777)
    val link = Color(0xFF4183C4)
    val background = Color(0xFFFFFFFF)

    val headingRule = Color(0xFFEEEEEE)
    val quoteBar = Color(0xFFDFE2E5)
    val ruleColor = Color(0xFFE7E7E7)
    val codeBorder = Color(0xFFE7EAED)
    val inlineCodeBackground = Color(0xFFF3F4F4)
    val fenceBackground = Color(0xFFF8F8F8)

    /** `margin: 0.8em 0` nos blocos. 0.8 * 16 = 12.8 */
    val blockGap = 12.8.dp

    /** `margin: 1rem` acima e abaixo dos headings, menos o blockGap já aplicado. */
    val headingGap = 16.dp

    val quoteBarWidth = 4.dp
    val quotePadding = 15.dp
    val listIndent = 30.dp
    val codePadding = 8.dp
    val ruleThickness = 2.dp
    val cornerRadius = 3.dp

    /** H1..H6: multiplicador de [base] e altura de linha, na ordem do tema. */
    val headingScale = listOf(2.25f, 1.75f, 1.5f, 1.25f, 1f, 1f)
    val headingLineHeight = listOf(1.2f, 1.225f, 1.43f, 1.4f, 1.4f, 1.4f)

    /** Só H1 e H2 têm régua embaixo no tema github. */
    fun hasRule(level: Int) = level <= 2

    val inlineCode = SpanStyle(
        fontFamily = FontFamily.Monospace,
        fontSize = base * 0.9f,
        background = inlineCodeBackground,
        color = text,
    )

    val documentPadding = PaddingValues(horizontal = 16.dp, vertical = 24.dp)
}
