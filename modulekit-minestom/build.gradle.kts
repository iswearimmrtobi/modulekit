plugins { `java-library` }

dependencies {
    api(project(":modulekit-api"))
    compileOnly(project(":modulekit-core"))
    implementation("net.minestom:minestom:2026.06.05-26.1.2")
}
