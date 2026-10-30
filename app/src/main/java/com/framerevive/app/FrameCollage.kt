package com.framerevive.app

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import java.time.format.DateTimeFormatter

object FrameCollage {
    /** One image decoded at a time on the image worker; output capped at 1920 pixels. */
    fun render(photos: List<FramePhoto>,width: Int,height: Int,blur: Boolean,date: Boolean,size: (FramePhoto)->CollageLayout.Size,load: (FramePhoto)->Bitmap): Bitmap {
        require(photos.size in 2..4)
        val scale=minOf(1f,1920f/maxOf(width,height))
        val w=maxOf(32,(width*scale).toInt());val h=maxOf(32,(height*scale).toInt())
        val output=Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888)
        try {
            val canvas=Canvas(output);canvas.drawColor(Color.rgb(20,22,25))
            val paint=Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
            val label=Paint(Paint.ANTI_ALIAS_FLAG).apply { color=Color.WHITE;textSize=maxOf(12f,18f*scale);setShadowLayer(3f,1f,1f,Color.BLACK) }
            fun rect(box:CollageLayout.Box)=RectF(box.left,box.top,box.right,box.bottom)
            for(panel in CollageLayout.arrange(w,h,photos.map(size))) {
                val i=panel.photo;val cell=panel.box
                val bitmap=load(photos[i])
                try {
                    val save=canvas.save();canvas.clipRect(rect(cell))
                    if(blur) {
                        val background=FramePhotoView.background(bitmap)
                        try { canvas.drawBitmap(background,null,rect(CollageLayout.fit(background.width,background.height,cell,true)),paint);canvas.drawColor(0x55000000) }
                        finally { background.recycle() }
                    }
                    canvas.drawBitmap(bitmap,null,rect(CollageLayout.fit(bitmap.width,bitmap.height,cell)),paint)
                    if(date) canvas.drawText(photos[i].date().format(DateTimeFormatter.ofPattern("MMM d, yyyy")),cell.left+12,cell.bottom-12,label)
                    canvas.restoreToCount(save)
                } finally { bitmap.recycle() }
            }
            return output
        } catch(e:Exception) { output.recycle();throw e }
    }
}
