
repositories {
    maven(url = "https://maven.neoforged.net/releases")
}

dependencies {
    shadow(project(":common")) {
        isTransitive = false
    }
    shadow(dasm()) {
        isTransitive = false
    }
    implementation("net.neoforged.fancymodloader:loader:7.0.10")
}

tasks.withType<JavaCompile> {
    options.release = 21
}
