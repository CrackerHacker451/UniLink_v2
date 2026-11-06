package com.example.unilink.repository

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import android.util.Log
import com.example.unilink.UniLinkApp
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageReference
import java.io.ByteArrayOutputStream
import java.util.UUID

class StorageRepository {
    private val storage: FirebaseStorage? by lazy {
        try {
            FirebaseStorage.getInstance()
        } catch (e: Exception) {
            Log.e("StorageRepository", "FirebaseStorage init failed: ${e.message}")
            null
        }
    }

    private fun getCompactBytesFromUri(uri: Uri, maxWidth: Int = 1920, maxHeight: Int = 1920): ByteArray? {
        return try {
            val resolver = UniLinkApp.instance.contentResolver
            val inputStream = resolver.openInputStream(uri)
            val originalBitmap = BitmapFactory.decodeStream(inputStream)
            inputStream?.close()

            if (originalBitmap != null) {
                val scaled = scaleBitmap(originalBitmap, maxWidth, maxHeight)
                
                // Solid white background canvas to prevent black alpha pixels
                val resultBitmap = Bitmap.createBitmap(scaled.width, scaled.height, Bitmap.Config.ARGB_8888)
                val canvas = android.graphics.Canvas(resultBitmap)
                canvas.drawColor(android.graphics.Color.WHITE)
                canvas.drawBitmap(scaled, 0f, 0f, null)

                val outputStream = ByteArrayOutputStream()
                resultBitmap.compress(Bitmap.CompressFormat.JPEG, 95, outputStream)
                outputStream.toByteArray()
            } else {
                resolver.openInputStream(uri)?.use { it.readBytes() }
            }
        } catch (e: Exception) {
            Log.e("StorageRepository", "Error reading/compressing bytes from URI: ${e.message}")
            try {
                UniLinkApp.instance.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            } catch (_: Exception) {
                null
            }
        }
    }

    fun copyUriToCacheFile(context: android.content.Context, uri: Uri): java.io.File? {
        return try {
            val contentResolver = context.contentResolver
            val inputStream = contentResolver.openInputStream(uri) ?: return null
            val cacheDir = java.io.File(context.cacheDir, "daredrop_cache").apply { if (!exists()) mkdirs() }
            val file = java.io.File(cacheDir, "vid_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(6)}.mp4")
            val outputStream = java.io.FileOutputStream(file)
            inputStream.use { input ->
                outputStream.use { output ->
                    input.copyTo(output)
                }
            }
            file
        } catch (e: Exception) {
            Log.e("StorageRepository", "Error copying video URI to cache file: ${e.message}")
            null
        }
    }

    fun uploadDareVideo(context: android.content.Context, videoUri: Uri, onResult: (String?) -> Unit) {
        val st = storage
        val localFile = copyUriToCacheFile(context, videoUri)
        val fileToUpload = if (localFile != null && localFile.exists()) Uri.fromFile(localFile) else videoUri

        if (st == null) {
            onResult(fileToUpload.toString())
            return
        }
        val filename = "daredrop_videos/video_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(8)}.mp4"
        val ref = st.reference.child(filename)

        ref.putFile(fileToUpload)
            .addOnSuccessListener {
                ref.downloadUrl.addOnSuccessListener { downloadUri ->
                    onResult(downloadUri.toString())
                }.addOnFailureListener {
                    onResult(fileToUpload.toString())
                }
            }
            .addOnFailureListener { e ->
                Log.e("StorageRepository", "Video upload error: ${e.message}, using local URI fallback")
                onResult(fileToUpload.toString())
            }
    }

    fun uploadDarePhoto(photoUri: Uri, onResult: (String) -> Unit) {
        val st = storage
        val bytes = getCompactBytesFromUri(photoUri)
        if (st == null || bytes == null) {
            onResult(photoUri.toString())
            return
        }
        val filename = "daredrop_photos/photo_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(8)}.jpg"
        val ref = st.reference.child(filename)

        ref.putBytes(bytes)
            .addOnSuccessListener {
                ref.downloadUrl.addOnSuccessListener { downloadUri ->
                    onResult(downloadUri.toString())
                }.addOnFailureListener {
                    val base64 = "data:image/jpeg;base64," + Base64.encodeToString(bytes, Base64.NO_WRAP)
                    onResult(base64)
                }
            }
            .addOnFailureListener { e ->
                Log.e("StorageRepository", "Photo storage error: ${e.message}, using Base64 fallback")
                val base64 = "data:image/jpeg;base64," + Base64.encodeToString(bytes, Base64.NO_WRAP)
                onResult(base64)
            }
    }

