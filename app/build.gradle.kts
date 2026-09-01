import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import java.io.File

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.compose)
}

kotlin {
    jvmToolchain(21)
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21)
        freeCompilerArgs.addAll(
            "-opt-in=com.lagradost.cloudstream3.InternalAPI",
            "-opt-in=com.lagradost.cloudstream3.Prerelease",
            "-opt-in=kotlin.uuid.ExperimentalUuidApi",
            "-opt-in=kotlin.time.ExperimentalTime",
            "-opt-in=androidx.compose.material3.ExperimentalMaterial3Api",
        )
    }
}

dependencies {
    implementation(project(":extloader"))
    implementation(compose.desktop.currentOs)
    implementation(compose.material3)
    implementation(compose.materialIconsExtended)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.swing)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.ktor.server.core)
    implementation(libs.ktor.server.cio)
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.cio)
    implementation(libs.ktor.client.okhttp)
    implementation(libs.okhttp)
    implementation(libs.jna)
    implementation(libs.jna.platform)
    implementation(libs.sqlite)
    implementation(libs.coil.compose)
    implementation(libs.coil.okhttp)
    implementation(libs.jackson.module.kotlin)
    implementation(libs.commons.compress)
    implementation(libs.xz)
    testImplementation(libs.kotlin.test)
    testImplementation(libs.kotlin.test.junit5)
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}

compose.desktop {
    application {
        mainClass = "dev.csdesktop.MainKt"
        nativeDistributions {
            val os = System.getProperty("os.name").orEmpty().lowercase()
            if (!os.contains("win")) {
                targetFormats(TargetFormat.Deb)
            }
            packageName = "CloudStreamDesktop"
            packageVersion = "1.0.0"
            description = "CloudStream Desktop — empty media-center shell"
            copyright = "GPL-3.0. Derived from CloudStream (c) reCloudStream contributors. Not affiliated."
            vendor = "cs-desktop"
            // jpackage strips unused JDK modules; sqlite-jdbc, JNA, OkHttp, and Ktor need these at runtime.
            modules(
                "java.sql",
                "java.naming",
                "java.logging",
                "java.management",
                "java.net.http",
                "java.instrument",
                "java.prefs",
                "java.security.jgss",
                "java.xml",
                "java.xml.crypto",
                "jdk.crypto.ec",
                "jdk.unsupported",
                "jdk.httpserver",
                "jdk.charsets",
                "jdk.localedata",
                "jdk.zipfs",
            )
            windows {
                menuGroup = "CloudStream Desktop"
                upgradeUuid = "3b6f1d2e-8c4a-4e91-9b7f-21a0c5d8e6aa"
            }
            linux {
                debMaintainer = "cs-desktop"
                menuGroup = "AudioVideo"
            }
        }
        jvmArgs += listOf("-Xmx512m", "-Dfile.encoding=UTF-8")
    }
}

tasks.test {
    useJUnitPlatform()
}

afterEvaluate {
    tasks.findByName("packageMsi")?.enabled = false
    tasks.findByName("packageExe")?.enabled = false
    tasks.findByName("createDistributable")?.doLast {
        val jdkBin = File(System.getProperty("java.home"), "bin")
        val jsvml = File(jdkBin, "jsvml.dll")
        val runtimeBin = layout.buildDirectory
            .dir("compose/binaries/main/app/CloudStreamDesktop/runtime/bin")
            .get()
            .asFile
        if (jsvml.isFile && runtimeBin.isDirectory) {
            jsvml.copyTo(File(runtimeBin, "jsvml.dll"), overwrite = true)
        }
        val portable = layout.buildDirectory
            .dir("compose/binaries/main/app/CloudStreamDesktop")
            .get()
            .asFile
        val dist = rootProject.layout.projectDirectory.dir("dist").asFile
        dist.mkdirs()
        File(dist, "CloudStreamDesktop-1.0.0.msi").delete()
        File(dist, "CloudStreamDesktop-1.0.0.exe").delete()
        val dest = File(dist, "CloudStreamDesktop")
        if (portable.isDirectory) {
            File(dest, "CloudStreamDesktop").deleteRecursively()
            val staging = File(dist, "CloudStreamDesktop.staging")
            staging.deleteRecursively()
            portable.copyRecursively(staging)
            if (dest.exists()) dest.deleteRecursively()
            if (!staging.renameTo(dest)) {
                dest.deleteRecursively()
                staging.copyRecursively(dest, overwrite = true)
                staging.deleteRecursively()
            }
        }
    }
}
