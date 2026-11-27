package io.github.walterfr.jotmd.editor

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.LruCache
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// Cache por destino: sem ele o LazyColumn re-decodifica a cada vez que o bloco volta à tela.
// 1/8 da heap, medido em KB (sizeOf devolve KB também).
private val bitmapCache = object : LruCache<String, Bitmap>(
    (Runtime.getRuntime().maxMemory() / 1024 / 8).toInt(),
) {
    override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount / 1024
}

// Cache de destinos que falharam ao abrir/decodificar para evitar I/O repetido a cada recomposição.
private val failedImageCache = LruCache<String, Boolean>(256)

/** Decodifica em duas passadas (dimensões, depois reduzida). Null = fallback textual. */
private fun decodeScaled(context: android.content.Context, destination: String): Bitmap? {
    if (failedImageCache.get(destination) == true) return null
    if (!isAllowedImageScheme(destination)) return null
    bitmapCache.get(destination)?.let { return it }
    return try {
        val uri = Uri.parse(destination.trim())
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        val options = BitmapFactory.Options().apply {
            inSampleSize = sampleSizeFor(bounds.outWidth, bounds.outHeight)
        }
        // Stream novo: o da primeira passada já foi consumido (SAF não garante mark/reset).
        val decoded = context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, options)
        }
        if (decoded != null) {
            failedImageCache.remove(destination)
            bitmapCache.put(destination, decoded)
        } else {
            failedImageCache.put(destination, true)
        }
        decoded
    } catch (_: Exception) {
        failedImageCache.put(destination, true)
        null
    } catch (_: OutOfMemoryError) {
        null
    }
}

@Composable
internal fun ImagePreview(image: ImageRef, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val cachedBitmap = remember(image.destination) { bitmapCache.get(image.destination) }
    val cachedFailure = remember(image.destination) { failedImageCache.get(image.destination) == true }
    val bitmap by produceState<Bitmap?>(initialValue = cachedBitmap, image.destination) {
        if (value == null && !cachedFailure && failedImageCache.get(image.destination) != true) {
            value = withContext(Dispatchers.IO) { decodeScaled(context, image.destination) }
        }
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
                    bitmap = bitmap!!.asImageBitmap(),
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
