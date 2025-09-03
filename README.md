# ktor-auto-openapi

Auto-generate an **OpenAPI 3.0** spec and serve a clean, collapsible **Swagger UI** for Ktor — using your real
`Routing` tree plus a tiny DSL (no YAML). This README matches the public API that exists in the code you shared.

---

## Features

- Walks Ktor `Routing` to build `paths`, params and sensible default responses.
- **Two grouping modes** for the UI’s tag hierarchy:
  - `PATH_PREFIX` (default): groups by constant URL prefixes (nested parents).
  - `MODULE_FILE`: groups by the route *file/module* (flat, one group per file).
- **Observed response codes**: merges real status codes sent by your app into the docs.
- **Security schemes**: Bearer (JWT) and/or API-key header.
- **Schemas** are generated from Kotlin types (enums, lists, sets, maps, nested objects).
- Polished UI: collapsible parents with carets, “Collapse all / Expand all”, hide noisy tag headers when groups exist.

---

## Requirements

- Kotlin **2.0.20**+
- Ktor **2.x** (server)
- JVM **11**+

---

## Installation

Add the library to your build (from your chosen repository) and Ktor server deps:

```kotlin
repositories { mavenCentral() /* + your repo if publishing privately */ }

dependencies {
  implementation("io.ktor:ktor-server-core-jvm:2.3.13")
  implementation("io.ktor:ktor-server-netty-jvm:2.3.13")
  implementation("io.github.raminrezaeiKoder:ktor-auto-openapi:<version>")
}
```

> During development you can use a composite build: `includeBuild("../ktor-auto-openapi")`.

---

## Quick start

```kotlin
import com.example.autoswagger.SwaggerAutoPlugin

fun Application.module() {
  install(SwaggerAutoPlugin) {
    // Required/visible info
    title = "My API"
    version = "1.0.0"
    description = "Auto OpenAPI for Ktor"

    // Where to serve the spec and UI
    openApiPath = "/openapi.json"
    swaggerUiPath = "/swagger"

    // Grouping behaviour
    hierarchyMode = SwaggerAutoPlugin.HierarchyMode.PATH_PREFIX // or MODULE_FILE

    // Security (optional)
    bearerAuth = true
    apiKeyHeaderName = null // e.g., "X-API-KEY"

    // Servers shown in the UI's Servers dropdown
    servers = listOf("http://localhost:8080")

    // Response-code observation (optional)
    observeResponses = true
    include500WhenObserved = false
  }

  routing {
    route("/v1/people") {
      get("/{id}") {
        doc {
          summary = "Get person"
          jsonResponse<Person>(200, "OK")
          response(404, "Not Found")
        }
      }
      post {
        doc {
          requestBodyJson<PersonCreateRequest>()
          jsonResponse<Person>(201, "Created")
          response(400, "Bad Request")
        }
      }
    }
  }
}
```

- **Docs UI** → `http://localhost:8080/swagger`  
- **Spec JSON** → `http://localhost:8080/openapi.json`  
- The UI bootstrap is served at `/<swaggerUiPath>/swagger-initializer.js` and core assets from the bundled resource folder (default: `swagger-ui`).

---

## Configuration reference (exact keys)

You can configure in code (as above) **or** via `application.conf` under `ktor.swagger`.

```hocon
ktor.swagger {
  # paths
  openApiPath = "/openapi.json"
  swaggerUiPath = "/swagger"
  assetsResourceFolder = "swagger-ui"  # folder in resources to serve

  # info
  title = "Ktor API"
  version = "1.0.0"
  description = "Auto generated docs"

  # security (all optional)
  bearerAuth = true                    # boolean (exact key)
  apiKeyHeaderName = "X-API-KEY"       # string, or omit

  # behavior
  observeResponses = true              # collect real status codes
  include500WhenObserved = false       # keep 500 in addition to observed

  # hierarchy mode (string)
  # accepted values that map to MODULE_FILE: "module", "file", "routefile", "module_file"
  # anything else → PATH_PREFIX (default)
  hierarchyMode = "path"               # "path"/"prefix" → PATH_PREFIX

  # rich info (all optional)
  termsOfService = "https://example.com/terms"
  contactName = "API Team"
  contactUrl = "https://example.com"
  contactEmail = "support@example.com"
  licenseName = "Apache-2.0"
  licenseUrl = "https://www.apache.org/licenses/LICENSE-2.0"
  servers = [ "http://localhost:8080", "https://api.example.com" ]
}
```

> **Exact mapping** in code: `hierarchyMode` is read as a string and normalized:  
> `"module" | "file" | "routefile" | "module_file"` → **MODULE_FILE**; otherwise **PATH_PREFIX**.

---

## DSL (document routes)

Attach docs directly to a `Route` with `doc { ... }`:

```kotlin
get("/{id}") {
  doc {
    summary = "Get person"
    description = "Fetch a single Person by id"
    // tags = setOf("people")  // optional; normally set by hierarchy
    jsonResponse<Person>(200, "OK")
    response(404, "Not Found")
  }
  // handler...
}
```

Helpers inside `doc`:

```kotlin
requestBody(                       // generic form
  contentType = "application/json",
  required = true,
  schema = SchemaRef.none()        // or SchemaRef.of<MyType>()
)

requestBodyJson<MyType>()          // JSON body with inferred schema
jsonResponse<MyType>(200, "OK")    // JSON response with inferred schema
response(204, "No Content")        // plain response (no body)
```

### Grouping by route file/module

- The plugin will try to infer a group name from the Kotlin `Kt` class of your handler lambda.
- You can **override** it per subtree:

```kotlin
routing {
  module("PeopleRoutes.kt")
  // ... your routes here
}
```

This only affects the `MODULE_FILE` mode.

### Preset responses for specific endpoints

Pin exact response codes (overrides inference/observation) using the **normalized** route pattern:

```kotlin
install(SwaggerAutoPlugin) {
  preset(method = "POST", pathPattern = "/v1/people", 201, 400, 409)
}
```

---

## Observed response codes (when `observeResponses = true`)

The plugin intercepts non-swagger responses and records status codes per `METHOD + pattern`.  
It also sends helpful headers on real responses:

- `X-Observed-For: <METHOD> <pattern>`
- `X-Observed-Codes: 200,404,...`
- `X-All-Codes: ...` (merged set used by the UI)

The Swagger initializer reads these headers and merges any codes not yet in the spec into the in-memory UI model.

---

## Generated OpenAPI outline

- `openapi: "3.0.3"`
- `info` → title, version, description, ToS, contact, license
- `servers` → from `servers`
- `tags` & `x-tagGroups` → structure depends on `hierarchyMode`
- `components`:
  - `schemas` → inferred from Kotlin types (deduplicated by simple class name)
  - `responses` → standard 400/401/403/404/409/422/500 with `Error` schema
  - `parameters` → `Page`, `Size`
  - `securitySchemes` → `bearerAuth`, `apiKeyAuth` if enabled
- `paths` → operations with `operationId`, `summary`, `description`, `tags`, `parameters`, `requestBody`, `responses`.

---

## Notes & gotchas

- **Servers list** is rendered once (the initializer served by the plugin avoids duplicates).
- When groups exist, per-operation tag headers are **hidden** to reduce noise. Parent group headings remain visible and collapsible.
- Schema names are derived from Kotlin simple class names; if you have duplicates across packages, consider wrappers or custom inline schemas.

---

## License

Apache-2.0. PRs and issues welcome!
