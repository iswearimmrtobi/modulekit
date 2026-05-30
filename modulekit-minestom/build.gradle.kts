plugins { `java-library` }

dependencies {
    api(project(":modulekit-api"))
    compileOnly(project(":modulekit-core"))
    compileOnly("net.minestom:minestom-snapshots:7135080bbc")
}
