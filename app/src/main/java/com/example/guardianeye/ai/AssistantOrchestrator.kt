package com.example.guardianeye.ai

import com.example.guardianeye.model.ResolutionSource
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AssistantOrchestrator @Inject constructor(
    private val executor: AssistantActionExecutor
) {

    suspend fun handle(
        actionPlan: AssistantActionPlan,
        alertId: String? = null,
        source: ResolutionSource = ResolutionSource.AUTO
    ): List<AssistantActionExecutor.ExecutionResult> {

        val results = mutableListOf<AssistantActionExecutor.ExecutionResult>()

        for (step in actionPlan.actions) {
            val result = executor.execute(
                action = step.name,
                alertId = alertId,
                args = step.args,
                source = source
            )
            results.add(result)

            // stop chain on error
            if (result is AssistantActionExecutor.ExecutionResult.Error) break
        }

        // Auto-resolve if we are in an alert context and at least one action was successful
        // The executor now handles the dual-save and Firebase deletion logic internally.
        if (alertId != null && results.any { it is AssistantActionExecutor.ExecutionResult.Success }) {
            executor.execute("RESOLVE", alertId, source = source)
        }

        return results
    }
}
