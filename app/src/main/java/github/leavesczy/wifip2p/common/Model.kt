package github.leavesczy.wifip2p.common

import java.io.File
import java.io.Serializable

/**
 * @Author: leavesCZY
 * @Date: 2024/4/1 11:18
 * @Desc:
 */
data class FileTransfer(val fileName: String) : Serializable

sealed class FileTransferViewState {

    data object Idle : FileTransferViewState()

    data object Connecting : FileTransferViewState()

    data class Receiving(val bytes:ByteArray = byteArrayOf()) : FileTransferViewState()

    data class Success(val file: File) : FileTransferViewState()

    data class Failed(val throwable: Throwable) : FileTransferViewState()

}