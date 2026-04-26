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
package net.micode.notes.tool

import android.app.PendingIntent

/**
 * PendingIntent 标志兼容性工具类。
 *
 * 从 Android 12 (API 31) 开始，创建 [PendingIntent] 时必须明确指定
 * [PendingIntent.FLAG_IMMUTABLE] 或 [PendingIntent.FLAG_MUTABLE]，
 * 否则会抛出 IllegalArgumentException。此工具类集中提供不可变标志及其组合，
 * 保证应用在任意需要 PendingIntent 的场景（通知、小部件等）都能安全创建。
 */
object PendingIntentCompat {

    /**
     * 返回单纯的不可变标志。
     * 使用场景：创建一个全新的 PendingIntent，且其内部 Intent 不需要后续更新。
     * 
     * @return [PendingIntent.FLAG_IMMUTABLE] 标志值。
     */
    fun immutableFlag(): Int = PendingIntent.FLAG_IMMUTABLE

    /**
     * 返回“更新当前 + 不可变”的组合标志。
     *
     * [PendingIntent.FLAG_UPDATE_CURRENT] 表示如果系统中已存在
     * 相同请求码（requestCode）和 Intent 的 PendingIntent，
     * 则更新其中的 Extra 数据，保留原 PendingIntent 实例。
     * 与 [PendingIntent.FLAG_IMMUTABLE] 组合后，
     * 既能更新内容，又能保证 PendingIntent 不可被外部应用修改。
     *
     * 典型场景：更新状态栏通知的进度或内容。
     *
     * @return [PendingIntent.FLAG_UPDATE_CURRENT] | [PendingIntent.FLAG_IMMUTABLE]
     */
    fun updateCurrentImmutableFlag(): Int {
        return PendingIntent.FLAG_UPDATE_CURRENT or immutableFlag()
    }
}