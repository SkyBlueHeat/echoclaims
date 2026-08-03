pluginManagement {
    val shadowVersion = providers.gradleProperty("shadowVersion").get()

    repositories {
        gradlePluginPortal()
    }

    plugins {
        id("com.gradleup.shadow") version shadowVersion
    }
}

rootProject.name = "worldecho"
