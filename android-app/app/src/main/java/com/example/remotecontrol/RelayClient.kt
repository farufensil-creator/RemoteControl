package com.example.remotecontrol

import android.util.Log
import org.java_websocket.client.WebSocketClient
import org.java_websocket.handshake.ServerHandshake
import org.json.JSONObject
import java.net.URI
import java.nio.ByteBuffer

/**
 * Connects OUT to the cloud relay server (e.g. wss://your-app.onrender.com).
 * Because the connection is outbound, this works over any internet connection —
 * no public IP or router port-forwarding needed on the phone.
 *
 * Pairing: phone registers with a 6-digit code. The viewer joins the same
 * code from anywhere in the world and gets matched to this phone.
 */
object RelayClient {

    private const val TAG = "RelayClient"
    private var client: WebSocketClient? = null

    var onCommand: ((JSONObject) -> Unit)? = null
    var isConnected: Boolean = false
        private set

    fun generateCode(): String = (100000..999999).random().toString()

    fun connect(relayUrl: String, code: String, onStatus: (String) -> Unit) {
        val uri = URI(relayUrl)
        client = object : WebSocketClient(uri) {

            override fun onOpen(handshakedata: ServerHandshake?) {
                Log.i(TAG, "Connected to relay, registering as phone with code $code")
                val handshake = JSONObject()
                handshake.put("type", "register")
                handshake.put("role", "phone")
                handshake.put("code", code)
                send(handshake.toString())
                isConnected = true
                onStatus("Connected (code: $code)")
            }

            override fun onMessage(message: String) {
                try {
                    onCommand?.invoke(JSONObject(message))
                } catch (e: Exception) {
                    Log.e(TAG, "Bad command from relay: $message", e)
                }
            }

            override fun onClose(code: Int, reason: String?, remote: Boolean) {
                Log.i(TAG, "Relay connection closed: $reason")
                isConnected = false
                onStatus("Disconnected")
            }

            override fun onError(ex: Exception?) {
                Log.e(TAG, "Relay connection error", ex)
                isConnected = false
                onStatus("Error: ${ex?.message}")
            }
        }
        client?.connect()
    }

    fun sendFrame(jpegBytes: ByteArray) {
        val c = client ?: return
        if (c.isOpen) {
            c.send(ByteBuffer.wrap(jpegBytes))
        }
    }

    fun disconnect() {
        client?.close()
        client = null
        isConnected = false
    }
}
