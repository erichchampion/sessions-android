package com.sessions_ai

/**
 * Detects whether a user message describes a multi-step or long-form task that benefits from planning (create_plan first).
 * Used to strengthen the system prompt and optionally increase initial token budget.
 */
object ComplexTaskDetector {

    private val complexPhrases = listOf(
        "research and write",
        "research then",
        "research and",
        "write an essay",
        "write a short essay",
        "write a two page",
        "write a 2 page",
        "two page essay",
        " essay ",
        " essay.",
        " essay on",
        " essay about",
        "draft then",
        "draft and",
        "outline then",
        "outline and",
        "step by step",
        "step-by-step",
        "steps to ",
        "steps for ",
        "guide to ",
        "guide for ",
        "how-to ",
        "how to do",
        "walkthrough",
        "tutorial on",
        "tutorial for",
        "multi-page",
        "multi page",
        "multiple steps",
        "several steps",
        "break down ",
        "breakdown of",
        "compare ",
        "compare and",
        "compare the",
        "analyze and summarize",
        "analyze then",
        "analyze and",
        "review and summarize",
        "review and",
        "summarize then",
        "summarize and",
        "summarize the",
        "find and summarize",
        "read and summarize",
        "gather and ",
        "check and report",
        "list then explain",
        "list and explain",
        "research then recommend",
        "recommend and explain",
        "pros and cons"
    )

    /** Returns true if the message indicates a complex or multi-step task that should use create_plan first. */
    fun isComplexTask(message: String?): Boolean {
        val lower = message?.trim()?.lowercase() ?: return false
        if (lower.isEmpty()) return false
        return complexPhrases.any { lower.contains(it) }
    }
}
