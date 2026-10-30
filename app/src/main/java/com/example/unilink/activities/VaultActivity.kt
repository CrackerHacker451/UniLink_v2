package com.example.unilink.activities

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.EditText
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.unilink.R
import com.example.unilink.adapters.VaultAdapter
import com.example.unilink.databinding.ActivityVaultBinding
import com.example.unilink.models.VaultItem
import com.example.unilink.repository.StorageRepository
import com.example.unilink.repository.VaultRepository
import com.example.unilink.utils.ThemeUtils

class VaultActivity : AppCompatActivity() {

    private lateinit var binding: ActivityVaultBinding
    private val vaultRepository = VaultRepository()
    private val storageRepository = StorageRepository()
    private var allItems = listOf<VaultItem>()

    private val pickPdfLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let { pdfUri ->
            promptPdfDetailsAndUpload(pdfUri)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        ThemeUtils.applyTheme(this)
        super.onCreate(savedInstanceState)
        binding = ActivityVaultBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener { finish() }

        setupRecyclerView()
        setupFilters()
        setupSearch()

        binding.btnUpload.setOnClickListener {
            showUploadTypeDialog()
        }

        loadVault()
    }

    private fun showUploadTypeDialog() {
        val options = arrayOf("📄 Upload PDF Document / Notes", "📝 Create Text Note / Paper Entry")
        AlertDialog.Builder(this)
            .setTitle("Upload Resource to Vault 📚")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> pickPdfLauncher.launch("application/pdf")
                    1 -> showTextUploadDialog()
                }
            }
            .show()
    }

    private fun promptPdfDetailsAndUpload(pdfUri: Uri) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_newton_login, null)
        val etTitle = dialogView.findViewById<EditText>(R.id.etUsername).apply {
            hint = "Resource Title (e.g. Data Structures PDF)"
            setText("")
        }
        val etDesc = dialogView.findViewById<EditText>(R.id.etPassword).apply {
            hint = "Description / Subject Tag"
            inputType = android.text.InputType.TYPE_CLASS_TEXT
            setText("")
        }

        AlertDialog.Builder(this)
            .setTitle("Upload PDF Resource 📄")
            .setMessage("Share study notes or exam papers with classmates and earn +15 UniCoins!")
            .setView(dialogView)
            .setPositiveButton("Upload PDF") { _, _ ->
                val title = etTitle.text.toString().trim().ifEmpty { "Campus Study Notes (PDF)" }
                val desc = etDesc.text.toString().trim().ifEmpty { "Shared PDF study resource" }

                Toast.makeText(this, "Uploading PDF to Vault...", Toast.LENGTH_SHORT).show()
                val fileName = "${title}_${System.currentTimeMillis()}"

                storageRepository.uploadVaultPdf(pdfUri, fileName) { fileUrl ->
                    if (fileUrl != null) {
                        vaultRepository.uploadPdfItem(title, "Notes", desc, fileUrl, listOf("Notes", "PDF")) { success ->
                            if (success) {
                                Toast.makeText(this, "PDF Uploaded Successfully! +15 UniCoins 🏆", Toast.LENGTH_LONG).show()
                                loadVault()
                            } else {
                                Toast.makeText(this, "Failed to record PDF in Vault", Toast.LENGTH_SHORT).show()
                            }
                        }
                    } else {
                        Toast.makeText(this, "Failed to upload PDF file to Cloud Storage", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showTextUploadDialog() {
        val builder = AlertDialog.Builder(this)
        builder.setTitle("Upload Note Entry")
        val types = arrayOf("Notes", "Papers", "Project Reports")
        builder.setItems(types) { _, which ->
            val type = types[which]
            vaultRepository.uploadItem("Sample $type", type, 10, "Shared $type for campus study", listOf("Study")) { success ->
                if (success) {
                    Toast.makeText(this, "Uploaded $type successfully!", Toast.LENGTH_SHORT).show()
                    loadVault()
                }
            }
        }
        builder.show()
    }

    private fun setupRecyclerView() {
        binding.rvVault.apply {
            layoutManager = LinearLayoutManager(this@VaultActivity)
            setHasFixedSize(true)
            setItemViewCacheSize(25)
        }
    }

    private fun setupFilters() {
        binding.cgVaultCategories.setOnCheckedStateChangeListener { _, _ ->
            com.example.unilink.utils.HapticUtils.playSelection(binding.cgVaultCategories)
            applyFilters()
        }
    }

    private fun setupSearch() {
        binding.etSearchVault.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                applyFilters()
            }
            override fun afterTextChanged(s: android.text.Editable?) {}
        })
    }

    private fun applyFilters() {
        val query = binding.etSearchVault.text.toString().lowercase().trim()
        val checkedChipId = binding.cgVaultCategories.checkedChipId
        val typeFilter = when (checkedChipId) {
            R.id.chipNotes -> "Notes"
            R.id.chipPapers -> "Papers"
            R.id.chipProjects -> "Project Reports"
            else -> null
        }

        val filtered = allItems.filter { item ->
            val matchesType = if (typeFilter == null) true else item.type == typeFilter
            val matchesQuery = item.title.lowercase().contains(query) ||
                              item.description.lowercase().contains(query) ||
                              item.uploaderName.lowercase().contains(query)

            matchesType && matchesQuery
        }

        updateUI(filtered)
    }

    private fun loadVault() {
        vaultRepository.getAllItems { items ->
            if (isFinishing || isDestroyed) return@getAllItems
            allItems = items
            applyFilters()
        }
    }

    private fun updateUI(filteredList: List<VaultItem>) {
        if (isFinishing || isDestroyed) return
        binding.tvEmptyVault.visibility = if (filteredList.isEmpty()) View.VISIBLE else View.GONE

        binding.rvVault.adapter = VaultAdapter(filteredList) { item ->
            if (item.fileUrl.isNotEmpty()) {
                try {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(item.fileUrl))
                    startActivity(intent)
                } catch (e: Exception) {
                    Toast.makeText(this, "Opening file: ${item.fileUrl}", Toast.LENGTH_SHORT).show()
                }
            } else {
                vaultRepository.unlockItem(item) { success, message ->
                    if (isFinishing || isDestroyed) return@unlockItem
                    Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}
