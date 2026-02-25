package com.sessions_ai.tools

class CreatePlanTool(private val store: PlanningStore = PlanningStore.shared) : Tool {
    override val name = "create_plan"
    override val description = "Create a step-by-step plan for a complex, multi-step task. Use when the request cannot be answered with a single tool call. Pass \"steps\" (array of step titles). Call this first before other tools for essays, research, or multi-part tasks."
    override val schema = mapOf(
        "type" to "object",
        "properties" to mapOf(
            "steps" to mapOf(
                "type" to "array",
                "items" to mapOf("type" to "string")
            )
        ),
        "required" to listOf("steps")
    )

    override suspend fun execute(args: Map<String, Any>): String {
        val stepsArg = args["steps"] ?: return "Error: missing \"steps\" argument. Pass an array of step titles, e.g. {\"steps\": [\"Step 1\", \"Step 2\"]}."
        val plan = Plan.from(stepsArg) ?: return "Error: \"steps\" must be a non-empty array of strings."
        
        store.setPlan(plan)
        val steps = plan.steps
        val lines = steps.mapIndexed { index, step -> "${index + 1}. [pending] ${step.title}" }
        return "Plan created with ${steps.size} step(s):\n" + lines.joinToString("\n")
    }
}

class GetPlanTool(private val store: PlanningStore = PlanningStore.shared) : Tool {
    override val name = "get_plan"
    override val description = "Get the current plan for this chat (numbered steps and status). No args. Use to see progress before continuing or before calling update_step."
    override val schema = mapOf("type" to "object", "properties" to emptyMap<String, Any>())

    override suspend fun execute(args: Map<String, Any>): String {
        val plan = store.getPlan() ?: return "No plan set for this chat. Use create_plan first to break the task into steps."
        if (plan.steps.isEmpty()) return "Plan has no steps."
        
        val lines = plan.steps.mapIndexed { index, step ->
            "${index + 1}. [${step.status.stringValue}] ${step.title}"
        }
        return "Current plan:\n" + lines.joinToString("\n")
    }
}

class UpdateStepTool(private val store: PlanningStore = PlanningStore.shared) : Tool {
    override val name = "update_step"
    override val description = "Update a plan step's status. Use when you start or finish a step. Pass step_index (1-based) and status (pending, in_progress, completed, error). Call update_step(completed) for the active step before send_message when a plan exists. Use error when a step fails."
    override val schema = mapOf(
        "type" to "object",
        "properties" to mapOf(
            "step_index" to mapOf("type" to "integer"),
            "status" to mapOf(
                "type" to "string",
                "enum" to listOf("pending", "in_progress", "completed", "error")
            )
        ),
        "required" to listOf("step_index", "status")
    )

    override suspend fun execute(args: Map<String, Any>): String {
        store.getPlan() ?: return "No plan set. Use create_plan first."
        
        val indexArg = args["step_index"] ?: args["stepIndex"] ?: args["index"]
        val index = (indexArg as? Number)?.toInt() ?: return "Error: missing or invalid \"step_index\" (1-based integer)."
        
        val plan = store.getPlan() ?: return "No plan set."
        if (index < 1 || index > plan.steps.size) {
            return "Error: step_index $index out of range (plan has ${plan.steps.size} steps, use 1 to ${plan.steps.size})."
        }
        
        val statusArg = (args["status"] as? String)?.lowercase() ?: ""
        val status = when (statusArg) {
            "pending" -> PlanStepStatus.PENDING
            "in_progress", "in progress" -> PlanStepStatus.IN_PROGRESS
            "completed", "complete", "done" -> PlanStepStatus.COMPLETED
            "error", "failed" -> PlanStepStatus.ERROR
            else -> return "Error: status must be one of: pending, in_progress, completed, error."
        }
        
        store.updateStep(index - 1, status)
        return "Step $index set to ${status.stringValue}."
    }
}
