package com.example.guardianeye.ai

import android.content.Context
import android.util.Log
import com.example.guardianeye.utils.PreferenceManager
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

/**
 * Manages the AI Model: downloading, initialization, deletion, and providing the instance.
 */
class AiModelManager(private val context: Context) {

    private val preferenceManager = PreferenceManager(context)
    private var llmInference: LlmInference? = null
    private var isModelReady = false

    suspend fun getModelName(): String {
        return preferenceManager.getAiModelFilename()
    }

    suspend fun setModelName(filename: String) {
        preferenceManager.saveAiModelFilename(filename)
    }

    suspend fun isModelDownloaded(): Boolean {
        return withContext(Dispatchers.IO) {
            val modelName = getModelName()
            val file = File(context.filesDir, modelName)
            file.exists()
        }
    }

    suspend fun initializeModel(): Boolean {
        if (isModelReady && llmInference != null) return true

        return withContext(Dispatchers.IO) {
            val modelName = getModelName()
            val modelFile = File(context.filesDir, modelName)
            if (modelFile.exists()) {
                try {
                    val options = LlmInference.LlmInferenceOptions.builder()
                        .setModelPath(modelFile.absolutePath)
                        .setMaxTokens(512)
                        .build()
                    llmInference = LlmInference.createFromOptions(context, options)
                    isModelReady = true
                    true
                } catch (e: Exception) {
                    Log.e("AiModelManager", "Error initializing LLM", e)
                    false
                }
            } else {
                false
            }
        }
    }

    fun getInferenceEngine(): LlmInference? {
        return if (isModelReady) llmInference else null
    }

    suspend fun downloadModel(
        url: String, 
        onProgress: (Float) -> Unit, 
        onComplete: (Boolean, String?) -> Unit
    ) {
        withContext(Dispatchers.IO) {
            val modelName = getModelName()
            val modelFile = File(context.filesDir, modelName)
            val client = OkHttpClient()
            val request = Request.Builder().url(url).build()

            try {
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) throw IOException("Unexpected code $response")
                    
                    val body = response.body
                    val contentLength = body.contentLength()
                    val inputStream = body.byteStream()
                    val outputStream = FileOutputStream(modelFile)
                    
                    val buffer = ByteArray(8 * 1024)
                    var bytesRead: Int
                    var totalBytesRead = 0L
                    
                    while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                        outputStream.write(buffer, 0, bytesRead)
                        totalBytesRead += bytesRead
                        if (contentLength > 0) {
                            val progress = totalBytesRead.toFloat() / contentLength.toFloat()
                            withContext(Dispatchers.Main) {
                                onProgress(progress)
                            }
                        }
                    }
                    outputStream.flush()
                    outputStream.close()
                    inputStream.close()
                }
                
                // Verify initialization immediately after download
                val success = initializeModel()
                withContext(Dispatchers.Main) {
                    onComplete(success, if (success) null else "Download successful but initialization failed")
                }

            } catch (e: Exception) {
                Log.e("AiModelManager", "Download failed", e)
                withContext(Dispatchers.Main) {
                    onComplete(false, e.message)
                }
                // Cleanup partial file
                if (modelFile.exists()) modelFile.delete()
            }
        }
    }

    suspend fun deleteModel(): Boolean {
        return withContext(Dispatchers.IO) {
            val modelName = getModelName()
            val modelFile = File(context.filesDir, modelName)
            if (modelFile.exists()) {
                val deleted = modelFile.delete()
                if (deleted) {
                    llmInference = null
                    isModelReady = false
                }
                deleted
            } else {
                false
            }
        }
    }

    fun generateSystemInstructions(): String {
        return """
            You are GuardianEye AI, a home security assistant.
            Your role is to analyze alerts and help the user take action.
            
            IMPORTANT:
            1. Keep responses concise and focused on safety.
            2. If you recommend an action, verify if it's available.
            3. Do NOT execute actions yourself. Instead, output the intent in JSON format if the user explicitly confirms an action.
            4. If the user asks for help, suggest "Call Emergency" or "Send SMS".
            
            Format your response as plain text for normal conversation.
            Only if the user confirms an action (like "yes, call them" or "ignore this"), append a JSON block at the very end:
            ```json
            { "intent": "CALL" }
            ```
            Valid intents: CALL, SMS, RESOLVE, IGNORE, OPEN_SETTINGS.
        """.trimIndent()
    }
}