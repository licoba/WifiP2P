package github.leavesczy.wifip2p.utils

import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack

class PCMStreamPlayer(
    private val sampleRate: Int = 16000,
    private val channelConfig: Int = AudioFormat.CHANNEL_OUT_MONO,
    private val audioFormat: Int = AudioFormat.ENCODING_PCM_16BIT
) {
    private var audioTrack: AudioTrack? = null
    private var isPlaying = false

    init {
        val bufferSize = AudioTrack.getMinBufferSize(sampleRate, channelConfig, audioFormat)
        audioTrack = AudioTrack(
            AudioManager.STREAM_MUSIC,
            sampleRate,
            channelConfig,
            audioFormat,
            bufferSize,
            AudioTrack.MODE_STREAM
        )
    }

    fun start() {
        if (isPlaying) {
            return
        }
        isPlaying = true
        audioTrack?.play()
    }

    fun stop() {
        if (!isPlaying) {
            return
        }
        isPlaying = false
        audioTrack?.stop()
        audioTrack?.release()
        audioTrack = null
    }

    fun write(data: ByteArray, offset: Int, size: Int) {
        if (!isPlaying) {
            throw IllegalStateException("AudioTrack is not playing. Call start() before writing data.")
        }
        audioTrack?.write(data, offset, size)
    }
}
