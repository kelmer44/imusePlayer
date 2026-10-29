import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    kotlin("jvm")
    id("org.jetbrains.compose")
}

group = "com.imuseplayer"
version = "1.0.0"

repositories {
    mavenCentral()
    maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
    google()
}

dependencies {
    implementation(compose.desktop.currentOs)
    implementation("org.luaj:luaj-jse:3.0.1")
    implementation("javazoom:jlayer:1.0.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:1.8.1")
    testImplementation("junit:junit:4.13.2")
}

// Compose bundles <root>/common/**; ScriptManager looks for <resources>/scripts/engines.
val stageResources = tasks.register<Copy>("stageResources") {
    from("scripts/engines") { into("common/scripts/engines") }
    into(layout.buildDirectory.dir("app-resources"))
}
tasks.matching { it.name.startsWith("prepareAppResources") }.configureEach { dependsOn(stageResources) }

compose.desktop {
    application {
        mainClass = "adventureexplorer.woodtick.WoodtickMainKt"

        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            packageName = "MI2 Music Explorer"
            packageVersion = (project.findProperty("appVersion") as String?) ?: "1.0.0"
            description = "Walk around Monkey Island 2 scenes and hear the iMUSE music transitions on an OPL2 AdLib emulation"
            modules("java.prefs")
            macOS { iconFile.set(project.file("src/main/resources/icons/fedora.icns")) }
            windows { iconFile.set(project.file("src/main/resources/icons/fedora.ico")) }
            appResourcesRootDir.set(project.layout.buildDirectory.dir("app-resources"))
        }
    }
}

// ./gradlew run -Pgame=/path/to/MONKEY2
tasks.register<JavaExec>("runWoodtick") {
    group = "application"
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("adventureexplorer.woodtick.WoodtickMainKt")
    (project.findProperty("game") as String?)?.let { args(it) }
}

// ./gradlew songdump --args="/path/to/MONKEY2 101 104"
tasks.register<JavaExec>("songdump") {
    group = "application"
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("adventureexplorer.woodtick.SongDumpKt")
}

// README screenshots: ./gradlew woodtickshot --args="/path/to/MONKEY2 docs/screenshots"
tasks.register<JavaExec>("woodtickshot") {
    group = "application"
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("adventureexplorer.woodtick.WoodtickShotKt")
}

// ./gradlew notecounts --args="/path/to/MONKEY2 110"
tasks.register<JavaExec>("notecounts") {
    group = "application"
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("adventureexplorer.woodtick.NoteCounts")
}

// ./gradlew layerdump --args="/path/to/MONKEY2 20"
tasks.register<JavaExec>("layerdump") {
    group = "application"
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("adventureexplorer.woodtick.LayerDump")
}
