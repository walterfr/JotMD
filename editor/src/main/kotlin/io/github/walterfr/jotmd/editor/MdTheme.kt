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
 * Presets de tema do Typora disponíveis no editor (`github`, `newsprint`,
 * `pixyll`, `whitey` e `night`).
 */
enum class MdThemePreset(val id: String, val displayName: String, val isDark: Boolean) {
    GITHUB("github", "GitHub", false),
    NEWSPRINT("newsprint", "Newsprint", false),
    PIXYLL("pixyll", "Pixyll", false),
    WHITEY("whitey", "Whitey", false),
    NIGHT("night", "Night", true),
}

/**
 * Paleta de cores do editor (temas do Typora — ver DESIGN.md).
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
    val sidebarBackground: Color,
    val activeItemBackground: Color,
    val activeItemBorder: Color,
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
    sidebarBackground = Color(0xFFFAFAFA),
    activeItemBackground = Color(0xFFE8EEF5),
    activeItemBorder = Color(0xFF4183C4),
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
    sidebarBackground = Color(0xFF2E3033),
    activeItemBackground = Color(0xFF222222),
    activeItemBorder = Color(0xFF8D8DF0),
)

val NewsprintMdPalette = MdPalette(
    text = Color(0xFF1F0909),
    headingText = Color(0xFF1F0909),
    muted = Color(0xFF656565),
    link = Color(0xFF065588),
    background = Color(0xFFF3F2EE),
    headingRule = Color(0xFFC5C5C5),
    quoteBar = Color(0xFFDADADA),
    ruleColor = Color(0xFFDCDAD5),
    codeBorder = Color(0xFFD6D4CE),
    tableBorder = Color(0xFFD6D4CE),
    inlineCodeBackground = Color(0xFFEAE8E1),
    fenceBackground = Color(0xFFEAE9E5),
    metaBackground = Color(0xFFEAE9E5),
    highlightBackground = Color(0xFFFFF3A3),
    highlightText = Color(0xFF1F0909),
    alertNote = LightMdPalette.alertNote,
    alertImportant = LightMdPalette.alertImportant,
    alertWarning = LightMdPalette.alertWarning,
    alertTip = LightMdPalette.alertTip,
    alertCaution = LightMdPalette.alertCaution,
    sidebarBackground = Color(0xFFECEAE4),
    activeItemBackground = Color(0xFFE2DFD7),
    activeItemBorder = Color(0xFF065588),
)

val PixyllMdPalette = MdPalette(
    text = Color(0xFF333333),
    headingText = Color(0xFF333333),
    muted = Color(0xFF7A7A7A),
    link = Color(0xFF463F5C),
    background = Color(0xFFFFFFFF),
    headingRule = Color(0xFFEEEEEE),
    quoteBar = Color(0xFF7A7A7A),
    ruleColor = Color(0xFFE7E7E7),
    codeBorder = Color(0xFFE7EAED),
    tableBorder = Color(0xFFDFE2E5),
    inlineCodeBackground = Color(0xFFF3F4F4),
    fenceBackground = Color(0xFFF8F8F8),
    metaBackground = Color(0xFFF7F7F7),
    highlightBackground = Color(0xFFFFF3A3),
    highlightText = Color(0xFF333333),
    alertNote = LightMdPalette.alertNote,
    alertImportant = LightMdPalette.alertImportant,
    alertWarning = LightMdPalette.alertWarning,
    alertTip = LightMdPalette.alertTip,
    alertCaution = LightMdPalette.alertCaution,
    sidebarBackground = Color(0xFFFAFAFA),
    activeItemBackground = Color(0xFFEFEBF5),
    activeItemBorder = Color(0xFF463F5C),
)

val WhiteyMdPalette = MdPalette(
    text = Color(0xFF333333),
    headingText = Color(0xFF333333),
    muted = Color(0xFF777777),
    link = Color(0xFF2484C1),
    background = Color(0xFFFEFEFE),
    headingRule = Color(0xFFEEEEEE),
    quoteBar = Color(0xFFE5E5E5),
    ruleColor = Color(0xFFE7E7E7),
    codeBorder = Color(0xFFEAEAEA),
    tableBorder = Color(0xFFDFDFDF),
    inlineCodeBackground = Color(0xFFF5F5F5),
    fenceBackground = Color(0xFFF8F8F8),
    metaBackground = Color(0xFFF6F6F6),
    highlightBackground = Color(0xFFFFF3A3),
    highlightText = Color(0xFF333333),
    alertNote = LightMdPalette.alertNote,
    alertImportant = LightMdPalette.alertImportant,
    alertWarning = LightMdPalette.alertWarning,
    alertTip = LightMdPalette.alertTip,
    alertCaution = LightMdPalette.alertCaution,
    sidebarBackground = Color(0xFFFAFAFA),
    activeItemBackground = Color(0xFFEAF3FA),
    activeItemBorder = Color(0xFF2484C1),
)

/**
 * Tokens tirados dos temas `github`, `newsprint`, `pixyll`, `whitey` e `night`
 * do Typora instalado — ver DESIGN.md.
 *
 * O Typora usa raiz de 14px no desktop. Aqui a raiz é 16sp, porque 14sp num
 * tablet segurado à distância de leitura fica pequeno. As proporções entre
 * níveis são as do Typora; só a base mudou.
 *
 * ponytail: estado global simples (`themePreset` observável pelo Compose). Vira
 * CompositionLocal só se duas telas no mesmo processo precisarem de temas
 * opostos ao mesmo tempo.
 */
