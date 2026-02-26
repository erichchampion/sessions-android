package com.sessions_ai.tools

import java.io.File

enum class PlanStepStatus(val stringValue: String) {
    PENDING("pending"),
    IN_PROGRESS("in_progress"),
    COMPLETED("completed"),
    ERROR("error")
}

data class PlanStep(
    val title: String,
    var status: PlanStepStatus = PlanStepStatus.PENDING
)

data class Plan(
    val steps: MutableList<PlanStep>
) {
    companion object {
        /** Parses a Plan from create_plan tool call args. Accepts List<*> or array-like with mixed types. */
        fun from(stepsArg: Any?): Plan? {
            if (stepsArg == null) return null
            val stringSteps = when (stepsArg) {
                is List<*> -> stepsArg.mapNotNull { item ->
                    when (item) {
                        is Map<*, *> -> listOf("title", "name", "step").firstNotNullOfOrNull { key ->
                            (item[key] as? String)?.trim()?.takeIf { it.isNotEmpty() }
                        }
                        else -> item?.toString()?.trim()?.takeIf { it.isNotEmpty() }
                    }
                }.filter { it.isNotEmpty() }
                is Array<*> -> stepsArg.mapNotNull { item ->
                    when (item) {
                        is Map<*, *> -> listOf("title", "name", "step").firstNotNullOfOrNull { key ->
                            (item[key] as? String)?.trim()?.takeIf { it.isNotEmpty() }
                        }
                        else -> item?.toString()?.trim()?.takeIf { it.isNotEmpty() }
                    }
                }.filter { it.isNotEmpty() }
                is String -> stepsArg.split(Regex("[\n,;]")).map { it.trim() }.filter { it.isNotEmpty() }
                else -> return null
            }
            if (stringSteps.isEmpty()) return null
            return Plan(stringSteps.mapTo(mutableListOf()) { PlanStep(it) })
        }
    }
}

/**
 * Thread-safe store for plans keyed by chat path. Set current chat before send so planning tools apply to the active chat.
 */
class PlanningStore private constructor() {

    @Volatile
    private var currentChatPath: String? = null

    private val plans = mutableMapOf<String, Plan>()
    private val lock = Any()

    private fun planKey(path: String): String = try {
        File(path).canonicalPath
    } catch (_: Exception) {
        path
    }

    /** Set the chat path to use for subsequent get/set until the next setCurrentChat. */
    fun setCurrentChat(path: String?) {
        synchronized(lock) {
            currentChatPath = path
        }
    }

    /** Get the plan for the current chat, or null if none or no current chat. */
    fun getPlan(): Plan? {
        synchronized(lock) {
            val path = currentChatPath ?: return null
            return plans[planKey(path)]
        }
    }

    /** Replace the plan for the current chat. No-op if no current chat. */
    fun setPlan(plan: Plan) {
        synchronized(lock) {
            val path = currentChatPath ?: return
            plans[planKey(path)] = plan
        }
    }

    /** Get plan for a specific chat path (e.g. for UI). */
    fun planFor(path: String?): Plan? {
        if (path == null) return null
        synchronized(lock) {
            return plans[planKey(path)]
        }
    }

    /** Remove the plan for a chat (e.g. when the chat is deleted). */
    fun removePlan(path: String) {
        synchronized(lock) {
            val key = planKey(path)
            plans.remove(key)
            if (currentChatPath != null && planKey(currentChatPath!!) == key) {
                currentChatPath = null
            }
        }
    }

    /** Move plan data from old path to new path (e.g. after rename). Updates currentChatPath if it was oldPath. */
    fun migratePlan(fromPath: String, toPath: String) {
        synchronized(lock) {
            val oldKey = planKey(fromPath)
            val newKey = planKey(toPath)
            if (oldKey == newKey) return
            plans[oldKey]?.let { plan ->
                plans[newKey] = plan
                plans.remove(oldKey)
            }
            if (currentChatPath != null && planKey(currentChatPath!!) == oldKey) {
                currentChatPath = toPath
            }
        }
    }

    /** Update the title of the step at the given index (0-based). No-op if no current chat, no plan, or index out of bounds. */
    fun updateStepTitle(stepIndex: Int, title: String) {
        synchronized(lock) {
            val path = currentChatPath ?: return
            val plan = plans[planKey(path)] ?: return
            if (stepIndex !in plan.steps.indices) return
            plan.steps[stepIndex] = plan.steps[stepIndex].copy(title = title.trim())
        }
    }

    /** Insert a new step at the given index (0-based). No-op if no current chat, no plan, or index out of bounds. */
    fun insertStep(index: Int, title: String) {
        synchronized(lock) {
            val path = currentChatPath ?: return
            val plan = plans[planKey(path)] ?: return
            val trimmed = title.trim()
            if (trimmed.isEmpty()) return
            if (index !in 0..plan.steps.size) return
            plan.steps.add(index, PlanStep(trimmed))
        }
    }

