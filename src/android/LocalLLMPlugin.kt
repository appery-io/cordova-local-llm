package io.ionic.localllm

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.apache.cordova.CallbackContext
import org.apache.cordova.CordovaPlugin
import org.apache.cordova.PluginResult
import org.json.JSONArray
import org.json.JSONObject

class LocalLLMPlugin : CordovaPlugin() {
    private val implementation = LocalLLM()
    private val mainScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private val pollingScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var availabilityPollingJob: Job? = null
    private var forceAvailabilityListenerUpdate = false
    private val listenerCallbacks = mutableMapOf<String, CallbackContext>()

    override fun onDestroy() {
        availabilityPollingJob?.cancel()
        pollingScope.cancel()
        mainScope.cancel()
        super.onDestroy()
    }

    override fun execute(
        action: String,
        args: JSONArray,
        callbackContext: CallbackContext,
    ): Boolean {
        when (action) {
            "systemAvailability" -> {
                mainScope.launch {
                    runCatching { implementation.availability() }
                        .onSuccess { status ->
                            callbackContext.success(JSONObject().put("status", status.value))
                        }
                        .onFailure { callbackContext.sendError(it) }
                }
                return true
            }

            "download" -> {
                mainScope.launch {
                    runCatching { implementation.download() }
                        .onSuccess { callbackContext.success() }
                        .onFailure { callbackContext.sendError(it) }
                }
                return true
            }

            "warmup" -> {
                mainScope.launch {
                    runCatching { implementation.warmup() }
                        .onSuccess { callbackContext.success() }
                        .onFailure { callbackContext.sendError(it) }
                }
                return true
            }

            "prompt" -> {
                mainScope.launch {
                    runCatching {
                        val options = parsePromptOptions(args)
                        implementation.prompt(options)
                    }
                        .onSuccess { text ->
                            callbackContext.success(JSONObject().put("text", text))
                        }
                        .onFailure { callbackContext.sendError(it) }
                }
                return true
            }

            "endSession" -> {
                runCatching {
                    val sessionId =
                        args.optJSONObject(0)?.optString("sessionId")
                            ?: throw LocalLLMError.MissingParameter("sessionId")
                    if (sessionId.isBlank()) {
                        throw LocalLLMError.MissingParameter("sessionId")
                    }
                    implementation.endSession(sessionId)
                }
                    .onSuccess { callbackContext.success() }
                    .onFailure { callbackContext.sendError(it) }
                return true
            }

            "generateImage" -> {
                mainScope.launch {
                    runCatching {
                        val payload = args.optJSONObject(0) ?: JSONObject()
                        val prompt =
                            payload.optString("prompt").takeIf { it.isNotBlank() }
                                ?: throw LocalLLMError.MissingParameter("prompt")
                        val count = payload.optInt("count", 1).coerceAtLeast(1)
                        implementation.generateImage(prompt, count)
                    }
                        .onSuccess { images ->
                            callbackContext.success(
                                JSONObject().put("pngBase64Images", JSONArray(images)),
                            )
                        }
                        .onFailure { callbackContext.sendError(it) }
                }
                return true
            }

            "addAvailabilityListener" -> {
                val listenerId =
                    args.optString(0).takeIf { it.isNotBlank() }
                        ?: run {
                            callbackContext.sendError(LocalLLMError.MissingParameter("listenerId"))
                            return true
                        }
                listenerCallbacks[listenerId] = callbackContext
                if (availabilityPollingJob?.isActive != true) {
                    startAvailabilityPolling()
                } else {
                    forceAvailabilityListenerUpdate = true
                }
                pushAvailabilityToListeners()
                return true
            }

            "removeAvailabilityListener" -> {
                val listenerId = args.optString(0)
                listenerCallbacks.remove(listenerId)
                if (listenerCallbacks.isEmpty()) {
                    stopAvailabilityPolling()
                }
                callbackContext.success()
                return true
            }

            "removeAllListeners" -> {
                listenerCallbacks.clear()
                stopAvailabilityPolling()
                callbackContext.success()
                return true
            }
        }

        return false
    }

    private fun startAvailabilityPolling() {
        if (availabilityPollingJob?.isActive == true) return

        availabilityPollingJob =
            pollingScope.launch {
                var lastAvailability: LLMAvailability? = null
                while (isActive) {
                    val current =
                        runCatching { implementation.availability() }
                            .getOrElse { LLMAvailability.Unavailable }

                    if (current != lastAvailability || forceAvailabilityListenerUpdate) {
                        lastAvailability = current
                        forceAvailabilityListenerUpdate = false
                        cordova.activity?.runOnUiThread { pushAvailabilityToListeners(current) }
                    }
                    delay(2000)
                }
            }
    }

    private fun stopAvailabilityPolling() {
        availabilityPollingJob?.cancel()
        availabilityPollingJob = null
    }

    private fun pushAvailabilityToListeners(status: LLMAvailability? = null) {
        val resolved =
            status
                ?: runCatching { implementation.availability() }
                    .getOrElse { LLMAvailability.Unavailable }

        val payload = JSONObject().put("status", resolved.value)
        for ((_, callback) in listenerCallbacks.toList()) {
            val result = PluginResult(PluginResult.Status.OK, payload)
            result.keepCallback = true
            callback.sendPluginResult(result)
        }
    }

    private fun parsePromptOptions(args: JSONArray): LLMPromptOptions {
        val payload = args.optJSONObject(0) ?: JSONObject()
        val prompt =
            payload.optString("prompt").takeIf { it.isNotBlank() }
                ?: throw LocalLLMError.MissingParameter("prompt")

        return LLMPromptOptions(
            sessionId = payload.optString("sessionId").takeIf { it.isNotBlank() },
            instructions = payload.optString("instructions").takeIf { it.isNotBlank() },
            options = parseLLMOptions(payload.optJSONObject("options")),
            prompt = prompt,
        )
    }

    private fun parseLLMOptions(optionsObject: JSONObject?): LLMOptions? {
        if (optionsObject == null) return null

        val temperature =
            optionsObject.optDouble("temperature").takeIf { !it.isNaN() }?.toFloat()?.coerceIn(0f, 1f)
        val maxOutputTokens =
            optionsObject.optInt("maximumOutputTokens").takeIf { it > 0 }?.coerceIn(1, 256)

        if (temperature == null && maxOutputTokens == null) return null
        return LLMOptions(temperature = temperature, maxOutputTokens = maxOutputTokens)
    }

    private fun CallbackContext.sendError(error: Throwable) {
        sendPluginResult(error.toPluginResult())
    }

    private fun Throwable.toPluginResult(): PluginResult {
        val llmError = this as? LocalLLMError
        val payload =
            JSONObject().apply {
                put("code", llmError?.code ?: "LOCAL_LLM_UNKNOWN_ERROR")
                put("message", llmError?.message ?: localizedMessage ?: toString())
                put("details", toString())
            }
        return PluginResult(PluginResult.Status.ERROR, payload)
    }
}
