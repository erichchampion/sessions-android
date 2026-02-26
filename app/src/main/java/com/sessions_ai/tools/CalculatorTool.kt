package com.sessions_ai.tools

/**
 * Evaluates a single mathematical expression (numbers and +, -, *, /, parentheses).
 * Matches iOS CalculatorTool behavior for parity.
 */
class CalculatorTool : Tool {

    override val name = "calculator"
    override val description = "Use when the user asks to calculate, compute, or do arithmetic (e.g. 'what is 103 times 6?', 'compute 15% of 200'). Always use this tool for numeric calculations—do not compute in your head. Pass \"expression\" in args (e.g. \"103*6\", \"2+3*4\"). For conceptual math (explain, define) answer in plain text; do not call this tool."
    override val schema = mapOf(
        "type" to "object",
        "properties" to mapOf("expression" to mapOf("type" to "string")),
        "required" to listOf("expression")
    )

    override suspend fun execute(args: Map<String, Any>): String {
        val exprString = (args["expression"] as? String) ?: (args["expr"] as? String)
            ?: return "Error: missing \"expression\" argument for calculator."
        val trimmed = exprString.trim()
        if (trimmed.isEmpty()) return "Error: missing \"expression\" argument for calculator."
        if (!isValidArithmetic(trimmed)) return "Error: invalid expression. Use only numbers and +, -, *, /, (, )."
        return try {
            val value = evaluate(trimmed)
            if (value.isNaN() || value.isInfinite()) "Error: result is not a finite number (e.g. division by zero)."
            else "Result: $value"
        } catch (_: Exception) {
            "Error: expression did not evaluate to a number."
        }
    }

    private fun isValidArithmetic(s: String): Boolean {
        val allowed = "0123456789. +-*/()"
        return s.all { it in allowed }
    }

    private fun evaluate(expr: String): Double {
        val pos = intArrayOf(0)
        val result = parseExpr(expr, pos)
        skipSpaces(expr, pos)
        if (pos[0] != expr.length) throw IllegalArgumentException("Unparsed remainder")
        return result
    }

    private fun skipSpaces(expr: String, pos: IntArray) {
        while (pos[0] < expr.length && expr[pos[0]] == ' ') pos[0]++
    }

    private fun parseExpr(expr: String, pos: IntArray): Double {
        skipSpaces(expr, pos)
        var left = parseTerm(expr, pos)
        while (pos[0] < expr.length) {
            skipSpaces(expr, pos)
            if (pos[0] >= expr.length) break
            when (expr[pos[0]]) {
                '+' -> { pos[0]++; left += parseTerm(expr, pos) }
                '-' -> { pos[0]++; left -= parseTerm(expr, pos) }
                else -> break
            }
        }
        return left
    }

    private fun parseTerm(expr: String, pos: IntArray): Double {
        skipSpaces(expr, pos)
        var left = parseFactor(expr, pos)
        while (pos[0] < expr.length) {
            skipSpaces(expr, pos)
            if (pos[0] >= expr.length) break
            when (expr[pos[0]]) {
                '*' -> { pos[0]++; left *= parseFactor(expr, pos) }
                '/' -> {
                    pos[0]++
                    val right = parseFactor(expr, pos)
                    if (right == 0.0) return Double.POSITIVE_INFINITY
                    left /= right
                }
                else -> break
            }
        }
        return left
    }

    private fun parseFactor(expr: String, pos: IntArray): Double {
        skipSpaces(expr, pos)
        if (pos[0] < expr.length && expr[pos[0]] == '(') {
            pos[0]++
            val v = parseExpr(expr, pos)
            skipSpaces(expr, pos)
            if (pos[0] < expr.length && expr[pos[0]] == ')') pos[0]++
            return v
        }
        val start = pos[0]
        if (pos[0] < expr.length && (expr[pos[0]] == '+' || expr[pos[0]] == '-')) pos[0]++
        while (pos[0] < expr.length && (expr[pos[0]].isDigit() || expr[pos[0]] == '.')) pos[0]++
        val numStr = expr.substring(start, pos[0]).trim()
        return numStr.toDoubleOrNull() ?: throw NumberFormatException("Not a number: $numStr")
    }
}
