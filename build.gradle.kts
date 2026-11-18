import xyz.jpenilla.runpaper.task.RunServer
import xyz.jpenilla.runtask.service.DownloadsAPIService

plugins {
    id("java-library")
    // run-paper 3.1.0 needs Gradle 9.7+ (see gradle/wrapper/gradle-wrapper.properties)
    id("xyz.jpenilla.run-paper") version "3.1.0"
}

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
}

dependencies {
    // Compile against the OLDEST version we support (1.21.1). Newer servers (1.21.x, 26.x)
    // load plugins built on older API; the reverse is not true. If something doesn't
    // compile here, it doesn't exist on 1.21.1 servers either.
    compileOnly("io.papermc.paper:paper-api:1.21.1-R0.1-SNAPSHOT")

    // Tests: pure planner/config tests plus MockBukkit integration tests.
    // MockBukkit is built against the paper-api version in its jar manifest (Paper-Version),
    // so the test classpath uses that API, not the 1.21.1 compile API. Test scope only.
    testImplementation("io.papermc.paper:paper-api:1.21.11-R0.1-SNAPSHOT")
    testImplementation("org.mockbukkit.mockbukkit:mockbukkit-v1.21:4.116.3")
    testImplementation(platform("org.junit:junit-bom:6.0.3"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

java {
    toolchain.languageVersion = JavaLanguageVersion.of(21)
}

tasks {
    test {
        useJUnitPlatform()
    }

    processResources {
        val props = mapOf("version" to version, "description" to project.description)
        inputs.properties(props)
        filesMatching("plugin.yml") {
            expand(props)
        }
    }
}

// ---------------------------------------------------------------------------
// Test servers — one per Minecraft version we promise to support, plus Folia.
// Each gets its own run directory (run/<version>) and the Java version that
// Minecraft version needs. Gradle downloads missing JDKs automatically
// (foojay resolver in settings.gradle.kts).
//
//   ./gradlew runServer          -> Paper 26.2   (latest tested, Java 25)
//   ./gradlew runServer1_21_11   -> Paper 1.21.11 (newest 1.21.x, Java 21)
//   ./gradlew runServer1_21_1    -> Paper 1.21.1  (oldest supported, Java 21)
//   ./gradlew runFolia           -> Folia 26.2    (Java 25)
//   ./gradlew runFolia1_21_11    -> Folia 1.21.11 (Java 21)
//   ./gradlew runServer26_1      -> Paper 26.1.2  (Java 25; the newest 26.x the tools/bot client can join)
//   ./gradlew runFolia26_1       -> Folia 26.1.2  (Java 25)
//   ./gradlew runServer26_3      -> Paper 26.3    (Java 25; newest)
//   ./gradlew runFolia26_3       -> Folia 26.3    (Java 25)
// ---------------------------------------------------------------------------
data class TestServer(
    val taskName: String,
    val minecraftVersion: String,
    val javaVersion: Int,
    val dir: String,
    val folia: Boolean = false,
)

val testServers = listOf(
    TestServer("runServer", "26.2", 25, "26.2"),
    TestServer("runServer1_21_11", "1.21.11", 21, "1.21.11"),
    TestServer("runServer1_21_1", "1.21.1", 21, "1.21.1"),
    TestServer("runFolia", "26.2", 25, "folia-26.2", folia = true),
    TestServer("runFolia1_21_11", "1.21.11", 21, "folia-1.21.11", folia = true),
    TestServer("runServer26_1", "26.1.2", 25, "26.1.2"),
    TestServer("runFolia26_1", "26.1.2", 25, "folia-26.1.2", folia = true),
    TestServer("runServer26_3", "26.3", 25, "26.3"),
    TestServer("runFolia26_3", "26.3", 25, "folia-26.3", folia = true),
)

val pluginJar = tasks.jar.flatMap { it.archiveFile }

runPaper.folia.registerTask()

testServers.forEach { server ->
    val serverDir = layout.projectDirectory.dir("run/${server.dir}")
    val serverJava = javaToolchains.launcherFor {
        languageVersion = JavaLanguageVersion.of(server.javaVersion)
    }

    val configureServer: RunServer.() -> Unit = {
        minecraftVersion(server.minecraftVersion)
        runDirectory.set(serverDir)
        javaLauncher.set(serverJava)
        jvmArgs("-Xms2G", "-Xmx2G")
        // Accept the Minecraft EULA for local test servers only.
        systemProperty("com.mojang.eula.agree", "true")
    }

    if (server.taskName == "runServer" || server.taskName == "runFolia") {
        // run-paper registers these itself and already picks up our plugin jar.
        tasks.named<RunServer>(server.taskName) { configureServer() }
    } else {
        tasks.register<RunServer>(server.taskName) {
            group = "run paper"
            val software = if (server.folia) "Folia" else "Paper"
            description = "Runs a $software ${server.minecraftVersion} test server with the plugin."
            if (server.folia) {
                displayName.set("Folia")
                downloadsApiService.set(DownloadsAPIService.folia(project))
            }
            configureServer()
            pluginJars(pluginJar)
        }
    }
}
