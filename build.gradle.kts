plugins {
    java
}

group = "fr.mattmunich"
version = "1.4-SNAPSHOT"

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(25))
}

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
}

dependencies {
    // Replaces paper-api. The dev bundle gives you full Paper API + Mojang-mapped NMS
    //paperweight.paperDevBundle("1.21.11-R0.1-SNAPSHOT")
    compileOnly("io.papermc.paper:paper-api:26.1.2.build.+")
    // Add any libraries you want to shade here using 'implementation'
    // implementation("com.zaxxer:HikariCP:x.x.x")
}

tasks.processResources {
    val props = mapOf("version" to project.version)
    inputs.properties(props)
    filteringCharset = "UTF-8"
    filesMatching("paper-plugin.yml") {
        expand(props)
    }
}