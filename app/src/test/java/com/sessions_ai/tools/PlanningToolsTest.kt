package com.sessions_ai.tools

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import kotlinx.coroutines.runBlocking

class PlanningToolsTest {

    private lateinit var store: PlanningStore

    @Before
    fun setup() {
        store = PlanningStore.shared
        store.clearPlan()
    }

    @Test
    fun testCreatePlanTool() = runBlocking {
        val tool = CreatePlanTool(store)
        val result = tool.execute(mapOf("steps" to listOf("Research", "Write", "Review")))
        
        assertTrue(result.contains("Plan created with 3 step(s)"))
        assertTrue(result.contains("1. [pending] Research"))
        
        val plan = store.getPlan()
        assertEquals(3, plan?.steps?.size)
        assertEquals("Research", plan?.steps?.first()?.title)
    }

    @Test
    fun testGetPlanTool() = runBlocking {
        store.setPlan(Plan(listOf(PlanStep("Do work"))))
        
        val tool = GetPlanTool(store)
        val result = tool.execute(emptyMap())
        
        assertTrue(result.contains("1. [pending] Do work"))
    }

    @Test
    fun testUpdateStepTool() = runBlocking {
        store.setPlan(Plan(listOf(PlanStep("Do work"))))
        
        val tool = UpdateStepTool(store)
        val result = tool.execute(mapOf("step_index" to 1, "status" to "in_progress"))
        
        assertEquals("Step 1 set to in_progress.", result)
        assertEquals(PlanStepStatus.IN_PROGRESS, store.getPlan()?.steps?.first()?.status)
    }

    @Test
    fun testUpdateStepToolMissingPlan() = runBlocking {
        val tool = UpdateStepTool(store)
        val result = tool.execute(mapOf("step_index" to 1, "status" to "in_progress"))
        
        assertEquals("No plan set. Use create_plan first.", result)
    }
}
