/*
 * Copyright (c) 2010-2011, The MiCode Open Source Community (www.micode.net)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package net.micode.notes.ui

import android.app.Activity
import android.app.AlertDialog
import android.content.DialogInterface
import android.content.Intent
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.view.Window
import android.view.WindowManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import net.micode.notes.R
import net.micode.notes.data.Notes
import net.micode.notes.data.NotesRepository
import java.io.IOException

/**
 * 闹钟提醒Activity
 * 
 * 当用户为便签设置时间提醒后，系统在指定时间启动此Activity。
 * 
 * 主要功能：
 * 1. 在锁屏界面或亮屏状态下显示提醒对话框
 * 2. 播放系统闹钟铃声，循环播放直至用户响应
 * 3. 提供"知道了"（关闭提醒）和"查看"（进入便签编辑页）两种响应方式
 * 4. 自动处理便签已被删除的情况
 * 
 * 继承关系：
 * - Activity：标准的Android活动组件
 * - DialogInterface.OnClickListener：处理对话框按钮点击事件
 * - DialogInterface.OnDismissListener：处理对话框关闭事件
 * 
 * @author MiCode Open Source Community
 */
class AlarmAlertActivity : Activity(), DialogInterface.OnClickListener,
    DialogInterface.OnDismissListener {
    
    // ==================== 成员变量 ====================
    
    /**
     * 触发提醒的便签ID
     * 从Intent中获取，用于查询便签内容和后续跳转
     */
    private var mNoteId: Long = 0
    
    /**
     * 便签内容摘要
     * 显示在对话框中的文本内容，超过60字符时会截断并添加"…"
     */
    private var mSnippet: String? = null
    
    /**
     * 媒体播放器实例
     * 用于播放系统闹钟铃声，可为null（当播放失败或未初始化时）
     */
    var mPlayer: MediaPlayer? = null

    // ==================== 生命周期方法 ====================
    
    /**
     * Activity创建时的回调方法
     * 
     * 执行流程：
     * 1. 配置窗口属性（支持锁屏显示、点亮屏幕）
     * 2. 从Intent中提取便签ID（支持EXTRA_UID和URI两种方式）
     * 3. 异步查询便签摘要内容
     * 4. 验证便签是否仍存在且可见
     * 5. 若便签有效则显示对话框并播放铃声，否则直接关闭
     * 
     * @param savedInstanceState 保存的实例状态（本Activity未使用）
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 移除标题栏，使提醒界面更简洁
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        // 配置窗口以支持闹钟提醒的特殊显示需求
        configureWindowForAlarm()

        val intent = intent

        try {
            // 提取便签ID - 支持两种数据传递方式
            // 方式1：直接从Intent的EXTRA_UID extra中获取
            // 方式2：从Intent的URI路径中解析（格式：content://.../noteId）
            mNoteId = intent.getLongExtra(Intent.EXTRA_UID, 0L).takeIf { it > 0L }
                ?: intent.data?.pathSegments?.getOrNull(1)?.toLongOrNull()
                ?: throw IllegalArgumentException("Missing note id in alarm intent")
            
            // 创建数据仓库实例，用于访问数据库
            val repository = NotesRepository(applicationContext)
            
            // 从数据库获取便签摘要内容
            // 使用runBlocking同步执行协程，简化异步处理（数据量小，影响可接受）
            val snippet = runBlocking(Dispatchers.IO) {
                repository.getSnippetById(mNoteId).orEmpty()
            }
            
            // 处理摘要过长的情况：截断并添加省略号标记
            mSnippet = if (snippet.length > SNIPPET_PREW_MAX_LEN)
                snippet.substring(
                    0,
                    SNIPPET_PREW_MAX_LEN
                ) + getResources().getString(R.string.notelist_string_info)
            else
                snippet
        } catch (e: IllegalArgumentException) {
            // 无法获取有效的便签ID，打印错误并结束Activity
            e.printStackTrace()
            return
        }

        // 初始化媒体播放器
        mPlayer = MediaPlayer()
        
        // 检查便签是否仍然存在且可见（可能在闹钟触发前已被删除）
        val visible = runBlocking(Dispatchers.IO) {
            NotesRepository(applicationContext).isVisibleNote(mNoteId, Notes.TYPE_NOTE)
        }
        
        if (visible) {
            // 便签有效：显示提醒对话框并播放闹钟铃声
            showActionDialog()
            playAlarmSound()
        } else {
            // 便签已不存在（已被删除或移入回收站），直接关闭Activity
            finish()
        }
    }

    // ==================== 属性方法 ====================
    
    /**
     * 判断屏幕是否处于亮屏/交互状态
     * 
     * 用于决定是否显示"查看"按钮：
     * - 屏幕亮起时，用户可以方便地点击"查看"进入便签编辑页
     * - 屏幕锁定时，仅显示"知道了"按钮，简化用户操作
     * 
     * 注意：使用isInteractive而非isScreenOn，因为isInteractive更能准确反映用户可交互状态
     * 
     * @return true表示屏幕亮起且用户可交互，false表示屏幕已关闭
     */
    private val isScreenOn: Boolean
        get() = (getSystemService(POWER_SERVICE) as PowerManager).isInteractive

    // ==================== 窗口配置方法 ====================
    
    /**
     * 配置窗口属性，确保闹钟提醒能够正常显示
     * 
     * 需要实现的特性：
     * 1. 锁屏状态下显示 - 即使手机被锁定，提醒界面也能显示
     * 2. 点亮屏幕 - 如果手机处于待机黑屏状态，自动点亮屏幕
     * 3. 保持屏幕常亮 - 提醒期间防止屏幕自动熄灭
     * 
     * Android版本兼容性处理：
     * - API 27 (Android 8.1) 及以上：使用 setShowWhenLocked() 和 setTurnScreenOn() 方法
     * - API 26 及以下：使用传统的 WindowManager.LayoutParams flags
     * 
     * 注意：低版本API使用了废弃的FLAG_SHOW_WHEN_LOCKED等标志，这是版本兼容的需要
     */
    @Suppress("DEPRECATION")
    private fun configureWindowForAlarm() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            // Android 8.1+：使用推荐的新版API
            setShowWhenLocked(true)     // 允许在锁屏界面上方显示
            setTurnScreenOn(true)       // 允许点亮屏幕
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)  // 保持屏幕常亮
            return
        }

        // Android 8.0及以下：使用传统的window flag组合实现相同效果
        window.addFlags(
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or          // 保持屏幕常亮
                    WindowManager.LayoutParams.FLAG_ALLOW_LOCK_WHILE_SCREEN_ON or  // 允许屏幕亮起时锁屏
                    WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or          // 锁屏时显示
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON               // 点亮屏幕
        )
    }

    // ==================== 闹钟铃声相关方法 ====================
    
    /**
     * 播放系统闹钟铃声
     * 
     * 铃声获取方式：
     * - 通过 RingtoneManager.getActualDefaultRingtoneUri() 获取系统默认闹钟铃声
     * - 使用 TYPE_ALARM 类型，获取闹钟类别铃声（而非通知或铃声）
     * 
     * 音频属性配置：
     * - USAGE_ALARM：标识为闹钟用途，系统会给予较高优先级
     * - CONTENT_TYPE_SONIFICATION：内容类型为提示音/通知音
     * 
     * 播放设置：
     * - isLooping = true：循环播放，确保用户不会因短暂错过而失效
     * 
     * 异常处理：
     * 捕获多种异常（IllegalArgumentException、SecurityException等），
     * 仅打印堆栈信息而不影响提醒的显示，确保闹钟提醒的基本功能可用
     */
    private fun playAlarmSound() {
        // 获取系统默认闹钟铃声的URIs
        val url = RingtoneManager.getActualDefaultRingtoneUri(this, RingtoneManager.TYPE_ALARM)
        val player = mPlayer ?: return  // 如果MediaPlayer未初始化，静默返回
        
        // 配置音频属性，明确标识为闹钟用途
        player.setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ALARM)           // 用途：闹钟
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)  // 内容类型：提示音
                .build()
        )
        
        // 设置数据源并开始播放
        try {
            player.setDataSource(this, url)   // 设置音频数据源
            player.prepare()                   // 准备播放（同步准备）
            player.isLooping = true            // 循环播放
            player.start()                     // 开始播放
        } catch (e: IllegalArgumentException) {
            // 参数异常（如URI格式错误）
            e.printStackTrace()
        } catch (e: SecurityException) {
            // 安全异常（如缺少读取铃声的权限）
            e.printStackTrace()
        } catch (e: IllegalStateException) {
            // 状态异常（MediaPlayer处于错误状态）
            e.printStackTrace()
        } catch (e: IOException) {
            // IO异常（铃声文件无法访问）
            e.printStackTrace()
        }
    }
    
    /**
     * 停止闹钟铃声并释放资源
     * 
     * 该方法在对话框关闭时调用，确保：
     * 1. 停止当前正在播放的铃声
     * 2. 释放MediaPlayer占用的系统资源
     * 3. 将引用置为null，辅助垃圾回收
     * 
     * 注意：该方法需要安全处理player为null的情况
     */
    private fun stopAlarmSound() {
        mPlayer?.let { player ->
            player.stop()    // 停止播放
            player.release() // 释放资源
            mPlayer = null   // 辅助GC
        }，
    }

    // ==================== 对话框相关方法 ====================
    
    /**
     * 显示提醒对话框
     * 
     * 对话框配置：
     * - 标题：应用名称（"便签"）
     * - 消息内容：便签摘要（mSnippet）
     * - "知道了"按钮（PositiveButton）：始终显示，点击后仅关闭对话框
     * - "查看"按钮（NegativeButton）：仅在屏幕亮起时显示
     * 
     * "查看"按钮的条件显示逻辑：
     * 屏幕亮起时，用户可以舒适地阅读和点击，因此显示"查看"按钮
     * 屏幕锁定时，用户可能只是短暂查看，显示"知道了"更简洁
     * 
     * 监听器设置：
     * - OnClickListener：处理按钮点击（本Activity实现）
     * - OnDismissListener：处理对话框关闭（本Activity实现）
     */
    private fun showActionDialog() {
        val dialog = AlertDialog.Builder(this)
        dialog.setTitle(R.string.app_name)                    // 标题：便签
        dialog.setMessage(mSnippet)                           // 内容：便签摘要
        dialog.setPositiveButton(R.string.notealert_ok, this) // 按钮：知道了
        if (this.isScreenOn) {
            // 屏幕亮起时才显示"查看"按钮
            dialog.setNegativeButton(R.string.notealert_enter, this)  // 按钮：查看
        }
        dialog.show().setOnDismissListener(this)  // 显示对话框并设置关闭监听器
    }

    // ==================== 回调接口实现 ====================
    
    /**
     * 对话框按钮点击事件处理
     * 
     * 按钮类型：
     * - BUTTON_NEGATIVE（"查看"按钮）：启动NoteEditActivity，跳转到便签编辑页面
     * - BUTTON_POSITIVE（"知道了"按钮）：无需额外处理，对话框关闭即可
     * 
     * 跳转逻辑：
     * 创建Intent，设置ACTION_VIEW动作，并将便签ID通过EXTRA_UID传递给编辑页面
     * 
     * @param dialog 触发点击事件的对话框
     * @param which 被点击的按钮类型（DialogInterface.BUTTON_POSITIVE 或 BUTTON_NEGATIVE）
     */
    override fun onClick(dialog: DialogInterface?, which: Int) {
        when (which) {
            DialogInterface.BUTTON_NEGATIVE -> {
                // 用户点击"查看"按钮：跳转到便签编辑Activity
                val intent = Intent(this, NoteEditActivity::class.java)
                intent.setAction(Intent.ACTION_VIEW)           // 动作：查看
                intent.putExtra(Intent.EXTRA_UID, mNoteId)     // 传递便签ID
                startActivity(intent)                          // 启动Activity
            }
            // BUTTON_POSITIVE（"知道了"按钮）不需要任何处理
            else -> {}
        }
    }
    
    /**
     * 对话框关闭事件处理
     * 
     * 无论用户通过何种方式关闭对话框（点击按钮、返回键、点击外部），
     * 都会执行以下操作：
     * 1. 停止闹钟铃声并释放MediaPlayer资源
     * 2. 关闭当前Activity
     * 
     * 这确保了提醒界面不会残留，资源被及时释放
     * 
     * @param dialog 被关闭的对话框
     */
    override fun onDismiss(dialog: DialogInterface?) {
        stopAlarmSound()  // 停止铃声并释放资源
        finish()          // 结束Activity
    }

    // ==================== 伴生对象 ====================
    
    companion object {
        /**
         * 便签摘要显示的最大长度（字符数）
         * 
         * 超过该长度的便签内容会被截断，并在末尾添加省略号（R.string.notelist_string_info）
         * 设置为60字符的原因：
         * - 手机屏幕宽度通常可显示约20-30个中文字符
         * - 60字符约等于2-3行文本，在对话框中显示比例合适
         * - 既能让用户了解便签内容，又不会因内容过长导致对话框过大
         */
        private const val SNIPPET_PREW_MAX_LEN = 60
    }
}