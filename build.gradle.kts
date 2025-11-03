plugins {
    kotlin("multiplatform") version "1.9.22"
    kotlin("plugin.serialization") version "1.9.22"
    application
}

group = "com.aichat"
version = "1.0.0"

application {
    mainClass.set("app.ApplicationKt")
}

kotlin {
    jvmToolchain(17)

    js(IR) {
        browser {
            commonWebpackConfig {
                cssSupport {
                    enabled.set(true)
                }
            }
            binaries.executable()
        }
    }

    jvm {
        withJava()
        compilations.all {
            kotlinOptions {
                jvmTarget = "17"
            }
        }
    }

    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")
                implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.2")
            }
        }

        val jsMain by getting {
            dependencies {
                implementation("org.jetbrains.kotlin-wrappers:kotlin-browser:1.0.0-pre.634")
            }
        }

        val jvmMain by getting {
            dependencies {
                // Ktor Server
                implementation("io.ktor:ktor-server-core:2.3.7")
                implementation("io.ktor:ktor-server-netty:2.3.7")
                implementation("io.ktor:ktor-server-content-negotiation:2.3.7")
                implementation("io.ktor:ktor-serialization-kotlinx-json:2.3.7")
                implementation("io.ktor:ktor-server-cors:2.3.7")
                implementation("io.ktor:ktor-server-auth:2.3.7")
                implementation("io.ktor:ktor-server-auth-jwt:2.3.7")

                // Ktor Client (for Claude/DeepSeek API)
                implementation("io.ktor:ktor-client-core:2.3.7")
                implementation("io.ktor:ktor-client-cio:2.3.7")
                implementation("io.ktor:ktor-client-content-negotiation:2.3.7")

                // Database
                implementation("org.jetbrains.exposed:exposed-core:0.44.1")
                implementation("org.jetbrains.exposed:exposed-dao:0.44.1")
                implementation("org.jetbrains.exposed:exposed-jdbc:0.44.1")
                implementation("org.jetbrains.exposed:exposed-java-time:0.44.1")
                implementation("org.postgresql:postgresql:42.6.0")
                implementation("com.zaxxer:HikariCP:5.0.1")

                // Logging
                implementation("ch.qos.logback:logback-classic:1.4.11")

                // Password hashing
                implementation("org.mindrot:jbcrypt:0.4")
            }
        }
    }
}

// Create fat JAR with manifest
tasks.register<Jar>("fatJar") {
    group = "build"
    manifest {
        attributes["Main-Class"] = "app.ApplicationKt"
    }
    archiveBaseName.set("ai-chat-jvm")
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    from(configurations.getByName("jvmRuntimeClasspath").map { if (it.isDirectory) it else zipTree(it) })
    with(tasks.getByName("jvmJar") as CopySpec)
}

tasks.named("jvmJar") {
    manifest {
        attributes["Main-Class"] = "app.ApplicationKt"
    }
}
