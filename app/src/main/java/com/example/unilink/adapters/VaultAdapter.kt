package com.example.unilink.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.unilink.databinding.ItemVaultResourceBinding
import com.example.unilink.models.VaultItem

class VaultAdapter(
    private val items: List<VaultItem>,
    private val onUnlockClick: (VaultItem) -> Unit
) : RecyclerView.Adapter<VaultAdapter.VaultViewHolder>() {

    class VaultViewHolder(val binding: ItemVaultResourceBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VaultViewHolder {
        val binding = ItemVaultResourceBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return VaultViewHolder(binding)
    }

    override fun onBindViewHolder(holder: VaultViewHolder, position: Int) {
        val item = items[position]
        holder.binding.tvTitle.text = item.title
        holder.binding.tvType.text = item.type
        holder.binding.tvUploader.text = "by ${item.uploaderName}"
        holder.binding.tvPrice.text = "${item.price} 🪙"
        
        holder.binding.btnUnlock.setOnClickListener { onUnlockClick(item) }
    }

    override fun getItemCount(): Int = items.size
}
