package github.leavesczy.wifip2p.utils
import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import androidx.core.app.ActivityCompat

@SuppressLint("MissingPermission")
class RealTimeAudioRecorder(
    private val sampleRate: Int = 16000,
    private val channelConfig: Int = AudioFormat.CHANNEL_IN_MONO,
    private val audioFormat: Int = AudioFormat.ENCODING_PCM_16BIT
) {
    private var audioRecord: AudioRecord? = null
    private var recordingThread: Thread? = null
    private var isRecording = false
    private var bufferSize: Int = 0

    init {
        bufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat)
        audioRecord = AudioRecord(
            MediaRecorder.AudioSource.VOICE_COMMUNICATION, // 使用通话降噪模式
            sampleRate,
            channelConfig,
            audioFormat,
            bufferSize
        )
    }

    interface AudioDataCallback {
        fun onAudioData(data: ByteArray, size: Int)
    }

    fun startRecording(callback: AudioDataCallback) {
        if (isRecording) return

        audioRecord?.startRecording()
        isRecording = true

        recordingThread = Thread {
            readAudioData(callback)
        }.apply {
            start()
        }
    }

    private fun readAudioData(callback: AudioDataCallback) {
        val data = ByteArray(bufferSize)

        while (isRecording) {
            val read = audioRecord?.read(data, 0, bufferSize) ?: 0
            if (read > 0) {
                callback.onAudioData(data, read)
            }
        }
    }

    fun stopRecording() {
        if (!isRecording) return

        isRecording = false
        audioRecord?.stop()
        recordingThread?.join()
        recordingThread = null
    }

    fun release() {
        audioRecord?.release()
        audioRecord = null
    }
}
