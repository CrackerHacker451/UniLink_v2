package com.example.unilink.models

data class FocusRoom(
    val id: String = "",
    val name: String = "",
    val color: String = "#22D3EE",
    val description: String = "",
    val currentParticipants: Int = 0,
    val isLive: Boolean = false
)
