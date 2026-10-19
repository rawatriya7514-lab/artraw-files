package com.artraw.files.util

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object Formatters {

    fun formatFileSize(bytes: Long): String {
        if (bytes <= 0) return "0 B"
        val units = arrayOf("B", "KB", "MB", "GB", "TB", "PB")
        var value = bytes.toDouble()
        var unitIndex = 0
        while (value >= 1024.0 && unitIndex < units.size - 1) {
            value /= 1024.0
            unitIndex++
        }
        return if (unitIndex == 0) {
            "${bytes} B"
        } else if (value >= 100.0) {
            String.format(Locale.US, "%.1f %s", value, units[unitIndex])
        } else {
            String.format(Locale.US, "%.2f %s", value, units[unitIndex])
        }
    }

    fun formatDate(timestamp: Long): String {
        if (timestamp <= 0) return "-"
        val sdf = SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault())
        return sdf.format(Date(timestamp))
    }

    fun formatShortDate(timestamp: Long): String {
        if (timestamp <= 0) return "-"
        val sdf = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
        return sdf.format(Date(timestamp))
    }
}
