package com.example.autoswagger

enum class HierarchyMode { PATH_PREFIX, MODULE_FILE, NONE }

class Config {
    var openApiPath: String = "/openapi.json"
    var swaggerUiPath: String = "/swagger"
    var title: String = "Ktor API"
    var version: String = "1.0.0"
    var description: String? = null

    var assetsResourceFolder: String = "swagger-ui"

    // Security (optional)
    var bearerAuth: Boolean = false
    var apiKeyHeaderName: String? = null

    // Observe responses?
    var observeResponses: Boolean = true
    var include500WhenObserved: Boolean = false

    // Hierarchy
    var hierarchyMode: HierarchyMode = HierarchyMode.PATH_PREFIX

    // Rich info (optional)
    var termsOfService: String? = null
    var contactName: String? = null
    var contactUrl: String? = null
    var contactEmail: String? = null
    var licenseName: String? = null
    var licenseUrl: String? = null
    var servers: List<String> = emptyList()

    // Optional explicit tag descriptions (key = tag name)
    val tagDescriptions: MutableMap<String, String> = mutableMapOf()
    fun tag(tag: String, description: String) {
        tagDescriptions[tag] = description
    }

    // Presets: METHOD + pattern -> codes
    internal val presetResponses: MutableMap<String, Set<Int>> = mutableMapOf()
    fun preset(method: String, pathPattern: String, vararg codes: Int) {
        val key = "${method.uppercase()} ${normalizePath(pathPattern)}"
        presetResponses[key] = codes.toSet()
    }
}