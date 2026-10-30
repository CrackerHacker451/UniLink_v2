package com.example.unilink.ui

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Message
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.unilink.R
import com.example.unilink.repository.BarterRepository
import com.example.unilink.repository.BarterTradeItem
import com.google.firebase.auth.FirebaseAuth

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BarterBlockScreen(
    initialShowCreateDialog: Boolean = false,
    onNavigateBack: () -> Unit = {},
    onChatTrader: (traderUid: String, traderName: String) -> Unit = { _, _ -> }
) {
    val context = LocalContext.current
    val repository = remember { BarterRepository() }
    val currentUid = remember { FirebaseAuth.getInstance().currentUser?.uid ?: "" }

    var selectedCategory by remember { mutableStateOf("All") }
    var tradesList by remember { mutableStateOf<List<BarterTradeItem>>(emptyList()) }
    var showPostTradeDialog by remember { mutableStateOf(initialShowCreateDialog) }

    // Real-Time Trade Listening
    DisposableEffect(selectedCategory) {
        val listener = repository.observeTrades(if (selectedCategory == "All") null else selectedCategory) { items ->
            tradesList = items
        }
        onDispose { listener.remove() }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF030712))
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Top App Bar
            TopAppBar(
                title = {
                    Column {
                        Text("Barter Block 🔄", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        Text("Cashless Student Trade Market", color = Color(0xFF06B6D4), fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                },
                actions = {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0x3310B981),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF10B981))
                    ) {
                        Text("LIVE MARKET", color = Color(0xFF10B981), fontSize = 9.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )

            // Category Filter Chips
            val categories = listOf("All", "Notes", "Tech", "Snacks", "Gear")
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(categories) { cat ->
                    val isSelected = (selectedCategory == cat)
                    FilterChip(
                        selected = isSelected,
                        onClick = { selectedCategory = cat },
                        label = {
                            Text(
                                text = when (cat) {
                                    "Notes" -> "📚 Notes & Books"
                                    "Tech" -> "🎧 Tech & Gadgets"
                                    "Snacks" -> "🍕 Snacks & Coffee"
                                    "Gear" -> "🎒 Gear"
                                    else -> "🌐 All Trades"
                                },
                                color = if (isSelected) Color.Black else Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Color(0xFF06B6D4),
                            containerColor = Color(0x26160F38)
                        ),
                        border = FilterChipDefaults.filterChipBorder(
                            borderColor = if (isSelected) Color(0xFF06B6D4) else Color(0x33FFFFFF),
                            enabled = true,
                            selected = isSelected
                        )
                    )
                }
            }

            // Post Trade Action Bar
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                shape = RoundedCornerShape(16.dp),
                color = Color(0x26160F38),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0x33FFFFFF))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Have books, notes, or gadgets to trade?", color = Color.White.copy(0.8f), fontSize = 12.sp)

                    Button(
                        onClick = { showPostTradeDialog = true },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF06B6D4)),
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, tint = Color.Black, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Post Trade 🔄", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }
            }

            // Active Trades List
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(bottom = 80.dp, top = 8.dp)
            ) {
                if (tradesList.isEmpty()) {
                    item {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(20.dp),
                            color = Color(0x1A000000),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0x22FFFFFF))
                        ) {
                            Column(
                                modifier = Modifier.padding(28.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(Icons.Default.SwapHoriz, contentDescription = null, tint = Color(0xFF06B6D4), modifier = Modifier.size(44.dp))
                                Spacer(modifier = Modifier.height(12.dp))
                                Text("No Active Barter Offers in this Category 🔄", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                                Spacer(modifier = Modifier.height(6.dp))
                                Text("Be the first student to post a cashless trade offer in '$selectedCategory'!", color = Color.White.copy(0.6f), fontSize = 12.sp, textAlign = TextAlign.Center)
                            }
                        }
                    }
                } else {
                    items(tradesList, key = { it.id }) { item ->
                        BarterTradeCard(
                            item = item,
                            isAuthor = item.authorUid == currentUid,
                            onChatTrader = { onChatTrader(item.authorUid, item.authorName) },
                            onMarkTraded = {
                                repository.deleteTrade(item.id) { success ->
                                    if (success) Toast.makeText(context, "Trade marked as completed! ✅", Toast.LENGTH_SHORT).show()
                                }
                            }
                        )
                    }
                }
            }
        }
    }

    // Post Barter Trade Dialog
    if (showPostTradeDialog) {
        var offeringText by remember { mutableStateOf("") }
        var wantingText by remember { mutableStateOf("") }
        var categoryChoice by remember { mutableStateOf("Notes") }

        AlertDialog(
            onDismissRequest = { showPostTradeDialog = false },
            containerColor = Color(0xFF160F38),
            title = {
                Text("🔄 Post a Barter Trade", color = Color.White, fontWeight = FontWeight.Bold)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Trade goods or notes with campus peers (Cashless):", color = Color.White.copy(0.7f), fontSize = 12.sp)

                    OutlinedTextField(
                        value = offeringText,
                        onValueChange = { offeringText = it },
                        label = { Text("Offering (e.g. Java & DSA Notes)") },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White, unfocusedTextColor = Color.White,
                            focusedBorderColor = Color(0xFF06B6D4), unfocusedBorderColor = Color(0x33FFFFFF)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = wantingText,
                        onValueChange = { wantingText = it },
                        label = { Text("Looking for (e.g. Casio Calculator / Coffee)") },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White, unfocusedTextColor = Color.White,
                            focusedBorderColor = Color(0xFF06B6D4), unfocusedBorderColor = Color(0x33FFFFFF)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Text("Category:", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf("Notes", "Tech", "Snacks", "Gear").forEach { cat ->
                            val isSel = (categoryChoice == cat)
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = if (isSel) Color(0xFF06B6D4) else Color(0x33FFFFFF),
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable { categoryChoice = cat }
                            ) {
                                Text(
                                    cat,
                                    color = if (isSel) Color.Black else Color.White,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.padding(vertical = 6.dp)
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (offeringText.isNotBlank() && wantingText.isNotBlank()) {
                            showPostTradeDialog = false
                            repository.publishTrade(offeringText, wantingText, categoryChoice) { success ->
                                if (success) {
                                    Toast.makeText(context, "Trade posted to live Barter Block! +10 UniCoins 🏆", Toast.LENGTH_SHORT).show()
                                } else {
                                    Toast.makeText(context, "Failed to post trade", Toast.LENGTH_SHORT).show()
                                }
                            }
                        } else {
                            Toast.makeText(context, "Please fill in offering and wanting fields", Toast.LENGTH_SHORT).show()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF06B6D4))
                ) {
                    Text("Post Live Trade 🚀", color = Color.Black, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showPostTradeDialog = false }) {
                    Text("Cancel", color = Color.White.copy(0.7f))
                }
            }
        )
    }
}

@Composable
fun BarterTradeCard(
    item: BarterTradeItem,
    isAuthor: Boolean,
    onChatTrader: () -> Unit,
    onMarkTraded: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = Color(0x26160F38),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0x33FFFFFF))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Author Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AsyncImage(
                        model = item.authorAvatarUrl ?: R.drawable.ic_profile_placeholder,
                        contentDescription = "Avatar",
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .border(1.dp, Color(0xFF06B6D4), CircleShape)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(item.authorName, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        Text(item.category, color = Color(0xFF06B6D4), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                }

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color(0x2610B981)
                ) {
                    Text("AVAILABLE", color = Color(0xFF10B981), fontSize = 9.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                }
            }

            // Offering Block
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0x1A000000))
                    .border(1.dp, Color(0x22FFFFFF), RoundedCornerShape(12.dp))
                    .padding(10.dp)
            ) {
                Text("📦 OFFERING:", color = Color(0xFF06B6D4), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                Text(item.offering, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)

                Spacer(modifier = Modifier.height(6.dp))

                Text("🔄 LOOKING FOR:", color = Color(0xFFEC4899), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                Text(item.wanting, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }

            // Actions
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (!isAuthor) {
                    Button(
                        onClick = onChatTrader,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF06B6D4)),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Message, contentDescription = null, tint = Color.Black, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Chat Trader & Swap 💬", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                } else {
                    Button(
                        onClick = onMarkTraded,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981)),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Mark as Traded / Complete ✅", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }
            }
        }
    }
}
