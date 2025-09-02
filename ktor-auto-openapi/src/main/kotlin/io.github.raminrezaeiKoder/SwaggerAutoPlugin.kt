package com.example.autoswagger

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.http.content.*
import io.ktor.server.response.*
import io.ktor.server.request.*
import io.ktor.server.routing.*
import io.ktor.util.*
import java.nio.charset.StandardCharsets

object SwaggerAutoPlugin {

    /* ─────────────────────────────── Install ─────────────────────────────── */

    val Instance = createApplicationPlugin(name = "SwaggerAutoPlugin", createConfiguration = ::Config) {
        val cfg = pluginConfig
        val conf = application.environment.config

        // Optional application.conf overrides
        runCatching { conf.config("ktor.swagger") }.onSuccess { c ->
            c.propertyOrNull("openApiPath")?.getString()?.let { cfg.openApiPath = ensureSlash(it) }
            c.propertyOrNull("swaggerUiPath")?.getString()?.let { cfg.swaggerUiPath = ensureSlash(it) }
            c.propertyOrNull("title")?.getString()?.let { cfg.title = it }
            c.propertyOrNull("version")?.getString()?.let { cfg.version = it }
            c.propertyOrNull("description")?.getString()?.let { cfg.description = it }
            c.propertyOrNull("assetsResourceFolder")?.getString()?.let { cfg.assetsResourceFolder = it }

            c.propertyOrNull("bearerAuth")?.getString()?.toBooleanStrictOrNull()?.let { cfg.bearerAuth = it }
            c.propertyOrNull("apiKeyHeaderName")?.getString()?.let { cfg.apiKeyHeaderName = it }

            c.propertyOrNull("observeResponses")?.getString()?.toBooleanStrictOrNull()
                ?.let { cfg.observeResponses = it }
            c.propertyOrNull("include500WhenObserved")?.getString()?.toBooleanStrictOrNull()
                ?.let { cfg.include500WhenObserved = it }

            // Hierarchy mode aliases
            c.propertyOrNull("hierarchyMode")?.getString()?.let { v ->
                cfg.hierarchyMode = when (v.trim().lowercase()) {
                    "module", "file", "routefile", "module_file" -> HierarchyMode.MODULE_FILE
                    "none", "normal", "flat", "simple"           -> HierarchyMode.NONE
                    "path", "prefix", "path_prefix"              -> HierarchyMode.PATH_PREFIX
                    else                                         -> HierarchyMode.PATH_PREFIX
                }
            }

            // Rich info
            c.propertyOrNull("termsOfService")?.getString()?.let { cfg.termsOfService = it }
            c.propertyOrNull("contactName")?.getString()?.let { cfg.contactName = it }
            c.propertyOrNull("contactUrl")?.getString()?.let { cfg.contactUrl = it }
            c.propertyOrNull("contactEmail")?.getString()?.let { cfg.contactEmail = it }
            c.propertyOrNull("licenseName")?.getString()?.let { cfg.licenseName = it }
            c.propertyOrNull("licenseUrl")?.getString()?.let { cfg.licenseUrl = it }
            c.propertyOrNull("servers")?.getList()?.let { values -> cfg.servers = values }
        }

        cfg.openApiPath = ensureSlash(cfg.openApiPath)
        cfg.swaggerUiPath = ensureSlash(cfg.swaggerUiPath)
        val uiBase = cfg.swaggerUiPath.trimEnd('/')
        val initializerPath = "$uiBase/swagger-initializer.js"

        // Route/operation index
        val opIndex = OperationIndex(application, cfg)

        // Observe final statuses
        if (cfg.observeResponses) {
            application.sendPipeline.intercept(ApplicationSendPipeline.After) {
                val method = call.request.httpMethod.value.uppercase()
                val rawPath = call.request.path()
                val isSwaggerAsset =
                    rawPath == cfg.openApiPath || rawPath == cfg.swaggerUiPath || rawPath.startsWith(cfg.swaggerUiPath + "/")
                if (isSwaggerAsset) return@intercept

                val status = call.response.status()?.value ?: HttpStatusCode.OK.value
                val pattern = opIndex.matchPattern(rawPath) ?: rawPath
                Observed.add(method, pattern, status)

                val route = opIndex.routeFor(method, pattern)
                val allCodes = computeEffectiveCodes(cfg, method, pattern, route)

                call.response.headers.append("X-Observed-For", "$method $pattern")
                call.response.headers.append("X-Observed-Codes", Observed.get(method, pattern).sorted().joinToString(","))
                call.response.headers.append("X-All-Codes", allCodes.sorted().joinToString(","))
            }
        }

        application.routing {
            // HTML shell
            get(cfg.swaggerUiPath) { call.respondText(swaggerHtml(cfg.title, uiBase), ContentType.Text.Html) }
            get("$uiBase/") { call.respondText(swaggerHtml(cfg.title, uiBase), ContentType.Text.Html) }

            // JS bootstrap (load template from resources and inject SPEC URL)
            get(initializerPath) {
                val templatePath = cfg.assetsResourceFolder.trimEnd('/') + "/pp-initializer.template.js"
                val raw = readResourceText(templatePath)
                if (raw == null) {
                    call.respond(HttpStatusCode.InternalServerError, "Missing resource: $templatePath")
                    return@get
                }
                val js = raw.replace("__SPEC_URL__", cfg.openApiPath)
                call.response.headers.append(HttpHeaders.CacheControl, "no-store")
                call.respondText(js, ContentType.parse("application/javascript"))
            }

            // Static assets
            staticResources(cfg.swaggerUiPath, cfg.assetsResourceFolder, index = "index.html")

            // Spec
            get(cfg.openApiPath) {
                val json = OpenApiGenerator(application, cfg).generate()
                call.response.headers.append(HttpHeaders.CacheControl, "no-store")
                call.respondText(json, ContentType.Application.Json)
            }
        }
    }

