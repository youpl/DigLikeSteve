pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

rootProject.name = "DigLikeSteve"

include(
    "dls-core",
    "dls-platform-paper",
    "dls-integrations",
    "dls-plugin",
    "dls-test-support",
)
