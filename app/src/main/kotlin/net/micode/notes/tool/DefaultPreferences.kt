// 声明包路径，属于 tool 工具模块，提供通用辅助功能
package net.micode.notes.tool

import android.content.Context
import android.content.SharedPreferences

/**
 * 默认 SharedPreferences 文件名的后缀。
 * 最终文件名示例：net.micode.notes_preferences
 */
private const val DEFAULT_PREFERENCES_SUFFIX = "_preferences"

/**
 * 获取应用默认的 SharedPreferences 实例的扩展函数。
 * 
 * 该函数会在任意 [Context] 上提供 `defaultPreferences()` 方法，
 * 返回一个以 “包名_preferences” 命名的默认偏好文件对应的 [SharedPreferences] 对象。
 * 访问模式为 [Context.MODE_PRIVATE]，仅本应用可读/写。
 * 
 * 使用场景：应用内全局配置、用户设置、缓存零散键值对等。
 * 
 * 示例：
 * ```kotlin
 * val prefs = context.defaultPreferences()
 * prefs.edit().putBoolean("first_launch", false).apply()
 * ```
 */
fun Context.defaultPreferences(): SharedPreferences =
    getSharedPreferences("${packageName}$DEFAULT_PREFERENCES_SUFFIX", Context.MODE_PRIVATE)