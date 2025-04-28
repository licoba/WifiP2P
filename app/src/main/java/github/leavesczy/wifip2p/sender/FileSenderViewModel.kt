package github.leavesczy.wifip2p.sender

import android.app.Application
import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.core.net.toFile
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.blankj.utilcode.util.FileIOUtils
import com.blankj.utilcode.util.FileUtils
import com.blankj.utilcode.util.PathUtils
import github.leavesczy.wifip2p.common.Constants
import github.leavesczy.wifip2p.common.FileTransfer
import github.leavesczy.wifip2p.common.FileTransferViewState
import github.leavesczy.wifip2p.utils.FIFOBytes
import github.leavesczy.wifip2p.utils.MyFileUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.io.ObjectInputStream
import java.io.ObjectOutputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import kotlin.random.Random

/**
 * @Author: CZY
 * @Date: 2022/9/26 10:38
 * @Desc:
 */
class FileSenderViewModel(context: Application) : AndroidViewModel(context) {

    private val _fileTransferViewState = MutableSharedFlow<FileTransferViewState>()

    val fileTransferViewState: SharedFlow<FileTransferViewState>
        get() = _fileTransferViewState

    private val _log = MutableSharedFlow<String>()

    val log: SharedFlow<String>
        get() = _log

    private var job: Job? = null
    private var receiveJob: Job? = null


    private var fifoRealTime = FIFOBytes(1024 * 1024 * 10)

    fun sendLocalAudioFile(ipAddress: String) {
        if (job != null) {
            return
        }
        job = viewModelScope.launch {
            withContext(context = Dispatchers.IO) {
                _fileTransferViewState.emit(value = FileTransferViewState.Idle)

                var socket: Socket? = null
                var outputStream: OutputStream? = null
                var objectOutputStream: ObjectOutputStream? = null
                var fileInputStream: FileInputStream? = null
                try {
                    val cacheFile =
                        File(PathUtils.getInternalAppDataPath() + "/audio/localTest.pcm")
                    FileUtils.createFileByDeleteOldFile(cacheFile)
                    FileIOUtils.writeFileFromBytesByStream(
                        cacheFile,
                        MyFileUtils.readFileFromAssets(getApplication(), "长句.pcm")
                    )
                    val fileTransfer = FileTransfer(fileName = cacheFile.name)
                    _fileTransferViewState.emit(value = FileTransferViewState.Connecting)
                    _log.emit(value = "待发送的本地音频文件: $fileTransfer")
                    _log.emit(value = "开启 Socket")

                    socket = Socket()
                    socket.bind(null)

                    _log.emit(value = "socket connect，如果三十秒内未连接成功则放弃")

                    socket.connect(InetSocketAddress(ipAddress, Constants.PORT), 30000)

                    _fileTransferViewState.emit(value = FileTransferViewState.Receiving())
                    _log.emit(value = "连接成功，开始传输本地音频文件")

                    outputStream = socket.getOutputStream()
                    objectOutputStream = ObjectOutputStream(outputStream)
                    objectOutputStream.writeObject(fileTransfer)
                    fileInputStream = FileInputStream(cacheFile)
                    val buffer = ByteArray(1024 * 100)
                    var length: Int
                    while (true) {
                        length = fileInputStream.read(buffer)
                        if (length > 0) {
                            outputStream.write(buffer, 0, length)
                        } else {
                            break
                        }
                        _log.emit(value = "正在传输本地音频文件，length : $length")
                    }
                    _log.emit(value = "本地音频文件发送成功")
                    _fileTransferViewState.emit(value = FileTransferViewState.Success(file = cacheFile))
                } catch (e: Throwable) {
                    e.printStackTrace()
                    _log.emit(value = "发送本地音频文件异常: " + e.message)
                    _fileTransferViewState.emit(value = FileTransferViewState.Failed(throwable = e))
                } finally {
                    fileInputStream?.close()
                    outputStream?.close()
                    objectOutputStream?.close()
                    socket?.close()
                }
            }
        }
        job?.invokeOnCompletion {
            job = null
        }
    }


