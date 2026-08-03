import java.util.concurrent.TimeUnit

plugins {
    java
    id("com.gradleup.shadow") version "9.6.1"
}

group = "io.github.skyblueheat.echoclaims"
version = "0.1.0-SNAPSHOT"

val paperApiVersion = project.property("paperApiVersion").toString()
val sqliteVersion = project.property("sqliteVersion").toString()
val junitVersion = project.property("junitVersion").toString()

val pluginVersion = version.toString()

// Paper server jar for integration tests (build 92 = stable for 26.2)
val paperServerUrl = "https://fill-data.papermc.io/v1/objects/059d00bbce0fa1707739618b3276f5c80b9655dc0f964306fa799a9c7cb01cc2/paper-26.2-92.jar"

repositories {
    mavenCentral()
    maven {
        name = "papermc"
        url = uri("https://repo.papermc.io/repository/maven-public/")
    }
}

// ─── Paper integration test source set (must be before dependencies) ───
sourceSets {
    create("paperIntegrationTest") {
        java {
            srcDir("src/paperIntegrationTest/java")
        }
        resources {
            srcDir("src/paperIntegrationTest/resources")
        }
    }
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:$paperApiVersion")

    implementation("org.xerial:sqlite-jdbc:$sqliteVersion")

    // Lets resource tests parse the shipped config.yml and message files with the same
    // YAML implementation the server uses.
    testImplementation("io.papermc.paper:paper-api:$paperApiVersion")

    testImplementation(platform("org.junit:junit-bom:$junitVersion"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")

    // Paper integration test plugin needs the main plugin's classes
    "paperIntegrationTestImplementation"("io.papermc.paper:paper-api:$paperApiVersion")
    "paperIntegrationTestImplementation"(sourceSets.main.get().output)
}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(25))
    withSourcesJar()
}

// Build a plugin JAR from the paperIntegrationTest source set (includes main classes)
val paperIntegrationTestJar = tasks.register<Jar>("paperIntegrationTestJar") {
    group = "verification"
    description = "Builds the test plugin JAR for Paper integration tests."
    from(sourceSets.main.get().output)
    from(sourceSets.getByName("paperIntegrationTest").output)
    archiveBaseName.set("SerializationTestPlugin")
    archiveClassifier.set("")
    archiveVersion.set("0.1.0")
    destinationDirectory.set(layout.buildDirectory.dir("paperIntegrationTest"))
    duplicatesStrategy = DuplicatesStrategy.INCLUDE
}

// Unit tests run against the un-shadowed classpath, so packaging faults (a bad relocation,
// a missing service file) can only be caught by loading the shaded JAR itself.
val shadowJarSmokeTest = tasks.register<JavaExec>("shadowJarSmokeTest") {
    group = "verification"
    description = "Opens a real SQLite database using only the shaded JAR."
    classpath = files(tasks.shadowJar.flatMap { it.archiveFile })
    mainClass.set(layout.projectDirectory.file("gradle/smoke/SqliteSmoke.java").asFile.path)
    args(layout.buildDirectory.dir("smoke").get().asFile.path)
    jvmArgs("--enable-native-access=ALL-UNNAMED")
}

tasks {
    withType<JavaCompile>().configureEach {
        options.encoding = "UTF-8"
        options.release.set(25)
        options.compilerArgs.addAll(listOf("-Xlint:all", "-Xlint:-processing"))
    }

    processResources {
        val tokens = mapOf("version" to pluginVersion)
        filteringCharset = "UTF-8"
        inputs.properties(tokens)
        filesMatching("plugin.yml") {
            expand(tokens)
        }
    }

    named<Copy>("processPaperIntegrationTestResources") {
        duplicatesStrategy = DuplicatesStrategy.INCLUDE
    }

    test {
        useJUnitPlatform()
        jvmArgs("--enable-native-access=ALL-UNNAMED")
        testLogging {
            events("passed", "skipped", "failed")
        }
    }

    shadowJar {
        archiveClassifier.set("")
        duplicatesStrategy = DuplicatesStrategy.INCLUDE
        mergeServiceFiles()
        // sqlite-jdbc must not be relocated: its bundled native library exports
        // Java_org_sqlite_core_NativeDB_* symbols, which no longer bind once the Java
        // classes are renamed.
        exclude("META-INF/*.SF", "META-INF/*.DSA", "META-INF/*.RSA")
        finalizedBy(shadowJarSmokeTest)
    }

    build {
        dependsOn(shadowJar)
    }
}

