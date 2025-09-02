import de.undercouch.gradle.tasks.download.Download
import java.net.URL

plugins {
    kotlin("jvm") version "2.0.20"
    `maven-publish`
    id("de.undercouch.download") version "5.6.0"
}

group = "io.github.raminrezaeiKoder"
version = "1.0.0"

repositories {
    mavenCentral()
}

kotlin { jvmToolchain(11) }

// Also produce -sources.jar and -javadoc.jar for consumers
java {
    withSourcesJar()
    withJavadocJar()
}

/* ---------- Dependencies (library) ---------- */
val ktorVersion = "2.3.13"
dependencies {
    api("io.ktor:ktor-server-core-jvm:$ktorVersion")

    // Only needed for your own demo/testing; safe to keep as implementation
    implementation("io.ktor:ktor-server-netty-jvm:$ktorVersion")
    implementation("ch.qos.logback:logback-classic:1.4.14")

    testImplementation("io.ktor:ktor-server-tests-jvm:$ktorVersion")
    testImplementation(kotlin("test"))
}

/* ---------- Swagger UI download & embed ---------- */
val swaggerUiVersion = "5.11.0"
val swaggerZip = layout.buildDirectory.file("downloads/swagger-ui-$swaggerUiVersion.zip")
val swaggerExtract = layout.buildDirectory.dir("swagger-ui-$swaggerUiVersion")

tasks.register<Download>("downloadSwaggerUi") {
    src("https://github.com/swagger-api/swagger-ui/archive/refs/tags/v$swaggerUiVersion.zip")
    dest(swaggerZip.get().asFile)
    onlyIfModified(true)
}

val unzipSwaggerUi by tasks.registering(Copy::class) {
    dependsOn("downloadSwaggerUi")
    from(zipTree(swaggerZip))
    into(swaggerExtract)
}

val generateInitializer by tasks.registering(Copy::class) {
    from("src/main/resources/swagger-ui/pp-initializer.template.js")
    // Simple token replace:
    filter { line: String -> line.replace("__SPEC_URL__", "/openapi.json") }
    rename { it.replace(".template", "") } // -> pp-initializer.js
    into(layout.buildDirectory.dir("generated-resources/swagger-ui"))
}

tasks.processResources {
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    dependsOn(unzipSwaggerUi, generateInitializer)

    val distDir = swaggerExtract.get().dir("swagger-ui-$swaggerUiVersion/dist")
    from(distDir) { into("swagger-ui") }

    // bring generated pp-initializer.js
    from(layout.buildDirectory.dir("generated-resources/swagger-ui")) { into("swagger-ui") }

    // keep the original template out of the final jar
    exclude("swagger-ui/pp-initializer.template.js")
}

/* ---------- Publishing ---------- */
publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            from(components["java"])

            groupId = project.group.toString()                // io.github.raminrezaeiKoder
            artifactId = "ktor-auto-openapi"
            version = project.version.toString()

            pom {
                name.set("ktor-auto-openapi")
                description.set("Ktor auto OpenAPI generator with embedded Swagger UI")
                url.set("https://github.com/raminrezaeiKoder/ktor-auto-openapi")
                licenses {
                    license {
                        name.set("Apache License, Version 2.0")
                        url.set("https://www.apache.org/licenses/LICENSE-2.0")
                    }
                }
                developers {
                    developer {
                        id.set("raminrezaeiKoder")
                        name.set("Ramin Rezaei")
                    }
                }
                scm {
                    url.set("https://github.com/raminrezaeiKoder/ktor-auto-openapi")
                    connection.set("scm:git:https://github.com/raminrezaeiKoder/ktor-auto-openapi.git")
                    developerConnection.set("scm:git:ssh://git@github.com/raminrezaeiKoder/ktor-auto-openapi.git")
                    tag.set("v$version")
                }
            }
        }
    }
    repositories {
        // Local: ./gradlew publishToMavenLocal
        mavenLocal()

        // GitHub Packages (set GITHUB_ACTOR and GITHUB_TOKEN in env/Gradle properties)
        maven {
            name = "GitHubPackages"
            url = uri("https://maven.pkg.github.com/raminrezaeiKoder/ktor-auto-openapi")
            credentials {
                username = System.getenv("GITHUB_ACTOR") ?: findProperty("gpr.user") as String? ?: "USERNAME"
                password = System.getenv("GITHUB_TOKEN") ?: findProperty("gpr.key") as String? ?: "TOKEN"
            }
        }
    }
}
