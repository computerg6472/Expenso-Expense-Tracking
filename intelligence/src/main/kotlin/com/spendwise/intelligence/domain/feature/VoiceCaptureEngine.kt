package com.spendwise.intelligence.domain.feature

data class VoiceCaptureResult(
    val transcription: String,
    val parsedExpense: ParsedExpense?,
    val confidence: Float = 0f,
    val isFallback: Boolean = false
)

interface VoiceCaptureEngine {
    /**
     * Processes recorded speech audio offline and parses it into an expense proposal.
     */
    suspend fun processSpeech(audioBytes: ByteArray): VoiceCaptureResult
}
