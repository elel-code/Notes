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

/**
 * 便签编辑页面的 ViewModel
 * 
 * 负责处理便签编辑页面中的耗时操作，避免在主线程中执行导致界面卡顿。
 * 主要功能包括：
 * 1. 导出便签为 TXT 文件
 * 2. 将便签内容生成长图片并保存
 * 
 * 技术特点：
 * - 使用 Kotlin Coroutines 处理异步操作
 * - 支持 Android 10+ 的 MediaStore API 和低版本的直接文件访问
 * - 生成图片时使用 StaticLayout 进行文本排版
 * - 自动处理内存回收，避免内存泄漏
 * 
 * @author MiCode Open Source Community
 */
class NoteEditViewModel : ViewModel() {
    
    // ==================== 公共 API ====================
    
    /**
     * 导出当前便签为 TXT 文件
     * 
     * 将便签的文本内容以纯文本格式保存到设备的下载目录中。
     * 文件名格式：note_yyyyMMdd_HHmmss.txt
     * 
     * 执行流程：
     * 1. 在 IO 线程中执行文件写入操作
     * 2. 写入完成后通过回调返回结果
     * 3. 根据 Android 版本使用不同的存储方式
     * 
     * @param context 上下文（用于获取应用目录和 ContentResolver）
     * @param noteText 要导出的便签文本内容
     * @param onComplete 完成回调，参数 Boolean 表示是否导出成功
     */
    fun exportCurrentNoteAsTxt(
        context: Context,
        noteText: String,
        onComplete: (Boolean) -> Unit
    ) {
        val appContext = context.applicationContext  // 使用 ApplicationContext 避免内存泄漏
        viewModelScope.launch(Dispatchers.IO) {
            val exported = writeNoteText(appContext, noteText)  // 执行导出
            withContext(Dispatchers.Main) {
                onComplete(exported)  // 回到主线程回调
            }
        }
    }

