package net.micode.notes.ui

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.text.format.DateFormat
import android.util.Log
import android.util.TypedValue
import androidx.annotation.RequiresApi
import androidx.core.content.res.ResourcesCompat
import androidx.core.graphics.createBitmap
import androidx.core.graphics.withTranslation
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import kotlin.math.max

class NoteEditViewModel : ViewModel() {
    fun exportCurrentNoteAsTxt(
        context: Context,
        noteText: String,
        onComplete: (Boolean) -> Unit
    ) {
        val appContext = context.applicationContext
        viewModelScope.launch(Dispatchers.IO) {
            val exported = writeNoteText(appContext, noteText)
            withContext(Dispatchers.Main) {
                onComplete(exported)
            }
        }
    }

    fun saveCurrentNoteAsLongImage(
        context: Context,
        noteText: String,
        textSize: Float,
        backgroundResId: Int,
        imageWidth: Int,
        onComplete: (Boolean) -> Unit
    ) {
        val appContext = context.applicationContext
        viewModelScope.launch(Dispatchers.IO) {
            val bitmap = createLongImageBitmap(
                appContext,
                noteText,
                textSize,
                backgroundResId,
                imageWidth
            )
            val saved = saveLongImageBitmap(appContext, bitmap)
            withContext(Dispatchers.Main) {
                onComplete(saved)
            }
        }
    }

