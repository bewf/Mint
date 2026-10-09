import org.gradle.api.tasks.testing.logging.TestExceptionFormat

plugins {
    id("dev.kikugie.loom-back-compat")
    id("org.jetbrains.kotlin.jvm") version "2.4.10"
    id("org.jetbrains.kotlin.plugin.compose") version "2.4.10"
    id("dev.deftu.gradle.bloom") version "0.2.0"
    id("me.modmuss50.mod-publish-plugin") version "2.2.0"
}

val modid: String = sc.properties["mod.id"]
val modname: String = sc.properties["mod.name"]
val modversion: String = sc.properties["mod.version"]
val mcversion: String = sc.current.version
val versionrange: String = sc.properties["mod.mc_compat"]
val loaderversion: String = sc.properties["deps.fabric_loader"]
val oneconfigversion: String = sc.properties["deps.oneconfig"]
val fapiversion: String = sc.properties["deps.fabric_api"]
val composeversion: String = sc.properties["deps.compose"]
val skikoversion: String = sc.properties["deps.skiko"]
val coroutinesversion: String = sc.properties["deps.coroutines"]

version = "$modversion+$mcversion"
base.archivesName = modid

val requiredJava: JavaVersion = JavaVersion.VERSION_25

val compatibleVersions: List<String> = sc.properties.rawOrNull("mod", "mc_releases")
    ?.asList().orEmpty().map { it.toString() }

repositories {
    fun strictMaven(url: String, alias: String, vararg groups: String) = exclusiveContent {
        forRepository { maven(url) { name = alias } }
        filter { groups.forEach(::includeGroup) }
    }

    mavenCentral()
    google()
    maven("https://redirector.kotlinlang.org/maven/compose-dev")
    maven("https://repo.polyfrost.org/releases") { name = "Polyfrost Releases" }
    maven("https://repo.polyfrost.org/snapshots") { name = "Polyfrost Snapshots" }
    maven("https://central.sonatype.com/repository/maven-snapshots") {
        name = "Sonatype Snapshots"
        content { includeGroup("net.kyori") }
    }
    strictMaven("https://maven.deftu.dev/releases", "Deftu", "dev.deftu")
    strictMaven("https://maven.terraformersmc.com/", "TerraformersMC", "com.terraformersmc")
    strictMaven("https://maven.fabricmc.net/", "FabricMC", "net.fabricmc")
    strictMaven("https://www.cursemaven.com", "CurseForge", "curse.maven")
    strictMaven("https://api.modrinth.com/maven", "Modrinth", "maven.modrinth")
}

dependencies {
    minecraft("com.mojang:minecraft:$mcversion")
    loomx.applyMojangMappings()

    modImplementation("net.fabricmc:fabric-loader:$loaderversion")

    modImplementation("org.polyfrost.oneconfig:$mcversion-fabric:$oneconfigversion")
    for (module in arrayOf("config", "config-impl", "events", "hud", "internal", "ui", "utils", "poly-compose")) {
        implementation("org.polyfrost.oneconfig:$module:$oneconfigversion")
    }

    compileOnly("org.jetbrains.compose.runtime:runtime-desktop:$composeversion")
    compileOnly("org.jetbrains.skiko:skiko-awt:$skikoversion")
    compileOnly("org.jetbrains.kotlinx:kotlinx-coroutines-core:$coroutinesversion")

    for (module in arrayOf("fabric-events-interaction-v0", "fabric-message-api-v1", "fabric-lifecycle-events-v1")) {
        modImplementation(fabricApi.module(module, fapiversion))
    }
}

loom {
    fabricModJsonPath = rootProject.file("src/main/resources/fabric.mod.json")

    decompilerOptions.named("vineflower") {
        options.put("mark-corresponding-synthetics", "1")
    }

    runConfigs.all {
        preferGradleTask = true
        generateRunConfig = true
        runDirectory = rootProject.file("run")
    }

    runConfigs.remove(runConfigs["server"])
}

java {
    withSourcesJar()
    targetCompatibility = requiredJava
    sourceCompatibility = requiredJava

    toolchain {
        vendor = JvmVendorSpec.ADOPTIUM
        languageVersion = JavaLanguageVersion.of(requiredJava.majorVersion)
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.release = requiredJava.majorVersion.toInt()
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    compilerOptions.jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_25)
}

bloom {
    replacement("@MOD_ID@", modid)
    replacement("@MOD_NAME@", modname)
    replacement("@MOD_VERSION@", modversion)
}

tasks {
    test {
        useJUnitPlatform()
        testLogging {
            showStackTraces = true
            exceptionFormat = TestExceptionFormat.FULL
        }
    }

    processResources {
        val props = mapOf(
            "mod_id" to modid,
            "mod_name" to modname,
            "mod_version" to modversion,
            "minecraft_version_range" to versionrange,
            "loader_version" to loaderversion
        )

        inputs.properties(props)

        filesMatching("fabric.mod.json") { expand(props) }
    }

    jar {
        inputs.property("archivesName", base.archivesName)

        from(rootProject.file("LICENSE.md")) {
            rename { "${it}_${inputs.properties["archivesName"]}" }
        }
    }

    register<Copy>("buildAndCollect") {
        group = "build"
        description = "Builds mod jars and copies results to `build/libs/{mod version}/`"

        inputs.property("version", modversion)
        from(loomx.modJar.flatMap { it.archiveFile }, loomx.modSourcesJar.flatMap { it.archiveFile })
        into(rootProject.layout.buildDirectory.file("libs/$modversion"))
    }
}

val modrinthId = listOf("oneconfig.publish.modrinth", "publish.modrinth")
    .firstNotNullOfOrNull { sc.properties.getOrNull<String>(it) ?: findProperty(it)?.toString() }
    ?.takeIf { it.isNotBlank() }
val modrinthToken = listOf("oneconfig.publish.modrinth.token", "publish.modrinth.token", "modrinth.token")
    .firstNotNullOfOrNull { findProperty(it) }?.toString()?.takeIf { it.isNotBlank() }

val changelogs = rootProject.file("CHANGELOG.md").takeIf { it.exists() }?.readText() ?: "No changelog provided."

publishMods {
    file = loomx.modJar.flatMap { it.archiveFile }

    displayName = modversion
    version = "v$modversion"
    changelog = changelogs
    type = me.modmuss50.mpp.ReleaseType.STABLE

    modLoaders.add("fabric")

    dryRun = modrinthId == null || modrinthToken == null

    if (modrinthId != null) {
        modrinth {
            projectId = modrinthId
            accessToken = modrinthToken.orEmpty()

            minecraftVersions.addAll(compatibleVersions.ifEmpty { listOf(mcversion) })

            requires("oneconfig", "fabric-api")
        }
    }
}
