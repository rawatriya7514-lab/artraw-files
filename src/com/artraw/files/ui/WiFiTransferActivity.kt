package com.artraw.files.ui

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import com.artraw.files.R
import java.io.File
import java.io.IOException
import java.net.InetAddress
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.util.Enumeration

/**
 * WiFiTransferActivity — Wireless file transfer between phone and PC.
 * Starts a simple HTTP server on the device so you can browse/download files
 * from any computer on the same WiFi network.
 */
class WiFiTransferActivity : Activity() {

    private lateinit var tvIpAddress: TextView
    private lateinit var tvPort: TextView
    private lateinit var tvStatus: TextView
    private lateinit var btnStartStop: Button

    private var serverSocket: ServerSocket? = null
    private var serverThread: Thread? = null
    private var isRunning = false

    private val handler = Handler(Looper.getMainLooper())

    private val PORT = 8080

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_wifi_transfer)

        tvIpAddress = findViewById(R.id.tvWifiIp)
        tvPort = findViewById(R.id.tvWifiPort)
        tvStatus = findViewById(R.id.tvWifiStatus)
        btnStartStop = findViewById(R.id.btnWifiStartStop)

        tvPort.text = "Port: $PORT"

        // Get local IP
        val ip = getLocalIpAddress()
        tvIpAddress.text = "IP: $ip"

        findViewById<View>(R.id.btnWifiBack).setOnClickListener {
            stopServer()
            finish()
        }

        btnStartStop.setOnClickListener {
            if (isRunning) {
                stopServer()
            } else {
                startServer()
            }
        }

        // Auto-check permission
        if (!android.os.Environment.isExternalStorageManager()) {
            com.artraw.files.util.PermissionsHelper.showPermissionJustification(this) {
                com.artraw.files.util.PermissionsHelper.requestStoragePermission(this, 1002)
            }
        }
    }

    private fun startServer() {
        try {
            serverSocket = ServerSocket(PORT)
            isRunning = true
            serverThread = Thread {
                while (isRunning && !serverSocket!!.isClosed) {
                    try {
                        val client: Socket = serverSocket!!.accept()
                        handleClient(client)
                    } catch (e: IOException) {
                        if (isRunning) {
                            handler.post {
                                tvStatus.text = "Server error: ${e.message}"
                                tvStatus.setTextColor(android.graphics.Color.parseColor("#D50000"))
                            }
                        }
                    }
                }
            }
            serverThread!!.start()

            btnStartStop.text = "Stop Server"
            tvStatus.text = "Server running on $PORT"
            tvStatus.setTextColor(android.graphics.Color.parseColor("#00C853"))

            Toast.makeText(this, "WiFi Transfer started. Open http://${getLocalIpAddress()}:$PORT on your PC", Toast.LENGTH_LONG).show()
        } catch (e: IOException) {
            Toast.makeText(this, "Failed to start server: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun stopServer() {
        isRunning = false
        try {
            serverSocket?.close()
        } catch (e: IOException) {}
        serverSocket = null
        serverThread = null

        btnStartStop.text = "Start Server"
        tvStatus.text = "Server stopped"
        tvStatus.setTextColor(android.graphics.Color.parseColor("#9AA5B1"))
    }

    private fun handleClient(client: Socket) {
        try {
            val input = client.inputStream
            val output = client.outputStream

            // Read request line
            val buffer = ByteArray(1024)
            var len = input.read(buffer)
            if (len <= 0) return

            val request = String(buffer, 0, len)
            val lines = request.split("\r\n")
            var path = "/"

            // Parse GET /path HTTP/1.1
            if (lines.isNotEmpty()) {
                val parts = lines[0].split(" ")
                if (parts.size >= 2) {
                    path = parts[1]
                }
            }

            // Build response
            val response = buildResponse(path)

            val responseBytes = response.toByteArray()
            val header = "HTTP/1.1 200 OK\r\n" +
                "Content-Type: text/html\r\n" +
                "Content-Length: ${responseBytes.size}\r\n" +
                "Connection: close\r\n\r\n"

            output.write(header.toByteArray())
            output.write(responseBytes)
            output.flush()
        } catch (e: Exception) {
            // Ignore client errors
        } finally {
            try { client.close() } catch (e: Exception) {}
        }
    }

    private fun buildResponse(path: String): String {
        val rootDir = android.os.Environment.getExternalStorageDirectory()
        val target = File(rootDir, path.removePrefix("/"))

        val sb = StringBuilder()
        sb.append("<!DOCTYPE html>")
        sb.append("<html><head>")
        sb.append("<title>ArtFiles WiFi Transfer</title>")
        sb.append("<style>")
        sb.append("body{font-family:sans-serif;background:#121833;color:#F0F4F8;padding:20px;}")
        sb.append("h1{color:#4ECDC4;}")
        sb.append("table{width:100%;border-collapse:collapse;margin-top:20px;}")
        sb.append("th,td{padding:12px;border-bottom:1px solid #2A3354;text-align:left;}")
        sb.append("tr:hover{background:#1A2240;}")
        sb.append("a{color:#00B4D8;text-decoration:none;}")
        sb.append("a:hover{text-decoration:underline;}")
        sb.append(".btn{display:inline-block;padding:8px 16px;background:#4ECDC4;color:#0A0E27;border-radius:6px;text-decoration:none;margin:4px 0;}")
        sb.append(".btn-danger{background:#D50000;color:#fff;}")
        sb.append(".up{color:#8338EC;}")
        sb.append(".dir{color:#4ECDC4;}")
        sb.append(".file{color:#F0F4F8;}")
        sb.append(".size{color:#9AA5B1;font-size:0.9em;}")
        sb.append("</style>")
        sb.append("</head><body>")

        sb.append("<h1>📁 ArtFiles WiFi Transfer</h1>")
        sb.append("<p>IP: <b>${getLocalIpAddress()}:$PORT</b></p>")

        // Navigation
        val currentPath = target.absolutePath
        val rootPath = rootDir.absolutePath
        sb.append("<p>Current: <code>$currentPath</code></p>")

        // Go up button
        if (currentPath != rootPath) {
            val parent = target.parentFile
            val parentPath = if (parent != null) parent.absolutePath.removePrefix(rootPath + "/") else ""
            sb.append("<a href='/$parentPath' class='btn'>⬆ Parent Folder</a> ")
        }

        sb.append("<a href='/' class='btn btn-danger'>🏠 Home</a>")

        sb.append("<table>")
        sb.append("<tr><th>Name</th><th>Type</th><th>Size</th><th>Action</th></tr>")

        // List directories first
        val dirs = target.listFiles()?.filter { it.isDirectory }?.sortedBy { it.name } ?: emptyList()
        val files = target.listFiles()?.filter { !it.isDirectory }?.sortedBy { it.name } ?: emptyList()

        for (dir in dirs) {
            val relPath = dir.absolutePath.removePrefix(rootPath + "/")
            sb.append("<tr>")
            sb.append("<td><span class='dir'>📁</span> <a href='/$relPath'>${dir.name}</a></td>")
            sb.append("<td>Folder</td>")
            sb.append("<td class='size'>-</td>")
            sb.append("<td><a href='/$relPath' class='btn'>Open</a></td>")
            sb.append("</tr>")
        }

        for (file in files) {
            val relPath = file.absolutePath.removePrefix(rootPath + "/")
            val size = formatSize(file.length())
            sb.append("<tr>")
            sb.append("<td><span class='file'>📄</span> ${file.name}</td>")
            sb.append("<td>File</td>")
            sb.append("<td class='size'>$size</td>")
            sb.append("<td><a href='/$relPath?download=1' class='btn btn-danger'>Download</a></td>")
            sb.append("</tr>")
        }

        sb.append("</table>")

        if (path.contains("download=1")) {
            // Handle file download
            return buildFileDownloadResponse(target)
        }

        sb.append("</body></html>")
        return sb.toString()
    }

    private fun buildFileDownloadResponse(file: File): String {
        // This is a simplified response - for full download support we'd stream the file
        // For now, return HTML indicating download link
        return "<html><body><h1>File Download</h1><p>${file.name}</p><p>Size: ${formatSize(file.length())}</p><p>Right-click and 'Save link as' to download.</p></body></html>"
    }

    private fun formatSize(bytes: Long): String {
        return when {
            bytes >= 1024L * 1024 * 1024 -> "%.2f GB".format(bytes / (1024.0 * 1024.0 * 1024.0))
            bytes >= 1024L * 1024 -> "%.2f MB".format(bytes / (1024.0 * 1024.0))
            bytes >= 1024L -> "%.2f KB".format(bytes / 1024.0)
            else -> "$bytes B"
        }
    }

    private fun getLocalIpAddress(): String {
        try {
            val en: Enumeration<NetworkInterface> = NetworkInterface.getNetworkInterfaces()
            while (en.hasMoreElements()) {
                val intf = en.nextElement()
                val enumIpAddr: Enumeration<InetAddress> = intf.inetAddresses
                while (enumIpAddr.hasMoreElements()) {
                    val inetAddress = enumIpAddr.nextElement()
                    if (!inetAddress.isLoopbackAddress && inetAddress.isSiteLocalAddress) {
                        return inetAddress.hostAddress
                    }
                }
            }
        } catch (e: Exception) {}
        return "Unknown"
    }

    override fun onDestroy() {
        stopServer()
        super.onDestroy()
    }
}