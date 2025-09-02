package com.example.autoswagger

import io.ktor.server.routing.*
import io.ktor.util.*
import kotlin.reflect.KType
import kotlin.reflect.typeOf

val OP_DOCS: AttributeKey<OperationDoc> = AttributeKey("__swagger_op_docs__")
private val MODULE_NAME_KEY: AttributeKey<String> = AttributeKey("__swagger_module_name__")

/** Optional: set a custom module/file name for this sub-tree. */
fun Route.module(name: String): Route {
    attributes.put(MODULE_NAME_KEY, name)
    return this
}

fun Route.doc(block: OperationDoc.Builder.() -> Unit): Route {
    val built = OperationDoc.Builder().apply(block).build()
    val existing: OperationDoc? = attributes.getOrNull(OP_DOCS)
    val merged = if (existing != null) combineDocs(existing, built) else built
    attributes.put(OP_DOCS, merged)
    return this
}

internal fun combineDocs(base: OperationDoc, override: OperationDoc): OperationDoc =
    OperationDoc(
        summary = override.summary ?: base.summary,
        description = override.description ?: base.description,
        tags = if (override.tags.isNotEmpty()) override.tags else base.tags,
        requestBody = override.requestBody ?: base.requestBody,
        responses = if (override.responses.isNotEmpty()) override.responses else base.responses
    )

data class OperationDoc(
    val summary: String? = null,
    val description: String? = null,
    val tags: Set<String> = emptySet(),
    val requestBody: RequestBodyDoc? = null,
    val responses: List<ResponseDoc> = emptyList()
) {
    class Builder {
        var summary: String? = null
        var description: String? = null
        var tags: Set<String> = emptySet()
        private var requestBody: RequestBodyDoc? = null
        private val responses: MutableList<ResponseDoc> = mutableListOf()

        fun requestBody(
            contentType: String = "application/json",
            required: Boolean = true,
            schema: SchemaRef = SchemaRef.none()
        ) {
            requestBody = RequestBodyDoc(required, mapOf(contentType to MediaTypeDoc(schema)))
        }

        @OptIn(ExperimentalStdlibApi::class)
        inline fun <reified T : Any> requestBodyJson(required: Boolean = true) =
            requestBody("application/json", required, SchemaRef.of<T>())

        fun response(
            status: Int,
            description: String,
            contentType: String? = null,
            schema: SchemaRef = SchemaRef.none()
        ) {
            if (contentType == null) responses += ResponseDoc(status, description, null)
            else responses += ResponseDoc(status, description, mapOf(contentType to MediaTypeDoc(schema)))
        }

        @OptIn(ExperimentalStdlibApi::class)
        inline fun <reified T : Any> jsonResponse(status: Int, description: String) =
            response(status, description, "application/json", SchemaRef.of<T>())

        fun build(): OperationDoc = OperationDoc(summary, description, tags, requestBody, responses.toList())
    }
}

data class RequestBodyDoc(val required: Boolean, val content: Map<String, MediaTypeDoc>)
data class ResponseDoc(val status: Int, val description: String, val content: Map<String, MediaTypeDoc>?)
data class MediaTypeDoc(val schema: SchemaRef)

/* For MODULE_FILE mode */
internal fun Route.lookupModuleNameFromAttributesOrHandlers(): String? {
    // explicit override on this route or ancestors
    var n: Route? = this
    while (n != null) {
        n.attributes.getOrNull(MODULE_NAME_KEY)?.let { return sanitizeModule(it) }
        n = n.parent
    }
    // infer from any handler lambda on this route (or nearest ancestor that has a handler)
    return detectOwnerFromHandlers(this)?.let(::sanitizeModule)
}

private fun sanitizeModule(raw: String): String {
    var name = raw.substringAfterLast('.')
    name = name.replace(Regex("Kt\\$.*"), "Kt")
    if (name.endsWith("Kt")) name = name.removeSuffix("Kt") + ".kt"
    return when {
        name.isBlank() -> "module"
        name.endsWith(".kt") -> name
        else -> "$name.kt"
    }
}

/** Try to locate any lambda class recorded in the route pipeline. */
private fun detectOwnerFromHandlers(route: Route): String? {
    fun ownersFromHandlers(r: Route): List<String> {
        val owners = mutableListOf<String>()
        val field = runCatching { r.javaClass.getDeclaredField("handlers") }.getOrNull() ?: return owners
        field.isAccessible = true
        val list = (field.get(r) as? Iterable<*>) ?: return owners
        for (h in list) {
            if (h != null) {
                owners += h.javaClass.name
                h.javaClass.enclosingClass?.name?.let { owners += it }
            }
        }
        return owners
    }

    fun scanOne(r: Route): String? {
        ownersFromHandlers(r).let { if (it.isNotEmpty()) return it.firstOrNull { n -> n.contains("Kt$") } ?: it.first() }
        r.children.sortedBy { (it.selector is HttpMethodRouteSelector).not() }.forEach { child ->
            ownersFromHandlers(child).let { if (it.isNotEmpty()) return it.firstOrNull { n -> n.contains("Kt$") } ?: it.first() }
        }
        return null
    }

    scanOne(route)?.let { return it }
    var a = route.parent
    while (a != null) { scanOne(a)?.let { return it }; a = a.parent }
    return null
}

/* schema refs used by DSL */
sealed class SchemaRef {
    data class Inline(val schema: OSchema) : SchemaRef()
    data class TypeRef(val type: KType) : SchemaRef()
    object None : SchemaRef()
    companion object {
        fun none(): SchemaRef = None
        @OptIn(ExperimentalStdlibApi::class)
        inline fun <reified T : Any> of(): SchemaRef = TypeRef(typeOf<T>())
    }
}