    /** Remove the step at the given index (0-based). No-op if no current chat, no plan, or index out of bounds. */
    fun removeStep(index: Int) {
        synchronized(lock) {
            val path = currentChatPath ?: return
            val plan = plans[planKey(path)] ?: return
            if (index !in plan.steps.indices) return
            plan.steps.removeAt(index)
        }
    }

    /** Update status of the step at the given index (0-based). No-op if no current chat, no plan, or index out of bounds. */
    fun updateStep(stepIndex: Int, status: PlanStepStatus) {
        synchronized(lock) {
            val path = currentChatPath ?: return
            val plan = plans[planKey(path)] ?: return
            if (stepIndex !in plan.steps.indices) return
            plan.steps[stepIndex].status = status
        }
    }

    /** Human-readable summary of the current chat's plan. Returns null if no plan. */
    fun getPlanSummary(): String? {
        synchronized(lock) {
            val path = currentChatPath ?: return null
            val plan = plans[planKey(path)] ?: return null
            if (plan.steps.isEmpty()) return null
            return formatPlanSummary(plan)
        }
    }

    /** Plan summary plus update instructions for the current chat. Returns null if no plan. */
    fun getPlanSummaryWithUpdateInstructions(): String? {
        synchronized(lock) {
            val path = currentChatPath ?: return null
            val plan = plans[planKey(path)] ?: return null
            if (plan.steps.isEmpty()) return null
            return formatPlanSummaryWithUpdateInstructions(plan)
        }
    }

    /** Plan summary for a specific path (e.g. for UI). Returns null if no plan for that path. */
    fun planSummaryFor(path: String?): String? {
        if (path == null) return null
        synchronized(lock) {
            val plan = plans[planKey(path)] ?: return null
            if (plan.steps.isEmpty()) return null
            return formatPlanSummary(plan)
        }
    }

    /** Plan summary plus update instructions for a specific path (for follow-up prompts). Returns null if no plan. */
    fun planSummaryWithUpdateInstructionsFor(path: String?): String? {
        if (path == null) return null
        synchronized(lock) {
            val plan = plans[planKey(path)] ?: return null
            if (plan.steps.isEmpty()) return null
            return formatPlanSummaryWithUpdateInstructions(plan)
        }
    }

    private fun formatPlanSummary(plan: Plan): String {
        val lines = plan.steps.mapIndexed { i, step ->
            "${i + 1}. [${step.status.stringValue}] ${step.title}"
        }
        return "Current plan:\n" + lines.joinToString("\n")
    }

    private fun formatPlanSummaryWithUpdateInstructions(plan: Plan): String {
        val summary = formatPlanSummary(plan)
        val activeIdx = plan.steps.indexOfFirst { it.status != PlanStepStatus.COMPLETED }
        val activeStepLine: String
        val remainingStepsLine: String
        if (activeIdx >= 0) {
            activeStepLine = "Active step now: ${activeIdx + 1}. ${plan.steps[activeIdx].title}"
            val remainingCount = plan.steps.size - activeIdx
            remainingStepsLine = if (remainingCount > 1) {
                val pendingIndices = (activeIdx + 2)..plan.steps.size
                "Steps ${pendingIndices.joinToString(" and ")} are still pending. Complete step ${activeIdx + 1} with update_step(completed), then respond to the user and continue to step ${activeIdx + 2}."
            } else {
                "Do not stop until all steps are completed."
            }
        } else {
            activeStepLine = "Active step now: all steps completed."
            remainingStepsLine = ""
        }
        val stepIndex1Based = activeIdx + 1
        val protocolBlock = if (activeIdx >= 0) """
Plan update protocol (REQUIRED when plan is active):
1. Before doing work on a step, call update_step with {"step_index": $stepIndex1Based, "status": "in_progress"}.
2. After completing that step's output, call update_step with {"step_index": $stepIndex1Based, "status": "completed"}.
3. Then respond to the user in natural language.
Use statuses: pending, in_progress, completed, error.
""".trimIndent() else """
Plan update protocol: Use statuses pending, in_progress, completed, error.
""".trimIndent()
        return summary + "\n\n$activeStepLine\n" +
            (if (remainingStepsLine.isNotEmpty()) "$remainingStepsLine\n" else "") +
            "\n$protocolBlock"
    }

    /** Clear plan for current chat only (convenience). */
    fun clearPlan() {
        synchronized(lock) {
            val path = currentChatPath ?: return
            plans.remove(planKey(path))
        }
    }

    companion object {
        val shared = PlanningStore()
    }
}
