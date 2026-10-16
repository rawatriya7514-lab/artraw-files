package com.artraw.files.util

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings

object PermissionsHelper {

    fun hasStoragePermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            val read = context.checkSelfPermission(android.Manifest.permission.READ_EXTERNAL_STORAGE)
            val write = context.checkSelfPermission(android.Manifest.permission.WRITE_EXTERNAL_STORAGE)
            read == PackageManager.PERMISSION_GRANTED && write == PackageManager.PERMISSION_GRANTED
        }
    }

    /**
     * Shows Play Store required justification dialog before requesting All Files Access.
     */
    fun showPermissionJustification(activity: Activity, onConfirm: () -> Unit) {
        AlertDialog.Builder(activity)
            .setTitle("Storage Access Required")
            .setMessage(
                "ArtFiles needs 'All Files Access' permission to browse, copy, move, " +
                "encrypt, and manage files on your device. " +
                "This is required for core file manager functionality.\n\n" +
                "You will be redirected to system settings to grant this permission."
            )
            .setPositiveButton("Grant Access") { _, _ ->
                onConfirm.invoke()
            }
            .setNegativeButton("Cancel", null)
            .setCancelable(false)
            .show()
    }

    fun requestStoragePermission(activity: Activity, requestCode: Int = 1001) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                    data = Uri.parse("package:${activity.packageName}")
                }
                activity.startActivityForResult(intent, requestCode)
            } catch (e: Exception) {
                // Fallback to general manage storage screen if package uri fails
                val intent = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                activity.startActivityForResult(intent, requestCode)
            }
        } else {
            activity.requestPermissions(
                arrayOf(
                    android.Manifest.permission.READ_EXTERNAL_STORAGE,
                    android.Manifest.permission.WRITE_EXTERNAL_STORAGE
                ),
                requestCode
            )
        }
    }
}
