plugins {
    // Lets Gradle download missing JDKs (e.g. Java 25 for the 26.x test servers).
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

rootProject.name = "AutoItem"
