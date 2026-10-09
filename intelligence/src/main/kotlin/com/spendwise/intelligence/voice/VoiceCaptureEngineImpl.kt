package com.spendwise.intelligence.voice

import com.spendwise.intelligence.capability.AiFeature
import com.spendwise.intelligence.capability.CapabilityProvider
import com.spendwise.intelligence.domain.feature.NlpExpenseParser
import com.spendwise.intelligence.domain.feature.VoiceCaptureEngine
import com.spendwise.intelligence.domain.feature.VoiceCaptureResult
import java.nio.charset.StandardCharsets
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class VoiceCaptureEngineImpl @Inject constructor(
    private val nlpExpenseParser: NlpExpenseParser,
    private val capabilityProvider: CapabilityProvider
) : VoiceCaptureEngine {

    override suspend fun processSpeech(audioBytes: ByteArray): VoiceCaptureResult {
        // Accepts spoken text bytes or transcribed audio transcript
        val text = try {
            String(audioBytes, StandardCharsets.UTF_8).trim()
        } catch (e: Exception) {
            ""
        }
        return processTranscribedSpeech(text)
    }

    suspend fun processTranscribedSpeech(speechText: String): VoiceCaptureResult {
        if (speechText.isBlank()) {
            return VoiceCaptureResult(
                transcription = "",
                parsedExpense = null,
                confidence = 0f,
                isFallback = true
            )
        }

        // Clean speech verbal fillers: "um", "uh", "please add", "add expense", "log"
        val cleaned = speechText
            .replace(Regex("""(?i)\b(um|uh|please add|add|log an expense for|log)\b"""), " ")
            .replace(Regex("""\s+"""), " ")
            .trim()

        val parsed = nlpExpenseParser.parse(cleaned)

        return VoiceCaptureResult(
            transcription = speechText,
            parsedExpense = parsed,
            confidence = parsed.confidence,
            isFallback = true
        )
    }
}
