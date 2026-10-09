package com.spendwise.app.data.cloud

import com.spendwise.app.data.ai.AiTools
import com.spendwise.app.data.ai.GeminiConfig
import com.spendwise.app.data.preferences.AppPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton

sealed class GeminiCallResult {
    data class TextSuccess(val responseText: String) : GeminiCallResult()
    data class FunctionCallRequest(val callName: String, val callArgs: JSONObject) : GeminiCallResult()
    data class Error(val message: String, val isNetworkOffline: Boolean = false) : GeminiCallResult()
}

@Singleton
class GeminiClient @Inject constructor(
    private val appPreferences: AppPreferences
) {

    private var lastRequestTime = 0L

    /**
     * Call Gemini with system prompt, message history, tool declarations, and optional tool response.
     * Implements 15s timeout, 1 automatic retry, and rate limiting.
     */
    suspend fun callGemini(
        systemInstruction: String,
        contents: JSONArray,
        includeTools: Boolean = true,
        validCategoryNames: List<String> = emptyList()
    ): GeminiCallResult = withContext(Dispatchers.IO) {
        val isEnabled = appPreferences.geminiAiEnabled.first()
        if (!isEnabled) {
            return@withContext GeminiCallResult.Error("Gemini AI is disabled in Settings.")
        }

        // Rate limiting check
        val now = System.currentTimeMillis()
        val timeSinceLast = now - lastRequestTime
        if (timeSinceLast < GeminiConfig.RATE_LIMIT_INTERVAL_MS) {
            delay(GeminiConfig.RATE_LIMIT_INTERVAL_MS - timeSinceLast)
        }
        lastRequestTime = System.currentTimeMillis()

        var attempts = 0
        var lastError: GeminiCallResult.Error? = null

        while (attempts <= GeminiConfig.MAX_RETRIES) {
            attempts++
            try {
                val apiKey = appPreferences.geminiApiKey.first().trim()
                val endpointUrl = if (apiKey.isNotBlank()) {
                    "${GeminiConfig.DEFAULT_API_ENDPOINT}?key=$apiKey"
                } else {
                    GeminiConfig.DEFAULT_API_ENDPOINT
                }

                val url = URL(endpointUrl)
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.setRequestProperty("Content-Type", "application/json")
                conn.connectTimeout = GeminiConfig.TIMEOUT_MILLIS
                conn.readTimeout = GeminiConfig.TIMEOUT_MILLIS
                conn.doOutput = true

                val body = JSONObject().apply {
                    put("contents", contents)

                    if (systemInstruction.isNotBlank()) {
                        val sysObj = JSONObject().apply {
                            val parts = JSONArray().apply {
                                put(JSONObject().put("text", systemInstruction))
                            }
                            put("parts", parts)
                        }
                        put("systemInstruction", sysObj)
                    }

                    if (includeTools) {
                        put("tools", AiTools.getToolDeclarations(validCategoryNames))
                    }
                }

                OutputStreamWriter(conn.outputStream).use { writer ->
                    writer.write(body.toString())
                    writer.flush()
                }

                val responseCode = conn.responseCode
                if (responseCode == HttpURLConnection.HTTP_OK) {
                    val responseStr = BufferedReader(InputStreamReader(conn.inputStream)).use { it.readText() }
                    val jsonResponse = JSONObject(responseStr)
                    val candidates = jsonResponse.optJSONArray("candidates")
                    val firstCandidate = candidates?.optJSONObject(0)
                    val content = firstCandidate?.optJSONObject("content")
                    val parts = content?.optJSONArray("parts")

                    if (parts != null && parts.length() > 0) {
                        val firstPart = parts.getJSONObject(0)

                        // Check for Function Call
                        if (firstPart.has("functionCall")) {
                            val funcCall = firstPart.getJSONObject("functionCall")
                            val name = funcCall.optString("name")
                            val args = funcCall.optJSONObject("args") ?: JSONObject()
                            return@withContext GeminiCallResult.FunctionCallRequest(name, args)
                        }

                        // Text response
                        val text = firstPart.optString("text").orEmpty()
                        return@withContext GeminiCallResult.TextSuccess(text)
                    }
                    return@withContext GeminiCallResult.TextSuccess("I've analyzed your financial data.")
                } else {
                    val errStr = BufferedReader(InputStreamReader(conn.errorStream ?: conn.inputStream)).use { it.readText() }
                    lastError = GeminiCallResult.Error("Gemini error ($responseCode): $errStr")
                }
            } catch (e: java.net.UnknownHostException) {
                lastError = GeminiCallResult.Error("You appear to be offline. Check your internet connection.", isNetworkOffline = true)
            } catch (e: java.net.SocketTimeoutException) {
                lastError = GeminiCallResult.Error("Gemini request timed out (15s). Please retry.")
            } catch (e: Exception) {
                lastError = GeminiCallResult.Error("Gemini request failed: ${e.message}")
            }

            // Retry after short pause if first attempt failed
            if (attempts <= GeminiConfig.MAX_RETRIES) {
                delay(800L)
            }
        }

        return@withContext lastError ?: GeminiCallResult.Error("Request could not be completed.")
    }
}
