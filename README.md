# cordova-local-llm

Cordova plugin for on-device LLM on **iOS** (Apple Intelligence / Foundation Models) and **Android** (Gemini Nano via ML Kit). 

## Requirements


| Feature           | iOS                                                          | Android                                  |
| ----------------- | ------------------------------------------------------------ | ---------------------------------------- |
| Minimum OS        | **iOS 18.4** (build), **iOS 26+** (text LLM)                 | **API 29** (Android 10)                  |
| Text LLM          | Apple Intelligence enabled                                   | Gemini Nano on device                    |
| `download()`      | Not supported                                                | Yes (model download)                     |
| `generateImage()` | Image Playground (iOS 18.4+)                                 | Not supported                            |
| `warmup()`        | Requires `sessionId` (iOS 26+)                               | Yes (no `sessionId`)                     |
| Emulator          | iOS 26+ Simulator on a Mac with Apple Intelligence (limited) | **Not supported** — physical device only |


## JavaScript API

After `deviceready`, the global `**window.LocalLLM`** object is available:

```javascript
document.addEventListener('deviceready', async () => {
  const { status } = await window.LocalLLM.systemAvailability();
  // available | unavailable | notready | downloadable (Android)

  if (status === 'downloadable') {
    await window.LocalLLM.download(); // Android only
  }

  const { text } = await window.LocalLLM.prompt({
    prompt: 'Explain on-device LLM in one sentence.',
  });

  const handle = await window.LocalLLM.addListener('systemAvailabilityChange', (e) => {
    console.log(e.status);
  });
  await handle.remove();
});
```

Errors are returned as objects: `{ code, message, details?, underlyingErrors?, nsErrorDomain?, nsErrorCode? }`.

### Methods


| Method                                        | iOS                        | Android |
| --------------------------------------------- | -------------------------- | ------- |
| `systemAvailability()`                        | Yes                        | Yes     |
| `download()`                                  | Error                      | Yes     |
| `prompt(options)`                             | Yes (iOS 26+)              | Yes     |
| `endSession({ sessionId })`                   | Yes                        | Yes     |
| `warmup(options)`                             | Yes (`sessionId` required) | Yes     |
| `generateImage(options)`                      | Yes (Image Playground)     | Error   |
| `addListener('systemAvailabilityChange', fn)` | Yes                        | Yes     |
| `removeAllListeners()`                        | Yes                        | Yes     |


### Image generation (iOS only)

```javascript
const res = await window.LocalLLM.generateImage({
  prompt: 'A calm lake at sunset',
  count: 1,
  promptImages: ['data:image/png;base64,...'], // optional reference images
});

const img = document.createElement('img');
img.src = 'data:image/png;base64,' + res.pngBase64Images[0];
```



Requires **JDK 17+** for Gradle.

---

## Testing: iOS vs Android

### Overview


|                | iOS                                                | Android                                      |
| -------------- | -------------------------------------------------- | -------------------------------------------- |
| Where to run   | Simulator **or** iPhone                            | **Physical phone only**                      |
| `notready`     | Apple Intelligence assets still loading            | Model downloading (`DOWNLOADING`)            |
| `downloadable` | Rare                                               | Call `LocalLLM.download()`                   |
| `available`    | Safe to call `prompt`                              | Safe to call `prompt`                        |
| JS debugging   | Safari → Develop → device/simulator                | Chrome → `chrome://inspect`                  |
| Images         | `generateImage` (if Image Playground is available) | `LOCAL_LLM_FEATURE_NOT_SUPPORTED_ON_ANDROID` |


### iOS

1. **Align versions**: macOS, Xcode, and the **Simulator runtime** should match (e.g. 26.5 / 26.5 / iOS 26.5). A mismatch often causes `ModelManagerError 1026` on `prompt` even when `systemAvailability` returns `available` or `notready`.
2. Enable **Apple Intelligence** on your Mac and on the simulator/device.
3. Set language to **English (US)** and Siri to **English (United States)** — otherwise assets may not download.
4. If status stays `notready`, wait or toggle Apple Intelligence off/on and restart your Mac.
5. For Simulator: `cordova build ios` → open in Xcode → Run. If the CLI fails because an old simulator is missing, pick a current device in Xcode (e.g. **iPhone 17, iOS 26.5**).
6. Image Playground is often **not supported** on Simulator — use a physical iPhone for `generateImage`.

### Android

1. **Emulators are not supported** — Gemini Nano needs compatible hardware (typically Pixel 8+ and similar devices with AICore).
2. Install **Android Studio**, SDK 29+, enable USB debugging on the phone.
3. Check availability → if `downloadable`, download the model → wait for `available`.
4. `maximumOutputTokens` in `options` is limited to **1–256** (same as Capacitor).
5. `warmup()` does not require `sessionId` (unlike iOS).

Logs: `adb logcat` or Android Studio Logcat. Filter by your app package or `LocalLLM`.

### Common issues


| Symptom                                      | iOS                                                             | Android                         |
| -------------------------------------------- | --------------------------------------------------------------- | ------------------------------- |
| `notready`                                   | Wait for AI assets; check OS/Xcode versions                     | Wait or call `download()`       |
| `GenerationError` / `ModelManagerError 1026` | Align macOS/Xcode/Simulator versions; toggle Apple Intelligence | —                               |
| `LOCAL_LLM_UNSUPPORTED_PLATFORM`             | Unsupported device / iOS < 26 for text                          | Device lacks Gemini Nano        |
| Image Playground not supported               | Simulator or device without Playground                          | Expected — use iOS              |
| `LOCAL_LLM_FEATURE_NOT_SUPPORTED_ON_ANDROID` | —                                                               | e.g. `generateImage` on Android |


---



