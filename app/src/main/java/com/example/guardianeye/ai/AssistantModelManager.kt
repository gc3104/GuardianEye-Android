package com.example.guardianeye.ai

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.example.guardianeye.model.Alert
import com.example.guardianeye.model.AlertPriority
import com.example.guardianeye.utils.PreferenceManager
import com.google.ai.edge.localagents.core.proto.Content
import com.google.ai.edge.localagents.core.proto.Part
import com.google.ai.edge.localagents.fc.GenerativeModel
import com.google.ai.edge.localagents.fc.LlmInferenceBackend
import com.google.ai.edge.localagents.fc.proto.ConstraintOptions
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import com.google.mediapipe.tasks.genai.llminference.LlmInferenceSession
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.Collections
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AssistantModelManager @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val preferenceManager: PreferenceManager
) {

    private var generativeModel: GenerativeModel? = null
    private var isModelReady = false
    
    val aiMutex = Mutex()
    var isConstraintsWorking: Boolean = false

    suspend fun getModelName(): String = preferenceManager.getAiModelFilename()

    fun getDownloadedModels(): List<File> {
        val files = context.filesDir.listFiles() ?: return emptyList()
        return files.filter { it.extension == "task" || it.extension == "litertlm" }
    }

    suspend fun isModelDownloaded(): Boolean = withContext(Dispatchers.IO) {
        val file = File(context.filesDir, getModelName())
        file.exists() && file.length() > 10 * 1024 * 1024
    }

    suspend fun initializeModel(): Boolean {
        if (isModelReady && generativeModel != null) return true

        return withContext(Dispatchers.IO) {
            aiMutex.withLock {
                if (isModelReady && generativeModel != null) return@withLock true

                val modelName = getModelName()
                val modelFile = File(context.filesDir, modelName)
                
                try {
                    if (!modelFile.exists()) {
                        Log.e(TAG, "Model file not found: $modelName")
                        return@withLock false
                    }

                    val maxTokens = preferenceManager.getAiMaxTokens()
                    val topK = preferenceManager.getAiTopK()
                    val topP = preferenceManager.getAiTopP()
                    val temperature = preferenceManager.getAiTemperature()
                    val backendStr = preferenceManager.getAiBackend()

                    Log.d(TAG, "Initializing model: $modelName")

                    val llmOptionsBuilder = LlmInference.LlmInferenceOptions.builder()
                        .setModelPath(modelFile.absolutePath)
                        .setMaxTokens(maxTokens)
                    
                    if (backendStr == "GPU") {
                        llmOptionsBuilder.setPreferredBackend(LlmInference.Backend.GPU)
                    } else {
                        llmOptionsBuilder.setPreferredBackend(LlmInference.Backend.CPU)
                    }

                    val llmInference = LlmInference.createFromOptions(context, llmOptionsBuilder.build())
                    
                    val sessionOptions = LlmInferenceSession.LlmInferenceSessionOptions.builder()
                        .setTopK(topK)
                        .setTopP(topP)
                        .setTemperature(temperature)
                        .build()

                    val formatter = AssistantFormatterFactory.getFormatter(modelName)
                    val backend = LlmInferenceBackend(llmInference, sessionOptions, formatter)
                    
                    val systemInstruction = Content.newBuilder()
                        .setRole("system")
                        .addParts(Part.newBuilder().setText("""
                            You are GuardianEye, a security AI agent. You can perform complex, multistep actions.
                            Always analyze the user's request and execute the necessary sequence of actions.
                            Example: If a user says "search for John and tell him I'm safe", you should:
                            1. SEARCH_CONTACTS(query="John")
                            2. SMS(contact="resolved_number", text="I'm safe")
                            Available actions: CALL, SMS, SEARCH_CONTACTS, SHARE_LOCATION, BROADCAST_ALARM, GET_SYSTEM_STATUS, TOGGLE_DETECTION.
                        """.trimIndent()).build())
                        .build()

                    generativeModel = GenerativeModel(
                        backend,
                        systemInstruction,
                        Collections.singletonList(AssistantToolRegistry.getTool())
                    )

                    isModelReady = true
                    true
                } catch (e: Exception) {
                    Log.e(TAG, "Init failed", e)
                    isModelReady = false
                    false
                }
            }
        }
    }

    fun getGenerativeModel(): GenerativeModel? = if (isModelReady) generativeModel else null

    fun getConstraintOptions(): ConstraintOptions {
        return ConstraintOptions.newBuilder()
            .setToolCallOnly(
                ConstraintOptions.ToolCallOnly.newBuilder()
                    .setConstraintPrefix("<tool_code>")
                    .setConstraintSuffix("</tool_code>")
                    .build()
            )
            .build()
    }

    fun downloadModel(url: String, filename: String): UUID {
        val request = OneTimeWorkRequestBuilder<DownloadWorker>()
            .setInputData(
                workDataOf(
                    DownloadWorker.KEY_URL to url,
                    DownloadWorker.KEY_FILENAME to filename
                )
            )
            .addTag("model_download")
            .build()

        WorkManager.getInstance(context).enqueue(request)
        return request.id
    }

    suspend fun importModel(uri: Uri, filename: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val destinationFile = File(context.filesDir, filename)
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                FileOutputStream(destinationFile).use { outputStream ->
                    inputStream.copyTo(outputStream)
                }
            }
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to import model", e)
            false
        }
    }

    suspend fun deleteModel(filename: String? = null): Boolean = withContext(Dispatchers.IO) {
        aiMutex.withLock {
            val targetName = filename ?: getModelName()
            val file = File(context.filesDir, targetName)
            if (targetName == getModelName()) {
                generativeModel = null
                isModelReady = false
                isConstraintsWorking = false
            }
            if (file.exists()) file.delete() else false
        }
    }

    fun getDefaultPlan(alert: Alert): AssistantActionPlan {
        return when (alert.priority) {
            AlertPriority.CRITICAL -> AssistantActionPlan(
                reply = "CRITICAL ALERT: Initiating emergency protocols.",
                actions = listOf(
                    ActionStep("CALL", mapOf("contact" to "emergency_contact")),
                    ActionStep("SHARE_LOCATION", mapOf("contact" to "emergency_contact")),
                    ActionStep("BROADCAST_ALARM")
                )
            )
            else -> AssistantActionPlan(reply = "Monitoring alert.")
        }
    }

    companion object {
        private const val TAG = "AssistantModelManager"
    }
}
