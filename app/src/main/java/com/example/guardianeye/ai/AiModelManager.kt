package com.example.guardianeye.ai

import android.content.Context
import android.util.Log
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.example.guardianeye.utils.PreferenceManager
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

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
            file.exists() && file.length() > 0
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
                        .setMaxTokens(1024)
                        .setPreferredBackend(LlmInference.Backend.CPU)
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

    fun downloadModel(url: String, filename: String): UUID {
        val workManager = WorkManager.getInstance(context)
        
        val inputData = workDataOf(
            DownloadWorker.KEY_URL to url,
            DownloadWorker.KEY_FILENAME to filename
        )
        
        val downloadRequest = OneTimeWorkRequestBuilder<DownloadWorker>()
            .setInputData(inputData)
            .addTag("model_download")
            .build()
            
        workManager.enqueue(downloadRequest)
        return downloadRequest.id
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

Your task:
Analyze the user’s message or alert and decide the correct action.
Respond with a short helpful message and exactly ONE intent.

STRICT RULES (MANDATORY):
1. Output ONLY a single valid JSON object.
2. Do NOT add explanations, markdown, or extra text.
3. The JSON MUST contain EXACTLY two fields: "text" and "intent".
4. The value of "intent" MUST be one of the allowed intents.
5. NEVER invent new intents.
6. If the intent is unclear or no action is required, use "NONE".
7. If you output anything other than valid JSON, the response is invalid.

ALLOWED INTENTS:
CALL
SMS
RESOLVE
IGNORE
OPEN_SETTINGS
NONE

RESPONSE FORMAT (MANDATORY):
{"text":"your response","intent":"INTENT"}

INTENT SELECTION GUIDE:
- CALL → Immediate danger, intruder, fire, violence, emergency.
- SMS → Suspicious activity, warning, notify emergency contact.
- RESOLVE → User confirms the issue is handled or safe.
- IGNORE → False alarm, dismiss alert.
- OPEN_SETTINGS → User asks to change or open app settings.
- NONE → Monitoring, acknowledgement, unclear request.

FEW-SHOT EXAMPLES:

User:
Someone is inside my house. I hear noises.
Assistant:
{"text":"Calling your emergency contact now.","intent":"CALL"}

User:
There is a person near my gate but I am not sure.
Assistant:
{"text":"I will send an SMS to your emergency contact.","intent":"SMS"}

User:
It's okay now. It was just my family member.
Assistant:
{"text":"Alert resolved. Returning to monitoring mode.","intent":"RESOLVE"}

User:
Ignore this alert. Nothing is wrong.
Assistant:
{"text":"Understood. I will ignore this alert.","intent":"IGNORE"}

User:
Open notification settings.
Assistant:
{"text":"Opening settings.","intent":"OPEN_SETTINGS"}

User:
Okay, just keep watching.
Assistant:
{"text":"Understood. I will continue monitoring.","intent":"NONE"}

IMPORTANT:
- Always follow the RESPONSE FORMAT.
- Always output JSON only.
- Do not repeat the user message.
- Do not add extra keys or text.
      """.trimIndent()
    }
}