object MdTokens {
    var themePreset: MdThemePreset by mutableStateOf(MdThemePreset.GITHUB)

    var isDark: Boolean
        get() = themePreset.isDark
        set(value) {
            themePreset = if (value) {
                MdThemePreset.NIGHT
            } else if (themePreset.isDark) {
                MdThemePreset.GITHUB
            } else {
                themePreset
            }
        }

    val palette: MdPalette
        get() = when (themePreset) {
            MdThemePreset.GITHUB -> LightMdPalette
            MdThemePreset.NEWSPRINT -> NewsprintMdPalette
            MdThemePreset.PIXYLL -> PixyllMdPalette
            MdThemePreset.WHITEY -> WhiteyMdPalette
            MdThemePreset.NIGHT -> DarkMdPalette
        }

    fun cycleThemePreset(): MdThemePreset {
        val entries = MdThemePreset.entries
        themePreset = entries[(themePreset.ordinal + 1) % entries.size]
        return themePreset
    }

    val blurText: Color get() = if (isDark) Color(0xFF686868) else Color(0xFFC8C8C8)
    var maxColumnWidth by mutableStateOf(860.dp)
    var sourceMaxColumnWidth by mutableStateOf(800.dp)

    var base by mutableStateOf(16.sp)
    var bodyLineHeight by mutableStateOf(25.6.sp) // 1.6

    fun applyPreferences(prefs: EditorPrefs) {
        val p = prefs.normalized()
        themePreset = p.themePreset
        base = p.fontSizeSp.sp
        bodyLineHeight = (p.fontSizeSp * 1.6f).sp
        maxColumnWidth = p.maxColumnWidthDp.dp
        sourceMaxColumnWidth = (p.maxColumnWidthDp - 60).coerceAtLeast(560).dp
    }

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
    val sidebarBackground: Color get() = palette.sidebarBackground
    val activeItemBackground: Color get() = palette.activeItemBackground
    val activeItemBorder: Color get() = palette.activeItemBorder

    /** Destaques de ocorrências de busca na nota (`--search-select-bg-color` / `.md-search-hit`). */
    val searchHitBackground: Color get() = if (isDark) Color(0xCFC78C3C) else Color(0x4DF8C074)
    val searchActiveBackground: Color get() = Color(0xFF428BCA)
    val searchActiveText: Color get() = Color.White

    /** Realce sintático para cercas de código e modo código-fonte (`codeblock.dark.css` / `sourcemode.dark.css`). */
    val syntaxKeyword: Color get() = if (isDark) Color(0xFFC88FD0) else Color(0xFFA71D5D)
    val syntaxString: Color get() = if (isDark) Color(0xFFD26B6B) else Color(0xFF183691)
    val syntaxComment: Color get() = if (isDark) Color(0xFFDA924A) else Color(0xFF6A737D)
    val syntaxNumber: Color get() = if (isDark) Color(0xFF64AB8F) else Color(0xFF005CC5)
    val syntaxType: Color get() = if (isDark) Color(0xFF8D8DF0) else Color(0xFF6F42C1)
    val syntaxHeading: Color get() = if (isDark) Color(0xFFCEBCCA) else Color(0xFF9B2393)
    val syntaxMarker: Color get() = if (isDark) Color(0xFF8AA1E1) else Color(0xFF6E7781)
    val syntaxLink: Color get() = if (isDark) Color(0xFF95B94B) else Color(0xFF0969DA)

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
