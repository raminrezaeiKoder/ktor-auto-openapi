ktor-auto-openapi

Auto-generate an OpenAPI 3.0 spec and ship a clean, collapsible Swagger UI for your Ktor server — with zero YAML and no annotation soup.

This library walks your Routing tree, infers sensible defaults, and gives you a small DSL to document the details that inference can’t know. You also get live response-code observation, hierarchical tag groups, and a module/file-based grouping mode when you prefer “one group per route file”.

Table of contents

Why this project?

Demo

Features

Requirements

Installation

Gradle (library dependency)

Local include / composite build

Quick start

Configuration

Hierarchy modes

Security

Servers

Preset responses

Tag descriptions

Documenting endpoints (DSL)

doc { ... }

Request/response helpers

Attach a custom “module/file” name

UI behavior & customizations

Observed response codes

Generated OpenAPI shape

Project layout

Troubleshooting

FAQ

Roadmap

Contributing

License

Why this project?

No YAML: generate spec from your Ktor routes.

Hierarchy that makes sense: choose between path-prefix groups or one group per route file.

Beautiful UI: collapsible parents, quiet tag headers, and a tidy toolbar with “Collapse all / Expand all”.

Reality-checked docs: automatically collects HTTP status codes actually sent by your app (opt-in).

Demo

Once installed, you’ll get:

OpenAPI JSON at /openapi.json (configurable)

Swagger UI at /swagger (configurable), using assets bundled in resources/swagger-ui plus a small bootstrap (swagger-initializer.js) and minimal CSS polish.

The UI starts collapsed, with caret icons on parent groups. When a tag group hierarchy is present, per-endpoint tag headers are hidden to reduce noise.

Features

🔎 Walks the Routing tree and infers:

path patterns, path params, required vs optional

default success/4xx/5xx responses

default request body for POST|PUT|PATCH

📁 Two grouping modes

PATH_PREFIX (default): groups by constant path prefixes (/v1/people, /v1/orders/... → nested parents)

MODULE_FILE: groups by route file (best when you separate routes into files/modules)

🧭 Observed response codes: add headers on real responses and merge the codes back into the spec/UI

🔐 Security: Bearer (JWT) and/or API key header

🧩 Simple DSL for summary/description/tags/request/response

🧱 Schema generation from Kotlin types (records enums, arrays, maps, nested objects)

🧰 Common components:

components.responses for typical 4xx/5xx with an Error schema

components.parameters for page/size

🖼 Clean, collapsible UI with tiny CSS, no theming framework required

⚙️ Everything configurable via code or application.conf

Requirements

Kotlin 2.0+

Ktor 2.x (server)

JVM 11+

Installation
Gradle (library dependency)
plugins {
  kotlin("jvm") version "2.0.20"
}

repositories {
  mavenCentral()
  // If you publish this library to your own repository, add it here.
}

dependencies {
  implementation("io.ktor:ktor-server-core-jvm:2.3.13")
  implementation("io.ktor:ktor-server-netty-jvm:2.3.13")
  // the library itself
  implementation("io.github.raminrezaeiKoder:ktor-auto-openapi:<version>")
}


If you haven’t published the library yet, use the local include / composite build
 approach during development.

Local include / composite build

If this repository and your app live side-by-side:

/your-app
/ktor-auto-openapi   <-- this repo


In your app’s settings.gradle.kts:

includeBuild("../ktor-auto-openapi")


Then:

dependencies {
  implementation("io.github.raminrezaeiKoder:ktor-auto-openapi")
}


Gradle will build and include the project directly.

Quick start

Install the plugin in your Ktor Application:

fun Application.module() {
  install(SwaggerAutoPlugin) {
    title = "My API"
    version = "1.0.0"
    description = "Auto-OpenAPI for Ktor"
    swaggerUiPath = "/swagger"
    openApiPath = "/openapi.json"

    // optional:
    hierarchyMode = SwaggerAutoPlugin.HierarchyMode.PATH_PREFIX
    bearerAuth = true
    servers = listOf("https://api.example.com", "http://localhost:8080")
  }

  routing {
    route("/v1/people") {
      get("/{id}") { /* ... */ }
      post { /* ... */ }
    }
  }
}


Run your server and open:

http://localhost:8080/swagger – UI

http://localhost:8080/openapi.json – spec

Configuration

