package com.example.mediaserverapp

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
import androidx.core.app.NotificationCompat
import java.net.Inet4Address
import java.net.NetworkInterface
import java.util.*
import java.util.concurrent.Executors

class MediaStreamingService : Service() {

    private lateinit var webSocketServer: LocalWebSocketServer
    private lateinit var audioRecord: AudioRecord
    private val executor = Executors.newSingleThreadExecutor()
    private var isStreaming = false
    private val serverPort = 8080

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        startWebSocketServer()
        initializeAudioRecorder()
        createNotificationChannel()
        startForeground(1, createNotification())
    }

    private fun startWebSocketServer() {
        webSocketServer = LocalWebSocketServer(serverPort)
        webSocketServer.start()
        Log.d("WebSocket", "服务器运行在: ws://${getLocalIpAddress()}:$serverPort")
    }

    private fun initializeAudioRecorder() {
        val sampleRate = 44100
        val bufferSize = AudioRecord.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )

        audioRecord = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            bufferSize
        )
    }

    private fun startAudioStream() {
        executor.execute {
            audioRecord.startRecording()
            val buffer = ByteArray(1024)
            isStreaming = true
            while (isStreaming) {
                val bytesRead = audioRecord.read(buffer, 0, buffer.size)
                if (bytesRead > 0) {
                    webSocketServer.broadcast(buffer.copyOf(bytesRead))
                }
            }
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

    private fun createNotificationChannel() {
        if (true) {  // Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
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
            .setSmallIcon(android.R.drawable.ic_dialog_info) // 或使用自定义图标
            .build()
    }

    override fun onDestroy() {
        isStreaming = false
        audioRecord.stop()
        audioRecord.release()
        webSocketServer.stop()
        executor.shutdown()
        super.onDestroy()
    }
}