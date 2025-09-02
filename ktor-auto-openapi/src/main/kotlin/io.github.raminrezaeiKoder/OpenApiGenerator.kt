package com.example.autoswagger

import io.ktor.server.application.*
import io.ktor.server.routing.*

internal class OpenApiGenerator(private val app: Application, private val cfg: Config) {
    private val components = mutableMapOf<String, OSchema>()

    fun generate(): String {
        val routing = app.pluginOrNull(Routing) ?: return emptyRichSpec()

        val grouped: Map<String, List<Route>> = routing.getAllRoutes()
            .filter { r -> val p = buildPath(r); p != cfg.openApiPath && !p.startsWith(cfg.swaggerUiPath) }
            .groupBy { buildPath(it) }

        val pathsJson = buildPaths(grouped)

        // Collect operation tags depending on mode (unique)
        val opTags: Set<String> = buildSet {
            for ((path, routes) in grouped) {
                for (r in routes) {
                    val method = (r.selector as? HttpMethodRouteSelector)?.method?.value ?: continue
                    val tag = when (cfg.hierarchyMode) {
                        HierarchyMode.PATH_PREFIX -> containerTagFromPattern(path)
                        HierarchyMode.MODULE_FILE -> r.lookupModuleNameFromAttributesOrHandlers() ?: fallbackModuleFromPath(path)
                        HierarchyMode.NONE        -> "All"
                    }
                    add(tag)
                }
            }
        }

        val (tagsJson, tagGroupsJson) = buildTagsAndGroups(opTags)

        val schemasJson = buildComponentsSchemas()
        val securitySchemesJson = buildSecuritySchemes()
        val responsesJson = buildCommonResponses()
        val parametersJson = buildCommonParameters()
        val serversJson = buildServersJson()
        val infoJson = buildInfoJson()

        return buildString {
            appendLine("{")
            appendLine(""" "openapi": "3.0.3",""")
            appendLine(""" "info": $infoJson,""")
            serversJson?.let { appendLine(""" "servers": $it,""") }
            tagsJson?.let { appendLine(""" "tags": $it,""") }
            tagGroupsJson?.let { appendLine(""" "x-tagGroups": $it,""") }
            appendLine(""" "components": {""")
            val parts = mutableListOf<String>()
            securitySchemesJson?.let { parts += "\"securitySchemes\": $it" }
            schemasJson?.let { parts += "\"schemas\": $it" }
            responsesJson?.let { parts += "\"responses\": $it" }
            parametersJson?.let { parts += "\"parameters\": $it" }
            appendLine(" " + (parts.joinToString(",\n ")))
            appendLine(" },")
            appendLine(""" "paths": $pathsJson""")
            appendLine("}")
        }
    }

    private fun emptyRichSpec(): String {
        val infoJson = buildInfoJson()
        val serversJson = buildServersJson()
        val securitySchemesJson = buildSecuritySchemes()
        val responsesJson = buildCommonResponses()
        val parametersJson = buildCommonParameters()
        val schemasJson = buildComponentsSchemas()
        return buildString {
            appendLine("{")
            appendLine(""" "openapi": "3.0.3",""")
            appendLine(""" "info": $infoJson,""")
            serversJson?.let { appendLine(""" "servers": $it,""") }
            appendLine(""" "components": {""")
            val parts = mutableListOf<String>()
            securitySchemesJson?.let { parts += "\"securitySchemes\": $it" }
            schemasJson?.let { parts += "\"schemas\": $schemasJson" }
            responsesJson?.let { parts += "\"responses\": $responsesJson" }
            parametersJson?.let { parts += "\"parameters\": $parametersJson" }
            appendLine(" " + (parts.joinToString(",\n ")))
            appendLine(" },")
            appendLine(""" "paths": {}""")
            appendLine("}")
        }
    }

