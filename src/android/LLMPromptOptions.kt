package io.ionic.localllm

data class LLMPromptOptions(
    val sessionId: String?,
    val instructions: String?,
    val options: LLMOptions?,
    val prompt: String,
)
