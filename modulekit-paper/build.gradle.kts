plugins { `java-library` }

dependencies {
    api(project(":modulekit-api"))
    api(project(":modulekit-core"))
    compileOnly("io.papermc.paper:paper-api:1.21.4-R0.1-SNAPSHOT")
}
