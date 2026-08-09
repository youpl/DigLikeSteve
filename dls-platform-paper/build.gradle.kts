description = "Adaptateurs Paper/Folia : événements, schedulers, monde, inventaires, persistance"

dependencies {
    api(project(":dls-core"))
    compileOnly(libs.paper.api)
    compileOnly(libs.sqlite.jdbc)

    testImplementation(libs.paper.api)
    testImplementation(libs.sqlite.jdbc)
    testImplementation(project(":dls-test-support"))
}
