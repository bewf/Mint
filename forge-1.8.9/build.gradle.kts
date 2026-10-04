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
    useOneConfig {
        version = "1.0.0-alpha.106"
        loaderVersion = "1.1.0-alpha.46"

        usePolyMixin = true
        polyMixinVersion = "0.8.4+build.2"

        // I embed stage0 in my jar, so I keep this off.
        applyLoaderTweaker = false

        for (module in arrayOf("commands", "config", "config-impl", "events", "hud", "internal", "ui", "utils")) {
            +module
        }
    }

    useDevAuth("1.2.1")
    useMixinExtras("0.4.1")

    disableRunConfigs(GameSide.SERVER)

    useMixinRefMap(modData.id)

    if (mcData.isForge) {
        useForgeMixin(modData.id)
    }
}

repositories {
    maven("https://repo.polyfrost.org/releases")
    maven("https://pkgs.dev.azure.com/djtheredstoner/DevAuth/_packaging/public/maven/v1")
}

dependencies {
    implementation("cc.polyfrost:oneconfig-1.8.9-forge:0.2.2-alpha+")
    shade("cc.polyfrost:oneconfig-1.8.9-forge:0.2.2-alpha+")
    include("cc.polyfrost:oneconfig-1.8.9-forge:0.2.2-alpha+")

    implementation("cc.polyfrost:oneconfig-wrapper-launchwrapper:1.0.0-beta17")
    shade("cc.polyfrost:oneconfig-wrapper-launchwrapper:1.0.0-beta17")
    include("cc.polyfrost:oneconfig-wrapper-launchwrapper:1.0.0-beta17")

    implementation("org.polyfrost.oneconfig:stage0:1.1.0-alpha.46")
    shade("org.polyfrost.oneconfig:stage0:1.1.0-alpha.46")
    include("org.polyfrost.oneconfig:stage0:1.1.0-alpha.46")

    runtimeOnly("me.djtheredstoner:DevAuth-forge-legacy:1.2.1")
}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(8))
}

tasks.withType<JavaCompile>().configureEach {
    sourceCompatibility = "1.8"
    targetCompatibility = "1.8"
    options.encoding = "UTF-8"
}

tasks.withType<JavaExec>().configureEach {
    javaLauncher.set(javaToolchains.launcherFor {
        languageVersion.set(JavaLanguageVersion.of(8))
    })
}

tasks.withType<Jar>().configureEach {
    manifest.attributes["ModSide"] = "CLIENT"
    manifest.attributes["TweakOrder"] = 0
    manifest.attributes["ForceLoadAsMod"] = true
    manifest.attributes["TweakClass"] = "cc.polyfrost.oneconfig.loader.stage0.LaunchWrapperTweaker"
    manifest.attributes.remove("Class-Path")
}

configurations.all {
    exclude(group = "org.jetbrains.kotlin", module = "kotlin-reflect")

    // Forge 1.8.9 ASM dies on multi-release jars (module-info.class under META-INF/versions/*)
    exclude(group = "org.jetbrains.kotlinx", module = "kotlinx-coroutines-jdk8")
    exclude(group = "org.jetbrains.kotlinx", module = "kotlinx-coroutines-core")
    exclude(group = "org.jetbrains.kotlinx", module = "kotlinx-coroutines-core-jvm")
}

tasks.named<JavaExec>("runClient") {
    args("--tweakClass", "cc.polyfrost.oneconfig.loader.stage0.LaunchWrapperTweaker")
    jvmArgs("-Ddevauth.enabled=true", "-Ddevauth.account=alt")
}

/* Version sync: gradle.properties -> generated Mint.java + mcmod.info */

val generatedDir = layout.buildDirectory.dir("generated/sources/versionedMint")

val generateVersionedMint = tasks.register<Copy>("generateVersionedMint") {
    from("src/main/java/me/bewf/mint/Mint.java")
    into(generatedDir.map { it.dir("me/bewf/mint") })

    filteringCharset = "UTF-8"

    filter { line: String ->
        line
            .replace("@MOD_NAME@", modName)
            .replace("@MOD_ID@", modId)
            .replace("@MOD_VERSION@", modVersion)
    }
}

val mainJavaWithoutMint = fileTree("src/main/java") {
    include("**/*.java")
    exclude("me/bewf/mint/Mint.java")
}

tasks.named<JavaCompile>("compileJava") {
    dependsOn(generateVersionedMint)
    setSource(mainJavaWithoutMint + fileTree(generatedDir))
}

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
