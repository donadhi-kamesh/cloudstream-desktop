package dev.csdesktop.extloader

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class DexToJarConverterTest {
    @TempDir
    lateinit var tmp: File

    @Test
    fun `ensureTools unpacks a local dex-tools zip without network`() {
        val toolsDir = File(tmp, "dex2jar")
        val zip = File(tmp, "dex-tools-v2.4.zip")
        ZipOutputStream(zip.outputStream()).use { zos ->
            zos.putNextEntry(ZipEntry("dex-tools-v2.4/lib/dex-translator-v2.4.jar"))
            zos.write(byteArrayOf(0x50, 0x4B, 0x03, 0x04, 0x00, 0x00))
            zos.closeEntry()
        }
        val converter = DexToJarConverter(
            toolsDir = toolsDir,
            zipFile = zip,
            downloadUrl = "http://127.0.0.1:1/should-not-download",
        )
        converter.ensureTools()
        assertTrue(File(toolsDir, "lib/dex-translator-v2.4.jar").isFile)
    }

    @Test
    fun `isZipFile rejects missing and tiny files`() {
        assertTrue(!DexToJarConverter.isZipFile(File(tmp, "missing.zip")))
        val empty = File(tmp, "empty.zip")
        empty.writeBytes(byteArrayOf(1, 2))
        assertTrue(!DexToJarConverter.isZipFile(empty))
        val zip = File(tmp, "ok.zip")
        zip.writeBytes(byteArrayOf(0x50, 0x4B, 0x03, 0x04))
        assertTrue(DexToJarConverter.isZipFile(zip))
    }
}
