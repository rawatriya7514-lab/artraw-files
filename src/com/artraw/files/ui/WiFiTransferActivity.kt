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
            com.artraw.files.util.PermissionsHelper.requestStoragePermission(this, 1002)
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

            val rootDir = android.os.Environment.getExternalStorageDirectory()
            val cleanPath = java.net.URLDecoder.decode(path.substringBefore("?"), "UTF-8")
            val target = File(rootDir, cleanPath.removePrefix("/"))

            // Direct binary download streaming
            if (path.contains("download=1") && target.exists() && target.isFile) {
                val header = "HTTP/1.1 200 OK\r\n" +
                    "Content-Type: application/octet-stream\r\n" +
                    "Content-Disposition: attachment; filename=\"${target.name}\"\r\n" +
                    "Content-Length: ${target.length()}\r\n" +
                    "Connection: close\r\n\r\n"
                output.write(header.toByteArray())
                target.inputStream().use { it.copyTo(output) }
                output.flush()
                return
            }

            // Build response
            val response = buildResponse(cleanPath)

            val responseBytes = response.toByteArray(Charsets.UTF_8)
            val header = "HTTP/1.1 200 OK\r\n" +
                "Content-Type: text/html; charset=utf-8\r\n" +
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
        sb.append("<html lang='en'><head>")
        sb.append("<meta charset='utf-8'><meta name='viewport' content='width=device-width, initial-scale=1.0'>")
        sb.append("<title>Artfiles — WiFi Storage Hub</title>")
        sb.append("<style>")
        sb.append("*{box-sizing:border-box;margin:0;padding:0;}")
        sb.append("body{font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',Roboto,Helvetica,sans-serif;background:#090D16;color:#E2E8F0;min-height:100vh;padding:24px;line-height:1.5;}")
        sb.append(".container{max-width:1100px;margin:0 auto;}")
        sb.append(".header{display:flex;align-items:center;justify-content:space-between;background:rgba(17,24,39,0.8);border:1px solid rgba(255,255,255,0.08);backdrop-filter:blur(16px);border-radius:16px;padding:20px 24px;margin-bottom:24px;box-shadow:0 10px 30px -5px rgba(0,0,0,0.4);}")
        sb.append(".brand{display:flex;align-items:center;gap:12px;}")
        sb.append(".brand-icon{width:40px;height:40px;border-radius:10px;background:linear-gradient(135deg,#4F46E5,#8B5CF6);display:flex;align-items:center;justify-content:center;font-size:20px;box-shadow:0 0 20px rgba(79,70,229,0.4);}")
        sb.append(".brand-title{font-size:20px;font-weight:700;color:#F8FAFC;letter-spacing:-0.02em;}")
        sb.append(".brand-desc{font-size:12px;color:#94A3B8;}")
        sb.append(".ip-badge{background:rgba(79,70,229,0.15);border:1px solid rgba(79,70,229,0.3);color:#A5B4FC;padding:6px 14px;border-radius:9999px;font-size:13px;font-weight:600;font-family:monospace;}")
        sb.append(".toolbar{display:flex;align-items:center;justify-content:space-between;margin-bottom:16px;flex-wrap:wrap;gap:12px;}")
        sb.append(".breadcrumbs{display:flex;align-items:center;gap:8px;font-size:13px;color:#94A3B8;overflow-x:auto;padding:8px 0;}")
        sb.append(".breadcrumbs a{color:#A5B4FC;text-decoration:none;font-weight:500;padding:4px 8px;border-radius:6px;background:rgba(255,255,255,0.03);border:1px solid rgba(255,255,255,0.06);}")
        sb.append(".breadcrumbs a:hover{background:rgba(79,70,229,0.2);color:#fff;}")
        sb.append(".nav-btns{display:flex;gap:8px;}")
        sb.append(".btn{display:inline-flex;align-items:center;gap:6px;padding:8px 16px;border-radius:10px;font-size:13px;font-weight:600;text-decoration:none;transition:all 0.15s ease;cursor:pointer;border:none;}")
        sb.append(".btn-primary{background:#4F46E5;color:#fff;box-shadow:0 4px 14px rgba(79,70,229,0.3);}")
        sb.append(".btn-primary:hover{background:#4338CA;transform:translateY(-1px);}")
        sb.append(".btn-subtle{background:rgba(255,255,255,0.05);color:#E2E8F0;border:1px solid rgba(255,255,255,0.1);}")
        sb.append(".btn-subtle:hover{background:rgba(255,255,255,0.1);}")
        sb.append(".btn-download{background:rgba(16,185,129,0.15);color:#34D399;border:1px solid rgba(16,185,129,0.3);padding:6px 12px;border-radius:8px;font-size:12px;font-weight:600;}")
        sb.append(".btn-download:hover{background:#10B981;color:#fff;}")
        sb.append(".card{background:rgba(17,24,39,0.7);border:1px solid rgba(255,255,255,0.07);border-radius:16px;overflow:hidden;box-shadow:0 8px 32px rgba(0,0,0,0.3);}")
        sb.append("table{width:100%;border-collapse:collapse;font-size:14px;}")
        sb.append("th{padding:14px 18px;text-align:left;color:#94A3B8;font-size:11px;font-weight:700;text-transform:uppercase;letter-spacing:0.05em;border-bottom:1px solid rgba(255,255,255,0.06);background:rgba(15,23,42,0.4);}")
        sb.append("td{padding:12px 18px;border-bottom:1px solid rgba(255,255,255,0.04);vertical-align:middle;}")
        sb.append("tr:hover td{background:rgba(255,255,255,0.02);}")
        sb.append(".file-link{display:flex;align-items:center;gap:12px;color:#F1F5F9;text-decoration:none;font-weight:500;}")
        sb.append(".file-link:hover{color:#818CF8;}")
        sb.append(".type-badge{padding:3px 8px;border-radius:6px;font-size:11px;font-weight:600;display:inline-block;}")
        sb.append(".badge-folder{background:rgba(59,130,246,0.15);color:#60A5FA;}")
        sb.append(".badge-file{background:rgba(148,163,184,0.12);color:#94A3B8;}")
        sb.append(".size-col{color:#94A3B8;font-family:monospace;font-size:13px;}")
        sb.append(".empty-state{text-align:center;padding:60px 20px;color:#64748B;}")
        sb.append("</style></head><body><div class='container'>")

        // Header
        sb.append("<div class='header'>")
        sb.append("<div class='brand'><div class='brand-icon'>⚡</div><div><div class='brand-title'>Artfiles WiFi Hub</div><div class='brand-desc'>Local High-Speed Android & PC File Transfer</div></div></div>")
        sb.append("<div class='ip-badge'>http://${getLocalIpAddress()}:$PORT</div>")
        sb.append("</div>")

        // Navigation
        val currentPath = target.absolutePath
        val rootPath = rootDir.absolutePath
        val relCurrent = if (currentPath != rootPath) currentPath.removePrefix(rootPath).removePrefix("/") else ""

        sb.append("<div class='toolbar'>")
        sb.append("<div class='breadcrumbs'>")
        sb.append("<a href='/'>🏠 Internal Storage</a>")
        if (relCurrent.isNotEmpty()) {
            val parts = relCurrent.split("/")
            var accumulated = ""
            for (p in parts) {
                accumulated += (if (accumulated.isEmpty()) "" else "/") + p
                sb.append("<span>/</span><a href='/$accumulated'>$p</a>")
            }
        }
        sb.append("</div>")

        sb.append("<div class='nav-btns'>")
        if (currentPath != rootPath) {
            val parent = target.parentFile
            val parentPath = if (parent != null && parent.absolutePath != rootPath) parent.absolutePath.removePrefix(rootPath + "/") else ""
            sb.append("<a href='/$parentPath' class='btn btn-subtle'>⬆ Up One Level</a>")
        }
        sb.append("<a href='/' class='btn btn-subtle'>Home</a>")
        sb.append("</div>")
        sb.append("</div>")

        sb.append("<div class='card'><table>")
        sb.append("<thead><tr><th>Name</th><th>Type</th><th>Size</th><th>Action</th></tr></thead><tbody>")

        val dirs = target.listFiles()?.filter { it.isDirectory }?.sortedBy { it.name.lowercase() } ?: emptyList()
        val files = target.listFiles()?.filter { !it.isDirectory }?.sortedBy { it.name.lowercase() } ?: emptyList()

        if (dirs.isEmpty() && files.isEmpty()) {
            sb.append("<tr><td colspan='4' class='empty-state'>Folder is empty</td></tr>")
        }

        for (dir in dirs) {
            val relPath = dir.absolutePath.removePrefix(rootPath + "/").removePrefix("/")
            sb.append("<tr>")
            sb.append("<td><a class='file-link' href='/$relPath'><span>📁</span> ${dir.name}</a></td>")
            sb.append("<td><span class='type-badge badge-folder'>Folder</span></td>")
            sb.append("<td class='size-col'>—</td>")
            sb.append("<td><a href='/$relPath' class='btn btn-subtle' style='padding:4px 10px;font-size:12px;'>Open</a></td>")
            sb.append("</tr>")
        }

        for (file in files) {
            val relPath = file.absolutePath.removePrefix(rootPath + "/").removePrefix("/")
            val size = formatSize(file.length())
            sb.append("<tr>")
            sb.append("<td><span class='file-link'><span>📄</span> ${file.name}</span></td>")
            sb.append("<td><span class='type-badge badge-file'>File</span></td>")
            sb.append("<td class='size-col'>$size</td>")
            sb.append("<td><a href='/$relPath?download=1' class='btn btn-download' download>⬇ Download</a></td>")
            sb.append("</tr>")
        }

        sb.append("</tbody></table></div>")
        sb.append("</div></body></html>")
        return sb.toString()
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