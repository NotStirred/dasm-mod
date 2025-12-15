
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
    implementation("net.neoforged.fancymodloader:loader:2.0.0")
}
