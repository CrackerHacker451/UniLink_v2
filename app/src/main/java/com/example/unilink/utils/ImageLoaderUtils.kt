package com.example.unilink.utils

import android.graphics.BitmapFactory
import android.util.Base64
import android.util.Log
import android.widget.ImageView
import coil.load
import coil.transform.CircleCropTransformation
import com.example.unilink.R

object ImageLoaderUtils {

    fun getFallbackAvatarUrl(userName: String?, fallbackSeed: String? = null): String {
        val nameToUse = userName?.takeIf { it.isNotBlank() } ?: fallbackSeed?.takeIf { it.isNotBlank() } ?: "Student"
        val cleanName = java.net.URLEncoder.encode(nameToUse, "UTF-8")
        return "https://ui-avatars.com/api/?name=$cleanName&background=random&color=fff&bold=true&size=256"
    }

    fun loadSmartImage(
        imageView: ImageView,
        url: String?,
        placeholderRes: Int = R.drawable.ic_profile_placeholder,
        isCircle: Boolean = false,
        fallbackName: String? = null
    ) {
        val targetUrl = if (url.isNullOrEmpty()) {
            if (!fallbackName.isNullOrEmpty()) getFallbackAvatarUrl(fallbackName) else null
        } else {
            url
        }

        if (targetUrl == null) {
            imageView.setImageResource(placeholderRes)
            return
        }

        if (targetUrl.startsWith("data:image/") || targetUrl.startsWith("data:")) {
            try {
                val base64Data = if (targetUrl.contains("base64,")) targetUrl.substringAfter("base64,") else targetUrl
                val decodedBytes = Base64.decode(base64Data, Base64.NO_WRAP)
                val bitmap = BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.size)
                if (bitmap != null) {
                    imageView.setImageBitmap(bitmap)
                    return
                }
            } catch (e: Exception) {
                Log.e("ImageLoaderUtils", "Failed to decode base64 image: ${e.message}")
            }
        }

        imageView.load(targetUrl) {
            crossfade(true)
            placeholder(placeholderRes)
            error(placeholderRes)
            if (isCircle) {
                transformations(CircleCropTransformation())
            }
        }
    }
}
