package com.example.gerenciadordeparquinho.utils

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64

fun base64ToBitmap(imageBase64: String?): Bitmap? {
    return try {
        imageBase64?.let {
            val decoded = Base64.decode(it, Base64.DEFAULT)
            BitmapFactory.decodeByteArray(decoded, 0, decoded.size)
        }
    } catch (e: Exception) {
        null
    }
}