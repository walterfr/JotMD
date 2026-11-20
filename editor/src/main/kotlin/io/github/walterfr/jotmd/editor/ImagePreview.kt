package io.github.walterfr.jotmd.editor

/** Largura máxima (px) do bitmap decodificado. Mais que isso é memória gasta sem ganho visível. */
internal const val IMAGE_TARGET_WIDTH = 2048

/**
 * Menor potência de 2 que deixa a largura decodificada <= [target].
 * `inSampleSize` só aceita potências de 2 de forma exata; dimensão inválida (0 ou
 * negativa, p.ex. arquivo corrompido) devolve 1 em vez de explodir.
 */
internal fun sampleSizeFor(width: Int, height: Int, target: Int = IMAGE_TARGET_WIDTH): Int {
    if (width <= 0 || height <= 0 || target <= 0) return 1
    var sample = 1
    while (width / sample > target) sample *= 2
    return sample
}

/**
 * O destino vem de markdown não confiável: só `content://` (SAF/MediaStore) é aberto.
 * `file://` leria arquivos privados do app; `http(s)` vazaria o IP e travaria na rede;
 * caminho relativo não tem base para resolver.
 */
internal fun isAllowedImageScheme(destination: String): Boolean =
    destination.trim().startsWith("content://", ignoreCase = true)
