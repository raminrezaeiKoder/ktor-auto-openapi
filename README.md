# ktor-auto-openapi

Auto-generate an **OpenAPI 3.0 spec** and serve a polished, collapsible **Swagger UI** for your Ktor application.  
No YAML, no annotation soup — just your routes, inferred defaults, and a tiny DSL for the details.

---

## ✨ Features
- 🚀 Walks `Routing` tree → builds `paths`, params, request bodies, default responses.
- 📂 Two grouping modes:
  - `PATH_PREFIX` – nested groups by URL prefixes (default).
  - `MODULE_FILE` – one group per route file/module.
- 🔍 Observes real responses → merges status codes into docs.
- 🔐 Security schemes: Bearer (JWT) and/or API key header.
- 🧾 Schemas auto-generated from Kotlin types (with enums, lists, maps).
- 🖼 Swagger UI polish:
  - collapsible parents with caret icons  
  - “Collapse all / Expand all” toolbar  
  - per-endpoint tag headers hidden when groups exist  

---

## 📦 Installation
Add to your project:

```kotlin
repositories { mavenCentral() }

dependencies {
  implementation("io.ktor:ktor-server-core-jvm:2.3.13")
  implementation("io.ktor:ktor-server-netty-jvm:2.3.13")
  implementation("io.github.raminrezaeiKoder:ktor-auto-openapi:<version>")
}
```

Or include locally with composite builds:

```kotlin
includeBuild("../ktor-auto-openapi")
```

---

## 🚀 Quick Start

```kotlin
fun Application.module() {
  install(SwaggerAutoPlugin) {
    title = "My API"
    version = "1.0.0"
    openApiPath = "/openapi.json"
    swaggerUiPath = "/swagger"
    hierarchyMode = SwaggerAutoPlugin.HierarchyMode.PATH_PREFIX
    bearerAuth = true
    servers = listOf("http://localhost:8080")
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
        }
      }
    }
  }
}
```

- **Docs UI** → `http://localhost:8080/swagger`  
- **Spec JSON** → `http://localhost:8080/openapi.json`

---

## ⚙️ Configuration
Configurable via code **or** `application.conf`:

```hocon
ktor.swagger {
  openApiPath = "/openapi.json"
  swaggerUiPath = "/swagger"
  hierarchyMode = "module"   # path|prefix|module|file
  bearerAuth = true
  apiKeyHeaderName = "X-API-KEY"
  observeResponses = true
  servers = [ "http://localhost:8080", "https://api.example.com" ]
}
```

### Preset responses
```kotlin
preset("POST", "/v1/people", 201, 400, 409)
```

### Custom tags
```kotlin
tag("v1/air-waybills", "Endpoints under /v1/air-waybills")
```

### Custom module name (MODULE_FILE mode)
```kotlin
routing { module("PeopleRoutes.kt"); /* routes... */ }
```

---

## 📖 DSL Summary
- `doc { ... }` → attach docs to a route.
- `requestBodyJson<T>()` → auto schema from type.
- `jsonResponse<T>(status, description)`
- `response(status, description, contentType?)`

Schemas deduplicated into `components.schemas`.

---

## 📝 License
Apache 2.0.  
PRs and issues welcome!
