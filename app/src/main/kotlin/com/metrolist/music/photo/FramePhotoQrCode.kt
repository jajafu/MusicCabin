package com.metrolist.music.photo

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter

@Composable
internal fun FramePhotoQrCode(url: String, description: String) {
    val qr = remember(url) {
        val size = 256
        val bits = QRCodeWriter().encode(url, BarcodeFormat.QR_CODE, size, size,
            mapOf(EncodeHintType.MARGIN to 2))
        val pixels = IntArray(size * size) { index ->
            if (bits[index % size, index / size]) android.graphics.Color.BLACK else android.graphics.Color.WHITE
        }
        Bitmap.createBitmap(pixels, size, size, Bitmap.Config.ARGB_8888).asImageBitmap()
    }
    Image(qr, description,
        Modifier.size(196.dp).background(Color.White).padding(8.dp))
}