    private fun buildInfoJson(): String = buildString {
        val fields = mutableListOf<String>()
        fields += "\"title\": ${cfg.title.json().quoted()}"
        fields += "\"version\": ${cfg.version.json().quoted()}"
        cfg.description?.let { fields += "\"description\": ${it.json().quoted()}" }
        cfg.termsOfService?.let { fields += "\"termsOfService\": ${it.json().quoted()}" }
        if (cfg.contactName != null || cfg.contactUrl != null || cfg.contactEmail != null) {
            val c = buildList {
                cfg.contactName?.let { add("\"name\": ${it.json().quoted()}") }
                cfg.contactUrl?.let { add("\"url\": ${it.json().quoted()}") }
                cfg.contactEmail?.let { add("\"email\": ${it.json().quoted()}") }
            }.joinToString(", ")
            fields += "\"contact\": { $c }"
        }
        if (cfg.licenseName != null) {
            val l = buildList {
                add("\"name\": ${cfg.licenseName!!.json().quoted()}")
                cfg.licenseUrl?.let { add("\"url\": ${it.json().quoted()}") }
            }.joinToString(", ")
            fields += "\"license\": { $l }"
        }
        append("{ ${fields.joinToString(", ")} }")
    }

    private fun buildServersJson(): String? =
        cfg.servers.takeIf { it.isNotEmpty() }
            ?.joinToString(prefix = "[", postfix = "]") { url -> """{ "url": ${url.json().quoted()} }""" }

    private fun buildTagsAndGroups(allTags: Set<String>): Pair<String?, String?> {
        if (allTags.isEmpty()) return null to null

        val tagsJson = allTags.sorted().joinToString(prefix = "[", postfix = "]") { t ->
            val desc = cfg.tagDescriptions[t] ?: when (cfg.hierarchyMode) {
                HierarchyMode.PATH_PREFIX -> "Endpoints under /$t"
                HierarchyMode.MODULE_FILE -> "Endpoints from $t"
                HierarchyMode.NONE        -> if (t == "All") "All endpoints" else "Endpoints"
            }
            """{ "name": ${t.json().quoted()}, "description": ${desc.json().quoted()} }"""
        }

        val tagGroupsJson = when (cfg.hierarchyMode) {
            HierarchyMode.PATH_PREFIX -> {
                val prefixes: Set<String> = buildPrefixSet(allTags)
                prefixes
                    .sortedWith(compareBy({ it.count { ch -> ch == '/' } }, { it }))
                    .joinToString(prefix = "[", postfix = "]") { prefix ->
                        val kids = directChildTags(prefix, allTags).sorted()
                        """{ "name": ${prefix.json().quoted()}, "tags": [${kids.joinToString { it.json().quoted() }}] }"""
                    }
            }
            HierarchyMode.MODULE_FILE -> {
                allTags.sorted().joinToString(prefix = "[", postfix = "]") { m ->
                    """{ "name": ${m.json().quoted()}, "tags": [${m.json().quoted()}] }"""
                }
            }
            HierarchyMode.NONE -> null
        }

        return tagsJson to tagGroupsJson
    }

    private fun buildPaths(grouped: Map<String, List<Route>>): String = buildString {
        append("{")
        var i = 0
        for ((path, routes) in grouped) {
            val ops =
                routes.mapNotNull { r -> (r.selector as? HttpMethodRouteSelector)?.method?.value?.let { it.uppercase() to r } }
            if (ops.isEmpty()) continue
            if (i++ > 0) append(",")
            append("\n ${path.json().quoted()}: {")
            var j = 0
            for ((method, route) in ops) {
                if (j++ > 0) append(",")
                append("\n ${method.lowercase().quoted()}: ")
                val json = runCatching { operationJson(method, path, route) }.getOrElse {
                    """{ "responses": { "500": { "description": "Generation error" } } }"""
                }
                append(json)
            }
            append("\n }")
        }
        append("\n}")
    }

