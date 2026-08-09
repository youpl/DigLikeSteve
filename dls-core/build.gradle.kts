plugins {
    `java-test-fixtures`
}

// dls-core ne doit dépendre d'aucune classe Minecraft (critère d'acceptation DAT §23).
// Aucune dependency de compilation n'est déclarée ici volontairement.

description = "Domaine, règles, ports et cas d'usage Java pur de Dig Like Steve"

dependencies {
    testImplementation(testFixtures(project(":dls-core")))
}
