# Sessions-AI (Android)

Sessions-AI is a high-performance, fully on-device Large Language Model (LLM) chat application for Android, built as a native port of the original iOS concept. It leverages local inference through `llama.cpp` to provide uncensored, offline AI capabilities directly on modern Android hardware.

## Architectural Overview

This project is built using modern Android development practices and Clean Architecture principles:

- **UI Layer**: Built entirely in **Jetpack Compose** (Material 3). It utilizes a unidirectional data flow (UDF) powered by `StateFlow` within an `AndroidViewModel` to seamlessly react to inference tokens and download progress.
- **Orchestration**: The `ChatOrchestrator` manages conversational memory, properly formatting raw prompt templates (e.g., Mistral, Qwen, ChatML) strictly required by instructions-tuned models before inference.
- **Model Catalog**: Defines available, tested Hugging Face GGUF models. It includes memory requirements, context window parameters, and designated chat templates.
- **Network / Download Manager**: Uses standard Android `HttpURLConnection` running on IO Coroutines to securely pull heavy (multi-gigabyte) LLM weight files from Hugging Face directly to protected app storage without exhausting device RAM.
- **Native Inference Layer (JNI)**: Contains direct C++ bindings for `llama.cpp` compiled natively via the Android NDK (CMake). The Kotlin Interface (`LLMService`) maps inputs to memory-safe C++ pointers for loading `.gguf` contexts and generating synchronous neural network outputs.

## Build Instructions

### Prerequisites
- Android Studio Ladybug (or newer recommended)
- Android SDK 35
- Android NDK (Native Development Kit) and CMake installed via SDK Manager.
- A modern Android device (or Emulator) running Android 13+ (API Min 33, Target 35). Emulators require an `x86_64` system image; physical devices require `arm64-v8a`.

### Compiling from Source
1. Clone the repository and open the `sessions-android` root folder in Android Studio.
2. The initial Gradle Sync will pull down the corresponding `llama.cpp` dependency source automatically.
3. Build the project:
   - Command line: `./gradlew assembleDebug`
   - Or click "Make Project" (the hammer icon) in Android Studio.

Note: The C++ compilation step (CMake) during the initial build can take a minute or two as it compiles the ggml tensor library.

## Testing Guidelines

This project was built adhering to Test-Driven Development (TDD). 

### 1. Unit Tests (Kotlin/JVM)
These tests strictly mock the external Native dependencies allowing for lightning-fast architectural regressions. They verify:
- `ChatOrchestrator` context prompting and instruction template wrapping.
- `LLMModelCatalog` lookup and correct parsing rules.
- Execution: `./gradlew testDebugUnitTest`

### 2. Instrumentation Tests (Device/Emulator)
(Future Implementation Step). These tests will run on physical hardware (or robust emulators) to ensure JNI stability, loading mock GGUF files and asserting that the `llama.cpp` wrapper does not crash or leak memory.
- Execution: `./gradlew connectedAndroidTest`

## License
Provided under the same terms as the parent Sessions-AI repository. `llama.cpp` is licensed under MIT.
