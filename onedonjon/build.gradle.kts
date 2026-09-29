plugins {
    java
}

group = "io.github.youpl"
version = "0.1.0"
description = "Donjons sur mesure, entierement configurables en jeu via GUI"

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(25))
    }
}

dependencies {
    // Seule dependance : l'API Paper. MythicMobs, MMOItems, Nexo, ModelEngine, Citizens et
    // Typewriter sont pilotes par reflexion / commandes : aucune version n'est imposee.
    compileOnly("io.papermc.paper:paper-api:26.1.2.build.74-stable")

    testImplementation("io.papermc.paper:paper-api:26.1.2.build.74-stable")
    testImplementation(platform("org.junit:junit-bom:5.11.4"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.compilerArgs.addAll(listOf("-parameters"))
}

tasks.processResources {
    val props = mapOf("version" to project.version.toString())
    inputs.properties(props)
    filesMatching("plugin.yml") { expand(props) }
}

tasks.jar {
    archiveBaseName.set("OneDonjon")
}

tasks.test {
    useJUnitPlatform()
    testLogging { events("passed", "skipped", "failed") }
}
