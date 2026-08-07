package com.travelingsalesman.agap.core.util

import android.graphics.Bitmap
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import com.google.zxing.BarcodeFormat
import com.google.zxing.MultiFormatWriter
import com.google.zxing.common.BitMatrix

object QrCodeUtils {
    fun generateQrCode(
        content: String,
        width: Int = 400,
        height: Int = 400,
        foregroundColor: Color = Color.Black,
        backgroundColor: Color = Color.White
    ): ImageBitmap? {
        if (content.isEmpty()) return null
        return try {
            val bitMatrix: BitMatrix = MultiFormatWriter().encode(
                content,
                BarcodeFormat.QR_CODE,
                width,
                height
            )
            val fgArgb = foregroundColor.toArgb()
            val bgArgb = backgroundColor.toArgb()
            val pixels = IntArray(width * height)
            for (y in 0 until height) {
                val offset = y * width
                for (x in 0 until width) {
                    pixels[offset + x] = if (bitMatrix.get(x, y)) fgArgb else bgArgb
                }
            }
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            bitmap.setPixels(pixels, 0, width, 0, 0, width, height)
            bitmap.asImageBitmap()
        } catch (e: Exception) {
            timber.log.Timber.e(e, "Error generating QR code for content: %s", content)
            null
        }
    }
}
