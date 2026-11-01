package com.chrizzzzilla.self.focus.audio

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import java.io.File
import java.nio.ByteOrder

/**
 * Dekodiert eine OGG-Datei aus den Assets in 16-Bit-PCM (Stereo, interleaved).
 * Die Datei wird dafuer einmal in den App-Cache kopiert (MediaExtractor braucht einen echten Pfad).
 */
internal object SampleDecoder {

    fun decode(context: Context, assetPath: String): ShortArray? = runCatching {
        val file = File(context.cacheDir, "audio/" + assetPath.replace('/', '_'))
        if (!file.exists()) {
            file.parentFile?.mkdirs()
            context.assets.open(assetPath).use { input -> file.outputStream().use { input.copyTo(it) } }
        }

        val extractor = MediaExtractor()
        extractor.setDataSource(file.absolutePath)
        val track = (0 until extractor.trackCount).first {
            extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true
        }
        extractor.selectTrack(track)
        val format = extractor.getTrackFormat(track)
        val codec = MediaCodec.createDecoderByType(format.getString(MediaFormat.KEY_MIME)!!)
        codec.configure(format, null, null, 0)
        codec.start()

        var channels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
        var out = ShortArray(44_100 * 2 * 70)
        var size = 0
        val info = MediaCodec.BufferInfo()
        var inputDone = false
        var outputDone = false
        while (!outputDone) {
            if (!inputDone) {
                val inIndex = codec.dequeueInputBuffer(10_000)
                if (inIndex >= 0) {
                    val buf = codec.getInputBuffer(inIndex)!!
                    val read = extractor.readSampleData(buf, 0)
                    if (read < 0) {
                        codec.queueInputBuffer(inIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                        inputDone = true
                    } else {
                        codec.queueInputBuffer(inIndex, 0, read, extractor.sampleTime, 0)
                        extractor.advance()
                    }
                }
            }
            val outIndex = codec.dequeueOutputBuffer(info, 10_000)
            when {
                outIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                    channels = codec.outputFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                }
                outIndex >= 0 -> {
                    val buf = codec.getOutputBuffer(outIndex)!!
                    buf.position(info.offset)
                    buf.limit(info.offset + info.size)
                    val shorts = buf.order(ByteOrder.nativeOrder()).asShortBuffer()
                    val n = shorts.remaining()
                    val frames = n / channels
                    val needed = size + frames * 2
                    if (needed > out.size) out = out.copyOf(maxOf(needed, out.size * 2))
                    for (f in 0 until frames) {
                        val l = shorts.get(f * channels)
                        val r = if (channels > 1) shorts.get(f * channels + 1) else l
                        out[size++] = l
                        out[size++] = r
                    }
                    codec.releaseOutputBuffer(outIndex, false)
                    if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) outputDone = true
                }
            }
        }
        codec.stop()
        codec.release()
        extractor.release()
        out.copyOf(size)
    }.getOrNull()
}
