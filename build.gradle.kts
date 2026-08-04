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
    maven {
        name = "opencollab-snapshots"
        url = uri("https://repo.opencollab.dev/maven-snapshots/")
        content {
            includeGroup("org.geysermc.mcprotocollib")
        }
    }
    maven {
        name = "opencollab-releases"
        url = uri("https://repo.opencollab.dev/maven-releases/")
        content {
            includeGroup("org.geysermc.mcprotocollib")
            includeGroup("org.cloudburstmc.math")
            includeGroup("com.nukkitx.fastutil")
            includeGroup("com.nukkitx")
        }
    }
}

// ─── Paper integration test + runtime validation source sets (must be before dependencies) ───
sourceSets {
    create("paperIntegrationTest") {
        java {
            srcDir("src/paperIntegrationTest/java")
        }
        resources {
            srcDir("src/paperIntegrationTest/resources")
        }
    }
    create("runtimeValidation") {
        java {
            srcDir("src/runtimeValidation/java")
        }
        resources {
            srcDir("src/runtimeValidation/resources")
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

    "runtimeValidationImplementation"("io.papermc.paper:paper-api:$paperApiVersion")
    "runtimeValidationImplementation"("org.geysermc.mcprotocollib:protocol:26.2-20260709.110151-15")
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

// Build the runtime validation plugin JAR (does NOT include main classes — uses real EchoClaims JAR)
val runtimeValidationJar = tasks.register<Jar>("runtimeValidationJar") {
    group = "verification"
    description = "Builds the runtime validation plugin JAR."
    from(sourceSets.getByName("runtimeValidation").output)
    archiveBaseName.set("RuntimeValidationPlugin")
    archiveClassifier.set("")
    archiveVersion.set("0.1.0")
    destinationDirectory.set(layout.buildDirectory.dir("runtimeValidation"))
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

    named<Copy>("processRuntimeValidationResources") {
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

// ─── Full EchoClaims runtime validation task ───
val runtimeValidation = tasks.register("runtimeValidation") {
    group = "verification"
    description = "Starts a disposable Paper server with the real EchoClaims JAR, connects a headless bot player, runs full runtime validation, and reports results."
    dependsOn(tasks.shadowJar, runtimeValidationJar)

    val serverDir = layout.buildDirectory.dir("runtimeServer").get().asFile
    val resultsFile = File(serverDir, "validation-results.txt")
    val paperJar = File(serverDir, "paper.jar")
    val echoClaimsJarFile = tasks.shadowJar.flatMap { it.archiveFile }
    val validationJarFile = runtimeValidationJar.get().archiveFile

    inputs.file(echoClaimsJarFile)
    inputs.file(validationJarFile)

    val javaPath = javaToolchains.launcherFor(java.toolchain).get().executablePath.asFile.absolutePath
    val runtimeValidationClasspath = sourceSets.getByName("runtimeValidation").runtimeClasspath.asPath

    doLast {
        val echoClaimsJar = echoClaimsJarFile.get().asFile
        val validationJar = validationJarFile.get().asFile

        // Clean server directory for fresh state (preserve cached Paper jar)
        val pluginsDir = File(serverDir, "plugins")
        val dbFile = File(serverDir, "plugins/EchoClaims/echoclaims.db")
        if (pluginsDir.exists()) pluginsDir.deleteRecursively()
        serverDir.mkdirs()

        // Download Paper server jar if not cached
        if (!paperJar.exists()) {
            logger.lifecycle("Downloading Paper server jar from $paperServerUrl")
            ant.invokeMethod("get", mapOf("src" to paperServerUrl, "dest" to paperJar))
        }
        logger.lifecycle("Paper server jar: ${paperJar.name} (${paperJar.length() / 1024 / 1024} MB)")

        // Create eula.txt
        File(serverDir, "eula.txt").writeText("eula=true\n")

        // Set online-mode=false so the bot can connect without authentication
        val properties = File(serverDir, "server.properties")
        properties.writeText(
            "online-mode=false\n" +
            "server-port=25565\n" +
            "level-type=minecraft\\:flat\n" +
            "generate-structures=false\n" +
            "spawn-protection=0\n" +
            "max-players=2\n" +
            "view-distance=3\n" +
            "simulation-distance=3\n" +
            "allow-flight=true\n" +
            "max-tick-time=-1\n"
        )

        // Copy EchoClaims JAR and validation plugin JAR to plugins/
        pluginsDir.mkdirs()
        echoClaimsJar.copyTo(File(pluginsDir, "EchoClaims.jar"), overwrite = true)
        validationJar.copyTo(File(pluginsDir, "RuntimeValidationPlugin.jar"), overwrite = true)

        // Remove stale results
        resultsFile.delete()

        // Start Paper server
        logger.lifecycle("Starting Paper server in $serverDir with $javaPath")
        logger.lifecycle("EchoClaims JAR: ${echoClaimsJar.name}")
        logger.lifecycle("Validation JAR: ${validationJar.name}")
        val processBuilder = ProcessBuilder(
            javaPath,
            "-Xmx2G",
            "-Dechoclaims.paper.runtime=true",
            "-jar", paperJar.absolutePath,
            "nogui"
        )
        processBuilder.directory(serverDir)
        processBuilder.redirectErrorStream(true)
        val serverProcess = processBuilder.start()

        // Wait for server to be ready (look for "Done" in output)
        logger.lifecycle("Waiting for Paper server to start...")
        val serverOutput = StringBuilder()
        var serverReady = false
        val readyDeadline = System.currentTimeMillis() + 120_000
        while (System.currentTimeMillis() < readyDeadline) {
            val available = serverProcess.inputStream.available()
            if (available > 0) {
                val bytes = ByteArray(available)
                serverProcess.inputStream.read(bytes)
                val text = String(bytes)
                serverOutput.append(text)
                if (text.contains("Done (") || text.contains("[Server thread/INFO]: Done")) {
                    serverReady = true
                    logger.lifecycle("Paper server is ready")
                    break
                }
            }
            if (!serverProcess.isAlive) {
                logger.error("Paper server exited before becoming ready")
                logger.error(serverOutput.toString())
                throw GradleException("Runtime validation failed: Paper server crashed during startup")
            }
            Thread.sleep(200)
        }

        if (!serverReady) {
            logger.error("Paper server did not become ready within 120 seconds")
            logger.error(serverOutput.toString())
            serverProcess.destroyForcibly()
            throw GradleException("Runtime validation failed: Paper server did not start")
        }

        // Start headless bot client
        logger.lifecycle("Starting headless bot client (MCProtocolLib 26.2-SNAPSHOT, protocol 776)")
        val botBuilder = ProcessBuilder(
            javaPath,
            "-cp", runtimeValidationClasspath,
            "io.github.skyblueheat.echoclaims.testplugin.HeadlessBotClient",
            "127.0.0.1", "25565", "TestBot"
        )
        botBuilder.redirectErrorStream(true)
        val botProcess = botBuilder.start()

        // File the validation plugin writes to request a second bot
        val secondBotTrigger = File(serverDir, "need-second-bot")
        secondBotTrigger.delete()
        var secondBotProcess: Process? = null

        // Wait for results file (timeout 420 seconds — extended for security flow checks)
        logger.lifecycle("Waiting for validation results...")
        val deadline = System.currentTimeMillis() + 420_000
        var resultsContent: String? = null
        while (System.currentTimeMillis() < deadline) {
            // Check if validation plugin needs a second bot
            if (secondBotProcess == null && secondBotTrigger.exists()) {
                logger.lifecycle("Starting second headless bot client (TestBot2)")
                val bot2Builder = ProcessBuilder(
                    javaPath,
                    "-cp", runtimeValidationClasspath,
                    "io.github.skyblueheat.echoclaims.testplugin.HeadlessBotClient",
                    "127.0.0.1", "25565", "TestBot2"
                )
                bot2Builder.redirectErrorStream(true)
                secondBotProcess = bot2Builder.start()
                secondBotTrigger.delete()
            }
            if (resultsFile.exists()) {
                resultsContent = resultsFile.readText()
                break
            }
            if (!serverProcess.isAlive) {
                if (resultsFile.exists()) {
                    resultsContent = resultsFile.readText()
                }
                break
            }
            // Read bot output for diagnostics
            val botAvail = botProcess.inputStream.available()
            if (botAvail > 0) {
                val botBytes = ByteArray(botAvail)
                botProcess.inputStream.read(botBytes)
                val botText = String(botBytes)
                for (line in botText.lines()) {
                    if (line.isNotBlank()) logger.lifecycle("[Bot] $line")
                }
            }
            if (secondBotProcess != null) {
                val bot2Avail = secondBotProcess.inputStream.available()
                if (bot2Avail > 0) {
                    val bot2Bytes = ByteArray(bot2Avail)
                    secondBotProcess.inputStream.read(bot2Bytes)
                    val bot2Text = String(bot2Bytes)
                    for (line in bot2Text.lines()) {
                        if (line.isNotBlank()) logger.lifecycle("[Bot2] $line")
                    }
                }
            }
            Thread.sleep(500)
        }

        // Stop bots if still running
        if (botProcess.isAlive) {
            botProcess.destroyForcibly()
        }
        if (secondBotProcess != null && secondBotProcess.isAlive) {
            secondBotProcess.destroyForcibly()
        }

        // If server is still running, shut it down
        if (serverProcess.isAlive) {
            logger.lifecycle("Stopping Paper server")
            serverProcess.outputStream.write("stop\n".toByteArray())
            serverProcess.outputStream.flush()
            serverProcess.waitFor(30, TimeUnit.SECONDS)
            if (serverProcess.isAlive) {
                serverProcess.destroyForcibly()
            }
        }

        // Report results
        if (resultsContent == null) {
            logger.error("ERROR: No validation-results.txt produced")
            throw GradleException("Runtime validation failed: no results file produced")
        }

        val lines = resultsContent.trim().lines()
        logger.lifecycle("=== EchoClaims Runtime Validation Results ===")
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
            throw GradleException("Runtime validation failed: $failed check(s) failed")
        }
        if (executed == 0) {
            throw GradleException("Runtime validation failed: no checks executed")
        }
    }
}
