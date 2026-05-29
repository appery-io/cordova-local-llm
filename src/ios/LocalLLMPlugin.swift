import Foundation
import FoundationModels

@objc(LocalLLMPlugin)
class LocalLLMPlugin: CDVPlugin {
    private let implementation = LocalLLM()
    private var availabilityPollingTask: Task<Void, Never>?
    private var forceAvailabilityListenerUpdate = false
    private var listenerCallbackIds: [String: String] = [:]

    // MARK: - Availability

    @objc(systemAvailability:)
    func systemAvailability(command: CDVInvokedUrlCommand) {
        sendOk(
            command,
            payload: ["status": LocalLLM.availability().rawValue]
        )
    }

    @objc(download:)
    func download(command: CDVInvokedUrlCommand) {
        sendError(
            command,
            error: LocalLLMError.featureNotSupported("model download")
        )
    }

    // MARK: - Listeners

    @objc(addAvailabilityListener:)
    func addAvailabilityListener(command: CDVInvokedUrlCommand) {
        guard
            let listenerId = command.argument(at: 0) as? String,
            !listenerId.isEmpty
        else {
            sendError(command, error: LocalLLMError.missingParameter("listenerId"))
            return
        }

        listenerCallbackIds[listenerId] = command.callbackId

        if availabilityPollingTask == nil {
            startAvailabilityPolling()
        } else {
            forceAvailabilityListenerUpdate = true
        }

        pushAvailabilityToListeners(status: LocalLLM.availability().rawValue)
    }

    @objc(removeAvailabilityListener:)
    func removeAvailabilityListener(command: CDVInvokedUrlCommand) {
        guard let listenerId = command.argument(at: 0) as? String else {
            sendOk(command, payload: [:] as [String: Any])
            return
        }
        listenerCallbackIds.removeValue(forKey: listenerId)
        if listenerCallbackIds.isEmpty {
            stopAvailabilityPolling()
        }
        sendOk(command, payload: [:] as [String: Any])
    }

    @objc(removeAllListeners:)
    func removeAllListeners(command: CDVInvokedUrlCommand) {
        listenerCallbackIds.removeAll()
        stopAvailabilityPolling()
        sendOk(command, payload: [:] as [String: Any])
    }

    // MARK: - Sessions & prompts

    @objc(warmup:)
    func warmup(command: CDVInvokedUrlCommand) {
        do {
            let args = command.arguments?.first as? [String: Any] ?? [:]
            guard let sessionId = args["sessionId"] as? String, !sessionId.isEmpty else {
                throw LocalLLMError.missingParameter("sessionId")
            }
            let promptPrefix = args["promptPrefix"] as? String

            try implementation.warmup(sessionId: sessionId, promptPrefix: promptPrefix)
            sendOk(command, payload: [:] as [String: Any])
        } catch {
            sendError(command, error: error)
        }
    }

    @objc(prompt:)
    func prompt(command: CDVInvokedUrlCommand) {
        let options = promptOptions(from: command)

        guard !options.prompt.isEmpty else {
            sendError(command, error: LocalLLMError.missingParameter("prompt"))
            return
        }

        Task {
            do {
                let text = try await implementation.prompt(options: options)
                sendOk(command, payload: ["text": text])
            } catch {
                sendError(command, error: error)
            }
        }
    }

    @objc(endSession:)
    func endSession(command: CDVInvokedUrlCommand) {
        let args = command.arguments?.first as? [String: Any] ?? [:]
        guard let sessionId = args["sessionId"] as? String, !sessionId.isEmpty else {
            sendError(command, error: LocalLLMError.missingParameter("sessionId"))
            return
        }
        implementation.endSession(sessionId)
        sendOk(command, payload: [:] as [String: Any])
    }

    @objc(generateImage:)
    func generateImage(command: CDVInvokedUrlCommand) {
        guard
            let args = command.arguments?.first as? [String: Any],
            let prompt = args["prompt"] as? String,
            !prompt.isEmpty
        else {
            sendError(command, error: LocalLLMError.missingParameter("prompt"))
            return
        }

        let count = max(1, args["count"] as? Int ?? 1)
        let promptImages = args["promptImages"] as? [String] ?? []

        Task {
            do {
                let images = try await implementation.generateImage(
                    prompt: prompt,
                    promptImages: promptImages,
                    variations: count
                )
                sendOk(command, payload: ["pngBase64Images": images])
            } catch {
                sendError(command, error: error)
            }
        }
    }

