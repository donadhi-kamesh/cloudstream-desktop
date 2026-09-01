plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    jvmToolchain(21)
    jvm {
        compilations.all {
            compileTaskProvider.configure {
                compilerOptions {
                    jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21)
                    freeCompilerArgs.addAll(
                        "-Xexpect-actual-classes",
                        "-opt-in=kotlin.RequiresOptIn",
                        "-opt-in=com.lagradost.cloudstream3.InternalAPI",
                        "-opt-in=com.lagradost.cloudstream3.Prerelease",
                        "-opt-in=kotlin.uuid.ExperimentalUuidApi",
                        "-opt-in=kotlin.time.ExperimentalTime",
                        "-opt-in=kotlin.io.encoding.ExperimentalEncodingApi",
                    )
                }
            }
        }
    }

    sourceSets {
        all {
            languageSettings {
                optIn("com.lagradost.cloudstream3.InternalAPI")
                optIn("com.lagradost.cloudstream3.Prerelease")
                optIn("kotlin.uuid.ExperimentalUuidApi")
                optIn("kotlin.time.ExperimentalTime")
                optIn("kotlin.io.encoding.ExperimentalEncodingApi")
            }
        }

        commonMain.dependencies {
            api(libs.annotation)
            api(libs.jackson.module.kotlin)
            api(libs.jackson.databind)
            api(libs.jsoup)
            api(libs.kotlinx.atomicfu)
            api(libs.kotlinx.coroutines.core)
            api(libs.kotlinx.datetime)
            api(libs.kotlinx.io.core)
            api(libs.kotlinx.serialization.json)
            api(libs.ksoup)
            api(libs.ktor.http)
            api(libs.nicehttp)
            api(libs.okhttp)
            api(libs.rhino)
            api(libs.bundles.cryptography)
            api(libs.json)
        }

        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
        }

        jvmMain.dependencies {
            api(libs.kotlin.reflect)
            api(libs.newpipeextractor)
            api(libs.okhttp)
        }

        jvmTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlin.test.junit5)
            implementation(libs.junit.jupiter)
            runtimeOnly(libs.junit.platform.launcher)
        }
    }
}

tasks.named<Test>("jvmTest") {
    useJUnitPlatform()
    // Keep the process-wide cookie jar out of the user's real data directory.
    systemProperty(
        "csdesktop.cookieStore",
        layout.buildDirectory.file("test-cookies/cookies.properties").get().asFile.absolutePath,
    )
}
