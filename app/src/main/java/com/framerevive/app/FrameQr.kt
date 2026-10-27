package com.framerevive.app

import android.graphics.Bitmap
import android.graphics.Color
import io.nayuki.qrcodegen.QrCode

object FrameQr {
    /** Generated entirely on device, with a four-module quiet zone. */
    fun bitmap(text: String): Bitmap {
        val qr = QrCode.encodeText(text, QrCode.Ecc.MEDIUM)
        val side = (qr.size + 8) * 4
        val pixels = IntArray(side * side) { Color.WHITE }
        for (y in 0 until side) for (x in 0 until side) {
            if (qr.getModule(x / 4 - 4, y / 4 - 4)) pixels[y * side + x] = Color.BLACK
        }
        return Bitmap.createBitmap(pixels, side, side, Bitmap.Config.ARGB_8888)
    }
}
