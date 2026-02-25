package com.sessions_ai

class SessionsAIEngine {

    companion object {
        init {
            System.loadLibrary("sessions-ai")
        }
    }

    external fun init(nativeLibDir: String)
    external fun load(modelPath: String): Int
    external fun processPrompt(prompt: String, nPredict: Int): Int
    external fun generateNextToken(): String?
    external fun unload()
    external fun shutdown()
}
