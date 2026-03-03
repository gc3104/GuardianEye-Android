package com.example.guardianeye.ai

data class AssistantActionPlan(
    val reply: String? = null,
    val actions: List<ActionStep> = emptyList()
)

data class ActionStep(
    val name: String,
    val args: Map<String, Any>? = null
)
