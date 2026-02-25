package com.sessions_ai.tools

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

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
    val steps: List<PlanStep>
) {
    companion object {
        fun from(stepsArg: Any?): Plan? {
            val stepsList = stepsArg as? List<*> ?: return null
            val stringSteps = stepsList.mapNotNull { it as? String }
            if (stringSteps.isEmpty()) return null
            return Plan(stringSteps.map { PlanStep(it) })
        }
    }
}

class PlanningStore private constructor() {
    private var currentPlan: Plan? = null
    
    fun setPlan(plan: Plan) {
        currentPlan = plan
    }

    fun getPlan(): Plan? {
        return currentPlan
    }

    fun updateStep(stepIndex: Int, status: PlanStepStatus) {
        val plan = currentPlan ?: return
        if (stepIndex in plan.steps.indices) {
            plan.steps[stepIndex].status = status
        }
    }
    
    fun clearPlan() {
        currentPlan = null
    }

    companion object {
        val shared = PlanningStore()
    }
}
