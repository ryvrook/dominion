plugins {
    java
}

group = "dev.ryvrook"
version = "0.2.1"

val hytaleChannel = (project.findProperty("hytale_channel") as String?) ?: "release"
val hytaleVersion = (project.findProperty("hytale_version") as String?) ?: "0.7.1"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(25))
    }
}

repositories {
    mavenCentral()
    maven {
        name = "hytale-$hytaleChannel"
        url = uri("https://maven.hytale.com/$hytaleChannel")
    }
}

dependencies {
    compileOnly("com.hypixel.hytale:Server:$hytaleVersion")

    // Tests run without a server. The server jar is only on the test classpath for bson and the logger.
    testImplementation("com.hypixel.hytale:Server:$hytaleVersion")
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
