plugins {
    kotlin("jvm") version "1.7.10"
    application
    id("com.github.johnrengelman.shadow") version "7.1.2"
}

repositories {
    mavenCentral()
    maven {
        url = uri("https://maven.pkg.jetbrains.space/public/p/space-sdk/maven")
    }
}

val ktorVersion = "2.1.0"

dependencies {
    implementation("org.jetbrains:space-sdk-jvm:110879-beta")
    implementation("com.github.ajalt.clikt:clikt:3.5.0")
    implementation("com.vladsch.flexmark:flexmark:0.64.0")
    implementation("com.vladsch.flexmark:flexmark-html2md-converter:0.64.0")
    implementation("io.ktor:ktor-client-content-negotiation:$ktorVersion")
    implementation("io.ktor:ktor-serialization-jackson:$ktorVersion")
    implementation("io.ktor:ktor-client-apache:$ktorVersion")
}

tasks {
    shadowJar {
        manifest {
            attributes(Pair("Main-Class", "circlet.cli.MainKt"))
        }
        archiveFileName.set("space.jar")
    }
}

application {
    mainClass.set("circlet.cli.MainKt")
}