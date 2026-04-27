/*
 * Copyright (c) 2010-2011, The MiCode Open Source Community (www.micode.net)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * ...
 */
package net.micode.notes.tool

import android.content.Context
import net.micode.notes.R           // 资源类，包含 drawable 资源 ID
import kotlin.random.Random

/**
 * 资源解析工具类。
 *
 * 定义便签背景颜色、字体大小的常量，以及对应的 Drawable 资源数组。
 * 提供根据用户偏好（随机 / 固定）获取默认背景 ID 的功能。
 */
object ResourceParser {
    // ==================== 背景颜色常量 ====================
    const val YELLOW: Int = 0
    const val BLUE: Int = 1
    const val WHITE: Int = 2
    const val GREEN: Int = 3
    const val RED: Int = 4

    /** 默认背景颜色：黄色 (0) */
    val BG_DEFAULT_COLOR: Int = YELLOW

    // ==================== 字体大小常量 ====================
    const val TEXT_SMALL: Int = 0
    const val TEXT_MEDIUM: Int = 1
    const val TEXT_LARGE: Int = 2
    const val TEXT_SUPER: Int = 3

    /** 默认字体大小：中等 (1) */
    val BG_DEFAULT_FONT_SIZE: Int = TEXT_MEDIUM

    /**
     * 获取当前应该使用的默认背景颜色 ID。
     *
     * 逻辑：
     * - 如果用户开启了随机背景，则从 0 到 resourcesSize-1 中随机选取一个。
     * - 否则返回用户在偏好设置中选定的固定默认背景颜色。
     *
     * @param context 上下文，用于读取偏好
     * @return 合法背景颜色 ID
     */
    fun getDefaultBgId(context: Context): Int {
        return if (NotesPreferences.isRandomBackgroundEnabled(context)) {
            // 随机背景开启，生成随机 ID
            Random.nextInt(NoteBgResources.resourcesSize)
        } else {
            // 使用偏好中保存的固定颜色，该方法内部已做边界校验
            NotesPreferences.getDefaultBackgroundColor(context)
        }
    }

    /**
     * 编辑界面使用的背景资源。
     * 包含编辑状态下的背景图和标题栏背景图两个系列。
     */
    object NoteBgResources {
        /** 编辑界面背景图数组，索引对应颜色 ID */
        private val BG_EDIT_RESOURCES = intArrayOf(
            R.drawable.edit_yellow,
            R.drawable.edit_blue,
            R.drawable.edit_white,
            R.drawable.edit_green,
            R.drawable.edit_red
        )

        /** 编辑界面标题栏背景图数组，索引对应颜色 ID */
        private val BG_EDIT_TITLE_RESOURCES = intArrayOf(
            R.drawable.edit_title_yellow,
            R.drawable.edit_title_blue,
            R.drawable.edit_title_white,
            R.drawable.edit_title_green,
            R.drawable.edit_title_red
        )

        /**
         * 根据颜色 ID 获取编辑界面的背景图资源 ID。
         * 内部调用 [safeIndex] 进行越界保护。
         */
        fun getNoteBgResource(id: Int): Int {
            return BG_EDIT_RESOURCES[safeIndex(id, BG_EDIT_RESOURCES.size)]
        }

        /**
         * 根据颜色 ID 获取编辑界面标题栏的背景图资源 ID。
         */
        fun getNoteTitleBgResource(id: Int): Int {
            return BG_EDIT_TITLE_RESOURCES[safeIndex(id, BG_EDIT_TITLE_RESOURCES.size)]
        }

        /** 背景资源数量（可用颜色的总数） */
        val resourcesSize: Int
            get() = BG_EDIT_RESOURCES.size
    }

    /**
     * 桌面小部件使用的背景资源。
     * 包含 2x 和 4x 两种规格的背景图数组。
     */
    object WidgetBgResources {
        /** 2x 小部件背景图数组 */
        private val BG_2X_RESOURCES = intArrayOf(
            R.drawable.widget_2x_yellow,
            R.drawable.widget_2x_blue,
            R.drawable.widget_2x_white,
            R.drawable.widget_2x_green,
            R.drawable.widget_2x_red,
        )

        /**
         * 根据颜色 ID 获取 2x 小部件的背景图资源 ID。
         */
        fun getWidget2xBgResource(id: Int): Int {
            return BG_2X_RESOURCES[safeIndex(id, BG_2X_RESOURCES.size)]
        }

        /** 4x 小部件背景图数组 */
        private val BG_4X_RESOURCES = intArrayOf(
            R.drawable.widget_4x_yellow,
            R.drawable.widget_4x_blue,
            R.drawable.widget_4x_white,
            R.drawable.widget_4x_green,
            R.drawable.widget_4x_red
        )

        /**
         * 根据颜色 ID 获取 4x 小部件的背景图资源 ID。
         */
        fun getWidget4xBgResource(id: Int): Int {
            return BG_4X_RESOURCES[safeIndex(id, BG_4X_RESOURCES.size)]
        }
    }

    /**
     * 安全索引函数。
     * 若传入的 ID 在 [0, size) 范围内则直接返回，否则回退到 [BG_DEFAULT_COLOR]（黄色）。
     * 用于防止因数据错误或偏好被篡改导致的数组越界。
     */
    private fun safeIndex(id: Int, size: Int): Int {
        return if (id in 0 until size) id else BG_DEFAULT_COLOR
    }
}