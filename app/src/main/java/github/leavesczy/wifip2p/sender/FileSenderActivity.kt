package github.leavesczy.wifip2p.sender

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.net.wifi.WpsInfo
import android.net.wifi.p2p.WifiP2pConfig
import android.net.wifi.p2p.WifiP2pDevice
import android.net.wifi.p2p.WifiP2pInfo
import android.net.wifi.p2p.WifiP2pManager
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.tmk.newfast.widgets.LogTextView
import github.leavesczy.wifip2p.BaseActivity
import github.leavesczy.wifip2p.DeviceAdapter
import github.leavesczy.wifip2p.DirectActionListener
import github.leavesczy.wifip2p.DirectBroadcastReceiver
import github.leavesczy.wifip2p.OnItemClickListener
import github.leavesczy.wifip2p.R
import github.leavesczy.wifip2p.common.FileTransferViewState
import github.leavesczy.wifip2p.utils.RealTimeAudioRecorder
import github.leavesczy.wifip2p.utils.WifiP2pUtils
import kotlinx.coroutines.launch

/**
 * @Author: leavesCZY
 * @Desc:
 */
@SuppressLint("NotifyDataSetChanged")
class FileSenderActivity : BaseActivity() {

    private val tvDeviceState by lazy { findViewById<TextView>(R.id.tvDeviceState) }
    private val tvConnectionStatus by lazy { findViewById<TextView>(R.id.tvConnectionStatus) }
    private val btnDisconnect by lazy { findViewById<Button>(R.id.btnDisconnect) }
    private val btnChooseFile by lazy { findViewById<Button>(R.id.btnChooseFile) }
    private val rvDeviceList by lazy { findViewById<RecyclerView>(R.id.rvDeviceList) }
    private val tvLog by lazy { findViewById<LogTextView>(R.id.tvLog) }
    private val btnDirectDiscover by lazy { findViewById<Button>(R.id.btnDirectDiscover) }
    private val btnSendSoundFile by lazy { findViewById<Button>(R.id.btnSendSoundFile) }
    private val btnSendRecordSound by lazy { findViewById<Button>(R.id.btnSendRecordSound) }
    private val btnEnableReceive by lazy { findViewById<Button>(R.id.btnEnableReceive) }
    private val fileSenderViewModel by viewModels<FileSenderViewModel>()
    private val realTimeAudioRecorder by lazy { RealTimeAudioRecorder() }


