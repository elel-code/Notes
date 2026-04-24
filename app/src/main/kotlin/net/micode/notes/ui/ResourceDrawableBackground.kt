package net.micode.notes.ui

import android.widget.ImageView
import androidx.annotation.DrawableRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView

@Composable
fun ResourceDrawableBackground(
    @DrawableRes resId: Int,
    modifier: Modifier = Modifier
) {
    AndroidView(
        modifier = modifier,
        factory = { context ->
            ImageView(context).apply {
                scaleType = ImageView.ScaleType.FIT_XY
                setImageResource(resId)
            }
        },
        update = { view ->
            view.setImageResource(resId)
        }
    )
}
