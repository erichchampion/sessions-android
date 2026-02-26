package com.sessions_ai.tools

/**
 * Converts numeric values between units (length, mass, temperature).
 * Matches iOS UnitConversionTool behavior for parity.
 */
class UnitConversionTool : Tool {

    override val name = "unit_conversion"
    override val description = "Use when the user asks to convert units (e.g. 'miles to km', 'celsius to fahrenheit', 'pounds to kg'). Args: value (number), from_unit, to_unit. Supported: miles, km, m, feet, inches; celsius, fahrenheit, kelvin; kg, pounds, grams. Example: 'convert 5 miles to km' → value=5, from_unit=\"miles\", to_unit=\"km\"."
    override val schema = mapOf(
        "type" to "object",
        "properties" to mapOf(
            "value" to mapOf("type" to "number"),
            "from_unit" to mapOf("type" to "string"),
            "to_unit" to mapOf("type" to "string")
        ),
        "required" to listOf("value", "from_unit", "to_unit")
    )

    override suspend fun execute(args: Map<String, Any>): String {
        val valueObj = args["value"] ?: return "Error: missing or invalid \"value\" for unit_conversion."
        val value = when (valueObj) {
            is Number -> valueObj.toDouble()
            is String -> valueObj.toDoubleOrNull()
            else -> null
        } ?: return "Error: missing or invalid \"value\" for unit_conversion."
        val fromStr = (args["from_unit"] as? String) ?: (args["from"] as? String)?.takeIf { it.isNotBlank() }
            ?: return "Error: missing \"from_unit\" or \"to_unit\" for unit_conversion."
        val toStr = (args["to_unit"] as? String) ?: (args["to"] as? String)?.takeIf { it.isNotBlank() }
            ?: return "Error: missing \"from_unit\" or \"to_unit\" for unit_conversion."
        val from = parseUnit(fromStr.trim().lowercase()) ?: return "Error: unsupported or invalid unit. Use e.g. km, miles, m, feet, celsius, fahrenheit, kg, pounds."
        val to = parseUnit(toStr.trim().lowercase()) ?: return "Error: unsupported or invalid unit. Use e.g. km, miles, m, feet, celsius, fahrenheit, kg, pounds."
        if (from.type != to.type) return "Error: cannot convert between different unit types (e.g. length vs temperature)."
        val converted = from.convert(value, to)
        val formatted = if (converted == converted.toLong().toDouble()) "${converted.toLong()}" else "%.4g".format(converted)
        return "Result: $formatted $toStr"
    }

    private enum class UnitType { LENGTH, MASS, TEMPERATURE }
    private data class Unit(val type: UnitType, val toBase: (Double) -> Double, val fromBase: (Double) -> Double)
    private fun parseUnit(s: String): Unit? = when (s) {
        "km", "kilometers", "kilometres" -> Unit(UnitType.LENGTH, { it * 1000 }, { it / 1000 })
        "m", "meters", "metres" -> Unit(UnitType.LENGTH, { it }, { it })
        "miles", "mi" -> Unit(UnitType.LENGTH, { it * 1609.344 }, { it / 1609.344 })
        "feet", "ft" -> Unit(UnitType.LENGTH, { it * 0.3048 }, { it / 0.3048 })
        "inches", "in" -> Unit(UnitType.LENGTH, { it * 0.0254 }, { it / 0.0254 })
        "kg", "kilograms" -> Unit(UnitType.MASS, { it }, { it })
        "pounds", "lbs", "lb" -> Unit(UnitType.MASS, { it * 0.453592 }, { it / 0.453592 })
        "grams", "g" -> Unit(UnitType.MASS, { it / 1000 }, { it * 1000 })
        "celsius", "c" -> Unit(UnitType.TEMPERATURE, { it }, { it })
        "fahrenheit", "f" -> Unit(UnitType.TEMPERATURE, { (it - 32) * 5 / 9 }, { it * 9 / 5 + 32 })
        "kelvin", "k" -> Unit(UnitType.TEMPERATURE, { it - 273.15 }, { it + 273.15 })
        else -> null
    }
    private fun Unit.convert(value: Double, to: Unit): Double = to.fromBase(this.toBase(value))
}
