package net.micode.notes.ui

import android.widget.ImageView
import androidx.annotation.DrawableRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView

/**
 * 资源图片背景组件
 * 
 * 这是一个 Compose 可组合函数，用于将 Android 传统的 Drawable 资源作为背景显示。
 * 
 * 主要功能：
 * - 将传统的图片资源（如 .png、.9.png、selector 等）显示为背景
 * - 自动拉伸图片以填满给定的空间（使用 FIT_XY 缩放类型）
 * - 兼容旧版 UI 资源，无需迁移到 Compose 的 Image 组件
 * 
 * 使用场景：
 * - 便签列表的背景（纸张纹理效果）
 * - 便签编辑页面的背景（纸张纹理）
 * - 设置页面的背景
 * - 任何需要显示传统 Drawable 资源作为背景的地方
 * 
 * 实现原理：
 * 使用 AndroidView 将传统的 ImageView 嵌入到 Compose UI 树中。
 * 这样可以复用项目中已有的图片资源，无需重新设计或转换。
 * 
 * 为什么需要这个组件：
 * - 项目长期以来积累了大量传统图片资源
 * - 这些资源可能包括 9-patch 图片、状态选择器等
 * - 完全迁移到 Compose 的 Image 组件成本较高
 * - 通过 AndroidView 桥接，可以平滑过渡
 * 
 * @param resId 图片资源 ID（R.drawable.xxx）
 * @param modifier 修饰符，用于控制组件的大小、位置等布局属性
 * 
 * @author MiCode Open Source Community
 */
@Composable
fun ResourceDrawableBackground(
    @DrawableRes resId: Int,
    modifier: Modifier = Modifier
) {
    // 使用 AndroidView 将传统 View 嵌入 Compose
    AndroidView(
        modifier = modifier,
        factory = { context ->
            // 创建 ImageView 实例
            ImageView(context).apply {
                // 设置缩放类型为 FIT_XY
                // FIT_XY 会将图片独立缩放以匹配目标尺寸，可能改变宽高比
                // 这确保背景图片完全填满给定区域，适合作为背景使用
                scaleType = ImageView.ScaleType.FIT_XY
                // 设置要显示的图片资源
                setImageResource(resId)
            }
        },
        update = { view ->
            // 当 resId 发生变化时，更新 ImageView 显示的图片
            // 这确保在重组时如果传入不同的资源 ID，背景会相应更新
            view.setImageResource(resId)
        }
    )
}