    private fun operationJson(method: String, pathPattern: String, route: Route): String {
        val m = method.uppercase()
        val inferred = inferDefaults(m, pathPattern, route)
        val userDoc: OperationDoc = route.attributes.getOrNull(OP_DOCS) ?: OperationDoc()
        val doc0 = combineDocs(inferred, userDoc)

        val tag = when (cfg.hierarchyMode) {
            HierarchyMode.PATH_PREFIX -> containerTagFromPattern(pathPattern)
            HierarchyMode.MODULE_FILE -> route.lookupModuleNameFromAttributesOrHandlers() ?: fallbackModuleFromPath(pathPattern)
            HierarchyMode.NONE        -> "All"
        }
        val doc = doc0.copy(tags = setOf(tag)) // single tag, never duplicates

        val pathParams = collectPathParams(route)
        val paramsJson = buildParamsJson(pathParams)
        val requestBodyJson = buildRequestBodyJson(doc.requestBody)
        val responsesJson = buildResponsesJson(m, pathPattern, doc)

        val parts = mutableListOf<String>()
        parts += "\"operationId\": ${operationId(m, pathPattern).json().quoted()}"
        doc.summary?.let { parts += "\"summary\": ${it.json().quoted()}" }
        doc.description?.let { parts += "\"description\": ${it.json().quoted()}" }
        if (doc.tags.isNotEmpty()) parts += "\"tags\": [${doc.tags.joinToString { it.json().quoted() }}]"
        if (paramsJson != null) parts += "\"parameters\": $paramsJson"
        if (requestBodyJson != null) parts += "\"requestBody\": $requestBodyJson"
        defaultSecurity()?.let { parts += "\"security\": $it" }
        parts += "\"responses\": $responsesJson"

        return "{${parts.joinToString(", ")}}"
    }

    private fun buildParamsJson(pathParams: List<String>): String? {
        if (pathParams.isEmpty()) return null
        val params = pathParams.map { n ->
            """{ "name": ${n.json().quoted()}, "in": "path", "required": true, "schema": { "type": "string" } }"""
        }
        return params.joinToString(prefix = "[", postfix = "]")
    }

    private fun buildRequestBodyJson(body: RequestBodyDoc?): String? {
        body ?: return null
        val content = body.content.entries.joinToString(prefix = "{", postfix = "}") { (ct, mt) ->
            "${ct.quoted()}: { \"schema\": ${schemaJson(mt.schema)} }"
        }
        return """{ "required": ${body.required}, "content": $content }"""
    }

    private fun buildResponsesJson(method: String, patternPath: String, doc: OperationDoc): String {
        val presetRaw = cfg.presetResponses["${method.uppercase()} $patternPath"]
        val preset: List<Int>? =
            presetRaw?.let { s -> buildSet { addAll(s); if (cfg.include500WhenObserved) add(500) }.sorted() }

        val list = mutableListOf<Pair<Int, String>>()
        when {
            !preset.isNullOrEmpty() -> preset.forEach { c -> list += c to statusText(c) }
            doc.responses.isNotEmpty() -> doc.responses.sortedBy { it.status }.forEach { r -> list += r.status to r.description }
            else -> {
                val success = when (method.uppercase()) { "POST" -> 201; "DELETE" -> 204; else -> 200 }
                list += success to statusText(success)
                if (patternPath.contains("{")) list += 400 to "Bad Request"
                list += 500 to "Internal Server Error"
            }
        }
        return list.joinToString(prefix = "{ ", postfix = " }", separator = ", ") { (code, desc) ->
            "\"$code\": { \"description\": ${desc.json().quoted()} }"
        }
    }

    private fun buildComponentsSchemas(): String? {
        if (components.isEmpty()) return "{}"
        return components.entries.joinToString(prefix = "{", postfix = "}") { (name, schema) ->
            name.quoted() + ": " + schema.toJson()
        }
    }

    private fun buildSecuritySchemes(): String? {
        val parts = mutableListOf<String>()
        if (cfg.bearerAuth) parts += """"bearerAuth": { "type": "http", "scheme": "bearer", "bearerFormat": "JWT" }"""
        cfg.apiKeyHeaderName?.let { header ->
            parts += """"apiKeyAuth": { "type": "apiKey", "in": "header", "name": ${header.json().quoted()} }"""
        }
        return if (parts.isEmpty()) null else parts.joinToString(prefix = "{", postfix = "}")
    }

