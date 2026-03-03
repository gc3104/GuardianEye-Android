package com.example.guardianeye.ai

import com.google.ai.edge.localagents.fc.GemmaFormatter
import com.google.ai.edge.localagents.fc.HammerFormatter
import com.google.ai.edge.localagents.fc.ModelFormatter

/**
 * Factory to provide the correct ModelFormatter based on the selected AI model.
 */
object AssistantFormatterFactory {
    fun getFormatter(modelName: String): ModelFormatter {
        return when {
            modelName.lowercase().contains("hammer") -> HammerFormatter()
            modelName.lowercase().contains("gemma") -> GemmaFormatter()
            // Default to Gemma as it's the standard for our task files
            else -> GemmaFormatter()
        }
    }
}
