plugins {
    java
}

group = "dev.ryvrook"
version = "0.6.0"

val hytaleChannel = (project.findProperty("hytale_channel") as String?) ?: "release"
val hytaleVersion = (project.findProperty("hytale_version") as String?) ?: "0.7.1"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(25))
    }
}

// Sibling jars to compile the optional integrations against, never shipped inside the mod. Each one is given with
// -PtrackerApiJar=<path>, -PfeatsJar=<path>, -PatlasJar=<path> and -PcoinageJar=<path>, else the sibling checkout next to this one,
// else the checkout under ~/Projects. Only the bridge classes in dev.ryvrook.dominion.integration touch them, and
// only after a presence check, so Dominion still loads on a server without any of them.
fun siblingJar(property: String, vararg candidates: String): String {
    (project.findProperty(property) as String?)?.trim()?.takeIf { it.isNotEmpty() }?.let { return it }
    val home = System.getProperty("user.home")
    val paths = candidates.flatMap { listOf("../$it", "$home/Projects/$it") }
    return paths.firstOrNull { file(it).exists() } ?: paths.first()
}
val trackerApiJar = siblingJar("trackerApiJar", "trackerapi/build/libs/TrackerAPI-1.0.3.jar")
val featsJar = siblingJar("featsJar", "feats/build/libs/Feats-1.5.1.jar")
val atlasJar = siblingJar("atlasJar", "atlas/build/libs/Atlas-1.6.0.jar")
// The Coinage api jar holds the api packages only. The tests use it too, for the bridge over a stand in Coinage API.
val coinageJar = siblingJar("coinageJar", "coinage/build/libs/Coinage-api-1.0.0.jar")

repositories {
    mavenCentral()
    maven {
        name = "hytale-$hytaleChannel"
        url = uri("https://maven.hytale.com/$hytaleChannel")
    }
}

dependencies {
    compileOnly("com.hypixel.hytale:Server:$hytaleVersion")
    // Optional at runtime, listed under OptionalDependencies. Only the integration bridges touch them.
    compileOnly(files(trackerApiJar))
    compileOnly(files(featsJar))
    compileOnly(files(atlasJar))
    compileOnly(files(coinageJar))

    // Tests run without a server. The server jar is only on the test classpath for bson and the logger.
    testImplementation("com.hypixel.hytale:Server:$hytaleVersion")
    testImplementation(files(coinageJar))
    // The Atlas jar too, for the bridge over a stand in AtlasApi, and the TrackerAPI jar for its event records.
    testImplementation(files(atlasJar))
    testImplementation(files(trackerApiJar))
    testImplementation(platform("org.junit:junit-bom:5.11.4"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    useJUnitPlatform()
    systemProperty("java.util.logging.manager", "com.hypixel.hytale.logger.backend.HytaleLogManager")
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release.set(25)
    options.compilerArgs.add("-Xlint:deprecation")
}

tasks.jar {
    archiveBaseName.set("DominionFactions")
    archiveVersion.set(project.version.toString())
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}

val installMod by tasks.registering(Copy::class) {
    group = "hytale"
    description = "Builds the mod and copies it into the local Hytale UserData/Mods folder."
    dependsOn(tasks.jar)
    val home = System.getProperty("user.home")
    val os = System.getProperty("os.name").lowercase()
    val defaultMods = when {
        os.contains("mac") -> "$home/Library/Application Support/Hytale/UserData/Mods"
        os.contains("win") -> (System.getenv("APPDATA") ?: "$home/AppData/Roaming") + "/Hytale/UserData/Mods"
        else -> "$home/.local/share/Hytale/UserData/Mods"
    }
    val target = (project.findProperty("modsDir") as String?) ?: defaultMods
    from(tasks.jar.flatMap { it.archiveFile })
    into(target)
}
