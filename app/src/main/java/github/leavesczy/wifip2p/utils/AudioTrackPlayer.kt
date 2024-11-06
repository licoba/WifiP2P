package github.leavesczy.wifip2p.utils

import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

class AudioTrackPlayer(
    private val sampleRate: Int = 16000,
    private val channelConfig: Int = AudioFormat.CHANNEL_OUT_MONO,
    private val audioFormat: Int = AudioFormat.ENCODING_PCM_16BIT
) {
    private val audioTrack: AudioTrack
    private val audioBufferQueue = LinkedBlockingQueue<Byte>()
    private var isPlaying = false
    private val bufferSize: Int = AudioTrack.getMinBufferSize(sampleRate, channelConfig, audioFormat)

    init {
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
        if (isPlaying) return
        isPlaying = true
        audioTrack.play()
        Thread {
            val buffer = ByteArray(bufferSize)
            while (isPlaying) {
                var bytesRead = 0
                while (bytesRead < bufferSize) {
                    val byte = audioBufferQueue.poll(10, TimeUnit.MILLISECONDS)
                    if (byte != null) {
                        buffer[bytesRead++] = byte
                    } else if (!isPlaying) {
                        break
                    }
                }
                if (bytesRead > 0) {
                    audioTrack.write(buffer, 0, bytesRead)
                }
            }
        }.start()
    }

    fun stop() {
        isPlaying = false
        audioTrack.stop()
        audioTrack.flush()
        audioBufferQueue.clear()
    }

    fun release() {
        stop()
        audioTrack.release()
    }

    fun write(data: ByteArray) {
        for (byte in data) {
            audioBufferQueue.offer(byte)
        }
    }
}