    /**
     * 将当前便签保存为长图片
     * 
     * 将便签的文本内容生成一张可滚动长截图风格的图片，
     * 图片会使用便签的背景样式，并保存到相册的 Notes 文件夹中。
     * 文件名格式：note_long_image_yyyyMMdd_HHmmss.png
     * 
     * 执行流程：
     * 1. 在 IO 线程中根据文本内容生成 Bitmap
     * 2. 将 Bitmap 保存到设备存储
     * 3. 回收 Bitmap 释放内存
     * 4. 通过回调返回结果
     * 
     * @param context 上下文（用于获取资源、测量尺寸）
     * @param noteText 便签文本内容
     * @param textSize 文本大小（像素），用于排版
     * @param backgroundResId 背景图片资源 ID
     * @param imageWidth 图片宽度（像素），通常为屏幕宽度
     * @param onComplete 完成回调，参数 Boolean 表示是否保存成功
     */
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
            // 生成长图片 Bitmap
            val bitmap = createLongImageBitmap(
                appContext,
                noteText,
                textSize,
                backgroundResId,
                imageWidth
            )
            // 保存 Bitmap
            val saved = saveLongImageBitmap(appContext, bitmap)
            withContext(Dispatchers.Main) {
                onComplete(saved)
            }
        }
    }

    // ==================== 长图片生成 ====================
    
    /**
     * 生成长图片 Bitmap
     * 
     * 将文本内容绘制成一张可滚动长截图样式的图片。
     * 
     * 实现原理：
     * 1. 使用 TextPaint 和 StaticLayout 进行文本测量和排版
     * 2. 根据文本实际高度计算图片所需高度
     * 3. 创建 Bitmap 并在 Canvas 上绘制背景和文本
     * 
     * @param context 上下文
     * @param noteText 便签文本内容
     * @param textSize 文本大小（像素）
     * @param backgroundResId 背景图片资源 ID
     * @param imageWidth 图片宽度（像素）
     * @return 生成的 Bitmap，失败时返回 null
     */
    private fun createLongImageBitmap(
        context: Context,
        noteText: String,
        textSize: Float,
        backgroundResId: Int,
        imageWidth: Int
    ): Bitmap? {
        return try {
            // 处理空内容：使用空格占位，避免空文本导致布局高度异常
            val content = noteText.ifEmpty { " " }
            
            // 计算内边距
            val horizontalPadding = dpToPx(context, LONG_IMAGE_HORIZONTAL_PADDING_DP)
            val verticalPadding = dpToPx(context, LONG_IMAGE_VERTICAL_PADDING_DP)
            
            // 计算文本实际可用宽度
            val contentWidth = max(imageWidth - horizontalPadding * 2, dpToPx(context, 160))

            // 配置文本画笔
            val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.rgb(51, 51, 51)  // 深灰色文字
                this.textSize = if (textSize > 0) textSize else spToPx(context, 18)  // 默认 18sp
            }

            // 创建文本布局（测量文本占用的实际高度）
            val layout = createTextLayout(context, content, textPaint, contentWidth)
            
            // 计算图片总高度（文本高度 + 上下内边距）
            val imageHeight = max(
                layout.height + verticalPadding * 2,
                dpToPx(context, LONG_IMAGE_MIN_HEIGHT_DP)  // 最小高度限制
            )

            // 创建 Bitmap 并绘制内容
            createBitmap(imageWidth, imageHeight, Bitmap.Config.ARGB_8888).apply {
                val canvas = Canvas(this)
                // 绘制背景
                drawLongImageBackground(context, canvas, backgroundResId, imageWidth, imageHeight)
                // 平移画布后绘制文本（留出内边距）
                canvas.withTranslation(horizontalPadding.toFloat(), verticalPadding.toFloat()) {
                    layout.draw(this)
                }
            }
        } catch (error: OutOfMemoryError) {
            // OOM 保护：避免因图片过大导致崩溃
            Log.e(TAG, "Create long image bitmap failed", error)
            null
        }
    }

    /**
     * 创建文本布局（StaticLayout）
     * 
     * StaticLayout 是 Android 提供的文本排版工具，可以自动处理换行、行间距等。
     * 
     * @param context 上下文
     * @param content 文本内容
     * @param textPaint 文本画笔
     * @param contentWidth 文本区域宽度（像素）
     * @return StaticLayout 实例，包含了文本排版信息
     */
    private fun createTextLayout(
        context: Context,
        content: String,
        textPaint: TextPaint,
        contentWidth: Int
    ): StaticLayout {
        val spacingAdd = dpToPx(context, 4).toFloat()  // 额外行间距（4dp）
        return StaticLayout.Builder
            .obtain(content, 0, content.length, textPaint, contentWidth)
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)  // 左对齐
            .setLineSpacing(spacingAdd, 1.4f)             // 行间距 = spacingAdd + 1.4倍行高
            .setIncludePad(false)                         // 不包含额外的上下内边距
            .build()
    }

    /**
     * 绘制长图片的背景
     * 
     * 优先使用传入的背景图片资源，如果加载失败则使用白色背景。
     * 
     * @param context 上下文
     * @param canvas 画布
     * @param backgroundResId 背景图片资源 ID
     * @param width 图片宽度
     * @param height 图片高度
     */
    private fun drawLongImageBackground(
        context: Context,
        canvas: Canvas,
        backgroundResId: Int,
        width: Int,
        height: Int
    ) {
        try {
            // 尝试加载背景图片并拉伸绘制
            ResourcesCompat.getDrawable(context.resources, backgroundResId, context.theme)?.let { background ->
                background.setBounds(0, 0, width, height)
                background.draw(canvas)
                return
            }
        } catch (error: Exception) {
            Log.e(TAG, "Draw long image background failed", error)
        }
        // 降级方案：使用白色背景
        canvas.drawColor(Color.WHITE)
    }

    // ==================== 长图片保存 ====================
    
    /**
     * 保存长图片 Bitmap
     * 
     * 根据 Android 版本选择不同的保存方式：
     * - Android 10+：使用 MediaStore API
     * - Android 9 及以下：直接保存到应用的私有外部存储目录
     * 
     * @param context 上下文
     * @param bitmap 要保存的 Bitmap
     * @return true 保存成功，false 保存失败
     */
    private fun saveLongImageBitmap(context: Context, bitmap: Bitmap?): Boolean {
        bitmap ?: return false

        return try {
            // 生成文件名：note_long_image_yyyyMMdd_HHmmss.png
            val displayName =
                "note_long_image_${DateFormat.format("yyyyMMdd_HHmmss", System.currentTimeMillis())}.png"
            val saved = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                saveBitmapToMediaStore(context, bitmap, displayName)  // Android 10+
            } else {
                saveBitmapToScopedFile(
                    context,
                    bitmap,
                    Environment.DIRECTORY_PICTURES,
                    "Notes",
                    displayName
                )  // Android 9 及以下
            }
            saved
        } finally {
            // 确保 Bitmap 被回收，释放内存
            bitmap.recycle()
        }
    }

    // ==================== TXT 导出 ====================
    
    /**
     * 写入便签文本到文件
     * 
     * 根据 Android 版本选择不同的保存方式。
     * 
     * @param context 上下文
     * @param noteText 要导出的文本内容
     * @return true 导出成功，false 导出失败
     */
    private fun writeNoteText(context: Context, noteText: String): Boolean {
        // 生成文件名：note_yyyyMMdd_HHmmss.txt
        val displayName =
            "note_${DateFormat.format("yyyyMMdd_HHmmss", System.currentTimeMillis())}.txt"
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            saveTextToMediaStore(context, noteText, displayName)  // Android 10+
        } else {
            saveTextToScopedFile(
                context,
                noteText,
                Environment.DIRECTORY_DOWNLOADS,
                "Notes",
                displayName
            )  // Android 9 及以下
        }
    }

    // ==================== MediaStore API（Android 10+） ====================
    
    /**
     * 使用 MediaStore 保存文本文件（Android 10+）
     * 
     * 使用 MediaStore API 可以在不申请存储权限的情况下将文件保存到公共目录。
     * 流程：
     * 1. 插入一条 IS_PENDING = 1 的记录（标记为待写入）
     * 2. 写入文件内容
     * 3. 将 IS_PENDING 更新为 0（标记为完成）
     * 
     * @param context 上下文
     * @param noteText 文本内容
     * @param displayName 文件名
     * @return true 保存成功，false 保存失败
     */
    @RequiresApi(Build.VERSION_CODES.Q)
    private fun saveTextToMediaStore(
        context: Context,
        noteText: String,
        displayName: String
    ): Boolean {
        val resolver = context.contentResolver
        // 构建文件元数据
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
            put(MediaStore.MediaColumns.MIME_TYPE, "text/plain")
            put(MediaStore.MediaColumns.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/Notes")  // 保存路径：Downloads/Notes/
            put(MediaStore.MediaColumns.IS_PENDING, 1)  // 标记为待写入状态
        }
        // 插入记录并获取 URI
        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: return false

        return try {
            // 写入文件内容
            resolver.openOutputStream(uri)?.bufferedWriter(Charsets.UTF_8).use { writer ->
                if (writer == null) {
                    resolver.delete(uri, null, null)  // 删除无效记录
                    return false
                }
                writer.write(noteText)
            }
            // 标记为完成
            markMediaStoreItemReady(context, uri)
            true
        } catch (error: IOException) {
            Log.e(TAG, "Export note as txt failed", error)
            resolver.delete(uri, null, null)  // 写入失败，删除记录
            false
        }
    }

    /**
     * 使用 MediaStore 保存图片（Android 10+）
     * 
     * 将 Bitmap 保存到相册的 Notes 文件夹中。
     * 
     * @param context 上下文
     * @param bitmap 图片
     * @param displayName 文件名
     * @return true 保存成功，false 保存失败
     */
    @RequiresApi(Build.VERSION_CODES.Q)
    private fun saveBitmapToMediaStore(
        context: Context,
        bitmap: Bitmap,
        displayName: String
    ): Boolean {
        val resolver = context.contentResolver
        // 构建文件元数据
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
            put(MediaStore.MediaColumns.MIME_TYPE, "image/png")
            put(MediaStore.MediaColumns.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/Notes")  // 保存路径：Pictures/Notes/
            put(MediaStore.MediaColumns.IS_PENDING, 1)  // 标记为待写入
        }
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return false

        return try {
            // 写入图片数据
            resolver.openOutputStream(uri).use { stream ->
                if (stream == null || !bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)) {
                    resolver.delete(uri, null, null)  // 删除无效记录
                    return false
                }
            }
            // 标记为完成
            markMediaStoreItemReady(context, uri)
            true
        } catch (error: IOException) {
            Log.e(TAG, "Save long image bitmap failed", error)
            resolver.delete(uri, null, null)
            false
        }
    }

    /**
     * 标记 MediaStore 项目为就绪状态
     * 
     * 将 IS_PENDING 字段从 1 更新为 0，通知媒体扫描器该文件已完成写入。
     * 这样可以确保图库等应用能够立即显示这个文件。
     * 
     * @param context 上下文
     * @param uri 文件的 Content URI
     */
    private fun markMediaStoreItemReady(context: Context, uri: android.net.Uri) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            return
        }

        context.contentResolver.update(
            uri,
            ContentValues().apply {
                put(MediaStore.MediaColumns.IS_PENDING, 0)  // 标记为完成
            },
            null,
            null
        )
    }

    // ==================== 传统文件保存（Android 9 及以下） ====================
    
    /**
     * 保存文本到应用私有外部存储（Android 9 及以下）
     * 
     * 使用 getExternalFilesDir 获取应用私有外部存储目录，不需要存储权限。
     * 
     * @param context 上下文
     * @param noteText 文本内容
     * @param type 文件类型目录（如 Environment.DIRECTORY_DOWNLOADS）
     * @param childDir 子目录名称
     * @param displayName 文件名
     * @return true 保存成功，false 保存失败
     */
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
            exportFile.delete()  // 写入失败，删除不完整的文件
            false
        }
    }

    /**
     * 保存图片到应用私有外部存储（Android 9 及以下）
     * 
     * @param context 上下文
     * @param bitmap 图片
     * @param type 文件类型目录
     * @param childDir 子目录名称
     * @param displayName 文件名
     * @return true 保存成功，false 保存失败
     */
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

    /**
     * 创建用于导出的文件
     * 
     * 在应用的私有外部存储目录下创建必要的目录和文件。
     * 
     * @param context 上下文
     * @param type 文件类型目录
     * @param childDir 子目录名称
     * @param displayName 文件名
     * @return File 对象，创建失败时返回 null
     */
    private fun createScopedExportFile(
        context: Context,
        type: String,
        childDir: String,
        displayName: String
    ): File? {
        // 获取基础目录（应用私有外部存储）
        val baseDir = context.getExternalFilesDir(type) ?: File(context.filesDir, "exports/$type")
        val exportDir = File(baseDir, childDir)
        
        // 创建目录
        if (!exportDir.exists() && !exportDir.mkdirs()) {
            Log.e(TAG, "Create export directory failed: ${exportDir.absolutePath}")
            return null
        }

        // 创建文件
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

    // ==================== 单位转换 ====================
    
    /**
     * 将 dp 转换为像素
     * 
     * @param context 上下文
     * @param dp dp 值
     * @return 像素值
     */
    private fun dpToPx(context: Context, dp: Int): Int {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            dp.toFloat(),
            context.resources.displayMetrics
        ).toInt()
    }

    /**
     * 将 sp 转换为像素
     * 
     * @param context 上下文
     * @param sp sp 值
     * @return 像素值
     */
    private fun spToPx(context: Context, sp: Int): Float {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_SP,
            sp.toFloat(),
            context.resources.displayMetrics
        )
    }

    // ==================== 伴生对象 ====================
    
    companion object {
        private const val TAG = "NoteEditViewModel"                        // 日志标签
        private const val LONG_IMAGE_HORIZONTAL_PADDING_DP = 24            // 长图水平内边距（24dp）
        private const val LONG_IMAGE_VERTICAL_PADDING_DP = 32              // 长图垂直内边距（32dp）
        private const val LONG_IMAGE_MIN_HEIGHT_DP = 240                   // 长图最小高度（240dp）
    }
}