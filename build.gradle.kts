import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar

plugins {
    id("com.gradleup.shadow") version "8.3.6" apply false
    id("signing")
    `java-library`
}

allprojects {
    group = "io.github.notstirred"
    version = "3.2.0"
}

subprojects {
    apply(plugin = "java-library")
    apply(plugin = "com.gradleup.shadow")

    group = "io.github.notstirred"
    version = "3.2.0"

    repositories {
        mavenCentral()
    }

    dependencies {
        compileOnly("org.apache.logging.log4j:log4j-api:2.25.1")
        runtimeOnly("org.apache.logging.log4j:log4j:2.25.1")
        implementation("com.google.code.gson:gson:2.8.9")
    }

    java {
        withSourcesJar()
        withJavadocJar()

        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }

    tasks.withType<JavaCompile> {
        options.release = 8
    }

    tasks.named<ShadowJar>("shadowJar") {
        archiveClassifier = ""
        configurations = listOf(project.configurations.named("shadow").get())

        manifest {
            attributes (
                "MixinConfigs" to "dasm-mod.mixins.json",
                "FMLModType" to "GAMELIBRARY",
                "Automatic-Module-Name" to "dasm"
            )
        }
    }
    tasks.named<Jar>("shadowJar") {
        configurations.add(project.configurations.named("shadow").get())
    }
}
