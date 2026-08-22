package com.example.unilink.activities

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.unilink.R
import com.example.unilink.adapters.VaultAdapter
import com.example.unilink.databinding.ActivityVaultBinding
import com.example.unilink.models.VaultItem
import com.example.unilink.repository.VaultRepository
import com.example.unilink.utils.ThemeUtils

class VaultActivity : AppCompatActivity() {

    private lateinit var binding: ActivityVaultBinding
    private val vaultRepository = VaultRepository()
    private var allItems = listOf<VaultItem>()

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
            val builder = androidx.appcompat.app.AlertDialog.Builder(this)
            builder.setTitle("Upload Resource")
            val types = arrayOf("Notes", "Papers", "Project Reports")
            builder.setItems(types) { _, which ->
                val type = types[which]
                vaultRepository.uploadItem("Sample $type", type, 20, "Description for $type", listOf("Study")) {
                    if (it) {
                        Toast.makeText(this, "Uploaded $type successfully!", Toast.LENGTH_SHORT).show()
                        loadVault()
                    }
                }
            }
            builder.show()
        }

        loadVault()
    }

    private fun setupRecyclerView() {
        binding.rvVault.layoutManager = LinearLayoutManager(this)
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
        binding.tvEmptyVault.visibility = if (filteredList.isEmpty()) android.view.View.VISIBLE else android.view.View.GONE
        
        binding.rvVault.adapter = VaultAdapter(filteredList) { item ->
            vaultRepository.unlockItem(item) { success, message ->
                if (isFinishing || isDestroyed) return@unlockItem
                Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
                if (success) {
                    Toast.makeText(this, "Downloading ${item.title}...", Toast.LENGTH_LONG).show()
                }
            }
        }
    }
}
