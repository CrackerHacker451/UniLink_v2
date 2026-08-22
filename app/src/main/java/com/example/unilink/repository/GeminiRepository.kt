package com.example.unilink.repository

import android.graphics.Bitmap
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.content
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class GeminiRepository {
    // Placeholder API Key - In real app, move to BuildConfig or secure storage
    private val apiKey = "YOUR_GEMINI_API_KEY"
    private val model = GenerativeModel(
        modelName = "gemini-1.5-flash",
        apiKey = apiKey
    )

    suspend fun getAiHint(doubt: String, isPremium: Boolean = false, userContext: String? = null): String? = withContext(Dispatchers.IO) {
        if (apiKey == "YOUR_GEMINI_API_KEY") {
            return@withContext getSimulatedAiResponse(doubt, isPremium)
        }
        
        try {
            val contextPrompt = if (userContext != null) "The student asking this has the following profile context: $userContext. " else ""
            val prompt = if (isPremium) {
                "${contextPrompt}You are UniAI Genius, a top-tier student tutor at a university. Provide a detailed, clear step-by-step solution for this student doubt: $doubt"
            } else {
                "${contextPrompt}You are UniAI, a helpful student assistant. Provide a brief, helpful hint (max 2 sentences) for this student doubt: $doubt"
            }
            
            val response = model.generateContent(content {
                text(prompt)
            })
            response.text
        } catch (e: Exception) {
            getSimulatedAiResponse(doubt, isPremium)
        }
    }

    private fun getSimulatedAiResponse(doubt: String, isPremium: Boolean): String {
        val responses = if (isPremium) {
            listOf(
                "Based on the campus curriculum, this problem usually requires applying the core principles of $doubt. I recommend looking at Chapter 4 of the standard notes. For a step-by-step solution: 1) Identify variables, 2) Apply the formula, 3) Verify the units.",
                "UniAI Genius here! I've analyzed your doubt. This is a classic example of a complex university-level question. You should first ensure you understand the theoretical background. Then, try breaking it down into smaller sub-problems. I've seen similar doubts solved by focusing on the logic rather than the math.",
                "Excellent question! As your Top-tier tutor, I suggest you review the latest lecture on this topic. Usually, $doubt is approached by setting up a comparative model. Let's look at it from a different perspective..."
            )
        } else {
            listOf(
                "UniAI Hint: Have you tried looking at the underlying concepts of $doubt? It might be simpler than it looks!",
                "Try breaking this down into two smaller parts. Usually, solving for the first variable helps reveal the path to the second.",
                "Helper Hint: Double-check your starting assumptions about this doubt. Sometimes a small error there changes everything!"
            )
        }
        return "✨ UniAI (Simulated): ${responses.random()}"
    }

    suspend fun analyzeImage(bitmap: Bitmap, prompt: String = "Explain what is in this image for a student"): String? = withContext(Dispatchers.IO) {
        if (apiKey == "YOUR_GEMINI_API_KEY") return@withContext "Vision analysis requires a Gemini API Key. Connect one in GeminiRepository.kt!"
        
        try {
            val response = model.generateContent(content {
                image(bitmap)
                text(prompt)
            })
            response.text
        } catch (e: Exception) {
            null
        }
    }
}
