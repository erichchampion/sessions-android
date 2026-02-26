package com.sessions_ai.tools

/**
 * Registry of named tools for LLM tool-call execution.
 */
class ToolRegistry private constructor() {

    private val tools = mutableMapOf<String, Tool>()

    fun register(tool: Tool) {
        tools[tool.name] = tool
    }

    fun unregister(name: String) {
        tools.remove(name)
    }

    fun tool(named: String): Tool? {
        return tools[named]
    }

    suspend fun execute(name: String, args: Map<String, Any>): String {
        val tool = tool(named = name) ?: throw IllegalArgumentException("Tool not found: $name")
        return tool.execute(args)
    }

    /**
     * Instructions for the LLM describing available tools and the exact call format.
     */
    /**
     * @param includeImageGeneration If false, generate_image is never mentioned in the prompt (Android omits image generation).
     */
    fun toolInstructionsForPrompt(
        toolNames: Set<String>? = null,
        includeReadAttachedFile: Boolean = true,
        includeImageGeneration: Boolean = false
    ): String {
        val allowed = allowedTools(toolNames, includeReadAttachedFile)
        val sortedNames = tools.keys.sorted()
        val availableList = sortedNames.filter { allowed.contains(it) }.joinToString(", ")
        
        val compactLines = compactToolLines.filter { line ->
            allowed.contains(line.first) && (line.first != "generate_image" || includeImageGeneration)
        }
        val compactBlock = compactLines.joinToString("\n") { "${it.first}: ${it.second}" }
        
        val exampleLines = mutableListOf("Examples (use these formats):")
        if (includeReadAttachedFile) {
            exampleLines.add("read_attached_file: <tool_call>{\"name\":\"read_attached_file\",\"args\":{\"index\":1,\"part\":1}}</tool_call>")
        }
        exampleLines.add("reminders: list first to get reminder_id; then delete/complete: <tool_call>{\"name\":\"reminders\",\"args\":{\"action\":\"list\"}}</tool_call> then <tool_call>{\"name\":\"reminders\",\"args\":{\"action\":\"delete\",\"reminder_id\":\"<id>\"}}</tool_call>")
        exampleLines.add("web_search: <tool_call>{\"name\":\"web_search\",\"args\":{\"query\":\"...\"}}</tool_call>")
        if (allowed.contains("calculator")) {
            exampleLines.add("calculator: <tool_call>{\"name\":\"calculator\",\"args\":{\"expression\":\"103*6\"}}</tool_call>")
        }
        if (allowed.contains("unit_conversion")) {
            exampleLines.add("unit_conversion: <tool_call>{\"name\":\"unit_conversion\",\"args\":{\"value\":5,\"from_unit\":\"miles\",\"to_unit\":\"km\"}}</tool_call>")
        }
        if (allowed.contains("wikipedia")) {
            exampleLines.add("wikipedia: <tool_call>{\"name\":\"wikipedia\",\"args\":{\"query\":\"...\"}}</tool_call>")
        }
        if (allowed.contains("fetch_page")) {
            exampleLines.add("fetch_page: <tool_call>{\"name\":\"fetch_page\",\"args\":{\"url\":\"https://...\"}}</tool_call>")
        }
        if (allowed.contains("create_plan")) {
            exampleLines.add("create_plan: <tool_call>{\"name\":\"create_plan\",\"args\":{\"steps\":[\"Research topic\",\"Draft outline\",\"Write sections\"]}}</tool_call>")
        }
        if (includeImageGeneration && allowed.contains("generate_image")) {
            exampleLines.add("generate_image: call then use send_message with the returned \"Image saved to:\" path so the user sees the image: <tool_call>{\"name\":\"generate_image\",\"args\":{\"prompt\":\"...\"}}</tool_call> then send_message with content including that path.")
        }
        
        val examplesBlock = exampleLines.joinToString("\n")
        val descriptionBlock = toolDescriptionBlock(toolNames, includeReadAttachedFile)
        
        // Android equivalent doesn't yet have concept phrase interpolation natively identical to iOS here.
        return """
        You are an assistant. For general questions, greetings, or conceptual questions (e.g. describe, explain, compare, discuss, what is X) answer in plain text and do NOT output any <tool_call>. Only call tools when the user explicitly needs a calculation, search, conversion, reminder, calendar, to look something up, or a multi-step task (essay, report, research). For multi-step tasks call create_plan first with concrete steps, then use other tools and update_step as you go.

        When you do need a tool, output ONLY valid <tool_call> lines in this format: <tool_call>{"name":"...","args":{...}}</tool_call> JSON.

        $examplesBlock

        Tool descriptions (use these to decide when to call a tool and what args to pass):
        $descriptionBlock

        Reference: $compactBlock

        After receiving "Result of <tool>: ..." in the conversation, respond in natural language to the user (e.g. "The answer is 104" or a one- to two-sentence summary of search results). Do not just echo the raw result or say "None" without using the content.

        Available: $availableList. Long output: use get_tool_result_part with "part":2 etc. Valid JSON, double quotes.
        """.trimIndent()
    }

