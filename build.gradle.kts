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

repositories {
    mavenCentral()
    maven {
        name = "papermc"
        url = uri("https://repo.papermc.io/repository/maven-public/")
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
}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(25))
    withSourcesJar()
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
