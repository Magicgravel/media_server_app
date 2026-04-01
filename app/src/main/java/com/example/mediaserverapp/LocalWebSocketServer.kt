package com.example.mediaserverapp

import org.java_websocket.WebSocket
import org.java_websocket.handshake.ClientHandshake
import org.java_websocket.server.WebSocketServer
import java.net.InetSocketAddress
import java.nio.ByteBuffer

class LocalWebSocketServer(port: Int) : WebSocketServer(InetSocketAddress(port)) {

    private val clients = mutableListOf<WebSocket>()

    override fun onStart() {
        println("WebSocket 服务器已启动")
    }

    override fun onOpen(conn: WebSocket, handshake: ClientHandshake) {
        clients.add(conn)
        sendToAll("新客户端连接: ${conn.remoteSocketAddress}")
    }

    override fun onClose(conn: WebSocket, code: Int, reason: String, remote: Boolean) {
        clients.remove(conn)
        sendToAll("客户端断开: ${conn.remoteSocketAddress}")
    }

    override fun onMessage(conn: WebSocket, message: String) {
        when (message) {
            "start_stream" -> sendToAll("SERVER: 开始流传输")
            "stop_stream" -> sendToAll("SERVER: 停止流传输")
        }
    }

    override fun onMessage(conn: WebSocket, message: ByteBuffer) {
        clients.forEach { client ->
            if (client.isOpen) {
                client.send(message.array())
            }
        }
    }

    override fun onError(conn: WebSocket?, ex: Exception) {
        println("服务器错误: ${ex.message}")
    }

    private fun sendToAll(text: String) {
        clients.forEach { it.send(text) }
    }
}