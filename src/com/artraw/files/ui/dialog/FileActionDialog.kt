package com.artraw.files.ui.dialog

import android.app.Activity
import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import com.artraw.files.R
import com.artraw.files.model.FileCategory
import com.artraw.files.model.FileItem
import com.artraw.files.preview.FileViewerHelper
import com.artraw.files.ui.BrowserActivity
import com.artraw.files.util.Formatters
import java.io.File

object FileActionDialog {

    fun show(
        activity: Activity,
        item: FileItem,
        onOpen: (() -> Unit)? = null
    ) {
        val dialogView = LayoutInflater.from(activity).inflate(R.layout.dialog_file_quick_actions, null)
        val dialog = AlertDialog.Builder(activity)
            .setView(dialogView)
            .create()

        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        val ivIcon = dialogView.findViewById<ImageView>(R.id.ivDialogFileIcon)
        val tvName = dialogView.findViewById<TextView>(R.id.tvDialogFileName)
        val tvPath = dialogView.findViewById<TextView>(R.id.tvDialogFilePath)
        val tvMeta = dialogView.findViewById<TextView>(R.id.tvDialogFileMeta)

        val btnOpen = dialogView.findViewById<View>(R.id.btnDialogActionOpen)
        val tvOpenText = dialogView.findViewById<TextView>(R.id.tvDialogActionOpenText)
        val btnCopyPath = dialogView.findViewById<View>(R.id.btnDialogActionCopyPath)
        val btnShare = dialogView.findViewById<View>(R.id.btnDialogActionShare)
        val btnDetails = dialogView.findViewById<View>(R.id.btnDialogActionDetails)
        val btnClose = dialogView.findViewById<View>(R.id.btnDialogClose)

        tvName.text = item.name
        tvPath.text = item.file.absolutePath

        if (item.isDirectory) {
            tvOpenText.text = activity.getString(R.string.action_open_folder)
            tvMeta.text = "Folder • ${Formatters.formatDate(item.file.lastModified())}"
            ivIcon.setImageResource(R.drawable.ic_folder)
            btnShare.visibility = View.GONE
        } else {
            tvOpenText.text = activity.getString(R.string.action_open)
            val catLabel = item.category.name.lowercase().replaceFirstChar { it.uppercase() }
            tvMeta.text = "$catLabel • ${Formatters.formatFileSize(item.file.length())} • ${Formatters.formatDate(item.file.lastModified())}"
            btnShare.visibility = View.VISIBLE
            val iconRes = when (item.category) {
                FileCategory.IMAGES -> R.drawable.ic_image
                FileCategory.VIDEOS -> R.drawable.ic_video
                FileCategory.AUDIO -> R.drawable.ic_audio
                FileCategory.DOCUMENTS -> R.drawable.ic_document
                FileCategory.ARCHIVES -> R.drawable.ic_archive
                FileCategory.APKS -> R.drawable.ic_apk
                else -> R.drawable.ic_file
            }
            ivIcon.setImageResource(iconRes)
        }

        // Action: OPEN
        btnOpen.setOnClickListener {
            dialog.dismiss()
            if (onOpen != null) {
                onOpen()
            } else {
                if (item.isDirectory) {
                    val intent = Intent(activity, BrowserActivity::class.java).apply {
                        putExtra("initial_path", item.file.absolutePath)
                    }
                    activity.startActivity(intent)
                } else {
                    FileViewerHelper.openFile(activity, item)
                }
            }
        }

        // Action: COPY PATH
        btnCopyPath.setOnClickListener {
            copyPathToClipboard(activity, item.file.absolutePath)
            dialog.dismiss()
        }

        // Action: SHARE
        btnShare.setOnClickListener {
            dialog.dismiss()
            FileViewerHelper.shareFiles(activity, listOf(item))
        }

        // Action: DETAILS
        btnDetails.setOnClickListener {
            dialog.dismiss()
            FileViewerHelper.showFileDetails(activity, item)
        }

        // Action: COMPRESS or EXTRACT
        val btnCompress = dialogView.findViewById<View>(R.id.btnDialogActionCompress)
        val ivCompressIcon = dialogView.findViewById<ImageView>(R.id.ivDialogCompressIcon)
        val tvCompressText = dialogView.findViewById<TextView>(R.id.tvDialogCompressText)

        val isArchive = item.extension.lowercase() in listOf("zip", "tar", "gz", "tgz", "jar")
        if (isArchive) {
            ivCompressIcon?.setImageResource(R.drawable.ic_unarchive)
            tvCompressText?.text = "Extract"
        } else {
            ivCompressIcon?.setImageResource(R.drawable.ic_compress)
            tvCompressText?.text = "Compress"
        }

        btnCompress?.setOnClickListener {
            dialog.dismiss()
            if (isArchive) {
                val intent = Intent(activity, com.artraw.files.ui.ArchiveViewerActivity::class.java).apply {
                    putExtra(com.artraw.files.ui.ArchiveViewerActivity.EXTRA_FILE_PATH, item.file.absolutePath)
                }
                activity.startActivity(intent)
            } else {
                // Compress single file or folder to ZIP
                val defaultZipName = "${item.file.nameWithoutExtension}.zip"
                val destZip = File(item.file.parentFile, defaultZipName)
                val progressDialog = android.app.ProgressDialog(activity).apply {
                    setTitle("Compressing to ZIP")
                    setMessage("Creating $defaultZipName...")
                    setProgressStyle(android.app.ProgressDialog.STYLE_HORIZONTAL)
                    max = 100
                    setCancelable(false)
                    show()
                }

                java.util.concurrent.Executors.newSingleThreadExecutor().execute {
                    val ok = com.artraw.files.archive.ArchiveManager.compress(listOf(item.file), destZip) { pct, _ ->
                        activity.runOnUiThread { progressDialog.progress = pct }
                    }
                    activity.runOnUiThread {
                        progressDialog.dismiss()
                        if (ok) {
                            Toast.makeText(activity, "Created: $defaultZipName", Toast.LENGTH_LONG).show()
                            if (activity is BrowserActivity) {
                                activity.refreshCurrentDirectory()
                            }
                        } else {
                            Toast.makeText(activity, "Compression failed.", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
        }

        // Action: EDIT (for Images)
        val btnEdit = dialogView.findViewById<View>(R.id.btnDialogActionEdit)
        val dividerEdit = dialogView.findViewById<View>(R.id.dividerDialogEdit)
        if (item.category == FileCategory.IMAGES) {
            btnEdit.visibility = View.VISIBLE
            dividerEdit?.visibility = View.VISIBLE
            btnEdit.setOnClickListener {
                dialog.dismiss()
                val intent = Intent(activity, com.artraw.files.ui.ImageEditorActivity::class.java).apply {
                    putExtra("image_path", item.file.absolutePath)
                }
                activity.startActivity(intent)
            }
        } else {
            btnEdit.visibility = View.GONE
            dividerEdit?.visibility = View.GONE
        }

        // Action: RENAME
        val btnRename = dialogView.findViewById<View>(R.id.btnDialogActionRename)
        btnRename.setOnClickListener {
            dialog.dismiss()
            val input = android.widget.EditText(activity).apply {
                setText(item.file.name)
                setSingleLine(true)
                setPadding(40, 30, 40, 30)
            }
            AlertDialog.Builder(activity)
                .setTitle("Rename File")
                .setView(input)
                .setPositiveButton("Rename") { _, _ ->
                    val newName = input.text.toString().trim()
                    if (newName.isNotEmpty() && newName != item.file.name) {
                        val newFile = File(item.file.parentFile, newName)
                        if (item.file.renameTo(newFile)) {
                            Toast.makeText(activity, "Renamed to $newName", Toast.LENGTH_SHORT).show()
                            if (activity is BrowserActivity) {
                                activity.refreshCurrentDirectory()
                            }
                        } else {
                            Toast.makeText(activity, "Rename failed", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
                .setNegativeButton(R.string.btn_cancel, null)
                .show()
        }

        // Action: DELETE
        val btnDelete = dialogView.findViewById<View>(R.id.btnDialogActionDelete)
        btnDelete.setOnClickListener {
            dialog.dismiss()
            AlertDialog.Builder(activity)
                .setTitle(R.string.dialog_delete_title)
                .setMessage("Permanently delete ${item.file.name}?")
                .setPositiveButton(R.string.action_delete) { _, _ ->
                    val ok = item.file.delete()
                    if (ok) {
                        Toast.makeText(activity, "Deleted ${item.file.name}", Toast.LENGTH_SHORT).show()
                        if (activity is BrowserActivity) {
                            activity.refreshCurrentDirectory()
                        }
                    } else {
                        Toast.makeText(activity, "Cannot delete file", Toast.LENGTH_SHORT).show()
                    }
                }
                .setNegativeButton(R.string.btn_cancel, null)
                .show()
        }

        btnClose.setOnClickListener {
            dialog.dismiss()
        }

        dialog.show()
    }

    fun copyPathToClipboard(context: Context, path: String) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("File Path", path)
        clipboard.setPrimaryClip(clip)
        val msg = context.getString(R.string.path_copied_toast, path)
        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
    }
}
