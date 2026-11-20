package com.framerevive.app

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.view.View

/** Static edge artwork, drawn only when the display or selected theme changes. */
class HolidayBorderView(context: Context) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var theme = "off"

    init {
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
        visibility = GONE
    }

    fun showTheme(value: String) {
        if (theme == value) return
        theme = value
        visibility = if (value == "off") GONE else VISIBLE
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (theme == "off") return
        canvas.save()
        canvas.scale(width / 1000f, height / 562.5f)
        if (theme == "christmas") {
            paint.color = Color.rgb(36, 67, 40)
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 2f
            val wire = Path().apply {
                moveTo(0f, 8f)
                for (i in 0 until 12) quadTo(i * 84f + 42f, 22f, (i + 1) * 84f, 8f)
            }
            canvas.drawPath(wire, paint)
            paint.style = Paint.Style.FILL
            val colors = intArrayOf(0xFFFF5656.toInt(), 0xFFFFCE63.toInt(), 0xFF5BDF77.toInt(), 0xFF5BA9FF.toInt())
            for (i in 0 until 16) {
                val x = 30f + i * 63f
                paint.color = colors[i % colors.size]
                paint.alpha = 35
                canvas.drawCircle(x, 20f, 10f, paint)
                paint.alpha = 255
                canvas.drawOval(x - 3f, 14f, x + 3f, 24f, paint)
                paint.color = Color.WHITE
                canvas.drawCircle(x - 0.6f, 17f, 1f, paint)
            }
            sprig(canvas, 12f, 548f, 1f)
            sprig(canvas, 988f, 548f, -1f)
        } else {
            for (side in 0..1) {
                val x = if (side == 0) 22f else 978f
                for (i in 0..2) {
                    canvas.save()
                    canvas.translate(x, 35f + i * 239f)
                    symbol(canvas, i + side)
                    canvas.restore()
                }
            }
        }
        canvas.restore()
    }

    private fun sprig(canvas: Canvas, x: Float, y: Float, direction: Float) {
        canvas.save()
        canvas.translate(x, y)
        canvas.scale(direction, 1f)
        paint.color = 0xFF52704A.toInt()
        paint.strokeWidth = 2f
        canvas.drawLine(0f, 0f, 43f, -25f, paint)
        for (i in 1..6) {
            val a = i * 6f
            canvas.drawLine(a, -a * 0.6f, a - 3f, -a * 0.6f - 12f, paint)
            canvas.drawLine(a, -a * 0.6f, a + 12f, -a * 0.6f + 2f, paint)
        }
        paint.color = 0xFFC94C48.toInt()
        canvas.drawCircle(11f, -7f, 3f, paint)
        canvas.drawCircle(18f, -5f, 3f, paint)
        canvas.restore()
    }

    private fun symbol(canvas: Canvas, variant: Int) {
        paint.style = Paint.Style.FILL
        when (theme) {
            "winter" -> {
                paint.color = 0xFFD3E9F1.toInt()
                paint.strokeWidth = 1.4f
                for (i in 0..5) {
                    canvas.save()
                    canvas.rotate(i * 60f)
                    canvas.drawLine(0f, 0f, 0f, -12f, paint)
                    canvas.drawLine(0f, -7f, -4f, -10f, paint)
                    canvas.drawLine(0f, -7f, 4f, -10f, paint)
                    canvas.restore()
                }
            }
            "valentine" -> {
                paint.color = 0xFFE58A9C.toInt()
                val heart = Path().apply {
                    moveTo(0f, 12f)
                    cubicTo(-25f, -3f, -10f, -19f, 0f, -7f)
                    cubicTo(10f, -19f, 25f, -3f, 0f, 12f)
                }
                canvas.drawPath(heart, paint)
            }
            "spring" -> {
                paint.color = if (variant % 2 == 0) 0xFFE5A4C7.toInt() else 0xFFE6E1C7.toInt()
                for (i in 0..4) {
                    canvas.save()
                    canvas.rotate(i * 72f)
                    canvas.drawOval(-4f, -14f, 4f, -2f, paint)
                    canvas.restore()
                }
                paint.color = 0xFFEBC26A.toInt()
                canvas.drawCircle(0f, 0f, 4f, paint)
            }
            "easter" -> {
                paint.color = if (variant % 2 == 0) 0xFFAFC8E2.toInt() else 0xFFDAB3CF.toInt()
                canvas.drawOval(-8f, -13f, 8f, 12f, paint)
                paint.color = 0xFFF4E5AE.toInt()
                canvas.drawCircle(-3f, -4f, 2f, paint)
                canvas.drawCircle(3f, 4f, 2f, paint)
            }
            "summer" -> {
                paint.color = 0xFFEBD087.toInt()
                val star = Path()
                for (i in 0 until 10) {
                    val angle = Math.PI * i / 5 - Math.PI / 2
                    val radius = if (i % 2 == 0) 13 else 5
                    val x = (kotlin.math.cos(angle) * radius).toFloat()
                    val y = (kotlin.math.sin(angle) * radius).toFloat()
                    if (i == 0) star.moveTo(x, y) else star.lineTo(x, y)
                }
                star.close()
                canvas.drawPath(star, paint)
            }
            "autumn" -> {
                paint.color = if (variant % 2 == 0) 0xFFD79B48.toInt() else 0xFFB46C3F.toInt()
                canvas.rotate(if (variant % 2 == 0) 30f else -30f)
                val leaf = Path().apply {
                    moveTo(0f, -16f)
                    cubicTo(19f, -4f, 10f, 9f, 0f, 15f)
                    cubicTo(-10f, 9f, -19f, -4f, 0f, -16f)
                }
                canvas.drawPath(leaf, paint)
                paint.color = 0xFF745238.toInt()
                paint.strokeWidth = 1f
                canvas.drawLine(0f, -10f, 0f, 19f, paint)
            }
            "halloween" -> {
                paint.color = 0xFFD98B39.toInt()
                canvas.drawOval(-14f, -9f, 14f, 12f, paint)
                paint.color = 0xFF667749.toInt()
                canvas.drawRect(-2f, -15f, 2f, -8f, paint)
                paint.color = 0xFF6B482E.toInt()
                paint.strokeWidth = 1f
                canvas.drawLine(-5f, -6f, -5f, 9f, paint)
                canvas.drawLine(5f, -6f, 5f, 9f, paint)
            }
        }
    }
}
