package github.leavesczy.wifip2p.receiver

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.net.wifi.p2p.WifiP2pDevice
import android.net.wifi.p2p.WifiP2pInfo
import android.net.wifi.p2p.WifiP2pManager
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import coil.load
import com.blankj.utilcode.util.FileIOUtils
import com.tmk.newfast.widgets.LogTextView
import github.leavesczy.wifip2p.BaseActivity
import github.leavesczy.wifip2p.DirectActionListener
import github.leavesczy.wifip2p.DirectBroadcastReceiver
import github.leavesczy.wifip2p.R
import github.leavesczy.wifip2p.common.FileTransferViewState
import github.leavesczy.wifip2p.utils.MyFileUtils
import github.leavesczy.wifip2p.utils.PCMStreamPlayer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.util.concurrent.Executors
import kotlin.coroutines.resume

/**
 * @Author: leavesCZY
 * @Desc:
 */
class FileReceiverActivity : BaseActivity() {

    private val tvLog by lazy { findViewById<LogTextView>(R.id.tvLog) }
    private val btnCreateGroup by lazy { findViewById<Button>(R.id.btnCreateGroup) }
    private val btnRemoveGroup by lazy { findViewById<Button>(R.id.btnRemoveGroup) }
    private val btnStartReceive by lazy { findViewById<Button>(R.id.btnStartReceive) }
    private val fileReceiverViewModel by viewModels<FileReceiverViewModel>()
    private lateinit var wifiP2pManager: WifiP2pManager
    private lateinit var wifiP2pChannel: WifiP2pManager.Channel
    private var connectionInfoAvailable = false
    private var broadcastReceiver: BroadcastReceiver? = null
    private val pcmStreamPlayer: PCMStreamPlayer by lazy { PCMStreamPlayer() }
    private val singleThreadExecutor = Executors.newSingleThreadExecutor()

    private val directActionListener = object : DirectActionListener {
        override fun wifiP2pEnabled(enabled: Boolean) {
            log("wifiP2pEnabled: $enabled")
        }

        override fun onConnectionInfoAvailable(wifiP2pInfo: WifiP2pInfo) {
            log("onConnectionInfoAvailable")
            log("isGroupOwner：" + wifiP2pInfo.isGroupOwner)
            log("groupFormed：" + wifiP2pInfo.groupFormed)
            if (wifiP2pInfo.groupFormed && wifiP2pInfo.isGroupOwner) {
                connectionInfoAvailable = true
            }
        }

        override fun onDisconnection() {
            connectionInfoAvailable = false
            log("onDisconnection")
        }

        override fun onSelfDeviceAvailable(wifiP2pDevice: WifiP2pDevice) {
            log("onSelfDeviceAvailable: \n$wifiP2pDevice")
        }

        override fun onPeersAvailable(wifiP2pDeviceList: Collection<WifiP2pDevice>) {
            log("onPeersAvailable , size:" + wifiP2pDeviceList.size)
            for (wifiP2pDevice in wifiP2pDeviceList) {
                log("wifiP2pDevice: $wifiP2pDevice")
            }
        }

        override fun onChannelDisconnected() {
            log("onChannelDisconnected")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_file_receiver)
        pcmStreamPlayer.start()

        initView()
        initDevice()
        initEvent()
    }

    private fun initView() {
        supportActionBar?.title = "文件接收端"
        btnCreateGroup.setOnClickListener {
            createGroup()
        }
        btnRemoveGroup.setOnClickListener {
            removeGroup()
        }
        btnStartReceive.setOnClickListener {
            fileReceiverViewModel.startListener()
        }

    }

    private fun initDevice() {
        val mWifiP2pManager = getSystemService(WIFI_P2P_SERVICE) as? WifiP2pManager
        if (mWifiP2pManager == null) {
            finish()
            return
        }
        wifiP2pManager = mWifiP2pManager
        wifiP2pChannel = wifiP2pManager.initialize(this, mainLooper, directActionListener)
        broadcastReceiver = DirectBroadcastReceiver(
            wifiP2pManager = wifiP2pManager,
            wifiP2pChannel = wifiP2pChannel,
            directActionListener = directActionListener
        )
        ContextCompat.registerReceiver(
            this,
            broadcastReceiver,
            DirectBroadcastReceiver.getIntentFilter(),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
    }

    private fun initEvent() {
        lifecycleScope.launch {
            launch {
                fileReceiverViewModel.fileTransferViewState.collect {
                    when (it) {
                        FileTransferViewState.Idle -> {
                            clearLog()
                            dismissLoadingDialog()
                        }

                        FileTransferViewState.Connecting -> {
                            showLoadingDialog(message = "正在连接")
                        }

                        is FileTransferViewState.Receiving -> {
//                            showLoadingDialog(message = "")
//                            Log.e("TAG","收到大小"+it.bytes.size)
                            singleThreadExecutor.execute {
                                if(it.bytes.isNotEmpty()) pcmStreamPlayer.write(it.bytes)
                            }
                        }

                        is FileTransferViewState.Success -> {
                            dismissLoadingDialog()
                        }

                        is FileTransferViewState.Failed -> {
                            dismissLoadingDialog()
                        }
                    }
                }
            }
            launch {
                fileReceiverViewModel.log.collect {
                    log(it)
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (broadcastReceiver != null) {
            unregisterReceiver(broadcastReceiver)
        }
        pcmStreamPlayer.stop()
        removeGroup()
    }

    @SuppressLint("MissingPermission")
    private fun createGroup() {
        lifecycleScope.launch {
            removeGroupIfNeed()
            wifiP2pManager.createGroup(wifiP2pChannel, object : WifiP2pManager.ActionListener {
                override fun onSuccess() {
                    val log = "createGroup onSuccess"
                    log(log = log)
                    showToast(message = log)
                }

                override fun onFailure(reason: Int) {
                    val log = "createGroup onFailure: $reason"
                    log(log = log)
                    showToast(message = log)
                }
            })
        }
    }

    private fun removeGroup() {
        lifecycleScope.launch {
            removeGroupIfNeed()
        }
    }

    @SuppressLint("MissingPermission")
    private suspend fun removeGroupIfNeed() {
        return suspendCancellableCoroutine { continuation ->
            wifiP2pManager.requestGroupInfo(wifiP2pChannel) { group ->
                if (group == null) {
                    continuation.resume(value = Unit)
                } else {
                    wifiP2pManager.removeGroup(wifiP2pChannel,
                        object : WifiP2pManager.ActionListener {
                            override fun onSuccess() {
                                val log = "removeGroup onSuccess"
                                log(log = log)
                                showToast(message = log)
                                continuation.resume(value = Unit)
                            }

                            override fun onFailure(reason: Int) {
                                val log = "removeGroup onFailure: $reason"
                                log(log = log)
                                showToast(message = log)
                                continuation.resume(value = Unit)
                            }
                        })
                }
            }
        }
    }

    private fun log(log: String) {
        tvLog.appendText(log)
    }

    private fun clearLog() {
        tvLog.text = ""
    }

}