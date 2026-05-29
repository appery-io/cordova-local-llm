# cordova-local-llm

Cordova-плагин для on-device LLM на **iOS** (Apple Intelligence / Foundation Models и Image Playground). Логика нативного слоя перенесена из `@capacitor/local-llm`; JavaScript API совместим с Capacitor-версией.

## Требования

| Функция | Минимум |
|--------|---------|
| Сборка / Image Playground | **iOS 18.4**, Xcode на Mac с поддерживаемой версией SDK |
| Текстовый LLM (`prompt`, `warmup`, сессии) | **iOS 26+**, включённый **Apple Intelligence** на устройстве или симуляторе |
| Mac для симулятора | Mac с Apple Silicon и включённым Apple Intelligence (для тестов LLM в Simulator) |

Android в этой Cordova-сборке **не включён** (в Capacitor-оригинале используется Gemini Nano).

## Установка плагина в свой проект

```bash
cd /path/to/your-cordova-app
cordova plugin add /path/to/capacitor-local-llm-main/cordova-local-llm
cordova platform add ios
```

Или из git (после публикации):

```bash
cordova plugin add cordova-local-llm
```

## JavaScript API

После `deviceready` глобальный объект **`LocalLLM`** (как в Capacitor):

```javascript
document.addEventListener('deviceready', async () => {
  const { status } = await LocalLLM.systemAvailability();
  console.log(status); // available | unavailable | notready

  const { text } = await LocalLLM.prompt({
    prompt: 'Explain on-device LLM in one sentence.',
  });

  const handle = await LocalLLM.addListener('systemAvailabilityChange', (e) => {
    console.log(e.status);
  });
  await handle.remove();
});
```

Ошибки приходят как объект `{ code, message }` (коды вроде `LOCAL_LLM_NOT_ENABLED`, `LOCAL_LLM_UNSUPPORTED_PLATFORM`).

Методы: `systemAvailability`, `prompt`, `endSession`, `warmup`, `generateImage`, `download` (на iOS — ошибка «не поддерживается»), `addListener`, `removeAllListeners`.

## Сборка примера на iPhone

В репозитории есть готовое demo: `cordova-example-app/`.

### 1. Установить инструменты

```bash
npm install -g cordova@12
# Xcode из App Store, Command Line Tools:
xcode-select --install
```

### 2. Подготовить пример

```bash
cd /Users/aantsypau/workspace/capacitor-local-llm-main/cordova-example-app
npm install
cordova plugin add ../cordova-local-llm
cordova platform add ios
```

### 3. Собрать

```bash
cordova build ios
```

Открыть проект в Xcode:

```bash
open platforms/ios/*.xcworkspace
# если workspace нет:
open platforms/ios/*.xcodeproj
```

### 4. Запуск на устройстве

1. Подключите iPhone (iOS 18.4+; для текста — **iOS 26**).
2. В Xcode: target приложения → **Signing & Capabilities** → выберите свою Team и уникальный Bundle ID.
3. На iPhone: **Настройки → Apple Intelligence** — включите (для `prompt`).
4. Выберите iPhone в списке устройств → **Run** (▶).

Через CLI (подставьте имя устройства из `xcrun xctrace list devices`):

```bash
cordova run ios --device
```

### 5. Симулятор (ограничения)

```bash
cordova emulate ios
```

- **Image Playground** (`generateImage`) может работать на подходящем симуляторе с iOS 18.4+.
- **Текстовый LLM** нужен симулятор **iOS 26+** и Mac с включённым Apple Intelligence; иначе `systemAvailability` вернёт `unavailable` / `notready`.

## Как протестировать

### Чеклист в demo-приложении

1. Запустите приложение, дождитесь `deviceready`.
2. **«Проверить доступность»** — ожидается `available` при готовой модели; иначе `notready` / `unavailable` (см. настройки Apple Intelligence).
3. Введите текст и нажмите **«Отправить prompt»** — в блоке ответа должен появиться текст модели (только iOS 26+).
4. **«Сгенерировать»** (изображение) — должна появиться картинка (iOS 18.4+, Image Playground).
5. Смените статус AI в системных настройках — подписчик `systemAvailabilityChange` должен обновить статус на экране.

### Проверка из Safari Web Inspector

1. На Mac: **Safari → Develop → [ваш iPhone] → Local LLM Cordova Demo**.
2. В консоли:

```javascript
LocalLLM.systemAvailability().then(console.log);
LocalLLM.prompt({ prompt: 'Hello' }).then(console.log).catch(console.error);
```

### Типичные ошибки

| Код / симптом | Что сделать |
|---------------|-------------|
| `LOCAL_LLM_UNSUPPORTED_PLATFORM` | Устройство или iOS ниже требований; для текста нужен iOS 26. |
| `LOCAL_LLM_NOT_ENABLED` | Включить Apple Intelligence в Настройках. |
| `LOCAL_LLM_NOT_READY` | Дождаться загрузки модели; повторить позже. |
| Плагин не найден | Собирать только после `cordova prepare` / `cordova build ios`, не открывать голый `www/` в браузере. |
| Signing error в Xcode | Настроить Apple Developer Team и Bundle ID. |

## Структура плагина

```
cordova-local-llm/
  plugin.xml
  package.json
  www/LocalLLM.js          # JS-мост (cordova.exec)
  src/ios/
    LocalLLM.swift         # Foundation Models / Image Playground
    LocalLLMPlugin.swift     # CDVPlugin
    Bridging-Header.h
```

## Отличия от Capacitor-версии

- Регистрация через `plugin.xml` и `CDVPlugin`, не `CAPPlugin`.
- Слушатели: `addAvailabilityListener` / `listenerId` вместо встроенного Capacitor `addListener`.
- Нет npm-пакета `@capacitor/core`; подключение через Cordova `clobbers` → `window.LocalLLM`.
- Только **iOS** в Cordova-сборке.

## Лицензия

MIT (как у исходного проекта).
