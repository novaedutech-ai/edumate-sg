package sg.edumate.backend

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.descriptors.PolymorphicKind
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.SerialKind
import kotlinx.serialization.descriptors.StructureKind

/**
 * Builds a JSON Schema (for Claude structured outputs) from a kotlinx.serialization descriptor,
 * so the model's output schema always matches the app's data classes.
 * Every object lists all its properties as required and forbids extra properties.
 */
@OptIn(ExperimentalSerializationApi::class)
object JsonSchemas {
    fun of(descriptor: SerialDescriptor, exclude: Set<String> = emptySet()): Map<String, Any?> = schema(descriptor, exclude)

    private fun schema(d: SerialDescriptor, exclude: Set<String>): Map<String, Any?> {
        val base: Map<String, Any?> = when (val kind = d.kind) {
            PrimitiveKind.STRING, PrimitiveKind.CHAR -> mapOf("type" to "string")
            PrimitiveKind.INT, PrimitiveKind.LONG, PrimitiveKind.SHORT, PrimitiveKind.BYTE -> mapOf("type" to "integer")
            PrimitiveKind.FLOAT, PrimitiveKind.DOUBLE -> mapOf("type" to "number")
            PrimitiveKind.BOOLEAN -> mapOf("type" to "boolean")
            SerialKind.ENUM -> mapOf("type" to "string", "enum" to (0 until d.elementsCount).map { d.getElementName(it) })
            StructureKind.LIST -> mapOf("type" to "array", "items" to schema(d.getElementDescriptor(0), exclude))
            StructureKind.CLASS, StructureKind.OBJECT -> {
                val names = (0 until d.elementsCount).map { d.getElementName(it) }.filterNot { it in exclude }
                mapOf(
                    "type" to "object",
                    "properties" to names.associateWith { n -> schema(d.getElementDescriptor(d.getElementIndex(n)), exclude) },
                    "required" to names,
                    "additionalProperties" to false,
                )
            }
            StructureKind.MAP, is PolymorphicKind, SerialKind.CONTEXTUAL -> error("Unsupported in output schema: ${d.serialName} ($kind)")
        }
        return if (d.isNullable) mapOf("anyOf" to listOf(base, mapOf("type" to "null"))) else base
    }
}
