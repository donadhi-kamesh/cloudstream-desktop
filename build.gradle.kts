plugins {
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.compose) apply false
}

allprojects {
    group = "dev.csdesktop"
    version = "1.0.0"
}

tasks.register("clean", Delete::class) {
    delete(rootProject.layout.buildDirectory)
}

tasks.register("stopBuildJvms") {
    doLast {
        ProcessHandle.allProcesses().forEach { handle ->
            val cmd = handle.info().commandLine().orElse("")
            val exe = handle.info().command().orElse("")
            val isJava = exe.contains("java", ignoreCase = true) || exe.contains("jpackage", ignoreCase = true)
            if (!isJava) return@forEach
            if (cmd.contains("KotlinCompileDaemon")) {
                handle.destroy()
            }
        }
        ProcessBuilder(
            "powershell",
            "-NoProfile",
            "-WindowStyle",
            "Hidden",
            "-Command",
            "Start-Sleep -Seconds 3; Get-CimInstance Win32_Process -Filter \"Name='java.exe'\" | Where-Object { \$_.CommandLine -match 'GradleDaemon|KotlinCompileDaemon' } | ForEach-Object { Stop-Process -Id \$_.ProcessId -Force -ErrorAction SilentlyContinue }",
        ).apply {
            redirectOutput(ProcessBuilder.Redirect.DISCARD)
            redirectError(ProcessBuilder.Redirect.DISCARD)
        }.start()
    }
}

subprojects {
    afterEvaluate {
        tasks.matching {
            it.name == "createDistributable" || it.name == "packageDeb"
        }.configureEach {
            finalizedBy(rootProject.tasks.named("stopBuildJvms"))
        }
    }
}
