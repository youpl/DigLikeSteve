plugins {
    alias(libs.plugins.shadow)
}

description = "Bootstrap JavaPlugin, assemblage et ressources finales"

dependencies {
    implementation(project(":dls-core"))
    implementation(project(":dls-platform-paper"))
    implementation(project(":dls-integrations"))

    compileOnly(libs.paper.api)
}

val pluginVersion = project.version.toString()

tasks.processResources {
    val props = mapOf(
        "version" to pluginVersion,
        "sqliteVersion" to libs.versions.sqlite.get(),
    )
    inputs.properties(props)
    filesMatching("plugin.yml") {
        expand(props)
    }
}

tasks.shadowJar {
    archiveBaseName.set("DigLikeSteve")
    archiveClassifier.set("")
    archiveVersion.set(pluginVersion)
    destinationDirectory.set(rootProject.layout.buildDirectory.dir("libs"))
    minimize()
}

tasks.build {
    dependsOn(tasks.shadowJar)
}
