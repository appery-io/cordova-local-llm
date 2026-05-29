package io.ionic.localllm

enum class LLMAvailability(val value: String) {
    Available("available"),
    Unavailable("unavailable"),
    NotReady("notready"),
    Downloadable("downloadable"),
}