    // MARK: - Polling

    private func startAvailabilityPolling() {
        guard availabilityPollingTask == nil else { return }

        availabilityPollingTask = Task { [weak self] in
            var lastAvailability: LLMAvailability?
            while !Task.isCancelled {
                guard let self else { return }
                let current = LocalLLM.availability()
                if current != lastAvailability || self.forceAvailabilityListenerUpdate {
                    lastAvailability = current
                    self.forceAvailabilityListenerUpdate = false
                    self.pushAvailabilityToListeners(status: current.rawValue)
                }
                try? await Task.sleep(nanoseconds: 2_000_000_000)
            }
        }
    }

    private func stopAvailabilityPolling() {
        availabilityPollingTask?.cancel()
        availabilityPollingTask = nil
    }

    private func pushAvailabilityToListeners(status: String) {
        let payload: [String: Any] = ["status": status]
        for callbackId in listenerCallbackIds.values {
            guard let result = CDVPluginResult(status: CDVCommandStatus_OK, messageAs: payload) else {
                continue
            }
            result.setKeepCallbackAs(true)
            commandDelegate.send(result, callbackId: callbackId)
        }
    }

    // MARK: - Argument parsing

    private func promptOptions(from command: CDVInvokedUrlCommand) -> LLMPromptOptions {
        let args = command.arguments?.first as? [String: Any] ?? [:]
        return LLMPromptOptions(
            sessionId: args["sessionId"] as? String,
            instructions: args["instructions"] as? String,
            options: llmOptions(from: args["options"] as? [String: Any]),
            prompt: args["prompt"] as? String ?? ""
        )
    }

    private func llmOptions(from dict: [String: Any]?) -> LLMOptions? {
        guard let dict else { return nil }
        return LLMOptions(
            temperature: dict["temperature"] as? Double,
            maximumOutputTokens: dict["maximumOutputTokens"] as? Int
        )
    }

    // MARK: - CDV results

    private func sendOk(_ command: CDVInvokedUrlCommand, payload: [String: Any]) {
        let result = CDVPluginResult(status: CDVCommandStatus_OK, messageAs: payload)
        commandDelegate.send(result, callbackId: command.callbackId)
    }

    private func sendError(_ command: CDVInvokedUrlCommand, error: Error) {
        let nsError = error as NSError

        if #available(iOS 26.0, *), let genError = error as? LanguageModelSession.GenerationError {
            let payload: [String: Any] = [
                "code": "LOCAL_LLM_GENERATION_ERROR",
                "message": genError.localizedDescription,
                "details": String(reflecting: genError),
                "nsErrorDomain": nsError.domain,
                "nsErrorCode": nsError.code,
            ]
            let result = CDVPluginResult(status: CDVCommandStatus_ERROR, messageAs: payload)
            commandDelegate.send(result, callbackId: command.callbackId)
            return
        }

        // Some FoundationModels errors bridge through as NSError without a typed enum instance.
        if #available(iOS 26.0, *), nsError.domain == "FoundationModels.LanguageModelSession.GenerationError" {
            var payload: [String: Any] = [
                "code": "LOCAL_LLM_GENERATION_ERROR",
                "message": error.localizedDescription,
                "details": String(reflecting: error),
                "nsErrorDomain": nsError.domain,
                "nsErrorCode": nsError.code,
            ]

            if let underlying = nsError.userInfo[NSMultipleUnderlyingErrorsKey] as? [NSError] {
                payload["underlyingErrors"] = underlying.map { u in
                    [
                        "domain": u.domain,
                        "code": u.code,
                        "description": u.localizedDescription,
                        "details": String(reflecting: u),
                    ]
                }
            }

            let result = CDVPluginResult(status: CDVCommandStatus_ERROR, messageAs: payload)
            commandDelegate.send(result, callbackId: command.callbackId)
            return
        }

        let llmError = error as? LocalLLMError
        let code = llmError?.errorCode ?? "LOCAL_LLM_UNKNOWN_ERROR"
        let message = llmError?.errorDescription ?? error.localizedDescription
        let payload: [String: Any] = [
            "code": code,
            "message": message,
            "details": String(reflecting: error),
            "nsErrorDomain": nsError.domain,
            "nsErrorCode": nsError.code,
        ]
        let result = CDVPluginResult(status: CDVCommandStatus_ERROR, messageAs: payload)
        commandDelegate.send(result, callbackId: command.callbackId)
    }
}
