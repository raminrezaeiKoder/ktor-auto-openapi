package com.example.autoswagger

import io.ktor.server.application.*
import io.ktor.server.routing.*

internal data class PatternMatcher(val pattern: String, val regex: Regex)
internal data class OpKey(val method: String, val pattern: String)

/** Walks the routing tree and indexes routes for pattern matching and reverse lookups. */
internal class OperationIndex(private val app: Application, private val cfg: Config) {
    @Volatile private var matchers: List<PatternMatcher> = emptyList()
    @Volatile private var routesByKey: Map<OpKey, Route> = emptyMap()

    fun matchPattern(rawPath: String): String? {
        ensureBuilt()
        return matchers.firstOrNull { it.regex.matches(rawPath) }?.pattern
    }

    fun routeFor(method: String, pattern: String): Route? {
        ensureBuilt()
        return routesByKey[OpKey(method.uppercase(), pattern)]
    }

    private fun ensureBuilt() {
        if (matchers.isNotEmpty()) return
        synchronized(this) {
            if (matchers.isNotEmpty()) return
            val routing = app.pluginOrNull(Routing) ?: return
            val ms = mutableListOf<PatternMatcher>()
            val map = mutableMapOf<OpKey, Route>()
            for (r in routing.getAllRoutes()) {
                val pattern = buildPath(r)
                if (pattern == cfg.openApiPath || pattern.startsWith(cfg.swaggerUiPath)) continue
                ms += PatternMatcher(pattern, patternToRegex(pattern))
                val method = (r.selector as? HttpMethodRouteSelector)?.method?.value?.uppercase()
                if (method != null) map[OpKey(method, pattern)] = r
            }
            matchers = ms.distinctBy { it.pattern }
            routesByKey = map
        }
    }

    private fun patternToRegex(pattern: String): Regex {
        val r = pattern.split('/').joinToString("/", prefix = "^", postfix = "$") { seg ->
            when {
                seg == "{*}" -> "[^/]+"
                seg == "{**}" -> ".*"
                seg.startsWith("{") && seg.endsWith("}") -> "[^/]+"
                else -> Regex.escape(seg)
            }
        }
        return Regex(r)
    }
}

/* ----------------- Routing traversal & helpers (shared) ----------------- */

internal fun Routing.getAllRoutes(): List<Route> {
    val out = mutableListOf<Route>()
    fun visit(r: Route) { out += r; r.children.forEach(::visit) }
    visit(this)
    return out
}

internal fun buildPath(route: Route): String {
    val parts = mutableListOf<String>()
    var n: Route? = route
    while (n != null) {
        when (val s = n.selector) {
            is PathSegmentConstantRouteSelector -> parts += s.value
            is PathSegmentParameterRouteSelector -> parts += "{${s.name}}"
            is PathSegmentOptionalParameterRouteSelector -> parts += "{${s.name}}"
            is PathSegmentWildcardRouteSelector -> parts += "{*}"
            is PathSegmentTailcardRouteSelector -> parts += "{**}"
        }
        n = n.parent
    }
    val joined = parts.asReversed().filter { it.isNotBlank() }.joinToString("/")
    return "/$joined"
}

internal fun collectPathParams(route: Route): List<String> {
    val names = linkedSetOf<String>()
    var n: Route? = route
    while (n != null) {
        when (val s = n.selector) {
            is PathSegmentParameterRouteSelector -> names += s.name
            is PathSegmentOptionalParameterRouteSelector -> names += s.name
        }
        n = n.parent
    }
    return names.toList().reversed()
}

internal fun hasRequiredQueryOrHeader(route: Route): Boolean {
    var n: Route? = route
    while (n != null) {
        when (n.selector) {
            is ParameterRouteSelector,
            is ConstantParameterRouteSelector,
            is HttpHeaderRouteSelector -> return true
        }
        n = n.parent
    }
    return false
}
