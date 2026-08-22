package com.example.unilink.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.unilink.databinding.ItemDuelPeerBinding
import com.example.unilink.models.DuelPeer

class DuelPeerAdapter(
    private val peers: List<DuelPeer>,
    private val onDuelClick: (DuelPeer) -> Unit
) : RecyclerView.Adapter<DuelPeerAdapter.DuelViewHolder>() {

    class DuelViewHolder(val binding: ItemDuelPeerBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DuelViewHolder {
        val binding = ItemDuelPeerBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return DuelViewHolder(binding)
    }

    override fun onBindViewHolder(holder: DuelViewHolder, position: Int) {
        val peer = peers[position]
        holder.binding.tvName.text = peer.name
        holder.binding.tvMajor.text = peer.major
        holder.binding.tvAvatarLetter.text = peer.initial
        
        holder.binding.btnDuel.setOnClickListener {
            onDuelClick(peer)
        }
    }

    override fun getItemCount(): Int = peers.size
}