    private fun allowedTools(toolNames: Set<String>?, includeReadAttachedFile: Boolean): Set<String> {
        val required = mutableListOf("get_tool_result_part")
        required.addAll(planningToolNamesInOrder)
        if (includeReadAttachedFile) {
            required.add("read_attached_file")
        }
        if (toolNames != null) {
            return toolNames.union(required)
        }
        return tools.keys.filter { includeReadAttachedFile || it != "read_attached_file" }.toSet()
    }

    fun parameterReferenceBlock(toolNames: Set<String>? = null, includeReadAttachedFile: Boolean = true): String {
        val allowed = allowedTools(toolNames, includeReadAttachedFile)
        return compactToolLines.filter { allowed.contains(it.first) }
            .joinToString("\n") { "${it.first}: ${it.second}" }
    }

    fun toolDescriptionBlock(toolNames: Set<String>? = null, includeReadAttachedFile: Boolean = true): String {
        val allowed = allowedTools(toolNames, includeReadAttachedFile)
        val sortedNames = tools.keys.sorted().filter { allowed.contains(it) }
        return sortedNames.mapNotNull { name ->
            val tool = tools[name] ?: return@mapNotNull null
            "$name: ${tool.description}"
        }.joinToString("\n")
    }

    fun getToolSchemas(toolNames: Set<String>? = null): List<Pair<String, Map<String, Any>>> {
        val schemas = tools.values.mapNotNull { tool ->
            if (toolNames != null && !toolNames.contains(tool.name)) return@mapNotNull null
            // For now skip null check, assume all implemented tools have schemas
            Pair(tool.name, tool.schema)
        }
        return schemas.sortedBy { it.first }
    }

    companion object {
        val planningToolNamesInOrder = listOf("create_plan", "get_plan", "update_step")

        private val compactToolLines = listOf(
            Pair("calendar", "list|add(title,start_date,end_date)|delete(event_id)"),
            Pair("calculator", "expression"),
            Pair("clipboard", "read|write(content)"),
            Pair("contacts", "search(query)"),
            Pair("create_plan", "steps([...])"),
            Pair("fetch_page", "url"),
            Pair("file_search", "search(query)"),
            Pair("generate_image", "prompt → returns path; include path in send_message"),
            Pair("get_plan", "no args"),
            Pair("get_tool_result_part", "part(1-based)"),
            Pair("mail", "draft(to,subject,body)"),
            Pair("maps", "open(query)|directions(start,end)"),
            Pair("notes", "create(title,content)"),
            Pair("read_attached_file", "index,part"),
            Pair("reminders", "add(title,?due_date)|list|delete(reminder_id)|complete(reminder_id)"),
            Pair("screen_capture", "no args"),
            Pair("system_control", "open_app(name)|status"),
            Pair("unit_conversion", "value,from_unit,to_unit"),
            Pair("update_step", "step_index,status"),
            Pair("web_search", "query"),
            Pair("wikipedia", "query")
        )

        val shared: ToolRegistry by lazy {
            ToolRegistry().apply {
                register(CalculatorTool())
                register(UnitConversionTool())
                register(WikipediaTool())
                register(DuckDuckGoSearchTool())
                register(FetchPageTool())
                register(CreatePlanTool())
                register(GetPlanTool())
                register(UpdateStepTool())
                
                // Add stubs for minimal functionality bridging
                // register(GetToolResultPartTool()) // Can be added later
                // register(ReadAttachedFileTool()) // Can be added later
            }
        }
    }
}
