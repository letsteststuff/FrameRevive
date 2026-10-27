package com.framerevive.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import androidx.appcompat.widget.AppCompatImageView

/** A small, preblurred image fills the unused space behind the sharp original. */
class FramePhotoView(context: Context) : AppCompatImageView(context) {
    var blurredBackground = true
        set(value) { field = value; invalidate() }
    private var backdrop: Bitmap? = null
    private val backdropPaint = Paint(Paint.FILTER_BITMAP_FLAG)

    fun setPhoto(photo: Bitmap, background: Bitmap) {
        val old = backdrop
        backdrop = background
        setImageBitmap(photo)
        old?.recycle()
    }

    override fun onDraw(canvas: Canvas) {
        backdrop?.takeIf { blurredBackground && scaleType != ScaleType.CENTER_CROP }?.let { b ->
            val scale = maxOf(width.toFloat() / b.width, height.toFloat() / b.height)
            val w = b.width * scale; val h = b.height * scale
            canvas.drawBitmap(b, null, RectF((width-w)/2, (height-h)/2, (width+w)/2, (height+h)/2), backdropPaint)
            canvas.drawColor(0x55000000)
        }
        super.onDraw(canvas)
    }

    companion object {
        /** Runs on the image worker; bounded to 96 pixels along the longest edge. */
        fun background(photo: Bitmap): Bitmap {
            val scale = 96f / maxOf(photo.width, photo.height)
            val w = maxOf(1, (photo.width * scale).toInt())
            val h = maxOf(1, (photo.height * scale).toInt())
            val small = Bitmap.createScaledBitmap(photo,w,h,true)
            var pixels = IntArray(w*h)
            small.getPixels(pixels,0,w,0,0,w,h)
            if (small !== photo) small.recycle()
            repeat(3) {
                val result = IntArray(w*h)
                for (y in 0 until h) for (x in 0 until w) {
                    var red=0; var green=0; var blue=0; var count=0
                    for (yy in maxOf(0,y-4)..minOf(h-1,y+4)) for (xx in maxOf(0,x-4)..minOf(w-1,x+4)) {
                        val c=pixels[yy*w+xx]; red+=Color.red(c); green+=Color.green(c); blue+=Color.blue(c); count++
                    }
                    result[y*w+x]=Color.rgb(red/count,green/count,blue/count)
                }
                pixels=result
            }
            return Bitmap.createBitmap(pixels,w,h,Bitmap.Config.ARGB_8888)
        }
    }
}
