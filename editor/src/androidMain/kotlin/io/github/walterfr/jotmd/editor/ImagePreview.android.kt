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
// ponytail: falhas não são cacheadas (re-tenta a cada composição); cachear null se virar custo.
private val bitmapCache = object : LruCache<String, Bitmap>(
    (Runtime.getRuntime().maxMemory() / 1024 / 8).toInt(),
) {
    override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount / 1024
}

/** Decodifica em duas passadas (dimensões, depois reduzida). Null = fallback textual. */
private fun decodeScaled(context: android.content.Context, destination: String): Bitmap? {
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
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
            ?.also { bitmapCache.put(destination, it) }
    } catch (_: Exception) {
        null
    } catch (_: OutOfMemoryError) {
        null
    }
}

@Composable
internal fun ImagePreview(image: ImageRef, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val bitmap by produceState<Bitmap?>(initialValue = bitmapCache.get(image.destination), image.destination) {
        if (value == null) value = withContext(Dispatchers.IO) { decodeScaled(context, image.destination) }
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
