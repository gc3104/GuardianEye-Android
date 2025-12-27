package com.example.guardianeye.model

import java.util.Date

data class Footage(
    val id: String = "",
    val name: String = "",
    val path: String = "",
    val timestamp: Date = Date(),
    val duration: String = "",
    val size: String = "",
    val thumbnailPath: String? = null
)