    private val wifiP2pDeviceList = mutableListOf<WifiP2pDevice>()
    private val deviceAdapter = DeviceAdapter(wifiP2pDeviceList)
    private var broadcastReceiver: BroadcastReceiver? = null
    private lateinit var wifiP2pManager: WifiP2pManager
    private lateinit var wifiP2pChannel: WifiP2pManager.Channel
    private var wifiP2pInfo: WifiP2pInfo? = null
    private var wifiP2pEnabled = false
    private val directActionListener = object : DirectActionListener {

        override fun wifiP2pEnabled(enabled: Boolean) {
            wifiP2pEnabled = enabled
        }

        override fun onConnectionInfoAvailable(wifiP2pInfo: WifiP2pInfo) {
            dismissLoadingDialog()
            wifiP2pDeviceList.clear()
            deviceAdapter.notifyDataSetChanged()
            btnDisconnect.isEnabled = true
            btnChooseFile.isEnabled = true
            log("onConnectionInfoAvailable")
            log("onConnectionInfoAvailable groupFormed: " + wifiP2pInfo.groupFormed)
            log("onConnectionInfoAvailable isGroupOwner: " + wifiP2pInfo.isGroupOwner)
            log("onConnectionInfoAvailable getHostAddress: " + wifiP2pInfo.groupOwnerAddress.hostAddress)
            val stringBuilder = StringBuilder()
            stringBuilder.append("\n")
            stringBuilder.append("是否群主：")
            stringBuilder.append(if (wifiP2pInfo.isGroupOwner) "是群主" else "非群主")
            stringBuilder.append("\n")
            stringBuilder.append("群主IP地址：")
            stringBuilder.append(wifiP2pInfo.groupOwnerAddress.hostAddress)
            tvConnectionStatus.text = stringBuilder
            if (wifiP2pInfo.groupFormed && !wifiP2pInfo.isGroupOwner) {
                this@FileSenderActivity.wifiP2pInfo = wifiP2pInfo
            }
        }

        override fun onDisconnection() {
            log("onDisconnection")
            btnDisconnect.isEnabled = false
            btnChooseFile.isEnabled = false
            wifiP2pDeviceList.clear()
            deviceAdapter.notifyDataSetChanged()
            tvConnectionStatus.text = null
            wifiP2pInfo = null
            showToast("处于非连接状态")
        }

        override fun onSelfDeviceAvailable(wifiP2pDevice: WifiP2pDevice) {
            log("onSelfDeviceAvailable")
            log("DeviceName: " + wifiP2pDevice.deviceName)
            log("DeviceAddress: " + wifiP2pDevice.deviceAddress)
            log("Status: " + wifiP2pDevice.status)
            val log = "deviceName：" + wifiP2pDevice.deviceName + "\n" +
                    "deviceAddress：" + wifiP2pDevice.deviceAddress + "\n" +
                    "deviceStatus：" + WifiP2pUtils.getDeviceStatus(wifiP2pDevice.status)
            tvDeviceState.text = log
        }

        override fun onPeersAvailable(wifiP2pDeviceList: Collection<WifiP2pDevice>) {
            log("onPeersAvailable :" + wifiP2pDeviceList.size)
            this@FileSenderActivity.wifiP2pDeviceList.clear()
            this@FileSenderActivity.wifiP2pDeviceList.addAll(wifiP2pDeviceList)
            deviceAdapter.notifyDataSetChanged()
            dismissLoadingDialog()
        }

        override fun onChannelDisconnected() {
            log("onChannelDisconnected")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_file_sender)
        initView()
        initDevice()
        initEvent()
    }

    @SuppressLint("MissingPermission")
    private fun initView() {
        supportActionBar?.title = "发送端"
        btnDisconnect.setOnClickListener {
            disconnect()
        }

        btnSendSoundFile.setOnClickListener {
            val ipAddress = wifiP2pInfo?.groupOwnerAddress?.hostAddress
            log("准备向IP $ipAddress 发送本地音频文件")
            if (!ipAddress.isNullOrBlank()) {
                fileSenderViewModel.sendLocalAudioFile(ipAddress)
            }
        }

        btnEnableReceive.setOnClickListener {
            log("启用接收功能")
            fileSenderViewModel.enableReceive()
        }

        btnSendRecordSound.setOnClickListener {
            val ipAddress = wifiP2pInfo?.groupOwnerAddress?.hostAddress
            log("准备向IP $ipAddress 发送实时录音")
            if (ipAddress.isNullOrBlank()) return@setOnClickListener
            val audioDataCallback = object : RealTimeAudioRecorder.AudioDataCallback {
                override fun onAudioData(data: ByteArray, size: Int) {
                    Log.d(TAG, "收到了录音数据 size:$size")
                    fileSenderViewModel.sendRealTimeAudio(ipAddress, data)
                }
            }
            realTimeAudioRecorder.startRecording(audioDataCallback)
        }
        btnDirectDiscover.setOnClickListener {
            if (!wifiP2pEnabled) {
                showToast("需要先打开Wifi")
                return@setOnClickListener
            }
            showLoadingDialog(message = "正在搜索附近设备")
            wifiP2pDeviceList.clear()
            deviceAdapter.notifyDataSetChanged()
            wifiP2pManager.discoverPeers(wifiP2pChannel, object : WifiP2pManager.ActionListener {
                override fun onSuccess() {
                    showToast("discoverPeers Success")
                    dismissLoadingDialog()
                }

                override fun onFailure(reasonCode: Int) {
                    showToast("discoverPeers Failure：$reasonCode")
                    dismissLoadingDialog()
                }
            })
        }
        deviceAdapter.onItemClickListener = object : OnItemClickListener {
            override fun onItemClick(position: Int) {
                val wifiP2pDevice = wifiP2pDeviceList.getOrNull(position)
                if (wifiP2pDevice != null) {
                    connect(wifiP2pDevice = wifiP2pDevice)
                }
            }
        }
        rvDeviceList.adapter = deviceAdapter
        rvDeviceList.layoutManager = object : LinearLayoutManager(this) {
            override fun canScrollVertically(): Boolean {
                return false
            }
        }
    }

