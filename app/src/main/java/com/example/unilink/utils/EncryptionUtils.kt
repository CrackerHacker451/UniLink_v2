package com.example.unilink.utils

import android.util.Base64
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * End-to-End Encryption Utility
 * Uses AES-GCM (256-bit) which is industry-standard for secure messaging.
 * Note: For true E2EE, keys should be derived from user-specific secrets 
 * and never sent to the server.
 */
object EncryptionUtils {
    private const val ALGORITHM = "AES/GCM/NoPadding"
    private const val TAG_LENGTH = 128
    private const val IV_LENGTH = 12

    // This is a Master Key placeholder. In a production E2EE setup, 
    // keys would be exchanged between users using DH/Signal protocol.
    private const val MASTER_SEED = "U1N_PRO_SECURE_ENCRYPTION_KEY_2024" 

    private fun generateKey(seed: String): SecretKeySpec {
        val keyBytes = seed.padEnd(32, '0').substring(0, 32).toByteArray()
        return SecretKeySpec(keyBytes, "AES")
    }

    fun encrypt(plainText: String, chatId: String): String {
        return try {
            val key = generateKey(MASTER_SEED + chatId)
            val cipher = Cipher.getInstance(ALGORITHM)
            val iv = ByteArray(IV_LENGTH)
            SecureRandom().nextBytes(iv)
            val gcmSpec = GCMParameterSpec(TAG_LENGTH, iv)
            
            cipher.init(Cipher.ENCRYPT_MODE, key, gcmSpec)
            val cipherText = cipher.doFinal(plainText.toByteArray())
            
            val combined = ByteArray(IV_LENGTH + cipherText.size)
            System.arraycopy(iv, 0, combined, 0, IV_LENGTH)
            System.arraycopy(cipherText, 0, combined, IV_LENGTH, cipherText.size)
            
            Base64.encodeToString(combined, Base64.DEFAULT)
        } catch (e: Exception) {
            plainText // Fallback
        }
    }

    fun decrypt(encryptedText: String, chatId: String): String {
        return try {
            val key = generateKey(MASTER_SEED + chatId)
            val combined = Base64.decode(encryptedText, Base64.DEFAULT)
            
            val iv = ByteArray(IV_LENGTH)
            System.arraycopy(combined, 0, iv, 0, IV_LENGTH)
            
            val cipherText = ByteArray(combined.size - IV_LENGTH)
            System.arraycopy(combined, IV_LENGTH, cipherText, 0, cipherText.size)
            
            val cipher = Cipher.getInstance(ALGORITHM)
            val gcmSpec = GCMParameterSpec(TAG_LENGTH, iv)
            cipher.init(Cipher.DECRYPT_MODE, key, gcmSpec)
            
            String(cipher.doFinal(cipherText))
        } catch (e: Exception) {
            encryptedText // Return as is if not encrypted
        }
    }
}