// ─── Paper-backed integration test task ───
val paperIntegrationTest = tasks.register("paperIntegrationTest") {
    group = "verification"
    description = "Downloads Paper, starts a disposable server with the test plugin, runs serialization round-trip tests, and reports results."
    dependsOn(paperIntegrationTestJar)

    val serverDir = layout.buildDirectory.dir("paperServer").get().asFile
    val resultsFile = File(serverDir, "results.txt")
    val paperJar = File(serverDir, "paper.jar")
    val pluginJarFile = paperIntegrationTestJar.get().archiveFile

    inputs.file(pluginJarFile)

    val javaPath = javaToolchains.launcherFor(java.toolchain).get().executablePath.asFile.absolutePath

    doLast {
        val pluginJar = pluginJarFile.get().asFile
        // Create disposable server directory
        serverDir.mkdirs()

        // Download Paper server jar if not cached
        if (!paperJar.exists()) {
            logger.lifecycle("Downloading Paper server jar from $paperServerUrl")
            ant.invokeMethod("get", mapOf("src" to paperServerUrl, "dest" to paperJar))
        }
        logger.lifecycle("Paper server jar: ${paperJar.name} (${paperJar.length() / 1024 / 1024} MB)")

        // Create eula.txt
        File(serverDir, "eula.txt").writeText("eula=true\n")

        // Copy test plugin jar to plugins/
        val pluginsDir = File(serverDir, "plugins").apply { mkdirs() }
        pluginJar.copyTo(File(pluginsDir, "SerializationTestPlugin.jar"), overwrite = true)

        // Remove stale results
        resultsFile.delete()

        // Start Paper server
        logger.lifecycle("Starting Paper server in $serverDir with $javaPath")
        val processBuilder = ProcessBuilder(
            javaPath,
            "-Xmx1G",
            "-jar", paperJar.absolutePath,
            "nogui"
        )
        processBuilder.directory(serverDir)
        processBuilder.redirectErrorStream(true)
        val serverProcess = processBuilder.start()

        // Wait for results file (timeout 180 seconds)
        val deadline = System.currentTimeMillis() + 180_000
        var resultsContent: String? = null
        while (System.currentTimeMillis() < deadline) {
            if (resultsFile.exists()) {
                resultsContent = resultsFile.readText()
                break
            }
            if (!serverProcess.isAlive) {
                // Server exited before producing results — check if it already shut down with results
                if (resultsFile.exists()) {
                    resultsContent = resultsFile.readText()
                }
                break
            }
            Thread.sleep(500)
        }

        // If server is still running, shut it down
        if (serverProcess.isAlive) {
            logger.lifecycle("Stopping Paper server")
            serverProcess.outputStream.write("stop\n".toByteArray())
            serverProcess.outputStream.flush()
            serverProcess.waitFor(15, TimeUnit.SECONDS)
            if (serverProcess.isAlive) {
                serverProcess.destroyForcibly()
            }
        }

        // Report results
        if (resultsContent == null) {
            logger.error("ERROR: No results.txt produced — server may have failed to start")
            throw GradleException("Paper integration tests failed: no results file produced")
        }

        val lines = resultsContent.trim().lines()
        logger.lifecycle("=== Paper Integration Test Results ===")
        var executed = 0
        var passed = 0
        var failed = 0
        var skipped = 0
        for (line in lines) {
            logger.lifecycle(line)
            if (line.startsWith("SUMMARY:")) {
                val summary = line.removePrefix("SUMMARY:")
                for (part in summary.split(",")) {
                    val kv = part.trim().split("=")
                    when (kv[0]) {
                        "executed" -> executed = kv[1].toInt()
                        "passed" -> passed = kv[1].toInt()
                        "failed" -> failed = kv[1].toInt()
                        "skipped" -> skipped = kv[1].toInt()
                    }
                }
            }
        }
        logger.lifecycle("=== Summary: executed=$executed, passed=$passed, failed=$failed, skipped=$skipped ===")

        if (failed > 0) {
            throw GradleException("Paper integration tests failed: $failed test(s) failed")
        }
        if (executed == 0) {
            throw GradleException("Paper integration tests failed: no tests executed")
        }
    }
}
