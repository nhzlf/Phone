package org.fossify.phone.helpers

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.drawable.BitmapDrawable
import android.widget.ImageView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions
import com.bumptech.glide.request.RequestOptions
import org.fossify.commons.extensions.getContrastColor
import org.fossify.commons.extensions.getNameLetter
import org.fossify.commons.helpers.letterBackgroundColors
import kotlin.math.abs

/**
 * 修改时间：2026-10-10 17:51:54（本机）
 * 修改原因：默认 SimpleContactsHelper 按「全名 hash」上色，同姓不同名颜色不一致；需同字同色。
 * 功能说明：无头像时画首字圆标；颜色按下标 = abs(首字.hashCode()) % 调色板，保证同字同色。
 */
object ContactLetterIconHelper {

    fun loadContactImage(context: Context, path: String, imageView: ImageView, placeholderName: String) {
        val placeholder = BitmapDrawable(context.resources, getContactLetterIcon(context, placeholderName))
        val options = RequestOptions()
            .diskCacheStrategy(DiskCacheStrategy.RESOURCE)
            .error(placeholder)
            .centerCrop()

        Glide.with(context)
            .load(path.ifBlank { null })
            .transition(DrawableTransitionOptions.withCrossFade())
            .placeholder(placeholder)
            .apply(options)
            .apply(RequestOptions.circleCropTransform())
            .into(imageView)
    }

    fun getContactLetterIcon(context: Context, name: String): Bitmap {
        val letter = name.getNameLetter()
        val size = context.resources.getDimensionPixelSize(
            org.fossify.commons.R.dimen.normal_icon_size
        )
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val circlePaint = Paint().apply {
            // 同字同色：只对首字哈希，不用全名
            color = letterBackgroundColors[abs(letter.hashCode()) % letterBackgroundColors.size].toInt()
            isAntiAlias = true
        }

        val textPaint = Paint().apply {
            color = circlePaint.color.getContrastColor()
            isAntiAlias = true
            textAlign = Paint.Align.CENTER
            textSize = size / 2f
            style = Paint.Style.FILL
        }

        canvas.drawCircle(size / 2f, size / 2f, size / 2f, circlePaint)
        val xPos = canvas.width / 2f
        val yPos = canvas.height / 2f - (textPaint.descent() + textPaint.ascent()) / 2f
        canvas.drawText(letter, xPos, yPos, textPaint)
        return bitmap
    }
}
