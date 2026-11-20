package io.github.walterfr.jotmd.editor

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Paleta de cores do editor (tema claro `github` e tema escuro `night` do
 * Typora — ver DESIGN.md).
 */
data class MdPalette(
    val text: Color,
    val headingText: Color,
    val muted: Color,
    val link: Color,
    val background: Color,
    val headingRule: Color,
    val quoteBar: Color,
    val ruleColor: Color,
    val codeBorder: Color,
    val tableBorder: Color,
    val inlineCodeBackground: Color,
    val fenceBackground: Color,
    val metaBackground: Color,
    val highlightBackground: Color,
    val highlightText: Color,
    val alertNote: Color,
    val alertImportant: Color,
    val alertWarning: Color,
    val alertTip: Color,
    val alertCaution: Color,
)

val LightMdPalette = MdPalette(
    text = Color(0xFF333333),
    headingText = Color(0xFF333333),
    muted = Color(0xFF777777),
    link = Color(0xFF4183C4),
    background = Color(0xFFFFFFFF),
    headingRule = Color(0xFFEEEEEE),
    quoteBar = Color(0xFFDFE2E5),
    ruleColor = Color(0xFFE7E7E7),
    codeBorder = Color(0xFFE7EAED),
    tableBorder = Color(0xFFD9DEE3),
    inlineCodeBackground = Color(0xFFF3F4F4),
    fenceBackground = Color(0xFFF8F8F8),
    metaBackground = Color(0xFFF7F7F7),
    highlightBackground = Color(0xFFFFF3A3),
    highlightText = Color(0xFF333333),
    alertNote = Color(0xFF0969DA),
    alertImportant = Color(0xFF8250DF),
    alertWarning = Color(0xFF9A6700),
    alertTip = Color(0xFF1F883D),
    alertCaution = Color(0xFFCF222E),
)

val DarkMdPalette = MdPalette(
    text = Color(0xFFB8BFC6),
    headingText = Color(0xFFDEDEDE),
    muted = Color(0xFF9DA2A6),
    link = Color(0xFF6DC1E7),
    background = Color(0xFF363B40),
    headingRule = Color(0xFF474D54),
    quoteBar = Color(0xFF474D54),
    ruleColor = Color(0xFF474D54),
    codeBorder = Color(0xFF474D54),
    tableBorder = Color(0xFF474D54),
    inlineCodeBackground = Color(0xFF2B2F33),
    fenceBackground = Color(0xFF333333),
    metaBackground = Color(0xFF2E3033),
    highlightBackground = Color(0xFFD3D40E),
    highlightText = Color(0xFF222222),
    alertNote = Color(0xFF2F81F7),
    alertImportant = Color(0xFFA371F7),
    alertWarning = Color(0xFFD29922),
    alertTip = Color(0xFF1F883D),
    alertCaution = Color(0xFFCF222E),
)

/**
 * Tokens tirados dos temas `github` e `night` do Typora instalado — ver DESIGN.md.
 *
 * O Typora usa raiz de 14px no desktop. Aqui a raiz é 16sp, porque 14sp num
 * tablet segurado à distância de leitura fica pequeno. As proporções entre
 * níveis são as do Typora; só a base mudou.
 *
 * ponytail: estado global simples (`isDark` observável pelo Compose). Vira
 * CompositionLocal só se duas telas no mesmo processo precisarem de temas
 * opostos ao mesmo tempo.
 */
object MdTokens {
    var isDark: Boolean by mutableStateOf(false)

    val palette: MdPalette get() = if (isDark) DarkMdPalette else LightMdPalette

    val base = 16.sp
    val bodyLineHeight = 25.6.sp // 1.6

    val text: Color get() = palette.text
    val headingText: Color get() = palette.headingText
    val muted: Color get() = palette.muted
    val link: Color get() = palette.link
    val background: Color get() = palette.background

    val headingRule: Color get() = palette.headingRule
    val quoteBar: Color get() = palette.quoteBar
    val ruleColor: Color get() = palette.ruleColor
    val codeBorder: Color get() = palette.codeBorder
    val tableBorder: Color get() = palette.tableBorder
    val inlineCodeBackground: Color get() = palette.inlineCodeBackground
    val fenceBackground: Color get() = palette.fenceBackground
    val metaBackground: Color get() = palette.metaBackground // pre.md-meta-block (front matter)
    val highlightBackground: Color get() = palette.highlightBackground
    val highlightText: Color get() = palette.highlightText

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

    val inlineCode: SpanStyle
        get() = SpanStyle(
            fontFamily = FontFamily.Monospace,
            fontSize = base * 0.9f,
            background = inlineCodeBackground,
            color = text,
        )

    val documentPadding = PaddingValues(horizontal = 16.dp, vertical = 24.dp)
}