    private fun buildCommonResponses(): String? {
        val body = """"application/json": { "schema": { "${'$'}ref": "#/components/schemas/Error" } }"""
        val mk = { code: Int, desc: String ->
            """"$code": { "description": ${desc.json().quoted()}, "content": { $body } }"""
        }
        val parts = listOf(
            mk(400, "Bad Request"), mk(401, "Unauthorized"), mk(403, "Forbidden"),
            mk(404, "Not Found"), mk(409, "Conflict"), mk(422, "Unprocessable Entity"),
            mk(500, "Internal Server Error")
        )
        if (!components.containsKey("Error")) {
            components["Error"] = OSchema(
                type = "object",
                properties = linkedMapOf(
                    "message" to OSchema(type = "string"),
                    "code" to OSchema(type = "string"),
                    "details" to OSchema(type = "object", additionalProperties = OSchema(type = "string"))
                ),
                required = listOf("message")
            )
        }
        return parts.joinToString(prefix = "{", postfix = "}")
    }

    private fun buildCommonParameters(): String? {
        val page = """"Page": { "name":"page", "in":"query", "required":false, "schema": { "type":"integer", "minimum":1 }, "description":"Page number (1-based)" }"""
        val size = """"Size": { "name":"size", "in":"query", "required":false, "schema": { "type":"integer", "minimum":1, "maximum":200 }, "description":"Page size" }"""
        return "{ $page, $size }"
    }

    private fun schemaJson(ref: SchemaRef): String = when (ref) {
        is SchemaRef.None -> """{ "type": "object" }"""
        is SchemaRef.Inline -> ref.schema.toJson()
        is SchemaRef.TypeRef -> buildSchemaForType(ref.type, components).toJson()
    }

    private fun inferDefaults(method: String, path: String, route: Route): OperationDoc {
        val pathParams = collectPathParams(route)
        val hasRequiredParams = pathParams.isNotEmpty() || hasRequiredQueryOrHeader(route)
        val success = when (method) { "POST" -> 201; "DELETE" -> 204; else -> 200 }
        val defaults = buildList {
            add(ResponseDoc(success, statusText(success), null))
            if (hasRequiredParams) add(ResponseDoc(400, "Bad Request", null))
            add(ResponseDoc(500, "Internal Server Error", null))
        }
        val defaultBody = if (method in listOf("POST", "PUT", "PATCH"))
            RequestBodyDoc(true, mapOf("application/json" to MediaTypeDoc(SchemaRef.none()))) else null

        return OperationDoc(
            summary = inferSummary(method, path),
            description = "${inferSummary(method, path)} endpoint.",
            tags = emptySet(), requestBody = defaultBody, responses = defaults
        )
    }

    private fun inferSummary(method: String, path: String): String {
        val nice = path.trim('/').split('/').joinToString(" ") { seg ->
            if (seg.startsWith("{") && seg.endsWith("}")) "by " + seg.removePrefix("{").removeSuffix("}") else seg
        }.replace(Regex("\\s+"), " ")
        val verb = when (method) {
            "GET" -> "Get"; "POST" -> "Create"; "PUT" -> "Replace"; "PATCH" -> "Update"; "DELETE" -> "Delete"
            else -> method.lowercase().replaceFirstChar { it.uppercase() }
        }
        return "$verb $nice".trim()
    }

    private fun defaultSecurity(): String? {
        val entries = mutableListOf<String>()
        if (cfg.bearerAuth) entries += """{ "bearerAuth": [] }"""
        cfg.apiKeyHeaderName?.let { entries += """{ "apiKeyAuth": [] }""" }
        return if (entries.isEmpty()) null else entries.joinToString(prefix = "[", postfix = "]")
    }

    private fun statusText(code: Int) = when (code) {
        200 -> "OK"; 201 -> "Created"; 204 -> "No Content"
        400 -> "Bad Request"; 401 -> "Unauthorized"; 403 -> "Forbidden"
        404 -> "Not Found"; 409 -> "Conflict"; 422 -> "Unprocessable Entity"
        500 -> "Internal Server Error"; 503 -> "Service Unavailable"
        else -> "Status $code"
    }
}
