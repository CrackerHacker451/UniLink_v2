package com.example.unilink.repository

import android.net.Uri
import com.google.firebase.storage.FirebaseStorage
import java.util.UUID

class StorageRepository {
    private val storage = FirebaseStorage.getInstance()

    fun uploadProfilePicture(uid: String, imageUri: Uri, onComplete: (String?) -> Unit) {
        val ref = storage.reference.child("profile_pictures/$uid.jpg")
        ref.putFile(imageUri)
            .addOnSuccessListener {
                ref.downloadUrl.addOnSuccessListener { uri ->
                    onComplete(uri.toString())
                }.addOnFailureListener {
                    onComplete(null)
                }
            }
            .addOnFailureListener {
                onComplete(null)
            }
    }

    fun uploadPostImage(imageUri: Uri, onComplete: (String?) -> Unit) {
        val fileName = UUID.randomUUID().toString()
        val ref = storage.reference.child("post_images/$fileName.jpg")
        ref.putFile(imageUri)
            .addOnSuccessListener {
                ref.downloadUrl.addOnSuccessListener { uri ->
                    onComplete(uri.toString())
                }.addOnFailureListener {
                    onComplete(null)
                }
            }
            .addOnFailureListener {
                onComplete(null)
            }
    }

    fun uploadChatImage(chatId: String, imageUri: Uri, onComplete: (String?) -> Unit) {
        val fileName = UUID.randomUUID().toString()
        val ref = storage.reference.child("chat_images/$chatId/$fileName.jpg")
        ref.putFile(imageUri)
            .addOnSuccessListener {
                ref.downloadUrl.addOnSuccessListener { uri ->
                    onComplete(uri.toString())
                }.addOnFailureListener {
                    onComplete(null)
                }
            }
            .addOnFailureListener {
                onComplete(null)
            }
    }
}