    private fun swaggerHtml(title: String, uiBase: String): String = buildString {
        appendLine("<!doctype html>")
        appendLine("<html><head><meta charset=\"utf-8\"/>")
        appendLine("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\"/>")
        appendLine("<title>${title.html()}</title>")
        appendLine("<link rel=\"stylesheet\" href=\"$uiBase/swagger-ui.css\" />")
        appendLine("<link rel=\"stylesheet\" href=\"$uiBase/pp-swagger.css\" />")
        appendLine("<link rel=\"icon\" type=\"image/png\" href=\"$uiBase/favicon-32x32.png\" sizes=\"32x32\" />")
        appendLine("<link rel=\"icon\" type=\"image/png\" href=\"$uiBase/favicon-16x16.png\" sizes=\"16x16\" />")
        appendLine("</head><body>")
        appendLine("<div id=\"swagger-ui\"></div>")
        appendLine("<script src=\"$uiBase/swagger-ui-bundle.js\"></script>")
        appendLine("<script src=\"$uiBase/swagger-ui-standalone-preset.js\"></script>")
        /* Use your initializer instead of the stock one */
        appendLine("<script src=\"$uiBase/pp-initializer.js\"></script>")
        appendLine("</body></html>")
    }

    private fun readResourceText(path: String): String? =
        Thread.currentThread().contextClassLoader
            .getResourceAsStream(path)
            ?.bufferedReader(StandardCharsets.UTF_8)
            ?.use { it.readText() }
}

/* -------------------------- Shared utilities -------------------------- */

internal fun ensureSlash(path: String) = if (path.startsWith("/")) path else "/$path"
internal fun normalizePath(path: String) = ensureSlash(path)

internal fun String.html(): String = this
    .replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
    .replace("\"", "&quot;").replace("'", "&#39;")

internal fun String.asJsString(): String = buildString {
    append('"')
    for (ch in this@asJsString) {
        when (ch) {
            '\\' -> append("\\\\")
            '"' -> append("\\\"")
            '\n' -> append("\\n")
            '\r' -> append("\\r")
            '\t' -> append("\\t")
            else -> append(ch)
        }
    }
    append('"')
}

/* Effective code set (used by response observer) */
internal fun computeEffectiveCodes(
    cfg: Config,
    method: String,
    pattern: String,
    route: Route?
): Set<Int> {
    val upper = method.uppercase()
    val preset: Set<Int> = cfg.presetResponses["$upper $pattern"] ?: emptySet()
    val docCodes: Set<Int> = route?.attributes?.getOrNull(OP_DOCS)?.responses?.map { it.status }?.toSet() ?: emptySet()
    val observed: Set<Int> = Observed.get(upper, pattern)

    if (preset.isNotEmpty()) return buildSet { addAll(preset); if (cfg.include500WhenObserved) add(500) }
    if (observed.isNotEmpty()) return buildSet { addAll(observed); if (cfg.include500WhenObserved) add(500) }
    if (docCodes.isNotEmpty()) return docCodes

    val success = when (upper) { "POST" -> 201; "DELETE" -> 204; else -> 200 }
    val hasRequired = route?.let { hasRequiredQueryOrHeader(it) || collectPathParams(it).isNotEmpty() } ?: pattern.contains("{")
    return buildSet {
        add(success)
        if (hasRequired) add(400)
        add(500)
    }
}
