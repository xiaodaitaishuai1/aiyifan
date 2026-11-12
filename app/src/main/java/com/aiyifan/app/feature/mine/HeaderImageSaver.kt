package com.aiyifan.app.feature.mine

import android.content.ContentValues
import android.content.Context
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.annotation.WorkerThread
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.util.UUID

/** Copies the packaged WebP without decoding, applying the viewer mask, or allocating a Bitmap. */
internal object HeaderImageSaver {
    @WorkerThread
    fun save(context: Context, name: String): Uri {
        require(name.matches(Regex("mine_header_[0-9]{3}\\.webp")))
        return context.assets.open("mine_header/$name").use { input ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val resolver = context.contentResolver
                val values = ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME, "${name.removeSuffix(".webp")}_${UUID.randomUUID()}.webp")
                    put(MediaStore.Images.Media.MIME_TYPE, "image/webp")
                    put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/Aiyifan")
                    put(MediaStore.Images.Media.IS_PENDING, 1)
                }
                val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                    ?: throw IOException("Could not create gallery item")
                try {
                    val output = resolver.openOutputStream(uri) ?: throw IOException("Could not open gallery item")
                    output.use { input.copyTo(it) }
                    val published = resolver.update(uri, ContentValues().apply {
                        put(MediaStore.Images.Media.IS_PENDING, 0)
                    }, null, null)
                    if (published != 1) throw IOException("Could not publish gallery item")
                    uri
                } catch (failure: Exception) {
                    try { resolver.delete(uri, null, null) } catch (cleanup: Exception) { failure.addSuppressed(cleanup) }
                    throw failure
                }
            } else {
                saveLegacy(context, name, input)
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun saveLegacy(context: Context, name: String, input: InputStream): Uri {
        val directory = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "Aiyifan")
        if (!directory.isDirectory && !directory.mkdirs()) throw IOException("Could not create pictures directory")
        val file = File.createTempFile(name.removeSuffix(".webp") + "_", ".webp", directory)
        try {
            file.outputStream().use { input.copyTo(it) }
            MediaScannerConnection.scanFile(context, arrayOf(file.absolutePath), arrayOf("image/webp"), null)
            return Uri.fromFile(file)
        } catch (failure: Exception) {
            file.delete()
            throw failure
        }
    }
}