    private var realTimeJob: Job? = null


    fun sendRealTimeAudio(ipAddress: String, data: ByteArray) {
        startRealTimeSendLoop(ipAddress)
        viewModelScope.launch {
            fifoRealTime.push(data)
        }
    }

    private var isLooping: Boolean = false
    private fun startRealTimeSendLoop(ipAddress: String) {
        if (isLooping) {
            return
        }
        isLooping = true
        realTimeJob = viewModelScope.launch(Dispatchers.IO) {
            Log.d(TAG, "开启socket和输出流")
            var socket: Socket? = null
            var outputStream: OutputStream? = null   // 发送字节流
            var objectOutputStream: ObjectOutputStream? = null   // 发送文件信息
            try {
                _log.emit(value = "开启 Socket")
                socket = Socket()
                socket.bind(null)
                _log.emit(value = "socket connect，如果三十秒内未连接成功则放弃")
                socket.connect(InetSocketAddress(ipAddress, Constants.PORT), 30000)
                _log.emit(value = "连接成功，开始传输本地音频文件")
                outputStream = socket.getOutputStream()
                objectOutputStream = ObjectOutputStream(outputStream)
                objectOutputStream.writeObject(FileTransfer(fileName = "realTime.pcm"))
                while (true) {
                    val length: Int = 1024 * 10
                    val buffer = fifoRealTime.pop(length)
                    outputStream.write(buffer, 0, length)
                    Log.d(TAG, "正在传输本地音频文件，length : $length")
                }
            } catch (e: Throwable) {
                e.printStackTrace()
                _log.emit(value = "发送本地音频文件异常: " + e.message)
                _fileTransferViewState.emit(value = FileTransferViewState.Failed(throwable = e))
            } finally {
                outputStream?.close()
                objectOutputStream?.close()
                socket?.close()
                isLooping = false
            }
        }
        realTimeJob?.invokeOnCompletion {
            realTimeJob = null
        }
    }

    fun enableReceive() {
        if (receiveJob != null) {
            return
        }
        receiveJob = viewModelScope.launch(context = Dispatchers.IO) {
            _fileTransferViewState.emit(value = FileTransferViewState.Idle)
            var serverSocket: ServerSocket? = null
            var clientInputStream: InputStream? = null
            var objectInputStream: ObjectInputStream? = null
            var fileOutputStream: FileOutputStream? = null
            try {
                _fileTransferViewState.emit(value = FileTransferViewState.Connecting)
                log(log = "开启 Socket")
                serverSocket = ServerSocket()
                serverSocket.bind(InetSocketAddress(Constants.PORT))
                serverSocket.reuseAddress = true
                serverSocket.soTimeout = 30000
                log(log = "socket accept，三十秒内如果未成功则断开链接")
                val client = serverSocket.accept()
                _fileTransferViewState.emit(value = FileTransferViewState.Receiving())
                clientInputStream = client.getInputStream()
                objectInputStream = ObjectInputStream(clientInputStream)
                val fileTransfer = objectInputStream.readObject() as FileTransfer
                log(log = "连接成功，待接收的文件: $fileTransfer")
                log(log = "开始传输文件")
                val buffer = ByteArray(1024 * 100)
                log(log = "文件接收成功")
            } catch (e: Throwable) {
                log(log = "文件接收异常: " + e.message)
            } finally {
                serverSocket?.close()
                clientInputStream?.close()
                objectInputStream?.close()
                fileOutputStream?.close()
            }
        }
        receiveJob?.invokeOnCompletion {
            receiveJob = null
        }

    }
    private suspend fun log(log: String) {
        Log.d(TAG, log)
        _log.emit(value = log)
    }


    companion object {
        const val TAG = "FileSenderViewModel"
    }

}