    private fun scaleBitmap(bitmap: Bitmap, maxWidth: Int, maxHeight: Int): Bitmap {
        val width = bitmap.width
        val height = bitmap.height

        if ((width <= maxWidth) && (height <= maxHeight)) return bitmap

        val aspectRatio = width.toFloat() / height.toFloat()
        val targetWidth: Int
        val targetHeight: Int

        if (width > height) {
            targetWidth = maxWidth
            targetHeight = (maxWidth / aspectRatio).toInt()
        } else {
            targetHeight = maxHeight
            targetWidth = (maxHeight * aspectRatio).toInt()
        }

        return Bitmap.createScaledBitmap(bitmap, targetWidth, targetHeight, true)
    }

    private fun createBase64DataUrl(bytes: ByteArray, mimeType: String = "image/jpeg"): String {
        val base64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
        return "data:$mimeType;base64,$base64"
    }

    private fun uploadBytesToRef(
        ref: StorageReference?,
        bytes: ByteArray,
        mimeType: String = "image/jpeg",
        onComplete: (String?) -> Unit
    ) {
        val fallbackUrl = createBase64DataUrl(bytes, mimeType)

        if (ref == null) {
            onComplete(fallbackUrl)
            return
        }

        ref.putBytes(bytes)
            .addOnSuccessListener {
                ref.downloadUrl.addOnSuccessListener { uri ->
                    onComplete(uri.toString())
                }.addOnFailureListener {
                    Log.w("StorageRepository", "downloadUrl failed, using data URL fallback")
                    onComplete(fallbackUrl)
                }
            }
            .addOnFailureListener { e ->
                Log.w("StorageRepository", "Firebase storage putBytes failed (${e.message}), using fallback")
                onComplete(fallbackUrl)
            }
    }

    fun uploadProfilePicture(uid: String, imageUri: Uri, onComplete: (String?) -> Unit) {
        val bytes = getCompactBytesFromUri(imageUri, maxWidth = 1080, maxHeight = 1080)
        if (bytes == null) {
            onComplete(imageUri.toString())
            return
        }
        val ref = storage?.reference?.child("profile_pictures/$uid.jpg")
        uploadBytesToRef(ref, bytes, "image/jpeg", onComplete)
    }

    fun uploadPostImage(imageUri: Uri, onComplete: (String?) -> Unit) {
        val bytes = getCompactBytesFromUri(imageUri, maxWidth = 1920, maxHeight = 1920)
        if (bytes == null) {
            onComplete(imageUri.toString())
            return
        }
        val fileName = UUID.randomUUID().toString()
        val ref = storage?.reference?.child("post_images/$fileName.jpg")
        uploadBytesToRef(ref, bytes, "image/jpeg", onComplete)
    }

    fun uploadChatImage(chatId: String, imageUri: Uri, onComplete: (String?) -> Unit) {
        val bytes = getCompactBytesFromUri(imageUri, maxWidth = 1440, maxHeight = 1440)
        if (bytes == null) {
            onComplete(imageUri.toString())
            return
        }
        val fileName = UUID.randomUUID().toString()
        val ref = storage?.reference?.child("chat_images/$chatId/$fileName.jpg")
        uploadBytesToRef(ref, bytes, "image/jpeg", onComplete)
    }

    fun uploadTeamAttachment(teamId: String, fileUri: Uri, fileName: String, onComplete: (String?) -> Unit) {
        val bytes = getCompactBytesFromUri(fileUri, maxWidth = 1920, maxHeight = 1920)
        if (bytes == null) {
            onComplete(fileUri.toString())
            return
        }
        val ref = storage?.reference?.child("teams/$teamId/attachments/$fileName")
        uploadBytesToRef(ref, bytes, "application/octet-stream", onComplete)
    }

    fun uploadVaultPdf(fileUri: Uri, fileName: String, onComplete: (String?) -> Unit) {
        val bytes = try {
            UniLinkApp.instance.contentResolver.openInputStream(fileUri)?.use { it.readBytes() }
        } catch (_: Exception) {
            null
        }

        if (bytes == null) {
            onComplete(fileUri.toString())
            return
        }

        val safeFileName = fileName.replace("[^a-zA-Z0-9._-]".toRegex(), "_")
        val ref = storage?.reference?.child("vault_files/${UUID.randomUUID()}_$safeFileName")
        
        if (ref == null) {
            onComplete(fileUri.toString())
            return
        }

        ref.putBytes(bytes)
            .addOnSuccessListener {
                ref.downloadUrl.addOnSuccessListener { uri ->
                    onComplete(uri.toString())
                }.addOnFailureListener {
                    onComplete(fileUri.toString())
                }
            }
            .addOnFailureListener {
                onComplete(fileUri.toString())
            }
    }

    fun uploadTeamBanner(teamId: String, imageUri: Uri, onComplete: (String?) -> Unit) {
        val bytes = getCompactBytesFromUri(imageUri, maxWidth = 800, maxHeight = 500)
        if (bytes == null) {
            onComplete(imageUri.toString())
            return
        }
        val ref = storage?.reference?.child("teams/$teamId/banner/cover.jpg")
        uploadBytesToRef(ref, bytes, "image/jpeg", onComplete)
    }
}
