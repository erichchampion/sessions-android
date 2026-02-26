package com.sessions_ai.tools

class CreatePlanTool(private val store: PlanningStore = PlanningStore.shared) : Tool {
    override val name = "create_plan"
    override val description = "Use when the user asks for a multi-step task: essay, research, report, or anything that needs several steps. Call this first with \"steps\" (array of step titles, e.g. [\"Research topic\", \"Draft outline\", \"Write sections\"]). Then use get_plan and update_step as you work through each step."
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
        val stepsArg = args["steps"] ?: args["step_titles"] ?: args["stepTitles"]
            ?: return "Error: missing \"steps\" argument. Pass an array of step titles, e.g. {\"steps\": [\"Step 1\", \"Step 2\"]} or {\"name\":\"create_plan\",\"steps\":[\"Step 1\",\"Step 2\"]}."
        val plan = Plan.from(stepsArg)
            ?: return "Error: \"steps\" must be a non-empty array of strings (e.g. [\"Research\", \"Draft\", \"Write\"])."
        
        store.setPlan(plan)
        if (store.getPlan() == null) {
            return "Error: No chat selected. The plan was not saved. Create or select a chat first, then call create_plan again with your steps."
        }
        val steps = plan.steps
        val lines = steps.mapIndexed { index, step -> "${index + 1}. [pending] ${step.title}" }
        return "Plan created with ${steps.size} step(s):\n" + lines.joinToString("\n")
    }
}

class GetPlanTool(private val store: PlanningStore = PlanningStore.shared) : Tool {
    override val name = "get_plan"
    override val description = "Get the current plan (numbered steps and status). No args. Call when a plan exists and you need to see progress or before calling update_step to mark a step completed."
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
    override val description = "Update a plan step's status. Pass step_index (1-based) and status: pending, in_progress, completed, or error. Call update_step with completed when you finish a step; use error if a step fails. Call before replying to the user when a plan exists."
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
