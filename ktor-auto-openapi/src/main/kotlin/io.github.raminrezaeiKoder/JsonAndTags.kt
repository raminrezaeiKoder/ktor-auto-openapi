package com.example.autoswagger

/* --- tiny JSON helpers --- */
internal fun String.quoted() = "\"$this\""
internal fun String.json(): String =
    this.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r")

/* --- tag tree helpers --- */
internal fun containerTagFromPattern(pathPattern: String): String {
    val parts = pathPattern.trim('/').split('/').filter { it.isNotBlank() }
    val isParamOrWildcard: (String) -> Boolean =
        { seg -> seg == "{*}" || seg == "{**}" || (seg.startsWith("{") && seg.endsWith("}")) }
    val constants = parts.takeWhile { seg -> !isParamOrWildcard(seg) }
    return if (constants.isEmpty()) "default" else constants.joinToString("/")
}

internal fun fallbackModuleFromPath(pathPattern: String): String =
    containerTagFromPattern(pathPattern).substringBefore('/').ifBlank { "module" } + ".kt"

internal fun buildPrefixSet(tags: Set<String>): Set<String> {
    val out = linkedSetOf<String>()
    for (t in tags) {
        val parts = t.split('/').filter { it.isNotBlank() }
        for (i in parts.indices) out += parts.take(i + 1).joinToString("/")
    }
    return out
}

internal fun directChildTags(prefix: String, allTags: Set<String>): List<String> {
    val pfx = if (prefix.isBlank()) "" else "$prefix/"
    return allTags.filter { t ->
        t.startsWith(pfx) && t.substring(pfx.length).let { rest -> rest.isNotBlank() && !rest.contains('/') }
    } + allTags.filter { it == prefix }
}

/* --- misc helpers used by generator --- */
internal fun operationId(method: String, path: String): String {
    val core = path.trim('/').split('/').joinToString("") { seg ->
        if (seg.startsWith("{") && seg.endsWith("}")) {
            "By" + seg.removePrefix("{").removeSuffix("}").replaceFirstChar { it.uppercase() }
        } else {
            seg.replace(Regex("[^A-Za-z0-9]"), "").replaceFirstChar { it.uppercase() }
        }
    }
    return method.lowercase() + core
}
