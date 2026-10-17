package com.artraw.files.ui

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.text.InputType
import android.view.View
import android.view.ViewGroup
import android.widget.AbsListView
import android.widget.BaseAdapter
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import com.artraw.files.R
import com.artraw.files.util.EdgeToEdgeHelper
import com.artraw.files.util.Formatters
import com.artraw.files.util.VaultManager
import java.io.File

class VaultActivity : Activity() {

    private lateinit var listViewVault: ListView
    private var vaultItems: List<VaultManager.VaultItem> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        VaultManager.init(this)
        setContentView(R.layout.activity_vault)

        EdgeToEdgeHelper.applyEdgeToEdge(
            activity = this,
            topSpacers = listOf(findViewById(R.id.statusBarSpacerVault)),
            bottomStickyViews = listOf(findViewById(R.id.bottomNavigationVault))
        )

        initViews()
        loadVault()
    }

    override fun onResume() {
        super.onResume()
        if (!VaultManager.hasPin()) {
            showSetPinDialog()
        } else if (!VaultManager.isVaultEnabled()) {
            showUnlockDialog()
        } else {
            loadVault()
        }
    }

    private fun initViews() {
        listViewVault = findViewById(R.id.listViewVault)

        findViewById<View>(R.id.btnVaultBack).setOnClickListener { finish() }

        findViewById<View>(R.id.btnNavHomeVault).setOnClickListener {
            startActivity(Intent(this, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
            })
            finish()
        }

        findViewById<View>(R.id.btnNavBrowseVault).setOnClickListener {
            startActivity(Intent(this, BrowserActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
            })
            finish()
        }

        findViewById<View>(R.id.btnNavSettingsVault).setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
    }

    private fun loadVault() {
        vaultItems = VaultManager.getVaultItems()
        val count = vaultItems.size
        val sizeStr = Formatters.formatFileSize(VaultManager.vaultSize())
        findViewById<TextView>(R.id.tvVaultInfo).text = "$count item${if (count != 1) "s" else ""} • $sizeStr"

        if (count == 0) {
            findViewById<View>(R.id.layoutVaultEmpty).visibility = View.VISIBLE
            listViewVault.visibility = View.GONE
        } else {
            findViewById<View>(R.id.layoutVaultEmpty).visibility = View.GONE
            listViewVault.visibility = View.VISIBLE
            listViewVault.adapter = VaultAdapter(vaultItems)
        }
    }

    private fun showSetPinDialog() {
        val input = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
            hint = "Set PIN (4+ digits)"
            setSingleLine()
        }
        AlertDialog.Builder(this)
            .setTitle("Set Vault PIN")
            .setMessage("Create a PIN to secure your vault")
            .setView(input)
            .setCancelable(false)
            .setPositiveButton("Set PIN") { _, _ ->
                val pin = input.text.toString().trim()
                if (pin.length < 4) {
                    Toast.makeText(this, "PIN must be at least 4 digits", Toast.LENGTH_SHORT).show()
                    showSetPinDialog()
                } else {
                    VaultManager.setPin(pin)
                    VaultManager.setVaultLocked(true)
                    loadVault()
                }
            }
            .show()
    }

    private fun showUnlockDialog() {
        val input = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
            hint = "Enter PIN"
            setSingleLine()
        }
        AlertDialog.Builder(this)
            .setTitle("Unlock Vault")
            .setMessage("Enter your vault PIN")
            .setView(input)
            .setCancelable(false)
            .setPositiveButton("Unlock") { _, _ ->
                val pin = input.text.toString().trim()
                if (VaultManager.verifyPin(pin)) {
                    VaultManager.setVaultLocked(true)
                    loadVault()
                } else {
                    Toast.makeText(this, "Invalid PIN", Toast.LENGTH_SHORT).show()
                    showUnlockDialog()
                }
            }
            .setNegativeButton("Lock Vault") { _, _ ->
                VaultManager.setVaultLocked(false)
                finish()
            }
            .show()
    }

    private inner class VaultAdapter(private val items: List<VaultManager.VaultItem>) : BaseAdapter() {
        override fun getCount(): Int = items.size
        override fun getItem(position: Int): VaultManager.VaultItem = items[position]
        override fun getItemId(position: Int): Long = position.toLong()

        override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
            val view: View
            val holder: VaultHolder

            if (convertView == null) {
                view = LinearLayout(this@VaultActivity).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = android.view.Gravity.CENTER_VERTICAL
                    setPadding(16, 14, 16, 14)
                    setBackgroundResource(R.drawable.bg_glass_file_item)
                    val params = AbsListView.LayoutParams(
                        AbsListView.LayoutParams.MATCH_PARENT,
                        AbsListView.LayoutParams.WRAP_CONTENT
                    )
                    layoutParams = params
                }
                val container = view as LinearLayout

                val icon = ImageView(this@VaultActivity).apply {
                    layoutParams = LinearLayout.LayoutParams(40, 40)
                    scaleType = ImageView.ScaleType.CENTER_INSIDE
                    setImageResource(R.drawable.ic_file)
                    setColorFilter(android.graphics.Color.parseColor("#8A2BE2"))
                }
                container.addView(icon)

                val info = LinearLayout(this@VaultActivity).apply {
                    orientation = LinearLayout.VERTICAL
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                        marginStart = 12
                    }
                }
                container.addView(info)

                val nameTv = TextView(this@VaultActivity).apply {
                    textSize = 15f
                    setTextColor(android.graphics.Color.parseColor("#F0F4F8"))
                    setSingleLine()
                    ellipsize = android.text.TextUtils.TruncateAt.END
                }
                info.addView(nameTv)

                val metaTv = TextView(this@VaultActivity).apply {
                    textSize = 12f
                    setTextColor(android.graphics.Color.parseColor("#9AA5B1"))
                    setSingleLine()
                    ellipsize = android.text.TextUtils.TruncateAt.END
                }
                info.addView(metaTv)

                holder = VaultHolder(icon = icon, nameTv = nameTv, metaTv = metaTv)
                view.tag = holder
            } else {
                view = convertView
                holder = view.tag as VaultHolder
            }

            val item = items[position]
            holder.nameTv.text = item.originalName
            holder.metaTv.text = Formatters.formatFileSize(item.size)

            view.setOnClickListener {
                showItemOptions(item, position)
            }

            return view
        }
    }

    private fun showItemOptions(item: VaultManager.VaultItem, position: Int) {
        AlertDialog.Builder(this)
            .setTitle(item.originalName)
            .setMessage(item.originalPath)
            .setPositiveButton("Restore") { _, _ ->
                if (VaultManager.restoreFromVault(item.vaultPath)) {
                    Toast.makeText(this, "Restored to original location", Toast.LENGTH_SHORT).show()
                    loadVault()
                } else {
                    Toast.makeText(this, "Restore failed", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Delete from Vault") { _, _ ->
                if (VaultManager.deleteFromVault(item.vaultPath)) {
                    Toast.makeText(this, "Deleted from vault", Toast.LENGTH_SHORT).show()
                    loadVault()
                } else {
                    Toast.makeText(this, "Delete failed", Toast.LENGTH_SHORT).show()
                }
            }
            .setNeutralButton("Cancel", null)
            .show()
    }
}

class VaultHolder(
    val icon: ImageView,
    val nameTv: TextView,
    val metaTv: TextView
)