You can set everything via the plugin block or application.conf at ktor.swagger.*.

ktor.swagger {
  openApiPath = "/openapi.json"
  swaggerUiPath = "/swagger"
  title = "Ktor API"
  version = "1.0.0"
  description = "Auto generated docs"
  assetsResourceFolder = "swagger-ui"

  # Security
  bearerAuth = true
  apiKeyHeaderName = "X-API-KEY"

  # Behavior
  observeResponses = true
  include500WhenObserved = false

  # Grouping
  hierarchyMode = "path"          # path|prefix|module|file|routefile

  # Rich info
  termsOfService = "https://example.com/terms"
  contactName = "API Team"
  contactUrl = "https://example.com"
  contactEmail = "support@example.com"
  licenseName = "Apache-2.0"
  licenseUrl = "https://www.apache.org/licenses/LICENSE-2.0"
  servers = [ "http://localhost:8080", "https://api.example.com" ]
}

Hierarchy modes

PATH_PREFIX (default): groups by constant path prefixes.

/v1/air-waybills/{id}
/v1/air-waybills/search
/v1/carriers/{id}


becomes

v1
  air-waybills
    (endpoints...)
  carriers
    (endpoints...)


MODULE_FILE: groups by “route file / module”.
The library uses reflection to infer the Kotlin Kt owner of your route handlers and turns that into a friendly <Name>.kt group, or you can set it explicitly (see Attach a custom “module/file” name
).

Security

Enable either/both:

bearerAuth = true
apiKeyHeaderName = "X-API-KEY"


This adds components.securitySchemes and default security to operations.

Servers

Provide servers = listOf("https://api.example.com", "http://localhost:8080") to render the standard Servers dropdown (only one servers block is rendered in the customized UI).

Preset responses

If you want to explicitly list codes (overriding inference & observation):

preset(method = "POST", pathPattern = "/v1/people", 201, 400, 409)


Patterns use the normalized route path (e.g., /v1/people/{id}).

Tag descriptions

You can add short descriptions for groups:

tag("v1/air-waybills", "Endpoints under /v1/air-waybills")

Documenting endpoints (DSL)

The plugin supplies a small DSL you can attach to Routes.

doc { ... }
routing {
  route("/v1/people") {
    get("/{id}") {
      doc {
        summary = "Get person by id"
        description = "Fetches a single Person"
        // optional: tags = setOf("people") – usually auto-assigned by hierarchy
        jsonResponse<Person>(200, "OK")
        response(404, "Not Found")
      }
      // handler...
    }
  }
}

Request/response helpers
doc {
  // Request body (defaults to application/json)
  requestBodyJson<PersonCreateRequest>(required = true)

  // Response with JSON schema
  jsonResponse<Person>(201, "Created")

  // Or explicit content type
  response(
    status = 204,
    description = "No Content",
    contentType = null // no body
  )
}


Schemas are generated from Kotlin types (including nested objects, lists, sets, maps, enums).
If a type appears in multiple places, it’s moved to components.schemas and referenced.

Attach a custom “module/file” name

When using MODULE_FILE grouping you can set the group name (usually your route file):

routing {
  module("PeopleRoutes.kt") {  // <- group name
    route("/v1/people") { /* ... */ }
  }
}

UI behavior & customizations

Collapsible parents with carets; “Collapse all” / “Expand all” toolbar.

When groups (tag hierarchy) exist, per-endpoint tag headers are hidden to reduce noise.

Minimal CSS polish is included in the HTML shell; you can replace or extend assets by editing the files under resources/swagger-ui:

pp-initializer.template.js → bootstraps Swagger UI (the plugin serves it as /swagger/swagger-initializer.js)

pp-swagger.css → UI tweaks

The plugin serves assets from assetsResourceFolder (defaults to swagger-ui). If you drop in a newer Swagger UI build, keep the file names expected by the shell (swagger-ui.css, swagger-ui-bundle.js, swagger-ui-standalone-preset.js, favicons…).

Observed response codes

If observeResponses = true, the plugin:

Intercepts actual responses (except for its own /swagger & /openapi.json assets)

Emits headers:

X-Observed-For: <METHOD> <pattern>

X-Observed-Codes: 200,404,...

X-All-Codes: ... (merged with presets or documented codes)

The Swagger UI bootstrap reads those headers and merges any new codes into the in-memory spec so your documentation reflects real behavior over time.

