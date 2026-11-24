package com.vliveconvert.app

import com.vliveconvert.app.convert.Converter
import com.vliveconvert.app.core.BinaryUtils
import com.vliveconvert.app.core.FooterUtil
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.lang.management.ManagementFactory
import java.nio.file.Files

class LargeAssetProfileTest {

    private val liveId = "-1234567890".padEnd(28, '0')
    private val bean = ManagementFactory.getThreadMXBean() as com.sun.management.ThreadMXBean

    private fun box(type: String, payload: ByteArray): ByteArray {
        val bytes = ByteArray(8 + payload.size)
        BinaryUtils.writeU32BE(bytes, 0, (payload.size + 8).toLong())
        System.arraycopy(type.toByteArray(Charsets.ISO_8859_1), 0, bytes, 4, 4)
        System.arraycopy(payload, 0, bytes, 8, payload.size)
        return bytes
    }

    private fun makePair(dir: File): File {
        val jpeg = java.io.ByteArrayOutputStream()
        jpeg.write(byteArrayOf(0xFF.toByte(), 0xD8.toByte()))
        jpeg.write(byteArrayOf(0xFF.toByte(), 0xC0.toByte(), 0, 11, 8, 0, 16, 0, 16, 1, 1, 0x11, 0))
        jpeg.write(byteArrayOf(0xFF.toByte(), 0xDA.toByte(), 0, 8, 1, 1, 0, 0, 0x3F, 0))
        jpeg.write(ByteArray(32 * 1024 * 1024) { ((it * 31 + 7) and 0x7F).toByte() })
        jpeg.write(byteArrayOf(0xFF.toByte(), 0xD9.toByte()))
        val json = FooterUtil.buildFooterJson(linkedMapOf(
            "com.android.camera.imageTime" to 12L,
            "com.android.camera.livephoto" to liveId,
            "version" to 2107
        ))
        val footer = FooterUtil.buildFooter(json, liveId, FooterUtil.vivoPrefix)
        val jpg = File(dir, "IMG_LARGE_PROFILE.jpg").apply {
            writeBytes(jpeg.toByteArray() + FooterUtil.vivoPrefix + footer)
        }
        val extInfo = box("uuid", "vivoMediaExtInfo".toByteArray(Charsets.US_ASCII) +
            FooterUtil.buildFooter(json, liveId, FooterUtil.vivoPrefix))
        File(dir, "IMG_LARGE_PROFILE.mp4").writeBytes(
            box("ftyp", "isom".toByteArray() + byteArrayOf(0, 0, 2, 0) + "isom".toByteArray()) +
                box("mdat", ByteArray(128 * 1024 * 1024) { ((it * 13 + 5) and 0x7F).toByte() }) +
                box("moov", ByteArray(0)) +
                box("uuid", "vivoMediaEStream".toByteArray(Charsets.US_ASCII) + ByteArray(122)) +
                extInfo)
        return jpg
    }

    @Test
    fun profileLargeConversion() {
        assumeTrue("设置 VLC_PROFILE_LARGE=1 才运行大夹具", System.getenv("VLC_PROFILE_LARGE") == "1")
        assertTrue(bean.isThreadAllocatedMemorySupported)
        val source = Files.createTempDirectory("vlc_large_src").toFile()
        val output = Files.createTempDirectory("vlc_large_out").toFile()
        val jpg = makePair(source)
        val threadId = Thread.currentThread().threadId()
        val allocatedBefore = bean.getThreadAllocatedBytes(threadId)
        val started = System.nanoTime()
        val result = Converter.convertToVivoSingle(jpg.absolutePath, output.absolutePath) { _, _, _ -> }
        val elapsedMs = (System.nanoTime() - started) / 1_000_000
        val allocated = bean.getThreadAllocatedBytes(threadId) - allocatedBefore
        assertTrue(File(result.path).length() > 128L * 1024 * 1024)
        println("[LargeAssetProfile] jpeg=32MiB mp4=128MiB allocated=${allocated / 1048576}MiB elapsed=${elapsedMs}ms")
    }
}
