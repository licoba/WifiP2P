package github.leavesczy.wifip2p.utils

import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.util.concurrent.Executors
import java.util.concurrent.LinkedBlockingQueue

class PCMStreamPlayer(
    private val sampleRate: Int = 16000,
    private val channelConfig: Int = AudioFormat.CHANNEL_OUT_MONO,
    private val audioFormat: Int = AudioFormat.ENCODING_PCM_16BIT
) {
    private var audioTrack: AudioTrack? = null
    private var isPlaying = false
    private val minBufferSize = AudioTrack.getMinBufferSize(sampleRate, channelConfig, audioFormat) // 4116

    init {
        audioTrack = AudioTrack(
            AudioManager.STREAM_MUSIC,
            sampleRate,
            channelConfig,
            audioFormat,
            minBufferSize,
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

    fun write(data: ByteArray) {
        if (!isPlaying) {
            throw IllegalStateException("AudioTrack is not playing. Call start() before writing data.")
        }
        Log.d("TAG","最小长度："+minBufferSize)
        audioTrack?.write(data, 0, data.size)
    }
}

