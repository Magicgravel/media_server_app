package com.example.mediaserverapp

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.TextView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager

class MainActivity : AppCompatActivity() {
    private val requiredPermissions = arrayOf(
        Manifest.permission.CAMERA,
        Manifest.permission.RECORD_AUDIO
    )
    
    private val PERMISSION_REQUEST_CODE = 1001
    
    private var isVideoOn = false
    private var isAudioOn = false

    private lateinit var toggleVideoButton: Button
    private lateinit var toggleAudioButton: Button
    private lateinit var videoStatusDot: View
    private lateinit var audioStatusDot: View
    
    private lateinit var logTextView: TextView
    private val logReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val message = intent.getStringExtra("log_message")
            if (message != null) {
                appendLog(message)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        toggleVideoButton = findViewById(R.id.toggleVideoButton)
        toggleAudioButton = findViewById(R.id.toggleAudioButton)
        videoStatusDot = findViewById(R.id.videoStatusDot)
        audioStatusDot = findViewById(R.id.audioStatusDot)
        logTextView = findViewById(R.id.logTextView)

        LocalBroadcastManager.getInstance(this).registerReceiver(
            logReceiver, IntentFilter("MediaStreamingLog")
        )

        toggleVideoButton.setOnClickListener {
            if (isVideoOn) {
                handleServiceAction("STOP_VIDEO")
                isVideoOn = false
                toggleVideoButton.text = "Start Video"
                updateDot(videoStatusDot, false)
            } else {
                handleServiceAction("START_VIDEO")
                isVideoOn = true
                toggleVideoButton.text = "Stop Video"
                updateDot(videoStatusDot, true)
            }
        }

        toggleAudioButton.setOnClickListener {
            if (isAudioOn) {
                handleServiceAction("STOP_AUDIO")
                isAudioOn = false
                toggleAudioButton.text = "Start Audio"
                updateDot(audioStatusDot, false)
            } else {
                handleServiceAction("START_AUDIO")
                isAudioOn = true
                toggleAudioButton.text = "Stop Audio"
                updateDot(audioStatusDot, true)
            }
        }
    }

    private fun updateDot(dot: View, isOn: Boolean) {
        val drawableRes = if (isOn) R.drawable.dot_green else R.drawable.dot_red
        dot.setBackgroundResource(drawableRes)
    }

    private fun handleServiceAction(actionStr: String) {
        if (checkPermissions()) {
            val serviceIntent = Intent(this, MediaStreamingService::class.java)
            serviceIntent.action = actionStr
            ContextCompat.startForegroundService(this, serviceIntent)
        } else {
            requestPermissions()
        }
    }

    private fun appendLog(msg: String) {
        runOnUiThread {
            logTextView.append(msg + "\n")
        }
    }

    private fun checkPermissions(): Boolean {
        return requiredPermissions.all {
            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun requestPermissions() {
        ActivityCompat.requestPermissions(this, requiredPermissions, PERMISSION_REQUEST_CODE)
    }
    
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSION_REQUEST_CODE && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
            // Permissions granted
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        LocalBroadcastManager.getInstance(this).unregisterReceiver(logReceiver)
    }
}