    private fun createLongImageBitmap(
        context: Context,
        noteText: String,
        textSize: Float,
        backgroundResId: Int,
        imageWidth: Int
    ): Bitmap? {
        return try {
            val content = noteText.ifEmpty { " " }
            val horizontalPadding = dpToPx(context, LONG_IMAGE_HORIZONTAL_PADDING_DP)
            val verticalPadding = dpToPx(context, LONG_IMAGE_VERTICAL_PADDING_DP)
            val contentWidth = max(imageWidth - horizontalPadding * 2, dpToPx(context, 160))

            val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.rgb(51, 51, 51)
                this.textSize = if (textSize > 0) textSize else spToPx(context, 18)
            }

            val layout = createTextLayout(context, content, textPaint, contentWidth)
            val imageHeight = max(
                layout.height + verticalPadding * 2,
                dpToPx(context, LONG_IMAGE_MIN_HEIGHT_DP)
            )

            createBitmap(imageWidth, imageHeight, Bitmap.Config.ARGB_8888).apply {
                val canvas = Canvas(this)
                drawLongImageBackground(context, canvas, backgroundResId, imageWidth, imageHeight)
                canvas.withTranslation(horizontalPadding.toFloat(), verticalPadding.toFloat()) {
                    layout.draw(this)
                }
            }
        } catch (error: OutOfMemoryError) {
            Log.e(TAG, "Create long image bitmap failed", error)
            null
        }
    }

    private fun createTextLayout(
        context: Context,
        content: String,
        textPaint: TextPaint,
        contentWidth: Int
    ): StaticLayout {
        val spacingAdd = dpToPx(context, 4).toFloat()
        return StaticLayout.Builder
            .obtain(content, 0, content.length, textPaint, contentWidth)
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setLineSpacing(spacingAdd, 1.4f)
            .setIncludePad(false)
            .build()
    }

    private fun drawLongImageBackground(
        context: Context,
        canvas: Canvas,
        backgroundResId: Int,
        width: Int,
        height: Int
    ) {
        try {
            ResourcesCompat.getDrawable(context.resources, backgroundResId, context.theme)?.let { background ->
                background.setBounds(0, 0, width, height)
                background.draw(canvas)
                return
            }
        } catch (error: Exception) {
            Log.e(TAG, "Draw long image background failed", error)
        }
        canvas.drawColor(Color.WHITE)
    }

    private fun saveLongImageBitmap(context: Context, bitmap: Bitmap?): Boolean {
        bitmap ?: return false

        return try {
            val displayName =
                "note_long_image_${DateFormat.format("yyyyMMdd_HHmmss", System.currentTimeMillis())}.png"
            val saved = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                saveBitmapToMediaStore(context, bitmap, displayName)
            } else {
                saveBitmapToScopedFile(
                    context,
                    bitmap,
                    Environment.DIRECTORY_PICTURES,
                    "Notes",
                    displayName
                )
            }
            saved
        } finally {
            bitmap.recycle()
        }
    }

    private fun writeNoteText(context: Context, noteText: String): Boolean {
        val displayName =
            "note_${DateFormat.format("yyyyMMdd_HHmmss", System.currentTimeMillis())}.txt"
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            saveTextToMediaStore(context, noteText, displayName)
        } else {
            saveTextToScopedFile(
                context,
                noteText,
                Environment.DIRECTORY_DOWNLOADS,
                "Notes",
                displayName
            )
        }
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun saveTextToMediaStore(
        context: Context,
        noteText: String,
        displayName: String
    ): Boolean {
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
            put(MediaStore.MediaColumns.MIME_TYPE, "text/plain")
            put(MediaStore.MediaColumns.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/Notes")
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: return false

        return try {
            resolver.openOutputStream(uri)?.bufferedWriter(Charsets.UTF_8).use { writer ->
                if (writer == null) {
                    resolver.delete(uri, null, null)
                    return false
                }
                writer.write(noteText)
            }
            markMediaStoreItemReady(context, uri)
            true
        } catch (error: IOException) {
            Log.e(TAG, "Export note as txt failed", error)
            resolver.delete(uri, null, null)
            false
        }
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun saveBitmapToMediaStore(
        context: Context,
        bitmap: Bitmap,
        displayName: String
    ): Boolean {
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
            put(MediaStore.MediaColumns.MIME_TYPE, "image/png")
            put(MediaStore.MediaColumns.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/Notes")
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return false

        return try {
            resolver.openOutputStream(uri).use { stream ->
                if (stream == null || !bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)) {
                    resolver.delete(uri, null, null)
                    return false
                }
            }
            markMediaStoreItemReady(context, uri)
            true
        } catch (error: IOException) {
            Log.e(TAG, "Save long image bitmap failed", error)
            resolver.delete(uri, null, null)
            false
        }
    }

    private fun markMediaStoreItemReady(context: Context, uri: android.net.Uri) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            return
        }

        context.contentResolver.update(
            uri,
            ContentValues().apply {
                put(MediaStore.MediaColumns.IS_PENDING, 0)
            },
            null,
            null
        )
    }

    private fun saveTextToScopedFile(
        context: Context,
        noteText: String,
        type: String,
        childDir: String,
        displayName: String
    ): Boolean {
        val exportFile = createScopedExportFile(context, type, childDir, displayName) ?: return false
        return try {
            exportFile.bufferedWriter(Charsets.UTF_8).use { writer ->
                writer.write(noteText)
            }
            true
        } catch (error: IOException) {
            Log.e(TAG, "Export note as txt failed", error)
            exportFile.delete()
            false
        }
    }

    private fun saveBitmapToScopedFile(
        context: Context,
        bitmap: Bitmap,
        type: String,
        childDir: String,
        displayName: String
    ): Boolean {
        val exportFile = createScopedExportFile(context, type, childDir, displayName) ?: return false
        return try {
            FileOutputStream(exportFile).use { output ->
                if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)) {
                    exportFile.delete()
                    return false
                }
                output.flush()
            }
            true
        } catch (error: IOException) {
            Log.e(TAG, "Save long image bitmap failed", error)
            exportFile.delete()
            false
        }
    }

    private fun createScopedExportFile(
        context: Context,
        type: String,
        childDir: String,
        displayName: String
    ): File? {
        val baseDir = context.getExternalFilesDir(type) ?: File(context.filesDir, "exports/$type")
        val exportDir = File(baseDir, childDir)
        if (!exportDir.exists() && !exportDir.mkdirs()) {
            Log.e(TAG, "Create export directory failed: ${exportDir.absolutePath}")
            return null
        }

        val exportFile = File(exportDir, displayName)
        return try {
            if (!exportFile.exists() && !exportFile.createNewFile()) {
                Log.e(TAG, "Create export file failed: ${exportFile.absolutePath}")
                null
            } else {
                exportFile
            }
        } catch (error: IOException) {
            Log.e(TAG, "Create export file failed", error)
            null
        }
    }

    private fun dpToPx(context: Context, dp: Int): Int {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            dp.toFloat(),
            context.resources.displayMetrics
        ).toInt()
    }

    private fun spToPx(context: Context, sp: Int): Float {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_SP,
            sp.toFloat(),
            context.resources.displayMetrics
        )
    }

    companion object {
        private const val TAG = "NoteEditViewModel"
        private const val LONG_IMAGE_HORIZONTAL_PADDING_DP = 24
        private const val LONG_IMAGE_VERTICAL_PADDING_DP = 32
        private const val LONG_IMAGE_MIN_HEIGHT_DP = 240
    }
}