    private fun initDevice() {
        val mWifiP2pManager = getSystemService(WIFI_P2P_SERVICE) as? WifiP2pManager
        if (mWifiP2pManager == null) {
            finish()
            return
        }
        wifiP2pManager = mWifiP2pManager
        wifiP2pChannel = mWifiP2pManager.initialize(this, mainLooper, directActionListener)
        broadcastReceiver =
            DirectBroadcastReceiver(mWifiP2pManager, wifiP2pChannel, directActionListener)
        ContextCompat.registerReceiver(
            this,
            broadcastReceiver,
            DirectBroadcastReceiver.getIntentFilter(),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
    }

    private fun initEvent() {
        lifecycleScope.launch {
            fileSenderViewModel.fileTransferViewState.collect {
                when (it) {
                    FileTransferViewState.Idle -> {
                        clearLog()
                        dismissLoadingDialog()
                    }

                    FileTransferViewState.Connecting -> {
                        showLoadingDialog(message = "")
                    }

                    is FileTransferViewState.Receiving -> {
                        showLoadingDialog(message = "")
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
        lifecycleScope.launch {
            fileSenderViewModel.log.collect {
                log(it)
            }
        }
    }

    override fun onDestroy() {
        realTimeAudioRecorder.stopRecording()
        super.onDestroy()
        if (broadcastReceiver != null) {
            unregisterReceiver(broadcastReceiver)
        }
    }

    @SuppressLint("MissingPermission")
    private fun connect(wifiP2pDevice: WifiP2pDevice) {
        val wifiP2pConfig = WifiP2pConfig()
        wifiP2pConfig.deviceAddress = wifiP2pDevice.deviceAddress
        wifiP2pConfig.wps.setup = WpsInfo.PBC
        showLoadingDialog(message = "正在连接，deviceName: " + wifiP2pDevice.deviceName)
        showToast("正在连接，deviceName: " + wifiP2pDevice.deviceName)
        wifiP2pManager.connect(
            wifiP2pChannel,
            wifiP2pConfig,
            object : WifiP2pManager.ActionListener {
                override fun onSuccess() {
                    log("connect onSuccess")
                }

                override fun onFailure(reason: Int) {
                    showToast("连接失败 $reason")
                    dismissLoadingDialog()
                }
            }
        )
    }

    private fun disconnect() {
        wifiP2pManager.cancelConnect(wifiP2pChannel, object : WifiP2pManager.ActionListener {
            override fun onFailure(reasonCode: Int) {
                log("cancelConnect onFailure:$reasonCode")
            }

            override fun onSuccess() {
                log("cancelConnect onSuccess")
                tvConnectionStatus.text = null
                btnDisconnect.isEnabled = false
                btnChooseFile.isEnabled = false
            }
        })
        wifiP2pManager.removeGroup(wifiP2pChannel, null)
    }

    @SuppressLint("SetTextI18n")
    private fun log(log: String) {
        tvLog.appendText(log)
    }

    private fun clearLog() {
        tvLog.text = ""
    }

    companion object {
        const val TAG = "FileSenderActivity"
    }


}