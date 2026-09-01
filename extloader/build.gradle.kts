plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
}

import java.net.URI


kotlin {
    jvmToolchain(21)
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21)
        freeCompilerArgs.addAll(
            "-opt-in=com.lagradost.cloudstream3.InternalAPI",
            "-opt-in=com.lagradost.cloudstream3.Prerelease",
            "-opt-in=kotlin.uuid.ExperimentalUuidApi",
        )
    }
}

dependencies {
    api(project(":core"))
    api(libs.okhttp)
    api(libs.jackson.module.kotlin)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.jsoup)
    implementation(libs.json)
    api(libs.annotation)
    implementation(libs.dexlib2)
    implementation(libs.asm)
    implementation(libs.asm.commons)
    implementation(libs.guava)
    testImplementation(libs.kotlin.test)
    testImplementation(libs.kotlin.test.junit5)
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.test {
    useJUnitPlatform()
}

val androidCompatDir = layout.buildDirectory.dir("android-compat")
val androidJarFile = androidCompatDir.map { it.file("android.jar") }
val aarDir = androidCompatDir.map { it.dir("aars") }
val mergerClasses = layout.buildDirectory.dir("android-compat-merger")

val downloadAndroidCompat by tasks.registering {
    outputs.file(androidJarFile)
    outputs.dir(aarDir)
    doLast {
        val jar = androidJarFile.get().asFile
        jar.parentFile.mkdirs()
        if (jar.length() < 1_000_000L) {
            val urls = listOf(
                "https://github.com/Sable/android-platforms/raw/master/android-33/android.jar",
                "https://github.com/Sable/android-platforms/raw/refs/heads/master/android-33/android.jar",
            )
            var last: Exception? = null
            for (url in urls) {
                try {
                    URI(url).toURL().openStream().use { input ->
                        jar.outputStream().use { input.copyTo(it) }
                    }
                    last = null
                    break
                } catch (e: Exception) {
                    last = e
                }
            }
            if (last != null && jar.length() < 1_000_000L) throw last
        }
        val dest = aarDir.get().asFile
        dest.mkdirs()
        val aars = listOf(
            "androidx/preference/preference/1.2.1/preference-1.2.1.aar",
            "androidx/fragment/fragment/1.6.2/fragment-1.6.2.aar",
            "androidx/appcompat/appcompat/1.6.1/appcompat-1.6.1.aar",
            "androidx/recyclerview/recyclerview/1.3.2/recyclerview-1.3.2.aar",
            "androidx/core/core/1.13.1/core-1.13.1.aar",
            "com/google/android/material/material/1.11.0/material-1.11.0.aar",
            "androidx/coordinatorlayout/coordinatorlayout/1.2.0/coordinatorlayout-1.2.0.aar",
        )
        for (rel in aars) {
            val name = rel.substringAfterLast('/')
            val out = File(dest, name)
            if (out.length() > 1000L) continue
            val url = "https://maven.google.com/$rel"
            runCatching {
                URI(url).toURL().openStream().use { input ->
                    out.outputStream().use { input.copyTo(it) }
                }
            }
        }
    }
}

val asmForMerger = configurations.detachedConfiguration(
    dependencies.create("org.ow2.asm:asm:9.7.1"),
    dependencies.create("org.ow2.asm:asm-tree:9.7.1"),
)

val compileAndroidCompatMerger by tasks.registering(JavaCompile::class) {
    source(fileTree("android-compat") { include("*.java") })
    classpath = asmForMerger
    destinationDirectory.set(mergerClasses)
    sourceCompatibility = "21"
    targetCompatibility = "21"
    options.encoding = "UTF-8"
    include("**/*.java")
}

val mergeAndroidCompat by tasks.registering(JavaExec::class) {
    dependsOn("compileJava", "compileKotlin", downloadAndroidCompat, compileAndroidCompatMerger)
    classpath = files(mergerClasses) + asmForMerger
    mainClass.set("MergeAndroidCompat")
    val javaOut = layout.buildDirectory.dir("classes/java/main")
    val kotlinOut = layout.buildDirectory.dir("classes/kotlin/main")
    argumentProviders.add {
        buildList {
            add("--android-jar")
            add(androidJarFile.get().asFile.absolutePath)
            add("--out")
            add(javaOut.get().asFile.absolutePath)
            add("--classes")
            add(javaOut.get().asFile.absolutePath)
            val k = kotlinOut.get().asFile
            if (k.isDirectory) {
                add("--classes")
                add(k.absolutePath)
            }
            val aars = aarDir.get().asFile
            if (aars.isDirectory) {
                aars.listFiles()?.forEach { f ->
                    if (f.isFile) {
                        add("--lib")
                        add(f.absolutePath)
                    }
                }
            }
        }
    }
    inputs.dir(javaOut)
    inputs.file(androidJarFile)
    outputs.dir(javaOut)
}

tasks.named("classes") { dependsOn(mergeAndroidCompat) }
tasks.named("jar") { dependsOn(mergeAndroidCompat) }
