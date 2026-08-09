description = "Intégrations facultatives : WorldEdit/FAWE, PlaceholderAPI, warps, spawn, crates"

dependencies {
    api(project(":dls-core"))
    api(project(":dls-platform-paper"))

    compileOnly(libs.paper.api)
    compileOnly(libs.placeholderapi) { isTransitive = false }
    compileOnly(libs.worldedit.bukkit) { isTransitive = false }
    compileOnly(libs.worldedit.core) { isTransitive = false }
}
