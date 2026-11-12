@file:Suppress("UnstableApiUsage", "Property_Name")

import dev.deftu.gradle.utils.GameSide
import org.gradle.jvm.toolchain.JavaLanguageVersion
import org.gradle.jvm.tasks.Jar

plugins {
    java

    val dgtVersion = "2.35.0"
    id("dev.deftu.gradle.tools") version(dgtVersion)
    id("dev.deftu.gradle.tools.resources") version(dgtVersion)
    id("dev.deftu.gradle.tools.bloom") version(dgtVersion)
    id("dev.deftu.gradle.tools.shadow") version(dgtVersion)
    id("dev.deftu.gradle.tools.minecraft.loom") version(dgtVersion)
    id("dev.deftu.gradle.tools.minecraft.releases") version(dgtVersion)
}

val modName = providers.gradleProperty("mod.name").get()
val modId = providers.gradleProperty("mod.id").get()
val modVersion = providers.gradleProperty("mod.version").get()
val modGroup = providers.gradleProperty("mod.group").get()

group = modGroup
version = modVersion

toolkitLoomHelper {
    // OneConfig V0 (cc.polyfrost) is declared in `dependencies` below. useOneConfig {} is the V1
    // integration and asks for org.polyfrost.oneconfig artifacts that do not exist for legacy Forge.

    useDevAuth("1.2.1")

    disableRunConfigs(GameSide.SERVER)
    useMixinRefMap(modData.id)

    if (mcData.isForge) {
        useForgeMixin(modData.id)
    }
}

repositories {
    maven("https://repo.polyfrost.org/releases")
    maven("https://repo.spongepowered.org/maven")
    maven("https://pkgs.dev.azure.com/djtheredstoner/DevAuth/_packaging/public/maven/v1")
}

dependencies {
    // Mixin API for compiling. OneConfig V0 ships Mixin at runtime only.
    compileOnly("org.spongepowered:mixin:0.7.11-SNAPSHOT")
    annotationProcessor("org.spongepowered:mixin:0.8.5-SNAPSHOT:processor")

    // OneConfig V0: compile against it only. The launchwrapper loads the real one at runtime.
    compileOnly("cc.polyfrost:oneconfig-1.8.9-forge:0.2.2-alpha+")

    // Launchwrapper tweaker: on the dev classpath so --tweakClass resolves, and bundled into the jar.
    implementation("cc.polyfrost:oneconfig-wrapper-launchwrapper:1.0.0-beta+")
    include("cc.polyfrost:oneconfig-wrapper-launchwrapper:1.0.0-beta+")

    runtimeOnly("me.djtheredstoner:DevAuth-forge-legacy:1.2.1")
}

// The code shared by every Mint module lives in ../shared. Bloom builds from a copy of the main source
// set and ignores anything set with setSource, so the shared code has to be part of the source set.
// It is copied into the build folder first, so Bloom sees ordinary files inside this project.
// The sourcepath is a second route that does not depend on Bloom: javac finds whatever the Forge
// code imports from the original folder.
val sharedSources = file("../shared/src/main/java")
val sharedCopy = layout.buildDirectory.dir("shared-src")

val syncShared by tasks.registering(Sync::class) {
    from(sharedSources)
    into(sharedCopy)
}

sourceSets.named("main") {
    java.srcDir(sharedCopy)
}

tasks.matching { it.name == "compileJava" || it.name.startsWith("bloom") }.configureEach {
    dependsOn(syncShared)
}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(8))
}

tasks.withType<JavaCompile>().configureEach {
    sourceCompatibility = "1.8"
    targetCompatibility = "1.8"
    options.encoding = "UTF-8"
    options.sourcepath = project.files(sharedSources)
}

tasks.withType<JavaExec>().configureEach {
    javaLauncher.set(javaToolchains.launcherFor {
        languageVersion.set(JavaLanguageVersion.of(8))
    })
}

tasks.withType<Jar>().configureEach {
    manifest.attributes["MixinConfigs"] = "mixins.mint.json"
    manifest.attributes["ModSide"] = "CLIENT"
    manifest.attributes["TweakOrder"] = 0
    manifest.attributes["ForceLoadAsMod"] = true
    manifest.attributes["TweakClass"] = "cc.polyfrost.oneconfig.loader.stage0.LaunchWrapperTweaker"
    manifest.attributes.remove("Class-Path")
}

configurations.all {
    exclude(group = "org.jetbrains.kotlin", module = "kotlin-reflect")

    // Forge 1.8.9 ASM cannot handle multi-release jars.
    exclude(group = "org.jetbrains.kotlinx", module = "kotlinx-coroutines-jdk8")
    exclude(group = "org.jetbrains.kotlinx", module = "kotlinx-coroutines-core")
    exclude(group = "org.jetbrains.kotlinx", module = "kotlinx-coroutines-core-jvm")
}

tasks.named<JavaExec>("runClient") {
    args("--tweakClass", "cc.polyfrost.oneconfig.loader.stage0.LaunchWrapperTweaker")
    jvmArgs(
        "-Ddevauth.enabled=true",
        "-Ddevauth.account=alt"
    )
}

// Bloom puts the mod version into Mint.java in place of @MOD_VERSION@.
tasks.processResources {
    inputs.property("mod_name", modName)
    inputs.property("mod_id", modId)
    inputs.property("mod_version", modVersion)

    filesMatching("mcmod.info") {
        expand(
            mapOf(
                "mod_name" to modName,
                "mod_id" to modId,
                "mod_version" to modVersion
            )
        )
    }
}
