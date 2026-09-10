package com.example.bible.auto

data class ObdDtcReport(
    val stored: List<String>,
    val pending: List<String>,
    val rawStored: String,
    val rawPending: String,
)

data class ObdDtcClearResult(
    val success: Boolean,
    val message: String,
)
