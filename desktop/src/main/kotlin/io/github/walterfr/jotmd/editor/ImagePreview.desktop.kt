package io.github.walterfr.jotmd.editor

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.skia.Image as SkiaImage
import java.io.File
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path

private const val MAX_IMAGE_BYTES = 32L * 1024L * 1024L
private const val IMAGE_CACHE_MAX = 32
private val EXPLICIT_SCHEME = Regex("^[A-Za-z][A-Za-z0-9+.-]{1,}:")

// Cache LRU simples por caminho + data de modificação: evita re-decodificar ao rolar o LazyColumn.
private val desktopBitmapCache = object : LinkedHashMap<Pair<String, Long>, ImageBitmap>(IMAGE_CACHE_MAX, 0.75f, true) {
    override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Pair<String, Long>, ImageBitmap>): Boolean =
        size > IMAGE_CACHE_MAX
}

/**
 * Resolve apenas arquivos locais existentes para evitar tráfego de rede (`http`/`https`)
 * ou vazamento de credenciais SMB por caminhos UNC (`\\servidor\...`).
 */
internal fun resolveLocalImageFile(destination: String): File? {
    val trimmed = destination.trim()
    if (trimmed.isEmpty()) return null
    if (trimmed.startsWith("\\\\") || trimmed.startsWith("//")) return null

    val file = when {
        trimmed.startsWith("file:", ignoreCase = true) -> {
            val uri = runCatching { URI(trimmed) }.getOrNull() ?: return null
            if (!uri.scheme.equals("file", ignoreCase = true) || !uri.authority.isNullOrEmpty()) return null
            runCatching { Path.of(uri).toFile() }.getOrNull() ?: return null
        }
        EXPLICIT_SCHEME.containsMatchIn(trimmed) -> return null
        trimmed.length >= 2 && trimmed[0].isLetter() && trimmed[1] == ':' -> {
            if (trimmed.length < 3 || (trimmed[2] != '\\' && trimmed[2] != '/')) return null
            File(trimmed)
        }
        else -> File(trimmed)
    }

    return if (file.isFile && file.canRead() && file.length() in 1..MAX_IMAGE_BYTES) file else null
}

private fun decodeDesktopImage(destination: String): ImageBitmap? {
    val file = resolveLocalImageFile(destination) ?: return null
    val key = file.absolutePath to file.lastModified()
    synchronized(desktopBitmapCache) {
        desktopBitmapCache[key]?.let { return it }
    }
    return try {
        val bytes = Files.readAllBytes(file.toPath())
        SkiaImage.makeFromEncoded(bytes).toComposeImageBitmap().also { bitmap ->
            synchronized(desktopBitmapCache) {
                desktopBitmapCache[key] = bitmap
            }
        }
    } catch (_: Exception) {
        null
    } catch (_: OutOfMemoryError) {
        null
    }
}

@Composable
internal fun ImagePreview(image: ImageRef, modifier: Modifier = Modifier) {
    val bitmap by produceState<ImageBitmap?>(initialValue = null, image.destination) {
        value = withContext(Dispatchers.IO) { decodeDesktopImage(image.destination) }
    }
    Box(
        modifier
            .fillMaxWidth()
            .background(MdTokens.fenceBackground, RoundedCornerShape(MdTokens.cornerRadius))
            .border(1.dp, MdTokens.codeBorder, RoundedCornerShape(MdTokens.cornerRadius))
            .padding(12.dp),
    ) {
        Column {
            if (bitmap != null) {
                Image(
                    bitmap = bitmap!!,
                    contentDescription = image.alt,
                    modifier = Modifier.fillMaxWidth().heightIn(max = 320.dp),
                )
            } else {
                BasicText(
                    AnnotatedString("[imagem] ${image.alt.ifBlank { "Imagem" }}"),
                    style = bodyStyle.copy(fontWeight = FontWeight.Medium),
                )
            }
            BasicText(
                AnnotatedString(image.destination),
                style = bodyStyle.copy(color = MdTokens.muted, fontSize = MdTokens.base * 0.8f),
            )
        }
    }
}
