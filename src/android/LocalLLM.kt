package io.ionic.localllm

import com.google.mlkit.genai.common.DownloadStatus
import com.google.mlkit.genai.common.FeatureStatus
import com.google.mlkit.genai.prompt.Generation
import com.google.mlkit.genai.prompt.GenerativeModel
import com.google.mlkit.genai.prompt.TextPart
import com.google.mlkit.genai.prompt.generateContentRequest

data class ChatSession(
    val instructions: String?,
    val history: MutableList<Pair<String, String>> = mutableListOf(),
)

class LocalLLM {
    private val model: GenerativeModel = Generation.getClient()
    private val sessions: MutableMap<String, ChatSession> = mutableMapOf()

    suspend fun availability(): LLMAvailability {
        try {
            return when (model.checkStatus()) {
                FeatureStatus.UNAVAILABLE -> LLMAvailability.Unavailable
                FeatureStatus.DOWNLOADABLE -> LLMAvailability.Downloadable
                FeatureStatus.DOWNLOADING -> LLMAvailability.NotReady
                FeatureStatus.AVAILABLE -> LLMAvailability.Available
                else -> LLMAvailability.Unavailable
            }
        } catch (ex: com.google.mlkit.genai.common.GenAiException) {
            if (ex.errorCode == 8) {
                throw LocalLLMError.UnsupportedPlatform()
            }
            throw ex
        }
    }

    suspend fun download() {
        model.download().collect { status ->
            when (status) {
                is DownloadStatus.DownloadStarted -> Unit
                is DownloadStatus.DownloadProgress -> Unit
                is DownloadStatus.DownloadCompleted -> Unit
                is DownloadStatus.DownloadFailed -> throw status.e
            }
        }
    }

    suspend fun warmup() {
        checkAvailability()
        model.warmup()
    }

    suspend fun prompt(options: LLMPromptOptions): String {
        checkAvailability()

        val sessionId = options.sessionId

        val fullPrompt =
            if (sessionId != null) {
                val session =
                    sessions.getOrPut(sessionId) {
                        ChatSession(instructions = options.instructions)
                    }

                buildString {
                    if (session.instructions != null) {
                        appendLine(session.instructions)
                        appendLine()
                    }

                    for ((userMsg, assistantMsg) in session.history) {
                        appendLine("User: $userMsg")
                        appendLine("Assistant: $assistantMsg")
                        appendLine()
                    }

                    append("User: ${options.prompt}")
                }
            } else {
                if (options.instructions != null) {
                    "${options.instructions}\n\n${options.prompt}"
                } else {
                    options.prompt
                }
            }

        val response =
            model.generateContent(
                generateContentRequest(TextPart(fullPrompt)) {
                    temperature = options.options?.temperature
                    topK = 16
                    maxOutputTokens = options.options?.maxOutputTokens
                },
            )

        val responseText = response.candidates.first().text

        if (sessionId != null) {
            sessions[sessionId]?.history?.add(Pair(options.prompt, responseText))
        }

        return responseText
    }

    fun endSession(sessionId: String) {
        sessions.remove(sessionId)
    }

    suspend fun generateImage(
        @Suppress("UNUSED_PARAMETER") prompt: String,
        @Suppress("UNUSED_PARAMETER") count: Int,
    ): List<String> {
        throw LocalLLMError.FeatureNotSupported("image generation")
    }

    private suspend fun checkAvailability() {
        when (availability()) {
            LLMAvailability.Downloadable -> throw LocalLLMError.NotReady()
            LLMAvailability.NotReady -> throw LocalLLMError.NotReady()
            LLMAvailability.Unavailable -> throw LocalLLMError.UnsupportedPlatform()
            LLMAvailability.Available -> return
        }
    }
}
