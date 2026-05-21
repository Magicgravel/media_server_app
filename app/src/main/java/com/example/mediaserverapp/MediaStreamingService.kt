package com.example.mediaserverapp

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.annotation.RequiresPermission
import androidx.core.app.NotificationCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import java.net.Inet4Address
import java.net.NetworkInterface
import java.util.*
import java.util.concurrent.Executors
import android.graphics.ImageFormat
import android.graphics.Rect
import android.graphics.YuvImage
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import java.io.ByteArrayOutputStream
import java.util.concurrent.ExecutorService

class MediaStreamingService : Service(), LifecycleOwner {

    private val lifecycleRegistry = LifecycleRegistry(this)
    override fun getLifecycle(): Lifecycle = lifecycleRegistry
    private lateinit var cameraExecutor: ExecutorService

    private lateinit var webSocketServer: LocalWebSocketServer
    private lateinit var audioRecord: AudioRecord
    private val executor = Executors.newSingleThreadExecutor()
    @Volatile private var isAudioStreaming = false
    @Volatile private var isVideoStreaming = false
    private val serverPort = 8080
    private var minBufferSize = 1024
    private var cameraProvider: ProcessCameraProvider? = null

    override fun onBind(intent: Intent?): IBinder? = null

    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    @RequiresApi(Build.VERSION_CODES.O)
    override fun onCreate() {
        super.onCreate()
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
        
        startWebSocketServer()
        initializeAudioRecorder()
        createNotificationChannel()
        startForeground(1, createNotification())
        
        cameraExecutor = Executors.newSingleThreadExecutor()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        when (action) {
            "START_VIDEO" -> startCameraStream()
            "STOP_VIDEO" -> stopCameraStream()
            "START_AUDIO" -> startAudioStream()
            "STOP_AUDIO" -> stopAudioStream()
        }
        return START_STICKY
    }

    private fun sendLogToActivity(msg: String) {
        val intent = Intent("MediaStreamingLog")
        intent.putExtra("log_message", msg)
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent)
        Log.d("StreamingService", msg)
    }

    private fun startWebSocketServer() {
        webSocketServer = LocalWebSocketServer(serverPort) { msg ->
            sendLogToActivity(msg)
        }
        webSocketServer.start()
        val ip = getLocalIpAddress()
        val cMsg = "WebSocket 服务器运行在: ws://$ip:$serverPort"
        sendLogToActivity(cMsg)
    }

    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    private fun initializeAudioRecorder() {
        val sampleRate = 44100
        minBufferSize = AudioRecord.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )

        audioRecord = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            minBufferSize
        )
    }

    private fun startAudioStream() {
        if (isAudioStreaming) return
        if (::audioRecord.isInitialized && audioRecord.state == AudioRecord.STATE_INITIALIZED) {
            sendLogToActivity("开始音频推流...")
            isAudioStreaming = true
            audioRecord.startRecording()
            executor.execute {
                val buffer = ByteArray(minBufferSize)
                while (isAudioStreaming) {
                    val bytesRead = audioRecord.read(buffer, 0, buffer.size)
                    if (bytesRead > 0) {
                        try {
                            webSocketServer.broadcast(buffer.copyOf(bytesRead))
                        } catch (e: Exception) {}
                    }
                }
            }
        }
    }

    private fun stopAudioStream() {
        if (!isAudioStreaming) return
        sendLogToActivity("停止音频推流...")
        isAudioStreaming = false
        if (::audioRecord.isInitialized && audioRecord.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
            audioRecord.stop()
        }
    }

    private fun getLocalIpAddress(): String? {
        return try {
            Collections.list(NetworkInterface.getNetworkInterfaces()).forEach { intf ->
                Collections.list(intf.inetAddresses).forEach { addr ->
                    if (!addr.isLoopbackAddress && addr is Inet4Address) {
                        return addr.hostAddress
                    }
                }
            }
            null
        } catch (e: Exception) {
            null
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun createNotificationChannel() {
        if (true) {
            val channel = NotificationChannel(
                "stream_channel",
                "流媒体服务",
                NotificationManager.IMPORTANCE_DEFAULT
            )
            getSystemService(NotificationManager::class.java)
                .createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        val ipAddress = getLocalIpAddress() ?: "IP获取失败"
        return NotificationCompat.Builder(this, "stream_channel")
            .setContentTitle("流媒体服务运行中")
            .setContentText("连接地址: ws://$ipAddress:$serverPort")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .build()
    }

    override fun onDestroy() {
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        stopAudioStream()
        stopCameraStream()
        
        if (::audioRecord.isInitialized) {
            audioRecord.release()
        }
        
        try {
            webSocketServer.stop()
        } catch (e: Exception) {}
        
        executor.shutdown()
        if (::cameraExecutor.isInitialized) {
            cameraExecutor.shutdown()
        }
        super.onDestroy()
    }

    private fun startCameraStream() {
        if (isVideoStreaming) return
        sendLogToActivity("开始视频推流...")
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({
            cameraProvider = cameraProviderFuture.get()
            
            val imageAnalyzer = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
                .also {
                    it.setAnalyzer(cameraExecutor) { imageProxy ->
                        if (isVideoStreaming) {
                            try {
                                val yBuffer = imageProxy.planes[0].buffer 
                                val uBuffer = imageProxy.planes[1].buffer
                                val vBuffer = imageProxy.planes[2].buffer

                                val ySize = yBuffer.remaining()
                                val uSize = uBuffer.remaining()
                                val vSize = vBuffer.remaining()

                                val nv21 = ByteArray(ySize + uSize + vSize)
                                yBuffer.get(nv21, 0, ySize)
                                vBuffer.get(nv21, ySize, vSize)
                                uBuffer.get(nv21, ySize + vSize, uSize)

                                val yuvImage = YuvImage(
                                    nv21, ImageFormat.NV21,
                                    imageProxy.width, imageProxy.height, null
                                )
                                val out = ByteArrayOutputStream()
                                yuvImage.compressToJpeg(Rect(0, 0, yuvImage.width, yuvImage.height), 50, out)
                                val jpegBytes = out.toByteArray()

                                webSocketServer.broadcast(jpegBytes)
                            } catch (e: Exception) {}
                        }
                        imageProxy.close()
                    }
                }

            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            try {
                cameraProvider?.unbindAll()
                cameraProvider?.bindToLifecycle(
                    this, cameraSelector, imageAnalyzer
                )
                isVideoStreaming = true
            } catch (exc: Exception) {
                sendLogToActivity("相机绑定失败: ${exc.message}")
                isVideoStreaming = false
            }

        }, ContextCompat.getMainExecutor(this))
    }

    private fun stopCameraStream() {
        if (!isVideoStreaming) return
        sendLogToActivity("停止视频推流...")
        isVideoStreaming = false
        try {
            cameraProvider?.unbindAll()
        } catch (e: Exception) {}
    }
}
