# Sessions-AI Android Port - Detailed Implementation Plan

## Overview
This plan outlines the architecture, technology stack, and step-by-step TDD procedure required to build a native Android application that mirrors the highly-capable iOS `Sessions-AI` application (excluding image generation).

## Technology Stack
- **Language**: Kotlin 2.x
- **UI Toolkit**: Jetpack Compose (Material 3) for reactive and declarative UI.
- **Architecture**: MVVM / Clean Architecture to separate UI, Business Logic, and Data/Native models.
- **Concurrency**: Kotlin Coroutines & Flow for fluid asynchronous task execution.
- **Local Inference Engine**: `llama.cpp` using the JNI / Android NDK bindings to load `.gguf` language models locally on-device.
- **Persistence**: Jetpack DataStore for preferences (like current selected model) and Room Database if history saving is required.
- **Networking/Downloads**: Ktor or Retrofit to handle Hugging Face model downloads.
- **Build System**: Gradle 8.x with Version Catalogs.
- **Minimum SDK**: 33 (Android 13) to target modern, performant devices capable of local LLM inference.
- **Target SDK**: 35 (Android 15).
- **Application ID**: `com.sessions_ai`

## 1. Project Initialization & Dependencies
1. Initialize the new Android Studio project with No Activity template or Compose Activity.
2. Setup `build.gradle.kts` (app level) for basic Compose, ViewModel, and Coroutines dependencies.
3. Setup the NDK path and build scripts (CMakeLists.txt) or incorporate a reliable upstream `llama.cpp` Android wrapper.
4. Establish the `com.sessions_ai` namespace.

## 2. Core LLM & Native Interoperability (TDD)
### Goal: Establish `LlamaCppService.kt` to communicate with C++ functions.
*   **Test**: `LlamaCppServiceTest.kt` -> Initialize mock interface, test context configuration limits, verify output structure.
*   **Implementation**: Create the JNI bindings that wrap the `llama.cpp` context creation, token generation, and sampler configurations (Top-P, Top-K, Temperature) analogous to `LlamaCppService.swift`. Ensure thread safety over a single inference coroutine dispatcher.

## 3. Model Catalog Implementation (TDD)
### Goal: Port `LLMModelCatalog.swift` configurations.
*   **Test**: `LLMModelCatalogTest.kt` -> Ensure default parameters, correctly mapped chat templates (Mistral, Llama, Qwen3, Phi), and file size formatting match expected constants.
*   **Implementation**: Create an enum/sealed class structure for supported models (`Qwen3`, `Phi-4`, `Mistral`) referencing HuggingFace parameters, chat templates, default max tokens, and memory targets.

## 4. Chat Orchestration & Execution (TDD)
### Goal: Port `ChatOrchestrator.swift` functionality.
*   **Test**: `ChatOrchestratorTest.kt` -> Validate multi-turn prompt templates (`<|im_start|>user\nHello...`), JSON stream decoding (ignoring thought streams, extracting tool calls), and context summarization boundaries.
*   **Implementation**: Implement a coroutine flow that processes messages, constructs templates based on the current model's `ChatTemplateFormat`, handles the `llama.cpp` native stream output, parses partial JSON, and emits display updates to the UI layer.

## 5. UI Layer Implementation
### Goal: Build responsive Jetpack Compose screens for Settings and Chat.
*   **Settings/Model Screen**: Create a Compose screen tracking Model Download state (Idle, Downloading, Complete) with file sizes fetched from the `LLMModelCatalog`. Launch download tasks using a work manager or simple bound service.
*   **Chat Interaction Screen**: Implement the main Chat scrolling list. Display real-time streaming updates from the Orchestrator, correctly parsing markdown responses for rich text viewing. Input field for querying.

## 6. Iterative refinement
*   Run the compiled application natively to test performance mapping memory overhead.
*   Handle complex behaviors like model offloading (unload model from RAM when backgrounded to prevent OS termination).
*   Add logging (`Log.i`, `Log.d`) mimicking `LoggingService.swift` on iOS for inference speed metrics (Tokens/sec).
