package com.example.autoswagger

import kotlin.reflect.KType
import kotlin.reflect.full.createType
import kotlin.reflect.full.memberProperties
import kotlin.reflect.jvm.jvmErasure

data class OSchema(
    val type: String? = null,
    val format: String? = null,
    val nullable: Boolean? = null,
    val properties: Map<String, OSchema>? = null,
    val required: List<String>? = null,
    val items: OSchema? = null,
    val additionalProperties: OSchema? = null,
    val enumValues: List<String>? = null,
    val ref: String? = null
)

internal fun OSchema.toJson(): String = buildString {
    val fields = mutableListOf<String>()
    fun addField(name: String, value: String?) { if (value != null) fields += "\"$name\": $value" }
    fun addRaw(name: String, raw: String?) { if (raw != null) fields += "\"$name\": $raw" }

    addField("type", type?.quoted())
    addField("format", format?.quoted())
    if (nullable == true) addRaw("nullable", "true")

    properties?.let {
        val ps = it.entries.joinToString(prefix = "{", postfix = "}") { (k, s) -> k.quoted() + ": " + s.toJson() }
        addRaw("properties", ps)
    }
    required?.let { r -> addRaw("required", r.joinToString(prefix = "[", postfix = "]") { it.quoted() }) }
    items?.let { addRaw("items", it.toJson()) }
    additionalProperties?.let { addRaw("additionalProperties", it.toJson()) }
    enumValues?.let { e -> addRaw("enum", e.joinToString(prefix = "[", postfix = "]") { it.json().quoted() }) }
    ref?.let { addField("\$ref", it.quoted()) }
    append("{${fields.joinToString(", ")}}")
}

internal fun buildSchemaForType(type: KType, components: MutableMap<String, OSchema>): OSchema {
    val k = type.jvmErasure
    when (k) {
        String::class -> return OSchema(type = "string")
        Int::class -> return OSchema(type = "integer", format = "int32")
        Long::class -> return OSchema(type = "integer", format = "int64")
        Short::class, Byte::class -> return OSchema(type = "integer", format = "int32")
        Double::class -> return OSchema(type = "number", format = "double")
        Float::class -> return OSchema(type = "number", format = "float")
        Boolean::class -> return OSchema(type = "boolean")
        java.time.LocalDate::class -> return OSchema(type = "string", format = "date")
        java.time.Instant::class, java.time.OffsetDateTime::class ->
            return OSchema(type = "string", format = "date-time")
    }
    if (k.java.isEnum) {
        val values = k.java.enumConstants?.map { it.toString() } ?: emptyList()
        return OSchema(type = "string", enumValues = values, nullable = type.isMarkedNullable)
    }
    val nullable = type.isMarkedNullable
    if (k == List::class || k == Set::class || k.java.isArray) {
        val elemType = type.arguments.firstOrNull()?.type ?: String::class.createType()
        val item = buildSchemaForType(elemType, components)
        return OSchema(type = "array", items = item, nullable = nullable)
    }
    if (k == Map::class) {
        val valueType = type.arguments.getOrNull(1)?.type ?: String::class.createType()
        val valueSchema = buildSchemaForType(valueType, components)
        return OSchema(type = "object", additionalProperties = valueSchema, nullable = nullable)
    }
    val name = k.simpleName ?: "AnonymousType"
    if (!components.containsKey(name)) {
        val props = linkedMapOf<String, OSchema>()
        val required = mutableListOf<String>()
        for (p in k.memberProperties) {
            val pt = p.returnType
            val ps = buildSchemaForType(pt, components)
            props[p.name] = ps
            if (!pt.isMarkedNullable) required += p.name
        }
        val comp = OSchema(type = "object", properties = props, required = if (required.isNotEmpty()) required else null)
        components[name] = comp
    }
    return OSchema(ref = "#/components/schemas/$name", nullable = nullable)
}
