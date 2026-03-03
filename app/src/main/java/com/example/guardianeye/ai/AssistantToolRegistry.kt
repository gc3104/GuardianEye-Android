package com.example.guardianeye.ai

import com.google.ai.edge.localagents.core.proto.FunctionDeclaration
import com.google.ai.edge.localagents.core.proto.Schema
import com.google.ai.edge.localagents.core.proto.Tool
import com.google.ai.edge.localagents.core.proto.Type

object AssistantToolRegistry {

    fun getTool(): Tool {
        return Tool.newBuilder()
            .addFunctionDeclarations(createCallFunction())
            .addFunctionDeclarations(createSmsFunction())
            .addFunctionDeclarations(createSearchContactsFunction())
            .addFunctionDeclarations(createShareLocationFunction())
            .addFunctionDeclarations(createBroadcastAlarmFunction())
            .addFunctionDeclarations(createGetSystemStatusFunction())
            .addFunctionDeclarations(createToggleDetectionFunction())
            .build()
    }

    private fun createCallFunction() = FunctionDeclaration.newBuilder()
        .setName("CALL")
        .setDescription("Call a phone contact or emergency contact")
        .setParameters(
            Schema.newBuilder()
                .setType(Type.OBJECT)
                .putProperties("contact", Schema.newBuilder().setType(Type.STRING).build())
                .addRequired("contact")
                .build()
        ).build()

    private fun createSmsFunction() = FunctionDeclaration.newBuilder()
        .setName("SMS")
        .setDescription("Send a text message")
        .setParameters(
            Schema.newBuilder()
                .setType(Type.OBJECT)
                .putProperties("contact", Schema.newBuilder().setType(Type.STRING).build())
                .putProperties("text", Schema.newBuilder().setType(Type.STRING).build())
                .addRequired("contact")
                .addRequired("text")
                .build()
        ).build()

    private fun createSearchContactsFunction() = FunctionDeclaration.newBuilder()
        .setName("SEARCH_CONTACTS")
        .setDescription("Search contacts by name")
        .setParameters(
            Schema.newBuilder()
                .setType(Type.OBJECT)
                .putProperties("query", Schema.newBuilder().setType(Type.STRING).build())
                .addRequired("query")
                .build()
        ).build()

    private fun createShareLocationFunction() = FunctionDeclaration.newBuilder()
        .setName("SHARE_LOCATION")
        .setDescription("Share current GPS location with a contact")
        .setParameters(
            Schema.newBuilder()
                .setType(Type.OBJECT)
                .putProperties("contact", Schema.newBuilder().setType(Type.STRING).build())
                .addRequired("contact")
                .build()
        ).build()

    private fun createBroadcastAlarmFunction() = FunctionDeclaration.newBuilder()
        .setName("BROADCAST_ALARM")
        .setDescription("Trigger local siren alarm")
        .setParameters(Schema.newBuilder().setType(Type.OBJECT).build())
        .build()

    private fun createGetSystemStatusFunction() = FunctionDeclaration.newBuilder()
        .setName("GET_SYSTEM_STATUS")
        .setDescription("Get current system state")
        .setParameters(Schema.newBuilder().setType(Type.OBJECT).build())
        .build()

    private fun createToggleDetectionFunction() = FunctionDeclaration.newBuilder()
        .setName("TOGGLE_DETECTION")
        .setDescription("Enable or disable detection features")
        .setParameters(
            Schema.newBuilder()
                .setType(Type.OBJECT)
                .putProperties("feature", Schema.newBuilder().setType(Type.STRING).build())
                .putProperties("enable", Schema.newBuilder().setType(Type.BOOLEAN).build())
                .addRequired("feature")
                .addRequired("enable")
                .build()
        ).build()
}
