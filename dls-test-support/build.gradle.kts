description = "Bancs d'intégration et doublures de ports partagés par les modules de plateforme"

dependencies {
    api(project(":dls-core"))
    api(testFixtures(project(":dls-core")))
    api(platform("org.junit:junit-bom:5.11.4"))
    api("org.junit.jupiter:junit-jupiter")
}
