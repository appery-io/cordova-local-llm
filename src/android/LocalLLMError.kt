package io.ionic.localllm

sealed class LocalLLMError(message: String, val code: String) : Exception(message) {
    class Uninitialized : LocalLLMError("LocalLLM not initialized", "LOCAL_LLM_NOT_INITIALIZED")

    class UnsupportedPlatform :
        LocalLLMError("Gemini Nano is not supported on this device", "LOCAL_LLM_UNSUPPORTED_PLATFORM")

    class NotReady : LocalLLMError("Gemini Nano model is not ready", "LOCAL_LLM_NOT_READY")

    class Unavailable : LocalLLMError("Gemini Nano is currently unavailable", "LOCAL_LLM_UNAVAILABLE")

    class MissingParameter(name: String) : LocalLLMError("$name is required", "LOCAL_LLM_MISSING_PARAMETER")

    class FeatureNotSupported(feature: String) :
        LocalLLMError("$feature is not supported on Android", "LOCAL_LLM_FEATURE_NOT_SUPPORTED_ON_ANDROID")
}
