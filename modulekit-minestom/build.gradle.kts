plugins { `java-library` }

dependencies {
    api(project(":modulekit-api"))
    compileOnly(project(":modulekit-core"))
    implementation("net.minestom:minestom:2026.08.16-26.2")
}