Use include500WhenObserved to force 500 to remain visible alongside observed codes.

Generated OpenAPI shape

openapi: 3.0.3

info: title/version/description/TOS/contact/license

servers: from servers list

tags and x-tagGroups:

PATH_PREFIX: nested groups by constant path segments

MODULE_FILE: one group per route file/module

components:

schemas: inferred from Kotlin types (deduplicated by simple name)

responses: standard 4xx/5xx with shared Error schema

parameters: Page, Size

securitySchemes: bearerAuth and/or apiKeyAuth if configured

paths: each operation has operationId, summary, description, tags, parameters, requestBody, responses, and default security if enabled

Project layout

Matches the screenshot:

ktor-auto-openapi/
  src/main/kotlin/io/github/raminrezaeiKoder/
    Config.kt
    DocsDsl.kt
    JsonAndTags.kt
    Observed.kt
    OpenApiGenerator.kt
    RoutingIndex.kt
    Schemas.kt
    SwaggerAutoPlugin.kt   <-- main plugin (shown in the issue)
  src/main/resources/swagger-ui/
    pp-initializer.template.js
    pp-swagger.css
  build.gradle.kts
  gradle.properties
  settings.gradle.kts
  LICENSE
  README.md


The single-file version you pasted (SwaggerAutoPlugin.kt) contains everything if you prefer a compact distribution.

Troubleshooting

UI shows two “Servers” lists
Older custom shells sometimes rendered a duplicate servers control. This library’s shell renders one servers dropdown. If you see two:

Clear browser cache (the initializer is served with Cache-Control: no-store)

Ensure you’re using the bundled swagger-initializer.js and not a stale custom one.

Groups don’t collapse / expand
The initializer now re-indexes group headings before bulk actions. Make sure the served swagger-initializer.js is the one from this project.

Endpoint appears under the wrong group

In MODULE_FILE mode, the library detects the owner class of your handler lambdas. If Kotlin generates odd names, call module("MyRoutes.kt") on the parent Route to pin the group.

Spec misses a response code

Turn on observeResponses = true and hit the endpoint; the UI will merge observed codes.

Or add a preset
 entry.

FAQ

Q: Can I hide the tag headers above endpoints?
A: Yes — when a tag hierarchy exists, the shell hides per-endpoint tag headers automatically (parents remain visible and collapsible).

Q: How do you decide default responses?
A: GET→200, POST→201, DELETE→204. If the path has params or required query/header, add 400. Always add 500 unless you’re using observed/preset codes.

Q: Do I have to write schemas?
A: No. Schemas are inferred from Kotlin types via reflection. You can still provide explicit SchemaRef.Inline if you want total control.

Q: Does it support OpenAPI YAML?
A: The spec is served as JSON at /openapi.json. You can convert to YAML in your CI if required.

Roadmap

 Optional dark theme CSS

 Pluggable schema naming strategy (avoid collisions across same simple names)

 More helpers for pagination/wrappers

 Export HTML/PDF doc from the spec

Contributing

Issues and PRs are welcome! Please:

Open an issue describing the change

Include a minimal repro if it’s a bug

Run ./gradlew build before sending a PR

License

This project is released under the Apache 2.0 License — see LICENSE
.

Appendix: Full example
fun Application.module() {
  install(SwaggerAutoPlugin) {
    title = "Air Cargo API"
    version = "1.2.0"
    description = "Auto-generated docs for our Ktor services"
    hierarchyMode = SwaggerAutoPlugin.HierarchyMode.MODULE_FILE
    bearerAuth = true
    servers = listOf("http://localhost:8080")
    // enforce exact codes for specific endpoints
    preset("POST", "/v1/air-waybills", 201, 400, 409)
  }

  routing {
    module("AirWaybillRoutes.kt")
    route("/v1/air-waybills") {
      post {
        doc {
          summary = "Create airwaybill"
          requestBodyJson<CreateMasterAirWaybillHttpRequest>()
          jsonResponse<MasterAirWaybill>(201, "Created")
          response(400, "Bad Request")
          response(409, "Conflict")
        }
        // handler ...
      }

      get("/{id}") {
        doc {
          summary = "Get airwaybill by id"
          jsonResponse<MasterAirWaybill>(200, "OK")
          response(404, "Not Found")
        }
        // handler ...
      }
    }
  }
}
