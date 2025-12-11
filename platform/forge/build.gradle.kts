
dependencies {
    shadow(project(":common")) {
        isTransitive = false
    }
    shadow(dasm())
}
