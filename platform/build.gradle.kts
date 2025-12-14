var buildSourceAndJavadoc = (System.getenv("SOURCE_JARS") ?: "true") == "true"

subprojects {
    val moduleName = name

    repositories {
        maven("https://repo.spongepowered.org/maven")
    }

    dependencies {
        shadow(project(":common")) {
            isTransitive = false
        }

        implementation("org.ow2.asm:asm:9.6")
        implementation("org.ow2.asm:asm-tree:9.6")
        implementation("org.ow2.asm:asm-util:9.6")
        implementation("org.ow2.asm:asm-commons:9.6")
        implementation("io.github.notstirred:dasm:3.2.0")
        implementation("org.spongepowered:mixin:0.8")
    }

    if (buildSourceAndJavadoc) {
        tasks.named<Jar>("sourcesJar") {
            from(project(":common").sourceSets.main.get().java)
        }
    }

    tasks.named<Javadoc>("javadoc") {
        classpath += project(":common").configurations.compileClasspath.get()
        source(project(":common").sourceSets.main.get().java)
    }
    tasks.withType<Jar> {
        archiveBaseName.set("dasm-$moduleName")
    }

    configurePublishing(moduleName) {
        artifact(tasks.getByName("shadowJar"))
        if (buildSourceAndJavadoc) {
            artifact(tasks.getByName("sourcesJar"))
            artifact(tasks.getByName("javadocJar"))
        }
    }
}

fun Project.configurePublishing(artifactName: String, setup: MavenPublication.() -> Unit) {
    apply(plugin = "java-library")
    apply(plugin = "maven-publish")
    apply(plugin = "signing")

    extensions.configure<PublishingExtension> {
        repositories {
            maven {
                name = "Staging"
                url = uri(rootProject.layout.buildDirectory.dir("staging-deploy"))
            }
        }
        publications {
            create<MavenPublication>("maven") {
                groupId = "io.github.notstirred"
                artifactId = "dasm-$artifactName"

                setup()

                pom {
                    name = "DASM Mod"
                    description = " library for declarative annotation based bytecode transformations."
                    url = "https://github.com/NotStirred/dasm-mod"
                    licenses {
                        license {
                            name = "MIT"
                            url = "https://opensource.org/license/mit/"
                        }
                    }
                    developers {
                        developer {
                            id = "NotStirred"
                            email = "tom.martin1239@gmail.com"
                        }
                    }
                    scm {
                        connection = "scm:git:git@github.com:NotStirred/dasm-mod.git"
                        developerConnection = "scm:git:git@github.com:NotStirred/dasm-mod.git"
                        url = "https://github.com/NotStirred/dasm-mod"
                    }
                }
            }
        }
    }
    extensions.configure<SigningExtension> {
        sign(extensions.getByType<PublishingExtension>().publications["maven"])
    }
}
