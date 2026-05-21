package com.example.mediaserverapp

import org.java_websocket.WebSocket
import org.java_websocket.handshake.ClientHandshake
import org.java_websocket.server.WebSocketServer
import java.net.InetSocketAddress
import java.nio.ByteBuffer

class LocalWebSocketServer(port: Int, private val logCallback: (String) -> Unit) : WebSocketServer(InetSocketAddress(port)) {

    override fun onStart() {
        logCallback("WebSocket 服务器已启动")
    }

    override fun onOpen(conn: WebSocket, handshake: ClientHandshake) {
        val msg = "新客户端连接: ${conn.remoteSocketAddress}"
        logCallback(msg)
        broadcast(msg)
    }

    override fun onClose(conn: WebSocket, code: Int, reason: String, remote: Boolean) {
        val msg = "客户端断开: ${conn.remoteSocketAddress}"
        logCallback(msg)
        broadcast(msg)
    }

    override fun onMessage(conn: WebSocket, message: String) {
        logCallback("收到消息: $message")
        when (message) {
            "start_stream" -> broadcast("SERVER: 开始流传输")
            "stop_stream" -> broadcast("SERVER: 停止流传输")
        }
    }

    override fun onMessage(conn: WebSocket, message: ByteBuffer) {
        broadcast(message)
    }

    override fun onError(conn: WebSocket?, ex: Exception) {
        logCallback("服务器错误: ${ex.message}")
    }
}
